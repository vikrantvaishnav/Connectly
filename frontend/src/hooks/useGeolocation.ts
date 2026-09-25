import { useCallback } from 'react'
import { useAppStore } from '../store/appStore'

/**
 * Geolocation with honest degradation:
 * - Checks the Permissions API first (denied ⇒ instant stop, no hanging request)
 * - JS watchdog so the UI can never get stuck on "Requesting…"
 * - No fake fallback position: if the user doesn't grant permission, geo stays
 *   null and Nearby/Discover simply ask for a real location. Nothing is ever
 *   stored server-side without an explicit grant.
 */
export function useGeolocation() {
  const geo = useAppStore((s) => s.geo)
  const setGeo = useAppStore((s) => s.setGeo)
  const pushToast = useAppStore((s) => s.pushToast)

  const requestLocation = useCallback(() => {
    if (!('geolocation' in navigator)) {
      setGeo({ status: 'unavailable', lat: null, lng: null, accuracyKm: null })
      pushToast('Geolocation is not supported by this browser', '🧭')
      return
    }

    setGeo({ status: 'prompt', lat: null, lng: null, accuracyKm: null })

    const finish = (status: 'granted' | 'denied' | 'unavailable', coords?: { lat: number; lng: number; accuracyKm: number }) => {
      if (watchdog) clearTimeout(watchdog)
      if (status === 'granted' && coords) {
        setGeo({ status, ...coords })
      } else {
        setGeo({ status, lat: null, lng: null, accuracyKm: null })
        pushToast(
          status === 'denied'
            ? 'Location permission denied — Nearby needs it to find people around you'
            : 'Location unavailable — check your browser settings and try again',
          '🧭',
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
