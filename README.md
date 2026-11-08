# Purse & Pulse

A personal Android app for habits, money (records, reports, accounts, savings, budgets, loans) and tasks.
It works fully offline. All data stays on the phone.

## Get the app
Open the **Releases** page of this repository on your phone, download the newest `.apk`, and open it to install.
To update, install the newer `.apk` over the old one. Do not uninstall first, or the data on the phone is deleted.

## Backups
In the app: Settings (gear) → Backup → **Share backup** saves a file to Google Drive, WhatsApp or anywhere you choose.
To restore: **Open backup file** (or Paste), then **Restore**.

## How it is built
`app/src/main/assets/www/index.html` is the whole app. GitHub Actions builds the APK on every push to `main`.
The app ID `com.usama.dailytracker` and the signing key never change, so renaming the app later keeps your data.
