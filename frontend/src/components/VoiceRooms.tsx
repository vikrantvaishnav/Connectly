import { useEffect, useRef, useState } from 'react'
import { Client, type IMessage } from '@stomp/stompjs'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiErrorMessage, getAccessToken, wsUrl } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'

interface RoomView {
  id: number
  name: string
  hostUsername: string
  status: string
  participantCount: number
  createdAt: string
}

interface ParticipantView {
  userId: number
  username: string
  name: string
  muted: boolean
}

/** STOMP signaling channel for a room; returns disposer. */
function useSignaling(roomId: number | null, onPeers: (p: ParticipantView[]) => void, onSignal: (from: number, data: unknown) => void) {
  const me = useAuthStore((s) => s.user)
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    if (roomId == null || !me?.id) return
    const client = new Client({
      brokerURL: wsUrl(),
      connectHeaders: { Authorization: `Bearer ${getAccessToken() ?? ''}` },
      reconnectDelay: 3000,
      onConnect: () => {
        // peer-list topic (from backend broadcasts)
        client.subscribe(`/topic/voice/${roomId}`, (msg: IMessage) => {
          try { onPeers(JSON.parse(msg.body)) } catch { /* ignore */ }
        })
        // WebRTC signaling arrives on my private queue (addressed delivery server-side).
        client.subscribe(`/user/queue/voice/${roomId}/signal`, (msg: IMessage) => {
          try {
            const parsed = JSON.parse(msg.body) as { from: number; to: number | null; data: unknown }
            onSignal(parsed.from, parsed.data)
          } catch { /* ignore */ }
        })
      },
    })
    client.activate()
    clientRef.current = client
    return () => { client.deactivate(); clientRef.current = null }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, me?.id])

  return clientRef
}

export function VoiceRoomsPanel() {
  const me = useAuthStore((s) => s.user)
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()

  const [activeRoom, setActiveRoom] = useState<number | null>(null)
  const [roomName, setRoomName] = useState('')
  const [creating, setCreating] = useState(false)

  const rooms = useQuery({
    queryKey: ['voice-rooms'],
    queryFn: async () => (await api.get<RoomView[]>('/voice/rooms')).data,
    refetchInterval: 10_000,
  })

  const create = useMutation({
    mutationFn: () => api.post<RoomView>('/voice/rooms', { name: roomName }),
    onSuccess: (res) => {
      setCreating(false); setRoomName('')
      queryClient.invalidateQueries({ queryKey: ['voice-rooms'] })
      setActiveRoom(res.data.id)
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  if (!me) return null

  if (activeRoom != null) {
    return <VoiceRoom roomId={activeRoom} onLeave={() => { setActiveRoom(null); queryClient.invalidateQueries({ queryKey: ['voice-rooms'] }) }} />
  }

  return (
    <div className="mx-auto max-w-2xl space-y-4 p-4 sm:p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">🎙️ Voice rooms</h1>
          <p className="text-xs text-[var(--muted)]">Live audio hangouts — WebRTC peer-to-peer</p>
        </div>
        <button
          onClick={() => setCreating(v => !v)}
          className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)]"
        >
          {creating ? 'Cancel' : '+ Start room'}
        </button>
      </div>

      {creating && (
        <form
          onSubmit={(e) => { e.preventDefault(); if (roomName.trim().length >= 2) create.mutate() }}
          className="flex gap-2 rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4"
        >
          <input
            value={roomName}
            onChange={(e) => setRoomName(e.target.value)}
            placeholder="Room name (e.g. Friday hangout)"
            maxLength={60}
            className="flex-1 rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
          />
          <button
            type="submit"
            disabled={roomName.trim().length < 2 || create.isPending}
            className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white disabled:opacity-40"
          >
            {create.isPending ? '…' : 'Create'}
          </button>
        </form>
      )}

      {rooms.data?.map((r) => (
        <div key={r.id} className="flex items-center gap-3 rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
          <span className="text-2xl" aria-hidden="true">🔊</span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold">{r.name}</p>
            <p className="text-xs text-[var(--muted)]">host @{r.hostUsername} · {r.participantCount} in room</p>
          </div>
          <button
            onClick={() => setActiveRoom(r.id)}
            className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500"
          >
            Join
          </button>
        </div>
      ))}
      {rooms.data?.length === 0 && !rooms.isPending && (
        <p className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--muted)]">
          No open rooms. Start one and share the vibe 🎧
        </p>
      )}
    </div>
  )
}

export function VoiceRoom({ roomId, onLeave }: { roomId: number; onLeave: () => void }) {
  const me = useAuthStore((s) => s.user)!
  const pushToast = useAppStore((s) => s.pushToast)

  const [peers, setPeers] = useState<ParticipantView[]>([])
  const [muted, setMuted] = useState(false)
  const [micError, setMicError] = useState<string | null>(null)
  const [micReady, setMicReady] = useState(false)
  const joinedRef = useRef(false)
  const localStreamRef = useRef<MediaStream | null>(null)
  const pcs = useRef<Map<number, RTCPeerConnection>>(new Map())
  const audioEls = useRef<Map<number, HTMLAudioElement>>(new Map())
  const pendingIce = useRef<Map<number, RTCIceCandidateInit[]>>(new Map())

  // ---- backend peer sync (REST) + realtime broadcasts (STOMP) ----
  const peersRef = useRef<ParticipantView[]>([])
  const syncPeers = (list: ParticipantView[]) => {
    peersRef.current = list
    setPeers(list)
  }

  const join = useMutation({
    mutationFn: () => api.post<ParticipantView[]>(`/voice/rooms/${roomId}/join`),
    onSuccess: (res) => syncPeers(res.data),
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const mute = useMutation({
    mutationFn: (m: boolean) => api.post<ParticipantView[]>(`/voice/rooms/${roomId}/mute`, { muted: m }),
    onSuccess: (res) => syncPeers(res.data),
  })

  const leaveClean = () => {
    pcs.current.forEach(pc => pc.close())
    pcs.current.clear()
    localStreamRef.current?.getTracks().forEach(t => t.stop())
    localStreamRef.current = null
    void api.post(`/voice/rooms/${roomId}/leave`).catch(() => {})
  }

  // Join only after the mic is resolved — a PeerConnection created before the
  // local stream exists transmits silence, so nobody would hear you.
  useEffect(() => {
    if ((micReady || micError) && !joinedRef.current) {
      joinedRef.current = true
      join.mutate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, micReady, micError])

  // Teardown on unmount / room change only (never when mic state flips).
  useEffect(() => {
    return () => { leaveClean() }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId])

  // ---- mic capture ----
  useEffect(() => {
    let cancelled = false
    navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } })
      .then(stream => {
        if (cancelled) { stream.getTracks().forEach(t => t.stop()); return }
        localStreamRef.current = stream
        stream.getAudioTracks().forEach(t => { t.enabled = !muted })
        setMicReady(true)
        // If a PeerConnection was created while the mic was still starting
        // (listen-only race), attach the tracks now so you can be heard.
        pcs.current.forEach(pc => {
          stream.getTracks().forEach(t => {
            try { pc.addTrack(t, stream) } catch { /* already attached */ }
          })
        })
      })
      .catch(() => setMicError('Microphone access denied — you can listen but not speak.'))
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    localStreamRef.current?.getAudioTracks().forEach(t => { t.enabled = !muted })
  }, [muted])

  // ---- WebRTC mesh ----
  const getPc = (peerId: number): RTCPeerConnection => {
    let pc = pcs.current.get(peerId)
    if (pc) return pc
    pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] })
    pcs.current.set(peerId, pc)

    localStreamRef.current?.getTracks().forEach(t => pc!.addTrack(t, localStreamRef.current!))

    pc.onicecandidate = (e) => {
      if (e.candidate && wsRef.current?.connected) {
        wsRef.current.publish({
          destination: `/ws/voice/${roomId}/signal`,
          headers: {},
          body: JSON.stringify({ from: me.id, to: peerId, data: { kind: 'ice', candidate: e.candidate.toJSON() } }),
        })
      }
    }
    pc.ontrack = (e) => {
      let el = audioEls.current.get(peerId)
      if (!el) {
        el = document.createElement('audio')
        el.autoplay = true
        el.setAttribute('playsinline', '')
        audioEls.current.set(peerId, el)
        document.body.appendChild(el)
      }
      el.srcObject = e.streams[0]
      // Autoplay policies: play() normally succeeds because joining was a user
      // gesture — but if the browser still blocks it, the next click resumes.
      el.play().catch(() => {
        const resume = () => {
          el!.play().catch(() => {})
          document.removeEventListener('click', resume)
        }
        document.addEventListener('click', resume)
      })
    }
    // If the connection dies (network switch, blocked TURN path), ask the
    // peers to renegotiate instead of staying silent forever.
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'failed') {
        try { pc.restartIce() } catch { /* best effort */ }
      }
    }
    return pc
  }

  const signalPeer = async (peerId: number, data: unknown) => {
    const d = data as { kind: string; sdp?: RTCSessionDescriptionInit; candidate?: RTCIceCandidateInit }
    const pc = getPc(peerId)
    try {
      if (d.kind === 'offer' && d.sdp) {
        await pc.setRemoteDescription(d.sdp)
        const answer = await pc.createAnswer()
        await pc.setLocalDescription(answer)
        wsRef.current?.publish({
          destination: `/ws/voice/${roomId}/signal`,
          body: JSON.stringify({ from: me.id, to: peerId, data: { kind: 'answer', sdp: pc.localDescription } }),
        })
        for (const c of pendingIce.current.get(peerId) ?? []) await pc.addIceCandidate(c)
        pendingIce.current.delete(peerId)
      } else if (d.kind === 'answer' && d.sdp) {
        if (pc.signalingState !== 'stable') await pc.setRemoteDescription(d.sdp)
        // ICE candidates that arrived while we waited for this answer must be
        // flushed now — dropping them is THE classic "can't hear the peer" bug.
        for (const c of pendingIce.current.get(peerId) ?? []) await pc.addIceCandidate(c)
        pendingIce.current.delete(peerId)
      } else if (d.kind === 'ice' && d.candidate) {
        if (pc.remoteDescription) await pc.addIceCandidate(d.candidate)
        else pendingIce.current.set(peerId, [...(pendingIce.current.get(peerId) ?? []), d.candidate])
      }
    } catch { /* transient negotiation races are normal in meshes */ }
  }

  const sendOffer = async (peerId: number) => {
    const pc = getPc(peerId)
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    wsRef.current?.publish({
      destination: `/ws/voice/${roomId}/signal`,
      body: JSON.stringify({ from: me.id, to: peerId, data: { kind: 'offer', sdp: pc.localDescription } }),
    })
  }
  void 0 // (keep formatting stable)

  // deterministic offerer (lower id offers) avoids offer collisions in the mesh
  const onPeers = (list: ParticipantView[]) => {
    const prev = new Set(peersRef.current.map(p => p.userId))
    syncPeers(list)
    for (const p of list) {
      if (p.userId === me.id) continue
      if (!prev.has(p.userId) && me.id < p.userId) void sendOffer(p.userId)
    }
    // drop connections for peers who left
    for (const [peerId, pc] of pcs.current) {
      if (!list.some(p => p.userId === peerId)) {
        pc.close(); pcs.current.delete(peerId)
        const el = audioEls.current.get(peerId)
        if (el) { el.remove(); audioEls.current.delete(peerId) }
      }
    }
  }

  const wsRef = useSignaling(roomId, onPeers, (from, data) => { void signalPeer(from, data) })

  const leave = useMutation({
    mutationFn: () => api.post(`/voice/rooms/${roomId}/leave`),
    onSettled: () => { leaveClean(); onLeave() },
  })

  return (
    <div className="mx-auto max-w-2xl space-y-4 p-4 sm:p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">🔊 In voice room</h1>
        <button onClick={() => leave.mutate()} className="rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-500">
          Leave
        </button>
      </div>

      {micError && (
        <p className="rounded-2xl border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-500">{micError}</p>
      )}

      <div className="space-y-2 rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
        <p className="text-xs font-bold uppercase tracking-wide text-[var(--muted)]">
          {peers.length} in room · peer-to-peer audio
        </p>
        {peers.map((p) => (
          <div key={p.userId} className="flex items-center gap-3 rounded-xl p-2">
            <span className={`h-2.5 w-2.5 rounded-full ${p.muted ? 'bg-zinc-500' : 'bg-emerald-400 animate-pulse'}`} />
            <span className="flex-1 text-sm">{p.name}{p.userId === me.id ? ' (you)' : ''}</span>
            {p.userId === me.id && (
              <button
                onClick={() => { setMuted(m => !m); mute.mutate(!muted) }}
                className={`rounded-lg px-3 py-1.5 text-xs font-medium ${muted ? 'bg-rose-600 text-white' : 'bg-[var(--surface-2)] text-[var(--text)]'}`}
              >
                {muted ? '🔇 Muted' : '🎙️ Mic on'}
              </button>
            )}
          </div>
        ))}
      </div>
      <p className="text-xs text-[var(--muted)]">Speak naturally — audio is peer-to-peer (WebRTC), the server only helps connect you.</p>
    </div>
  )
}
