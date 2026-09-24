/** Distance + geo helpers for the Nearby demo (haversine, client-side). */

const EARTH_RADIUS_KM = 6371

export function distanceKm(
  aLat: number,
  aLng: number,
  bLat: number,
  bLng: number,
): number {
  const dLat = toRad(bLat - aLat)
  const dLng = toRad(bLng - aLng)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(aLat)) * Math.cos(toRad(bLat)) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h))
}

export function formatDistance(km: number): string {
  if (km < 1) return `${Math.round(km * 1000)} m away`
  if (km < 10) return `${km.toFixed(1)} km away`
  return `${Math.round(km)} km away`
}

function toRad(deg: number): number {
  return (deg * Math.PI) / 180
}

/** Deterministic gradient artwork used instead of external images. */
export const artwork = {
  gradient: { bg: 'from-fuchsia-500 via-violet-600 to-indigo-700', label: 'Design system — dark palette' },
  sunrise: { bg: 'from-amber-400 via-orange-500 to-rose-600', label: 'Bandra promenade, 5:41am' },
  concert: { bg: 'from-cyan-400 via-blue-600 to-violet-800', label: 'Neon Monsoon live at Prithvi' },
} as const

export type ArtworkKey = keyof typeof artwork

export function isArtworkKey(v?: string): v is ArtworkKey {
  return v === 'gradient' || v === 'sunrise' || v === 'concert'
}
