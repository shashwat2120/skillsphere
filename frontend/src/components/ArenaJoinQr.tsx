import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'

/**
 * A join code is easy to read out loud and painful to type on a phone
 * keyboard mid-lecture. This renders the same join URL as a QR code so a
 * phone can scan its way straight to {@code /arena/join/:code} — the spec's
 * "phones join by QR" — entirely client-side, with no network call and
 * nothing sent anywhere: the URL is encoded directly into the pixels.
 */
export function ArenaJoinQr({ url, size = 168 }: { url: string; size?: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    if (!url || !canvasRef.current) return
    setError(false)
    QRCode.toCanvas(canvasRef.current, url, { width: size, margin: 1 }).catch(() => setError(true))
  }, [url, size])

  if (error) return null

  return (
    <canvas
      ref={canvasRef}
      width={size}
      height={size}
      className="rounded-sq border border-line bg-white p-2"
      aria-label="QR code to join the arena"
    />
  )
}
