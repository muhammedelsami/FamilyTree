"""
A thin client for the Google Play Android Publisher API, shared by the release scripts.

Authenticates with a service account key passed in as a string — in CI it comes from a
repository secret and never touches the disk.
"""

import json
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD_API = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"


class Play:
    def __init__(self, key: str, package: str):
        credentials = service_account.Credentials.from_service_account_info(
            json.loads(key),
            scopes=["https://www.googleapis.com/auth/androidpublisher"],
        )
        credentials.refresh(Request())
        self.session = requests.Session()
        self.session.headers["Authorization"] = f"Bearer {credentials.token}"
        self.base = f"{API}/{package}"
        self.upload_base = f"{UPLOAD_API}/{package}"

    def request(self, method: str, path: str, **kwargs) -> requests.Response:
        return self.session.request(method, f"{self.base}/{path}", timeout=120, **kwargs)

    def call(self, method: str, path: str, *, allow_404: bool = False, **kwargs):
        response = self.request(method, path, **kwargs)
        if allow_404 and response.status_code == 404:
            return None
        return self.parse(method, path, response)

    def upload(self, path: str, file_path: str):
        with open(file_path, "rb") as body:
            response = self.session.post(
                f"{self.upload_base}/{path}",
                params={"uploadType": "media"},
                headers={"Content-Type": "application/octet-stream"},
                data=body,
                timeout=600,
            )
        return self.parse("POST", path, response)

    @staticmethod
    def parse(method: str, path: str, response: requests.Response):
        if not response.ok:
            # The API's own message is the useful part: a missing permission, an unlinked
            # payments profile or a rejected version code all come back as a 4xx that says which.
            sys.exit(f"{method} {path} failed with HTTP {response.status_code}:\n{response.text}")
        return response.json() if response.content else {}
