# One-time repository setup

These steps get you from "I have an Expo account" to "I can download a working APK from GitHub Actions". You do this **once**. After that, every push produces a new APK.

## 1. Create a GitHub account

If you don't already have one: https://github.com/signup (free).

## 2. Create a private repository

1. Go to https://github.com/new
2. Repository name: `mmbs-app` (or any name you like)
3. Set it to **Private**
4. Do **not** initialise with a README / .gitignore / license (we already have those)
5. Click *Create repository*
6. On the next screen, copy the HTTPS URL — it looks like `https://github.com/<your-user>/mmbs-app.git`

## 3. Push this folder to the repository

From this `mmbs-app/` folder, run (replacing `<URL>` with the URL from step 2):

```bash
git init
git add .
git commit -m "Phase A0: build pipeline scaffold"
git branch -M main
git remote add origin <URL>
git push -u origin main
```

You'll be prompted to authenticate to GitHub. The simplest path on Windows is to install [GitHub CLI](https://cli.github.com/) and run `gh auth login` once, then `git push` picks up your credentials automatically.

## 4. Watch the first build

1. Open your repository in the browser, click the **Actions** tab
2. You should see a workflow run "Build APK" either in progress or just completed
3. Click into the run
4. At the bottom of the run summary, under **Artifacts**, you'll see:
   - `mmbs-debug` — unshrunk APK for on-device testing
   - `mmbs-release` — shrunk APK (smaller, this is what we ship)
5. Click an artifact name to download a zip containing the `.apk`

## 5. Install the APK on your Android phone

1. Extract the downloaded zip to get the `.apk`
2. Transfer it to your phone (email / Drive / USB)
3. On the phone, tap the `.apk` file and allow *Install unknown apps* for whatever app you used to transfer it
4. Install and open it — you should see the MMBS scaffold screen

This confirms the build pipeline works end-to-end. After that, every new phase will ship as a fresh APK through the same flow.

## 6. Phase A — Add the service account secret

Phase A talks to Google Sheets via a Service Account (no per-user OAuth). The
JSON key is bundled into the APK at build time from a GitHub secret.

### 6.1 Create the Service Account (one-time, in Google Cloud Console)

1. Go to https://console.cloud.google.com/ and create (or pick) a project.
2. Enable the APIs the app uses:
   - https://console.cloud.google.com/apis/library/sheets.googleapis.com → *Enable*
   - https://console.cloud.google.com/apis/library/drive.googleapis.com  → *Enable*
3. Open https://console.cloud.google.com/iam-admin/serviceaccounts → *Create service account*.
   - Name: `mmbs-app`
   - No role needed at the project level.
   - *Done*.
4. Click the new service account → *Keys* tab → *Add key → Create new key → JSON*. A `.json` file downloads.
5. Open the JSON in a text editor and copy the value of `client_email` — looks like
   `mmbs-app@<project>.iam.gserviceaccount.com`.

### 6.2 Share the spreadsheet with that email

1. Open `NGO_Accounting_System_v4.xlsx` in Google Sheets.
2. *Share* → paste the `client_email` from step 4 → role *Editor* → *Send*.

### 6.3 Add the secret to GitHub

1. Repo → *Settings → Secrets and variables → Actions → New repository secret*.
2. Name: `SERVICE_ACCOUNT_JSON`
3. Value: paste the **full JSON file contents** (including the `{`/`}` braces and
   the `private_key` field). Click *Add secret*.
4. Trigger a rebuild: *Actions → Build APK → Run workflow*.

The workflow writes the secret into `app/src/main/assets/service_account.json`
during the build. The file is never committed.

### 6.4 First-launch flow on the phone

1. Install the APK and open it.
2. Sign in with your email — must already be in the `AppUsers` sheet
   (column A = email, column B = `Treasurer` / `Committee Member` / `Auditor`).
3. Paste the spreadsheet URL or ID.
4. The app does its first sync; KPIs appear on Home.

### Security notes

- The service account JSON is embedded in the APK. Anyone who extracts the APK
  could read it and use it against the spreadsheet. Acceptable for a private
  sideload to known committee members; not acceptable for a public listing.
- Sheet edit history records every change as the service account, not the
  individual user. Sign-in email is stored locally and used only for the
  AppUsers gate.
