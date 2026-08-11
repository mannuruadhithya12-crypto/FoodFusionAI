import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { haversineKm, isValidCoord, geohashEncode, geohashRange } from "./geoUtils";

/**
 * getNearbyRestaurants — callable function
 *
 * Server-side geohash query: returns restaurants within [radiusKm] of the
 * customer's position, sorted by distance.
 *
 * This is the authoritative counterpart to the Android NearbyRestaurantRepository.
 * The Android client may call this instead of running the geohash queries directly,
 * or use it as a fallback when Firestore SDK queries fail.
 *
 * Parts J, K, L — Restaurant geolocation, geohash queries, nearby discovery
 *
 * Cost note: queries at precision=5 generate at most 9 prefix range scans.
 */
export const getNearbyRestaurants = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
  }

  const { latitude, longitude, radiusKm = 10, onlyOpen = true } = data;

  if (!isValidCoord(latitude, longitude)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      `Invalid coordinates: lat=${latitude} lon=${longitude}`
    );
  }
  if (typeof radiusKm !== "number" || radiusKm <= 0 || radiusKm > 50) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "radiusKm must be between 0 and 50."
    );
  }

  const db = admin.firestore();

  // ── Build geohash prefixes for bounding box ──────────────────────────────────
  const PRECISION = 5; // ~4.9 km × 4.9 km per cell
  const latDelta = radiusKm / 110.574;
  const lonDelta = radiusKm / (111.320 * Math.cos((latitude * Math.PI) / 180));

  const samplePoints = [
    { lat: latitude,              lon: longitude              },
    { lat: latitude - latDelta,   lon: longitude - lonDelta   },
    { lat: latitude - latDelta,   lon: longitude              },
    { lat: latitude - latDelta,   lon: longitude + lonDelta   },
    { lat: latitude,              lon: longitude - lonDelta   },
    { lat: latitude,              lon: longitude + lonDelta   },
    { lat: latitude + latDelta,   lon: longitude - lonDelta   },
    { lat: latitude + latDelta,   lon: longitude              },
    { lat: latitude + latDelta,   lon: longitude + lonDelta   },
  ];

  const prefixes = [...new Set(
    samplePoints.map(p =>
      geohashEncode(
        Math.max(-90,  Math.min(90,  p.lat)),
        Math.max(-180, Math.min(180, p.lon)),
        PRECISION
      )
    )
  )].sort();

  // ── Run parallel prefix queries ──────────────────────────────────────────────
  const snapshots = await Promise.all(
    prefixes.map(prefix => {
      const [start, end] = geohashRange(prefix);
      let query = db.collection("restaurants")
        .where("geohash", ">=", start)
        .where("geohash", "<=", end);
      if (onlyOpen) {
        query = query.where("isOpen", "==", true) as any;
      }
      return query.get().catch(() => null);
    })
  );

  // ── Merge, dedup, distance-filter ────────────────────────────────────────────
  const seen = new Set<string>();
  const results: any[] = [];

  for (const snap of snapshots) {
    if (!snap) continue;
    for (const doc of snap.docs) {
      if (seen.has(doc.id)) continue;
      seen.add(doc.id);

      const r = { id: doc.id, ...doc.data() };
      if (!r.latitude || !r.longitude) continue;

      const dist = haversineKm(
        { latitude, longitude },
        { latitude: r.latitude, longitude: r.longitude }
      );
      if (dist > radiusKm) continue;

      const deliveryRadius = r.deliveryRadiusKm > 0 ? r.deliveryRadiusKm : 8.0;
      results.push({
        ...r,
        distanceKm:    parseFloat(dist.toFixed(2)),
        distanceLabel: formatDistance(dist),
        isDeliverable: dist <= deliveryRadius,
      });
    }
  }

  results.sort((a, b) => a.distanceKm - b.distanceKm);

  return { restaurants: results, count: results.length };
});

function formatDistance(km: number): string {
  if (km < 1) return `${Math.round(km * 1000)} m`;
  if (km < 10) return `${km.toFixed(1)} km`;
  return `${Math.round(km)} km`;
}
