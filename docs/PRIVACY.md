# Privacy Policy — FamilyTree

**Last updated: 25 September 2026**

FamilyTree (`com.familytrees.app`) is an offline family-tree application for Android.

## The short version

**None of the family-tree data you enter ever leaves your device unless you export it
yourself.** There is no account to create, no server to sign in to, and no analytics.

The app uses two Google Firebase services, neither of which receives your tree: Firebase
Crashlytics, which sends a technical report when the app crashes, and Firebase Cloud
Messaging, which delivers occasional announcements about the app. Both are described
below.

## What the app stores, and where

Everything you enter — people, families, dates, places, events, notes, sources and the
photos you attach — is written to a database in the app's own private storage on your
device. Media files you attach are read from, or copied into, folders on the device.

Nobody but you and the apps you grant access to can read that storage. Uninstalling the
app removes it.

Your data leaves the device only when *you* choose to move it:

- exporting a GEDCOM (`.ged`) file to a location you pick,
- creating a ZIP backup and sharing or copying it,
- sharing a tree with someone else as a file.

In each case you choose the destination and the app hands the file to Android's own
sharing or file-picker interface. The app itself uploads nothing.

## Crash reports (Firebase Crashlytics)

When the app crashes, Crashlytics sends Google a report so the fault can be found and
fixed. A report contains the technical state of the crash: the stack trace (which part of
the app's code failed), the app version, the device model, the Android version, memory
and storage figures at the time, and a random identifier generated for this installation
so that repeated crashes on one device can be told apart. It does not contain the people,
names, dates, places, notes or photos in your trees. Google retains these reports for 90
days, under the Firebase terms and Google's privacy policy.

## Announcements (Firebase Cloud Messaging)

To receive announcements, the app registers with Firebase Cloud Messaging, which assigns
the installation a random registration token, and joins a single topic that every
installation shares. The token is not sent anywhere else — the app has no server — and
announcements are written to everyone alike, not to any individual. Whether they appear
on screen is up to you: they need the notification permission, and each kind has its own
channel you can switch off in Android's settings.

## Network access

The app uses the network for three things only: Google Play Billing for the single
optional in-app purchase, Crashlytics crash reports, and receiving announcements through
Cloud Messaging. It does not use the network to transmit, back up or synchronise your
family tree.

If a family tree you import contains a photo referenced by a web address rather than a
local file, the app does not download it; such a reference is shown as a link only.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Google Play Billing for the in-app purchase, crash reports, and receiving announcements. |
| `POST_NOTIFICATIONS` | Birthday reminders, generated on the device, and announcements. Optional — refusing it leaves the rest of the app fully working. |
| `com.android.vending.BILLING` | The in-app purchase. |
| `com.google.android.c2dm.permission.RECEIVE` | Receiving announcements through Firebase Cloud Messaging. |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE` | Used by Android's WorkManager to reschedule the local birthday reminder after a restart. |

Access to photos and files is requested only when you pick a file, through Android's own
document picker, and is limited to what you select.

## Purchases

The optional premium purchase is processed by Google Play. The app never sees or stores
your payment details; it records only whether the purchase is active. Google's own
privacy policy governs the transaction.

## Children

The app is not directed at children, and collects nothing about anyone beyond the crash
reports and registration token described above.

## Backups

If you have Android's system backup enabled, Android may include the app's data in your
device backup, under Google's terms and your device settings. This is a platform feature
outside the app's control; you can disable it in your device's backup settings.

## Changes

Any change to this policy will be published in this file in the app's public repository,
with the date above updated.

## Contact

Questions about this policy: open an issue in the project's GitHub repository.
