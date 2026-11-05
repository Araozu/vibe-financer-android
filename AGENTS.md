# AGENTS.md — VibeFinancer Android

Personal Android companion for the VibeFinancer web backend. Compose + Material 3 with
dynamic colors. minSdk 35 (Android 15), targetSdk 36 (Android 16).

## Scope

The app only mirrors the **main page** of the web app (`src/routes/(main)/+page.svelte`):
summary stats, inline "new transaction" form, balance overview chart, recent transactions
grouped by day, and the savings goal card.

- No budgets. The user does not want them in the app.
- No tabs, bottom navigation, drawer, or extra pages. The dashboard is one screen.
- Settings (server URL, widget account, sign out) and the full-screen form (widget deep
  link) are the only other screens.

## Colors

- Everything uses `MaterialTheme.colorScheme` roles, so the palette follows the wallpaper
  (Material You). Do not hardcode brand colors.
- Income green is the only fixed color (`incomeColor()` in `DashboardScreen.kt`), with a
  dark-theme variant.

## Layout of the code

- `data/` — Retrofit `ApiService`, DTOs (`Models.kt`), `FinancerRepository`, `SessionStore`.
- `ui/DashboardScreen.kt` — dashboard screen and `DashboardState`.
- `ui/DashboardSummary.kt` — pure functions: `summarize`, `groupByDay`, filters. No Compose here.
- `ui/BalanceOverviewChart.kt` — multi-line Canvas chart and account legend.
- `ui/TxnFormScreen.kt` — `TransactionForm` (shared by the dashboard and the deep-link screen).
- `widget/BalanceWidget.kt` — Glance widget; tap opens `vibefinancer://new-transaction?accountId=…`.

## Build

```sh
cd android
./gradlew :app:assembleDebug            # JDK 21, JAVA_HOME already points at it
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires `sdk.dir` in `local.properties` (not committed).

## Backend the app talks to

The emulator reaches the host at `10.0.2.2`. The app's default server URL is
`http://10.0.2.2:5124`, so the web dev server must be running on port 5124 and listening on
all interfaces:

```sh
cd /home/fernando/projects/svelte/vibe-financer && pnpm dev   # vite dev --port 5124
```

Seed user in the local DB: `test@example.com` / `test1234` (local dev only).

## T3 Android emulator flow

Use the T3 device tools. Do not use `xcrun simctl`, `idb`, or `adb` to tap, type, or take
screenshots. `adb` is fine for builds and installs.

1. **Find the device.** Call `device_list`. The AVD is `Medium_Phone_API_37.0`. Its serial
   changes depending on whether it is booted: `emulator-5554` when it's on, and the AVD name
   when it's off.
2. **Open it.** Call `device_open` with `hostId: "local"` and the `deviceId` from step 1.
   It boots the emulator if needed and returns an `agent-device` command. Keep that path in a
   shell variable. Example:
   ```sh
   AD=/home/fernando/.t3/userdata/device/targets/<target>/agent-device
   ```
   Use the command exactly as returned. Do not add `--platform`, `--serial`, `--session`, or
   `--config`. The command is already bound to the device.
3. **Launch the app.**
   ```sh
   $AD open com.vibefinancer.app
   ```
   After an `adb install`, a snapshot can show the launcher. If so, run the `open` again.
4. **Inspect.** `$AD snapshot -i` lists `@eN` refs. Refs go stale after any tap or fill, so
   re-snapshot first.
5. **Act.** `$AD fill @eN "text"`, `$AD press @eN`, `$AD scroll down 900`. Prefer refs, then
   `label="…"`.
6. **Screenshot.** `$AD screenshot /tmp/opencode/<name>.png`, then look at it with the
   read tool. Copy the images you want to keep into `android/screenshots/`.
7. **Deep links.** `$AD open com.vibefinancer.app "vibefinancer://new-transaction?accountId=<id>"`.
   The widget always sends `accountId`. A link without it is ignored by `MainActivity`.

### Troubleshooting

- `DEVICE_IN_USE` with another session ID: that session belongs to another thread. Do not
  close or reuse it. Retry once with `$AD open com.vibefinancer.app --wait 60000`. Run
  `$AD session list` to inspect (read-only).
- `DEVICE_NOT_FOUND` (`No Android device with serial emulator-5554`): the emulator was shut
  down. Call `device_open` again with the AVD name (`Medium_Phone_API_37.0`).
- The app shows a login screen: the session cookie expired. Sign in with the seed user.
- Blank dashboard or "Cannot reach server": the web dev server is not running on 5124, or
  it isn't listening on all interfaces (see above).

## Compose gotchas

- `ExposedDropdownMenu` is a member of `ExposedDropdownMenuBoxScope`. Do not import it.
  Calling it inside the `ExposedDropdownMenuBox` lambda is enough.
- `rememberSaveable` is in `androidx.compose.runtime.saveable`, not `androidx.compose.runtime`.
- Chart day labels: `chartData` dates are ISO timestamps. The series starts at day 1, so
  use the index (`i + 1`), not the date string.
- `now` for "posted vs upcoming" must be refreshed on every reload. Otherwise a transaction
  saved a minute ago shows as upcoming.

## Git

This is its own repository, nested inside `vibe-financer` (which ignores `/android`).
Commit and push from `android/`. Remote: `git@github.com:Araozu/vibe-financer-android.git`.
The repo's `core.sshcommand` already uses `~/.ssh/ai`.
