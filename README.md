# VibeFinancer Android app

Tiny personal companion app for the VibeFinancer web backend. Material 3 with
dynamic colors. minSdk 35 (Android 15), targetSdk 36 (Android 16).

## Features

- Sign in against the backend (`POST /api/auth/login`, session cookie stored locally)
- Dashboard: net-worth card with 30-day trend graph, month navigation,
  per-account balances, recent transactions (all from `GET /api/accounts/detailed`
  + `GET /api/accounts/net-worth`)
- New-transaction form: expense / income / transfer, optional category/payee,
  date+time pickers (`POST /api/transactions`)
- Settings: server URL, widget account picker, sign out
- Home-screen widget: shows the selected account's balance, tap opens the
  new-transaction form (`vibefinancer://new-transaction?accountId=…`),
  Refresh button re-fetches the balance

## Backend

Needs two JSON endpoints that were added to the web app for this client:

- `POST /api/auth/login` `{email, password}` → `{sessionToken}` (also sets cookie)
- `POST /api/transactions` `{accountId, type, amount (major units), name?,
  category?, payee?, toAccountId?, exchangeRate?, createdAt?}`

Run the backend (default `http://localhost:5124`), the app defaults to
`http://10.0.2.2:5124` (emulator loopback to host). Uses cleartext HTTP —
fine for personal/dev use, do not ship this to anyone else.

## Build

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
