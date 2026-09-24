import { useCallback } from 'react'
import { useAppStore } from '../store/appStore'

const FALLBACK = { lat: 19.1176, lng: 72.906, accuracyKm: 0.5 }

/**
 * Geolocation with graceful degradation:
 * - Checks the Permissions API first (denied ⇒ instant fallback, no hanging request)
 * - Real permission flow when the browser supports it
 * - JS watchdog so the UI can never get stuck on "Requesting…"
 * - Demo-location fallback (Powai, Mumbai) keeps the Nearby page functional
 */
export function useGeolocation() {
  const geo = useAppStore((s) => s.geo)
  const setGeo = useAppStore((s) => s.setGeo)
  const pushToast = useAppStore((s) => s.pushToast)

  const requestLocation = useCallback(() => {
    if (!('geolocation' in navigator)) {
      setGeo({ status: 'unavailable', ...FALLBACK })
      pushToast('Geolocation not supported — using demo location', '🧪')
      return
    }

    setGeo({ status: 'prompt', ...FALLBACK })

    const finish = (status: 'granted' | 'denied' | 'unavailable', coords?: { lat: number; lng: number; accuracyKm: number }) => {
      if (watchdog) clearTimeout(watchdog)
      if (status === 'granted' && coords) {
        setGeo({ status, ...coords })
        pushToast('Location updated — Nearby refreshed', '📍')
      } else {
        setGeo({ status, ...FALLBACK })
        pushToast(
          status === 'denied'
            ? 'Location permission denied — showing demo location instead'
            : 'Location unavailable — showing demo location instead',
          '🧪',
        )
      }
    }

    // Never leave the UI in "Requesting…" — some embedded browsers never call back
    const watchdog = setTimeout(() => finish('unavailable'), 9_000)

    const queryPerms =
      'permissions' in navigator
        ? navigator.permissions.query({ name: 'geolocation' as PermissionName })
        : null

    queryPerms
      ?.then((perms) => {
        if (perms.state === 'denied') {
          finish('denied')
          return
        }
        requestPosition()
      })
      .catch(requestPosition)

    function requestPosition() {
      navigator.geolocation.getCurrentPosition(
        (pos) =>
          finish('granted', {
            lat: pos.coords.latitude,
            lng: pos.coords.longitude,
            accuracyKm: Math.max(0.1, Math.round((pos.coords.accuracy / 1000) * 10) / 10),
          }),
        (err) => finish(err.code === err.PERMISSION_DENIED ? 'denied' : 'unavailable'),
        { timeout: 8000, maximumAge: 60_000 },
      )
    }
  }, [setGeo, pushToast])

  return { geo, requestLocation }
}
