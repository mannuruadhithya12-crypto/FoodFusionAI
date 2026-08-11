# Phase 16 — Real Maps, Geolocation & Live Delivery Platform

## 1. Overview

Phase 16 replaces every placeholder, mock coordinate, fake ETA, and hardcoded distance in FoodFusion AI with a real, secure, production-quality geospatial architecture.

The implementation is entirely within the single `:app` Android module plus `firebase/functions/`. There are no separate driver, admin, or partner modules — these are future phases.

---

## 2. Maps Architecture

```
CustomerLocationViewModel          MapPickerFragment
        │                                 │
        ▼                                 ▼
  LocationProvider              GeocodingService (Android Geocoder)
  (FusedLocationProviderClient)         │
        │                       ResolvedAddress
        ▼                                 │
    GeoPoint ──────────────────── Address (lat/lon/geohash/placeId)
        │
        ├─── DistanceCalculator (Haversine, bearing, ETA)
        ├─── GeoHashUtil (encode, queryPrefixes, rangeForPrefix)
        └─── RoutingService (Directions API + 5-min cache + fallback)
```

### Key classes

| Class | Location | Purpose |
|---|---|---|
| `GeoPoint` | `data/location/` | Immutable coordinate pair |
| `LocationProvider` | `data/location/` | FusedLocationProviderClient wrapper |
| `GeocodingService` | `data/location/` | Coordinates ↔ address (Android Geocoder) |
| `RoutingService` | `data/location/` | Directions API + Haversine fallback + 5-min cache |
| `DistanceCalculator` | `data/location/` | Haversine distance, bearing, ETA estimate |
| `GeoHashUtil` | `data/location/` | Geohash encode + Firestore query prefix generation |
| `LocationFreshness` | `data/location/` | GPS age classification (HEALTHY/STALE/OFFLINE) |
| `MarkerAnimator` | `data/location/` | ValueAnimator smooth marker movement + heading rotation |
| `LocationPermissionHelper` | `data/location/` | Fragment permission launcher wrapper |

---

## 3. Location Architecture

### Customer flow
1. User taps "Delivering to" header on HomeFragment
2. `LocationPermissionHelper` checks current state — shows rationale dialog if needed
3. On grant → `CustomerLocationViewModel.fetchCurrentLocation()` via `LocationProvider`
4. GPS fix → `GeocodingService.reverseGeocode()` → `ResolvedAddress.displayLabel` in header
5. User can tap "Pick on Map" in AddEditAddressFragment → `MapPickerFragment`
6. Map pin dragged → debounced reverse geocode (400 ms) → address fields auto-filled
7. Confirm → `MapPickerViewModel.confirmSelection()` → `GeoHashUtil.encode()` stored on Address

### Permission states handled

| State | UI response |
|---|---|
| `Granted` | Fetch GPS immediately |
| `Denied` | Rationale dialog with "Allow" / "Not now" |
| `DeniedPermanently` | "Open Settings" dialog |
| `LocationDisabled` | "Enable Location" dialog → opens system location settings |
| `NotRequested` | No automatic prompt — only on user tap |

Location is **never** requested automatically on app start.

---

## 4. Firestore Location Schema

### `deliveryLocations/{orderId}`
```
orderId:               string
driverId:              string
latitude:              number
longitude:             number
accuracy:              number  (metres)
heading:               number  (degrees 0–360)
speed:                 number  (m/s)
timestamp:             number  (device epoch millis)
updatedAt:             Timestamp (server timestamp)
isActive:              boolean (false = delivery complete, hides from customer)
suspiciousMovementFlag: boolean
```

Written exclusively by the `updateDriverLocation` Cloud Function (Admin SDK).
Customers may READ only when `isActive == true` and the order belongs to them.

### `deliveryZones/{zoneId}`
```
zoneId:                    string
name:                      string
centerLat:                 number
centerLon:                 number
radiusKm:                  number
deliveryFee:               number (INR)
minimumOrderAmount:        number (INR)
maximumDeliveryDistanceKm: number
isActive:                  boolean
```

### `locationAlerts/{alertId}`
```
orderId:    string
driverId:   string
alerts:     string[]  (e.g. ["IMPOSSIBLE_SPEED: 145.3 km/h"])
newLat:     number
newLon:     number
detectedAt: Timestamp
resolved:   boolean
```

Written by `updateDriverLocation` / `flagSuspiciousLocation`. Admin-only access.

### Address fields added (Phase 16)
- `latitude`, `longitude` — real GPS coordinates (0.0 = not yet geocoded)
- `geohash` — 9-char geohash for Firestore range queries
- `placeId` — Google Places ID (empty when manually entered)

### Restaurant fields added (Phase 16)
- `latitude`, `longitude`, `geohash`
- `deliveryRadiusKm` (default 8 km when 0)
- `deliveryZoneId`

---

## 5. Geospatial Strategy

**Why geohash?** Firestore has no native radius query. Geohash encodes lat/lon into a string where prefixes correspond to geographic cells. A radius search becomes multiple string range queries.

**Query flow:**
1. Compute bounding box around customer (using lat/lon deltas per km)
2. Sample 9 corner/edge/centre points of the box
3. Encode each to geohash precision 5 (~4.9 km × 4.9 km per cell)
4. Deduplicate → at most 9 unique prefixes
5. For each prefix: `WHERE geohash >= prefix AND geohash <= prefix + "\uf8ff"`
6. Run all queries in parallel (Promise.all / async/await)
7. Merge results, deduplicate by ID, post-filter by exact Haversine distance

**Precision guide:**
- 5 chars → ~4.9 km (used for query prefixes)
- 9 chars → ~5 m (stored on documents for max fidelity)

---

## 6. Driver GPS Lifecycle

```
Order → OUT_FOR_DELIVERY
        │
        ▼
LocationTrackingService.startTracking(orderId)
        │
        ▼
ForegroundService started (notification shown)
        │
        ▼
LocationProvider.locationUpdates(Mode.DELIVERY)
  interval: 7 s, min interval: 5 s, min distance: 10 m
        │
        ▼
Accuracy gate: reject fixes > 150 m
        │
        ▼
updateDriverLocation Cloud Function
  ├─ Auth + ownership check
  ├─ Rate limit: max 1 write / 5 s
  ├─ Bounds validation
  ├─ Impossible speed detection (>120 km/h → alert)
  └─ Write to deliveryLocations/{orderId}
        │
        ▼
Order → DELIVERED or CANCELLED
        │
        ▼
LocationTrackingService.stopTracking()
  ├─ Cancel location updates
  ├─ deactivateDriverLocation Cloud Function (isActive = false)
  └─ stopForeground()
```

---

## 7. ETA Architecture

ETA computation has three layers, used in order of preference:

| Layer | Source | State shown |
|---|---|---|
| Road routing | Directions API via `RoutingService` | `AVAILABLE` |
| Haversine straight-line | `DistanceCalculator` | `APPROXIMATE` |
| Stored `estimatedDeliveryAt` | Order document | `APPROXIMATE` |
| No data | — | `UNAVAILABLE` |

ETA is marked `STALE` when the driver GPS fix is more than 5 minutes old.

**ETA window:** base minutes ± 20% to avoid false precision. Displayed as "12–18 min" not "15 min".

**Server-side ETA** (set in `updateOrderStatus` when transitioning to PREPARING):
```
ETA = now + (15 min prep) + (distanceKm / 25 km/h * 60) + (5 min buffer)
```
Falls back to 30 min when restaurant or customer coordinates are not yet stored.

---

## 8. Delivery Radius & Zone Validation

All delivery eligibility and fee calculation is **server-authoritative**. The Android client never determines the final delivery fee — it calls `validateDeliveryLocation` which returns:

```json
{
  "isDeliverable": true,
  "distanceKm": 4.2,
  "deliveryFee": 25,
  "reason": "",
  "zoneId": "zone_bangalore_south"
}
```

**Fee tiers** (when no zone override):
- ≤ 3 km → ₹0
- 3–6 km → ₹25
- 6–10 km → ₹49
- > 10 km → ₹79

Zone fee overrides restaurant fee. Restaurant fee overrides tier fee.

---

## 9. Security Model

### What clients can and cannot do

| Operation | Customer | Driver | Server (CF) |
|---|---|---|---|
| Read own order | ✅ | — | ✅ |
| Read another user's order | ❌ | ❌ | — |
| Write order status | ❌ | ❌ | ✅ |
| Write delivery fee | ❌ | ❌ | ✅ |
| Read own active delivery GPS | ✅ | — | ✅ |
| Read another order's GPS | ❌ | ❌ | — |
| Write GPS directly to Firestore | ❌ | ❌ | ✅ (Admin SDK) |
| Write GPS via Cloud Function | — | ✅ (own order only) | — |
| Read GPS after delivery ends | ❌ | — | — |

### GPS spoofing defences
- **Impossible speed:** jump > 120 km/h between fixes → `suspiciousMovementFlag = true` + `locationAlerts` entry
- **Accuracy gate:** fixes worse than 150 m rejected client-side and server-side
- **Rate limiting:** max 1 write per 5 seconds enforced in `updateDriverLocation`
- **Mock location indicator:** client can pass `isMockLocation` flag → triggers alert
- **Stale timestamp replay:** timestamp older than 60 s triggers alert

These create operational alerts for admin review. Drivers are **not** auto-banned from a single signal.

### Firestore rules summary
- `deliveryLocations/{orderId}`: customer read only when `isActive == true` AND order belongs to them; no client writes
- `deliveryZones`: public read, no client writes
- `locationAlerts`: no client access at all
- `orders`: sensitive fields (`orderStatus`, `deliveryFee`, etc.) protected from client mutation

---

## 10. API Configuration

### Required Google Cloud APIs
| API | Used for |
|---|---|
| Maps SDK for Android | Map rendering in MapPickerFragment, LiveTrackingFragment |
| Places API | Address autocomplete in MapPickerFragment |
| Directions API | Road routing in RoutingService |
| Geocoding API | Coordinates ↔ address (Android Geocoder uses this internally) |

### Key management
- API key stored in `local.properties` (gitignored) as `MAPS_API_KEY=AIza...`
- Injected into `AndroidManifest.xml` as `${MAPS_API_KEY}` via Secrets Gradle Plugin
- Injected into `BuildConfig.MAPS_API_KEY` for use in `RoutingService`
- `secrets.defaults.properties` (committed) holds safe placeholder `YOUR_MAPS_API_KEY_HERE`
- When placeholder is detected at runtime, `RoutingService` uses Haversine fallback only

### Key restrictions (production checklist)
- [ ] Restrict Maps SDK key to app package `com.company.foodfusionai` + SHA-1 fingerprint
- [ ] Restrict Directions API key to server IP or use a separate server-side key
- [ ] Enable API quotas and billing alerts in Google Cloud Console

---

## 11. Cost Controls

| Concern | Mitigation |
|---|---|
| Directions API calls (≈$5/1000) | 5-min in-memory cache in `RoutingService`; only called when driver location changes significantly |
| GPS writes to Firestore | Rate-limited to 1 write / 5 s per driver; accuracy gate drops low-quality fixes |
| Geohash queries | Max 9 range queries per restaurant search; prefix deduplication avoids redundant reads |
| Firestore listeners | Scoped: customer listens only to own order; admin queries bounded subsets |
| Background location | Only active during `OUT_FOR_DELIVERY` state; foreground service stops immediately on delivery completion |
| Geocoder calls | Debounced 400 ms in `MapPickerViewModel`; uses Android system Geocoder (no direct API billing) |

---

## 12. Offline Behaviour

### Customer
- Firestore offline persistence keeps last-known order state visible
- `isOffline` flag in `LiveTrackingUiState` shows "📵 Offline — Updated X ago" banner
- Last-known driver position displayed with staleness indicator
- ETA shown as APPROXIMATE / STALE with "updating…" suffix

### Driver
- GPS continues locally during network loss
- Location updates are silently dropped (not queued) to avoid stale replay on reconnect
- Service continues running; resumes Firestore writes when network returns

---

## 13. Files Created / Modified (Phase 16)

### New Kotlin files
```
app/src/main/java/com/foodfusionai/app/
  data/location/
    GeoPoint.kt
    LocationProvider.kt
    GeocodingService.kt
    GeoHashUtil.kt
    DistanceCalculator.kt
    LocationFreshness.kt
    LocationPermissionHelper.kt
    LocationPermissionState.kt
    LocationResult.kt
    LocationUnavailableException.kt (in LocationProvider.kt)
    MarkerAnimator.kt
    RouteResult.kt          (also contains EtaState, EtaInfo)
    RoutingService.kt
  data/models/
    DeliveryZone.kt
    DriverLocation.kt
  data/repository/
    NearbyRestaurantRepository.kt
    DeliveryValidationRepository.kt
    DriverLocationRepository.kt
  services/
    LocationTrackingService.kt
  ui/location/
    CustomerLocationViewModel.kt
    MapPickerFragment.kt
    MapPickerViewModel.kt
  ui/order/
    LiveTrackingFragment.kt
    LiveTrackingUiState.kt
    LiveTrackingViewModel.kt
```

### Modified Kotlin files
```
data/models/Address.kt              — added geohash, placeId, hasCoordinates
data/models/Restaurant.kt          — added lat/lon/geohash/deliveryRadiusKm/deliveryZoneId
data/models/order/Order.kt         — AddressSnapshot + lat/lon
ui/home/HomeFragment.kt            — real location header, permission dialogs
ui/checkout/CheckoutViewModel.kt   — server-authoritative delivery fee
ui/checkout/CheckoutUiState.kt     — deliveryValidation, isValidatingDelivery
ui/checkout/CheckoutFragment.kt    — removed hardcoded addresses
ui/profile/address/AddEditAddressFragment.kt  — map picker integration
ui/profile/address/AddressViewModel.kt        — lat/lon/geohash/placeId params
ui/order/OrderDetailsFragment.kt   — "Track Live" button + navigation
ui/order/LiveTrackingViewModel.kt  — import fixes, BuildConfig guard
```

### New Cloud Functions (TypeScript)
```
firebase/functions/src/location/
  geoUtils.ts                 — haversineKm, speedKmh, isValidCoord, geohashEncode
  updateDriverLocation.ts     — GPS write with auth/rate-limit/spoofing
  deactivateDriverLocation.ts — marks isActive=false after delivery
  validateDeliveryLocation.ts — server-authoritative delivery fee + radius check
  getNearbyRestaurants.ts     — geohash prefix queries
  calculateLiveEta.ts         — Haversine ETA for active delivery
  flagSuspiciousLocation.ts   — impossible speed + mock location alerts
```

### Modified Cloud Functions
```
firebase/functions/src/order/updateOrderStatus.ts  — dynamic ETA (replaced +30 min)
firebase/functions/src/index.ts                    — exports 6 new functions
```

### Config / resource files
```
gradle/libs.versions.toml          — added Maps/Places/Location/Secrets versions
build.gradle.kts                   — secrets plugin
app/build.gradle.kts               — secrets plugin + Maps dependencies + secrets{} block
app/secrets.defaults.properties    — placeholder key with setup instructions
app/src/main/AndroidManifest.xml   — MAPS_API_KEY meta-data, LocationTrackingService, permissions
app/src/main/res/
  drawable/ic_location.xml
  drawable/ic_driver.xml
  layout/fragment_map_picker.xml
  layout/fragment_live_tracking.xml
  navigation/nav_graph.xml         — mapPickerFragment + liveTrackingFragment destinations
  values/strings.xml               — 50+ Phase 16 strings
  values/colors_phase16.xml
  values-night/colors.xml
firebase/firestore.rules           — deliveryLocations + deliveryZones + locationAlerts rules
firebase/firestore.indexes.json    — geohash + delivery + order indexes
```

### Test files
```
app/src/test/java/com/foodfusionai/app/location/
  LocationFreshnessTest.kt   (16 tests)
  DistanceCalculatorTest.kt  (12 tests)
  GeoHashUtilTest.kt         (14 tests)
  DeliveryRadiusTest.kt      (8 tests)
  EtaTest.kt                 (10 tests)
  LocationSpoofingTest.kt    (9 tests)
```
Total: **69 unit tests**

---

## 14. Setup Instructions

### First-time setup
1. Copy `app/secrets.defaults.properties` → `local.properties`
2. Replace `MAPS_API_KEY=YOUR_MAPS_API_KEY_HERE` with your real key
3. Enable Maps SDK for Android, Places API, and Directions API in Google Cloud Console
4. Restrict the key to package `com.company.foodfusionai` + debug SHA-1
5. Add `google-services.json` to `app/` directory
6. Build: `./gradlew :app:assembleDebug`

### Without a real Maps key
The app compiles and runs. Map screens display correctly (Maps SDK shows tiles with placeholder key restrictions). `RoutingService` falls back to Haversine straight-line estimates. ETA shows `APPROXIMATE` state.

### Deploying Cloud Functions
```bash
cd firebase/functions
npm run build
firebase deploy --only functions
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```
