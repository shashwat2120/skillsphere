import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { AlertTriangle, ArrowRight, Copy, Loader2, Trophy, Users } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { useArenaSocket } from '@/lib/useArenaSocket'
import { ArenaJoinQr } from '@/components/ArenaJoinQr'

interface QuestionView {
  arenaQuestionId: number
  position: number
  totalQuestions: number
  stem: string
  options: { id: number; text: string }[]
  timeLimitSec: number
}

interface ArenaView {
  id: number
  title: string
  skillName: string
  joinCode: string
  status: 'LOBBY' | 'RUNNING' | 'PAUSED' | 'ENDED'
  allowGuests: boolean
  questionCount: number
  secondsPerQuestion: number
  participantCount: number
  currentQuestion: QuestionView | null
}

interface ParticipantView {
  id: number
  displayName: string
  score: number
  correctCount: number
  answerCount: number
  finalRank: number | null
}

interface ConfusionAlert {
  userId: number
  skillName: string
  failureCount: number
  severity: string
}

/**
 * The instructor's live control panel: the code to hand out, the current
 * question, the leaderboard, and a running feed of anyone visibly stuck.
 *
 * <p>Every mutation (start, advance, end) is a plain REST call — the panel's
 * own click triggers its own screen to update via the same broadcast every
 * participant's device receives, rather than trusting its local response to
 * be the truth. That's deliberate: it's the same code path a spectator's
 * projector view would use, so there is only one source of truth for "what
 * does the room see right now" instead of two that could drift apart.
 */
export function ArenaHostPage() {
  const { arenaId } = useParams<{ arenaId: string }>()
  const [arena, setArena] = useState<ArenaView | null>(null)
  const [leaderboard, setLeaderboard] = useState<ParticipantView[]>([])
  const [participants, setParticipants] = useState<ParticipantView[]>([])
  const [alerts, setAlerts] = useState<ConfusionAlert[]>([])

  const { subscribe } = useArenaSocket((destination, body) => {
    if (destination.endsWith('/state')) setArena(body as ArenaView)
    else if (destination.endsWith('/leaderboard')) setLeaderboard(body as ParticipantView[])
    else if (destination.endsWith('/lobby')) setParticipants(body as ParticipantView[])
    else if (destination.endsWith('/confusion')) setAlerts((prev) => [body as ConfusionAlert, ...prev].slice(0, 5))
  })

  const { isLoading } = useQuery({
    queryKey: ['arena', arenaId, 'host'],
    queryFn: async () => {
      const data = (await api.get<ArenaView>(`/realtime/arenas/id/${arenaId}`)).data
      setArena(data)
      return data
    },
  })

  useEffect(() => {
    if (!arenaId) return
    subscribe(`/topic/arena/${arenaId}/state`)
    subscribe(`/topic/arena/${arenaId}/lobby`)
    subscribe(`/topic/arena/${arenaId}/leaderboard`)
    subscribe(`/topic/arena/${arenaId}/confusion`)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [arenaId])

  const start = useMutation({
    mutationFn: async () => (await api.post<ArenaView>(`/realtime/arenas/${arenaId}/start`)).data,
    onSuccess: setArena,
    onError: (error) => toast.error(errorMessage(error, 'Could not start.')),
  })

  const advance = useMutation({
    mutationFn: async () => (await api.post<ArenaView>(`/realtime/arenas/${arenaId}/advance`)).data,
    onSuccess: setArena,
  })

  const joinUrl = arena ? `${window.location.origin}/arena/join/${arena.joinCode}` : ''

  if (isLoading || !arena) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <Loader2 className="size-5 animate-spin text-fg-muted" />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl">
      <header className="mb-5 border-b border-line pb-4">
        <h1 className="text-[19px] font-semibold">{arena.title}</h1>
        <p className="mt-1 text-[13px] text-fg-muted">{arena.skillName}</p>
      </header>

      {arena.status === 'LOBBY' && (
        <div className="rounded-sq border border-line bg-surface p-6 text-center">
          <p className="legend">Join code</p>
          <p className="num mt-1 text-5xl font-semibold tracking-wider">{arena.joinCode}</p>
          <div className="mt-3 flex items-center justify-center gap-2">
            <code className="rounded-sq bg-bg-sunken px-2 py-1 text-xs">{joinUrl}</code>
            <button
              onClick={() => { navigator.clipboard.writeText(joinUrl); toast.success('Copied.') }}
              className="text-fg-muted hover:text-fg"
            >
              <Copy className="size-3.5" />
            </button>
          </div>

          <div className="mt-5 flex justify-center">
            <ArenaJoinQr url={joinUrl} />
          </div>
          <p className="mt-2 text-xs text-fg-subtle">Scan to join on a phone</p>

          <div className="mt-6 flex items-center justify-center gap-2 text-sm text-fg-muted">
            <Users className="size-4" /> {participants.length || arena.participantCount} joined
          </div>

          <Button className="mt-5" disabled={start.isPending} onClick={() => start.mutate()}>
            {start.isPending && <Loader2 className="size-3.5 animate-spin" />}
            Start arena
          </Button>
        </div>
      )}

      {arena.status === 'RUNNING' && arena.currentQuestion && (
        <div>
          <div className="mb-4 flex items-center justify-between">
            <p className="legend">
              Question {arena.currentQuestion.position} of {arena.currentQuestion.totalQuestions}
            </p>
            <Button size="sm" disabled={advance.isPending} onClick={() => advance.mutate()}>
              {advance.isPending ? <Loader2 className="size-3.5 animate-spin" /> : <ArrowRight className="size-3.5" />}
              {arena.currentQuestion.position >= arena.currentQuestion.totalQuestions ? 'End arena' : 'Next question'}
            </Button>
          </div>

          <div className="rounded-sq border border-line bg-surface p-5">
            <p className="text-[15px] leading-relaxed">{arena.currentQuestion.stem}</p>
            <div className="mt-3 space-y-1.5">
              {arena.currentQuestion.options.map((o) => (
                <div key={o.id} className="rounded-sq border border-line px-3 py-2 text-sm text-fg-muted">
                  {o.text}
                </div>
              ))}
            </div>
          </div>

          <AnimatePresence>
            {alerts.length > 0 && (
              <div className="mt-4 space-y-2">
                {alerts.map((a, i) => (
                  <motion.div
                    key={i}
                    initial={{ opacity: 0, x: 12 }}
                    animate={{ opacity: 1, x: 0 }}
                    className="flex items-center gap-2 rounded-sq border border-decaying/30 bg-decaying-bg px-3 py-2 text-xs"
                  >
                    <AlertTriangle className="size-3.5 shrink-0 text-decaying" />
                    <span>
                      Learner {a.userId} — {a.failureCount} wrong in a row on {a.skillName}
                    </span>
                  </motion.div>
                ))}
              </div>
            )}
          </AnimatePresence>

          <Leaderboard entries={leaderboard} />
        </div>
      )}

      {arena.status === 'ENDED' && (
        <div>
          <div className="mb-4 flex items-center gap-2 text-sm font-medium">
            <Trophy className="size-4 text-mastered" /> Final results
          </div>
          <Leaderboard entries={leaderboard} showRank />
        </div>
      )}
    </div>
  )
}

function Leaderboard({ entries, showRank }: { entries: ParticipantView[]; showRank?: boolean }) {
  return (
    <div className="mt-4">
      <p className="legend mb-2">Leaderboard</p>
      <ol className="space-y-1.5">
        {entries.map((p, i) => (
          <li
            key={p.id}
            className="flex items-center gap-3 rounded-sq border border-line bg-surface px-3 py-2 text-sm"
          >
            <span className="num w-6 text-fg-subtle">{showRank ? p.finalRank ?? i + 1 : i + 1}</span>
            <span className="flex-1">{p.displayName}</span>
            <span className="num text-xs text-fg-subtle">{p.correctCount}/{p.answerCount}</span>
            <span className="num font-semibold">{p.score}</span>
          </li>
        ))}
        {!entries.length && <p className="text-xs text-fg-subtle">No answers yet.</p>}
      </ol>
    </div>
  )
}
