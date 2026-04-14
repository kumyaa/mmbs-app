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

## 6. (Later — Phase A) Add the service account secret

When we reach Phase A authentication, you'll:

1. Create a Google Cloud Service Account following a guide I'll write
2. Share your spreadsheet with the service account's email (Editor access)
3. Go to your GitHub repo → *Settings → Secrets and variables → Actions → New repository secret*
4. Name: `SERVICE_ACCOUNT_JSON`
5. Value: paste the full contents of the service account JSON
6. The build workflow will inject this into the APK automatically

**Do not** commit the service account JSON to the repo. `.gitignore` already blocks it.
