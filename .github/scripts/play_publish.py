"""
Publishes a release build to a Google Play testing track.

  play_publish.py next-version-code
      Prints one more than the highest version code Play has ever seen for the app, across
      every bundle, APK and track. Play rejects a version code it already has, so the build
      is numbered from Play itself rather than from a counter kept in the repository — no
      version-bump commit, no loop of CI runs triggering each other.

  play_publish.py upload AAB [MAPPING]
      Uploads the bundle (and the R8 mapping, so crash reports come back readable) and
      releases it to TRACK.

Environment:
  PLAY_SERVICE_ACCOUNT_JSON  service account key with release access to the app
  PACKAGE_NAME               e.g. com.familytrees.app
  TRACK                      upload only; e.g. internal
"""

import os
import sys
from typing import Optional

from play_api import Play


def next_version_code(play: Play) -> int:
    edit = play.call("POST", "edits")["id"]
    try:
        codes = [int(b["versionCode"]) for b in play.call("GET", f"edits/{edit}/bundles").get("bundles", [])]
        codes += [int(a["versionCode"]) for a in play.call("GET", f"edits/{edit}/apks").get("apks", [])]
        for track in play.call("GET", f"edits/{edit}/tracks").get("tracks", []):
            for release in track.get("releases", []):
                codes += [int(code) for code in release.get("versionCodes", [])]
    finally:
        # Read-only: the edit was only a window onto the app, and an abandoned edit would
        # otherwise sit there until it expires.
        play.request("DELETE", f"edits/{edit}")
    return max(codes, default=0) + 1


def set_release(play: Play, edit: str, track: str, version_code: int) -> None:
    def put(status: str):
        return play.request(
            "PUT",
            f"edits/{edit}/tracks/{track}",
            json={"track": track, "releases": [{"versionCodes": [str(version_code)], "status": status}]},
        )

    response = put("completed")
    # An app that has never been published can only hold draft releases; the release then
    # waits in Play Console for the first publication, which is a decision made by hand.
    if not response.ok and "draft app" in response.text:
        print("The app is still a draft in Play Console, so the release is created as a draft.")
        response = put("draft")
    Play.parse("PUT", f"edits/{edit}/tracks/{track}", response)


def commit(play: Play, edit: str) -> None:
    response = play.request("POST", f"edits/{edit}:commit")
    # Apps whose changes are held for review (for instance after a rejection) refuse an edit
    # that would send itself for review; this one is only a testing release, so it may wait.
    if not response.ok and "changesNotSentForReview" in response.text:
        response = play.request("POST", f"edits/{edit}:commit", params={"changesNotSentForReview": "true"})
    Play.parse("POST", f"edits/{edit}:commit", response)


def upload(play: Play, track: str, aab: str, mapping: Optional[str]) -> None:
    edit = play.call("POST", "edits")["id"]
    version_code = int(play.upload(f"edits/{edit}/bundles", aab)["versionCode"])
    print(f"Uploaded bundle with version code {version_code}.")

    if mapping and os.path.isfile(mapping):
        play.upload(f"edits/{edit}/apks/{version_code}/deobfuscationFiles/proguard", mapping)
        print("Uploaded the R8 mapping file.")

    set_release(play, edit, track, version_code)
    commit(play, edit)
    print(f"Released version code {version_code} to the '{track}' track.")


def main() -> None:
    play = Play(os.environ["PLAY_SERVICE_ACCOUNT_JSON"], os.environ["PACKAGE_NAME"])
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "next-version-code":
        print(next_version_code(play))
    elif command == "upload" and len(sys.argv) >= 3:
        upload(play, os.environ["TRACK"], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
