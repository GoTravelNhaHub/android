# GoTravelNha for Android

Kotlin + Jetpack Compose (Material 3). Package `com.gotravelnha.app`, min SDK 26, target 36.

Talks to the same API as www (`../../backend`, api.gotravelnha.com) and signs in through Keycloak
(`../../keycloak`, public client `gotravelnha-mobile`, redirect `com.gotravelnha.app:/oauth2redirect`).

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # Gradle 8.14 does not run on JDK 25
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # booking logic (same cases as backend/test/partner-link-resolver.test.ts)
./gradlew lintDebug
./gradlew assembleRelease        # needs a signing config before it can be published

# Local backend (:3002) and Keycloak (:8080) from the emulator
./gradlew installDebug -PgtnApiBaseUrl=http://10.0.2.2:3002 -PgtnOidcIssuer=http://10.0.2.2:8080/realms/gotravelnha
```

The token `iss` must match Keycloak's `KC_HOSTNAME`, so for emulator sign-in set `KC_HOSTNAME` to an address the
emulator also uses (see `keycloak/docs/integration.md`).

## Layout (`app/src/main/java/com/gotravelnha/app/`)

| Path | Role |
| --- | --- |
| `GoTravelNhaApp.kt` | Application: one `AuthManager`, `GoTravelNhaApi`, `PartnerLauncher` |
| `auth/` | AppAuth (Authorization Code + PKCE, `offline_access`); state encrypted with an Android Keystore key |
| `data/ApiClient.kt` | OkHttp + kotlinx.serialization; Bearer token, one refresh-and-retry on 401; every endpoint |
| `data/Models.kt` | Response shapes (mirror `web/types`) |
| `booking/Booking.kt` | Port of `web/utils/community-booking.ts`. Keep in step: partner-links answers are keyed by `klookQuery()` |
| `booking/PartnerLauncher.kt` | Partner links go through `POST /api/affiliate/click` (affiliate ids added server-side), Custom Tab |
| `ui/` | Tabs: Home (explore), Community, AI Planner, My Trips (plans + saved), Profile. Trip detail, trip map (osmdroid / OpenStreetMap), saved plan |

Strings: `res/values{,-vi,-zh}/strings.xml`, generated from the same en/vi/zh table as the iOS
`Localizable.xcstrings`. Add a key to both apps. Shared links to `https://www.gotravelnha.com/community/trips/<slug>`
open the trip in the app once the site serves `/.well-known/assetlinks.json` for this package.
