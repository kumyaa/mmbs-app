# MMBS Tracker (Android)

Native Android app for the MMBS committee: member management, fee tracking,
income/expense log, and HDFC bank reconciliation — backed by a Google Sheets
workbook on Google Drive.

- **Target:** Android 10+ (API 29), phones only, portrait.
- **Distribution:** private APK sideload (not Play Store).
- **Goal:** minimum APK size. No Jetpack Compose, no Play Services, no Apache POI.

See `../MMBS_PRD_v1.7.docx` for the product spec and `SETUP.md` for the one-time setup steps.

## Status

| Phase | Scope | Status |
|---|---|---|
| A0 | Build pipeline scaffold — empty app that boots | **current** |
| A | Auth + Sync + Members + Payments | pending |
| B | Transactions + CSV export + Settings | pending |
| C | Bank Reconciliation | pending |

## Build

All builds run in **GitHub Actions** — you don't need a local JDK or Android SDK.
Push to the repository (or click *Run workflow* on the Actions tab) and download
the APK from the workflow run's artifacts.

See [`SETUP.md`](SETUP.md) for first-time repository setup.
