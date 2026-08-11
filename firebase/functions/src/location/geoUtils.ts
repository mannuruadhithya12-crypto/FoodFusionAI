/**
 * Server-side geo utility functions for Phase 16.
 *
 * These run inside Cloud Functions (Node.js 18) and intentionally do NOT
 * import any Android / browser libraries — only pure math.
 */

export interface GeoCoord {
  latitude: number;
  longitude: number;
}

const EARTH_RADIUS_KM = 6371.0;

/** Haversine great-circle distance in km. */
export function haversineKm(a: GeoCoord, b: GeoCoord): number {
  const dLat = toRad(b.latitude  - a.latitude);
  const dLon = toRad(b.longitude - a.longitude);
  const sinDlat = Math.sin(dLat / 2);
  const sinDlon = Math.sin(dLon / 2);
  const h =
    sinDlat * sinDlat +
    Math.cos(toRad(a.latitude)) *
      Math.cos(toRad(b.latitude)) *
      sinDlon * sinDlon;
  return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

/** Speed in km/h between two GPS fixes given time delta in ms. */
export function speedKmh(
  from: GeoCoord & { timestamp: number },
  to:   GeoCoord & { timestamp: number }
): number {
  const distKm = haversineKm(from, to);
  const deltaHours = (to.timestamp - from.timestamp) / 3_600_000;
  if (deltaHours <= 0) return 0;
  return distKm / deltaHours;
}

/** Validate latitude/longitude are within legal bounds. */
export function isValidCoord(lat: number, lon: number): boolean {
  return (
    typeof lat === "number" &&
    typeof lon === "number" &&
    isFinite(lat) && isFinite(lon) &&
    lat >= -90  && lat <= 90  &&
    lon >= -180 && lon <= 180
  );
}

function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

/**
 * Basic geohash encoder (precision 5 ≈ 4.9 km × 4.9 km).
 * Matches the client-side GeoHashUtil.encode().
 */
const BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

export function geohashEncode(lat: number, lon: number, precision = 9): string {
  let minLat = -90,  maxLat = 90;
  let minLon = -180, maxLon = 180;
  let hash = "";
  let bits = 0, bitsTotal = 0, hashValue = 0;
  let isLon = true;

  while (hash.length < precision) {
    const mid = isLon ? (minLon + maxLon) / 2 : (minLat + maxLat) / 2;
    const val = isLon ? lon : lat;
    if (val >= mid) {
      hashValue = (hashValue << 1) | 1;
      if (isLon) minLon = mid; else minLat = mid;
    } else {
      hashValue = hashValue << 1;
      if (isLon) maxLon = mid; else maxLat = mid;
    }
    isLon = !isLon;
    bitsTotal++;
    if (++bits === 5) {
      hash += BASE32[hashValue];
      hashValue = 0;
      bits = 0;
    }
  }
  return hash;
}

/**
 * Returns [start, end] range strings for a geohash prefix.
 * Used for Firestore range queries.
 */
export function geohashRange(prefix: string): [string, string] {
  return [prefix, prefix + "\uf8ff"];
}
