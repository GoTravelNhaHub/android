# Android release audit: GoTravelNha

Audited 2026-10-31. "Verified" means tested on this date, not read from the code alone.

## Status

The app builds, shrinks and runs as a release build, but **it can't ship to production yet**.

| # | Blocker | Owner | Fix |
|---|---|---|---|
| 1 | **No account deletion.** Play requires an in-app path and a public web page, because the app lets people create accounts. | Backend, web, app | See "Account deletion" below |
| 2 | **No upload key.** Release builds are unsigned until `keystore.properties` exists. | You | See "Signing" below |
| 3 | **Play Console setup not done:** listing, Data safety form, content rating, 512×512 icon, 1024×500 feature graphic, screenshots. | You | See "Play Console" below |
| 4 | **Closed test** (if this is a personal developer account created after 13 Nov 2023): 12 testers opted in for 14 straight days before you can apply for production. Review usually takes up to 7 days. | You | Plan at least 3 weeks |
| 5 | **Sign-in not tested to completion on a release build.** The Keycloak page opens, but no test account was used, so the code exchange, token storage and sign-out are unverified. | You | Run it once on the first signed build |

Should fix before launch, not strictly blocking:

- `https://www.gotravelnha.com/.well-known/assetlinks.json` returns 404, so shared trip links open the browser, not the app. It needs the SHA-256 of Play's **app signing** key (Play Console > Test and release > App signing), which exists only after the first upload.
- The backend routes from migration `20261030000000_trip_map_partner_links` must be deployed (`mobile/README.md`).
- No crash reporting. You'd rely on Play's Android vitals. This is a product choice; adding an SDK also changes the Data safety answers.

## Findings

| Area | Finding | Result |
|---|---|---|
| Application ID, namespace | `com.gotravelnha.app` for both | OK. Permanent after the first upload |
| Version | `versionCode` 1, `versionName` 1.0.0 | OK |
| Toolchain | Kotlin 2.2.21, Java 17 target, build on JDK 17–21 | OK |
| AGP, Gradle | Were 8.13.1 and 8.14; now 8.13.2 and 8.14.6 | Updated, see "Toolchain decision" |
| SDK levels | compileSdk 36, targetSdk 36, minSdk 26 | OK. Play has required API 36 for new apps since 31 Aug 2026 |
| Build types | debug, release. Release has R8 and resource shrinking on, not debuggable | OK |
| Authentication | AppAuth 0.11.1, Authorization Code + PKCE in a Custom Tab, client `gotravelnha-mobile`, redirect `com.gotravelnha.app:/oauth2redirect` | OK |
| Token storage | AppAuth state in SharedPreferences, encrypted with an Android Keystore key | OK |
| Endpoints | `api.gotravelnha.com`, `auth.gotravelnha.com`, `www.gotravelnha.com`, set in `gradle.properties` as HTTPS | OK |
| Secrets | Only public ids in `BuildConfig` (OIDC client id, affiliate ids) | OK |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`. No location, camera or storage | OK. A third permission in the merged manifest is AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED` |
| Cleartext | Release allowed HTTP to `10.0.2.2` and `localhost` | **Fixed:** debug builds only |
| Backup | `allowBackup=false`, but no rules for Android 12+ device transfer | **Fixed:** `data_extraction_rules.xml` |
| Logging | One `Log.w`, with a sign-in exception. No HTTP logging | **Fixed:** v/d/i/w stripped in release |
| Third-party SDKs | AppAuth, OkHttp, Coil, osmdroid, AndroidX, Compose. No ads or analytics SDKs | OK. osmdroid map tiles come from OpenStreetMap |
| Icons, label | Vector adaptive icon, with a monochrome layer. Label "GoTravelNha" (en, vi, zh strings) | OK. Check the monochrome icon: it reuses the colour foreground |
| Splash | No SplashScreen library. Android 12+ shows the system splash with the icon | Acceptable |
| 16 KB pages | One native library, `libandroidx.graphics.path.so`, from Compose. ELF alignment 0x4000, `zipalign -P 16` passes | OK, verified |
| Lint (release) | 0 errors, 29 warnings: mostly `UseKtx` and dependency-version notices | OK |

## Changes made

- `app/build.gradle.kts`, `keystore.properties.example`, `.gitignore`: optional release signing, with the key kept out of git.
- `app/src/main/res/xml/network_security_config.xml`: HTTPS only. Dev hosts moved to `app/src/debug/res/xml/`.
- `app/src/main/res/xml/data_extraction_rules.xml`, `AndroidManifest.xml`: no cloud backup or device transfer.
- `app/proguard-rules.pro`: strips v/d/i/w logging. Errors stay.
- `gradle/libs.versions.toml`, Gradle wrapper: AGP 8.13.2, Gradle 8.14.6.

Keep `app/build/outputs/mapping/release/mapping.txt` for each release, or upload it to Play, to read crash stack traces.

### Toolchain decision

Stayed on the latest AGP 8 patch instead of moving to AGP 9.5 and Gradle 9. Nothing in Play requires it, and a major build-system migration right before a first release adds risk without changing the app. Do it after launch.

## Verified

| Check | Result |
|---|---|
| `bundleRelease` | Passes. AAB 5.4 MB |
| `lintRelease`, `testDebugUnitTest`, `testReleaseUnitTest` | Pass |
| Release APK, shrunk with R8, on emulator (API 36.1) | Home, trip detail and Community load live API data. Profile opens |
| Deep link `/community/trips/...` | Opens the app's trip screen when launched directly (link verification is still pending assetlinks) |
| Log in | Opens the Keycloak page in a Custom Tab |
| Crashes, serialization or missing-class errors in logcat | None |

The smoke test used a temporary `.smoke` app ID and the debug key, applied from an init script outside the repo. It has been removed from the emulator.

Not tested: completed sign-in, sign-out, AI planner, saving trips, booking hand-off to Klook and Trip.com, a real device, a Play-signed build.

## Account deletion

Current state:

- Keycloak's self-service delete is off: the realm has no `delete_account` required action.
- `DELETE` in `backend/src/users/handlers/id.ts` is admin-only and refuses your own account. It deletes the database user only, with no Keycloak call in that handler.
- `/delete-account` on the website returns 404. The privacy page offers no deletion path that was found.

Needed:

1. A backend endpoint for the signed-in user that deletes their data and their Keycloak user, and revokes sessions.
2. A public web page (for example `/delete-account`) that names GoTravelNha, works without the app, and lets people request deletion. It can be a form or an email address. Disclose any data you keep and for how long.
3. A "Delete account" entry in the app's Profile screen. It can link to that page.
4. Add the URL to the Data safety form.

Facebook sign-in also generally requires a data deletion URL in the Facebook app settings, so the same page can serve both.

## Signing

```bash
keytool -genkeypair -v -keystore ~/keys/gotravelnha-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
cp keystore.properties.example keystore.properties   # then fill it in
./gradlew bundleRelease
```

Use Play App Signing. Back up the `.jks` and its passwords somewhere outside this repo.

## Play Console

Facts for the Data safety form, from this audit. Confirm them against the backend's actual handling:

- All traffic is encrypted in transit.
- Collected when signed in: name, email, trips and plans, AI planner input.
- The AI planner sends what people type to the backend. If that goes to an AI provider, disclose it as sharing or processing as appropriate.
- Partner clicks go through `POST /api/affiliate/click`.
- No ads SDK, no location permission.
- Privacy policy URL: `https://www.gotravelnha.com/privacy` (returns 200).
- App access: give reviewers a working test login. Most of the app needs an account.
