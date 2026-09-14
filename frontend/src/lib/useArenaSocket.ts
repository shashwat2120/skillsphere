import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import { getAccessToken } from '@/lib/api'

/**
 * One STOMP connection to the arena's live topics, with automatic
 * reconnection.
 *
 * <p>Sends the access token as a CONNECT header when one exists — a signed-in
 * learner's session carries their identity onto the socket the same way it
 * does onto a REST call. A guest with no token connects exactly the same
 * way, just without that header; {@code StompAuthChannelInterceptor} accepts
 * both, since arenas exist specifically to let guests take part.
 *
 * <p>{@code reconnectDelay} is stompjs's own automatic reconnect — a real
 * requirement here, not a nicety: a live quiz running on a phone over
 * classroom wifi is exactly the connection that drops mid-session, and
 * without this every dropped packet would end someone's game.
 */
export function useArenaSocket(onMessage: (destination: string, body: unknown) => void) {
  const [connected, setConnected] = useState(false)
  const clientRef = useRef<Client | null>(null)
  const subscriptionsRef = useRef<Map<string, { unsubscribe: () => void }>>(new Map())
  const onMessageRef = useRef(onMessage)
  onMessageRef.current = onMessage

  useEffect(() => {
    // Same origin as the frontend, not a hardcoded backend port — the dev
    // server proxies /ws to the backend (vite.config.ts) exactly the way it
    // proxies /api, and for the same reason: one origin in the browser.
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const client = new Client({
      brokerURL: `${wsProtocol}//${window.location.host}/ws`,
      connectHeaders: (() => {
        const token = getAccessToken()
        const headers: Record<string, string> = {}
        if (token) headers.Authorization = `Bearer ${token}`
        return headers
      })(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => setConnected(true),
      onWebSocketClose: () => setConnected(false),
      onStompError: (frame) => console.error('STOMP error', frame.headers.message),
    })
    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
      clientRef.current = null
    }
  }, [])

  function subscribe(destination: string) {
    const client = clientRef.current
    if (!client || subscriptionsRef.current.has(destination)) return

    const trySubscribe = () => {
      if (!client.connected) return
      const sub = client.subscribe(destination, (message) => {
        try {
          onMessageRef.current(destination, JSON.parse(message.body))
        } catch {
          onMessageRef.current(destination, message.body)
        }
      })
      subscriptionsRef.current.set(destination, sub)
    }

    if (client.connected) {
      trySubscribe()
    } else {
      const original = client.onConnect
      client.onConnect = (frame) => {
        original?.(frame)
        trySubscribe()
      }
    }
  }

  return { connected, subscribe }
}
