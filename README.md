# Co-parenting Log

Local-first Android archive for SMS and MMS history with journal generation through OpenRouter.

## What it does

- Onboards the user with a guided startup flow that explains how to operate the app.
- Requests the default SMS role so SMS/MMS capture is reliable on Android 16.
- Syncs SMS and MMS from the telephony provider into local storage.
- Lets the user pick a contact, normalize 10-digit and 11-digit US numbers, and generate journals for a date range.
- Uses OpenRouter for summary and journal writing, with configurable models and API key storage.
- Shows RCS as enterprise-only: Google Messages archival on fully managed Android devices.

## Notes

- RCS capture is not available on normal consumer devices through a public API. The app surfaces this as an unsupported state instead of pretending it is synced.
- Message archive data stays on-device in this implementation.
- The first sync and journal generation require the SMS role plus runtime permissions.

## Build

Open the project in Android Studio and sync Gradle, or run `./gradlew` from the repo root after setting the Android SDK path.

If Gradle cannot find the SDK, create a `local.properties` file with:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```
