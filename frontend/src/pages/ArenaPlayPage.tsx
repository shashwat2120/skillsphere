import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { CheckCircle2, Loader2, Trophy, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { useArenaSocket } from '@/lib/useArenaSocket'

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

interface AnswerResult {
  correct: boolean
  pointsAwarded: number
  newScore: number
  correctOptionId: number | null
}

const GUEST_TOKEN_KEY = 'skillsphere_arena_guest_token'

function getGuestToken(): string {
  let token = localStorage.getItem(GUEST_TOKEN_KEY)
  if (!token) {
    token = crypto.randomUUID()
    localStorage.setItem(GUEST_TOKEN_KEY, token)
  }
  return token
}

/**
 * Join and play — public, no account required.
 *
 * <p>Outside {@code AppShell} and {@code RequireAuth} entirely: a guest who
 * scanned a QR code has never seen this product before and has no session
 * to be gated behind. A signed-in learner reaches the exact same page and
 * plays the exact same way; the only difference is that {@code api.ts}'s
 * interceptor happens to attach their token, which is how their answers end
 * up tied to their own account instead of a disposable guest identity.
 */
export function ArenaPlayPage() {
  const { code: codeFromUrl } = useParams<{ code?: string }>()
  const [phase, setPhase] = useState<'code' | 'name' | 'lobby' | 'playing' | 'ended'>('code')
  const [code, setCode] = useState(codeFromUrl?.toUpperCase() ?? '')
  const [name, setName] = useState('')
  const [arena, setArena] = useState<ArenaView | null>(null)
  const [participant, setParticipant] = useState<{ id: number; displayName: string } | null>(null)
  const [answered, setAnswered] = useState<AnswerResult | null>(null)
  const [selectedOption, setSelectedOption] = useState<number | null>(null)
  const [leaderboard, setLeaderboard] = useState<ParticipantView[]>([])
  const answerStartedAt = { current: Date.now() }

  const { subscribe } = useArenaSocket((destination, body) => {
    if (destination.endsWith('/state')) {
      const next = body as ArenaView
      setArena(next)
      setAnswered(null)
      setSelectedOption(null)
      if (next.status === 'ENDED') setPhase('ended')
      else if (next.status === 'RUNNING') setPhase('playing')
    } else if (destination.endsWith('/leaderboard')) {
      setLeaderboard(body as ParticipantView[])
    }
  })

  useEffect(() => {
    if (codeFromUrl) setCode(codeFromUrl.toUpperCase())
  }, [codeFromUrl])

  const preview = useMutation({
    mutationFn: async () => (await api.get<ArenaView>(`/realtime/arenas/${code}`)).data,
    onSuccess: (data) => {
      setArena(data)
      setPhase('name')
    },
    onError: (error) => toast.error(errorMessage(error, 'No arena with that code.')),
  })

  const join = useMutation({
    mutationFn: async () =>
      (
        await api.post<{ id: number; displayName: string }>(`/realtime/arenas/${code}/join`, {
          guestToken: getGuestToken(),
          displayName: name,
        })
      ).data,
    onSuccess: (data) => {
      setParticipant(data)
      setPhase('lobby')
      subscribe(`/topic/arena/${arena!.id}/state`)
      subscribe(`/topic/arena/${arena!.id}/leaderboard`)
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not join.')),
  })

  const submitAnswer = useMutation({
    mutationFn: async (optionId: number) =>
      (
        await api.post<AnswerResult>(`/realtime/arenas/${arena!.id}/answer`, {
          participantId: participant!.id,
          arenaQuestionId: arena!.currentQuestion!.arenaQuestionId,
          selectedOptionId: optionId,
          responseTimeMs: Date.now() - answerStartedAt.current,
        })
      ).data,
    onSuccess: setAnswered,
    onError: (error) => toast.error(errorMessage(error, 'Could not submit that answer.')),
  })

  const myRank = leaderboard.findIndex((p) => p.id === participant?.id)

  return (
    <div className="min-h-screen bg-bg">
      <div className="mx-auto max-w-md px-4 py-10">
        <p className="mb-6 text-center font-semibold tracking-tight">SkillSphere Arena</p>

        {phase === 'code' && (
          <div className="rounded-sq border border-line bg-surface p-6 text-center">
            <p className="legend mb-3">Enter the join code</p>
            <input
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              maxLength={6}
              placeholder="ABCDEF"
              className="num w-full rounded-sq border border-line bg-bg px-3 py-3 text-center text-2xl tracking-widest outline-none focus:border-line-strong"
            />
            <Button
              className="mt-4 w-full"
              disabled={code.length < 4 || preview.isPending}
              onClick={() => preview.mutate()}
            >
              {preview.isPending && <Loader2 className="size-3.5 animate-spin" />}
              Continue
            </Button>
          </div>
        )}

        {phase === 'name' && arena && (
          <div className="rounded-sq border border-line bg-surface p-6 text-center">
            <p className="text-sm font-medium">{arena.title}</p>
            <p className="legend mt-1">{arena.skillName}</p>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Your name"
              autoFocus
              className="mt-4 w-full rounded-sq border border-line bg-bg px-3 py-2 text-center text-sm outline-none focus:border-line-strong"
            />
            <Button className="mt-4 w-full" disabled={!name.trim() || join.isPending} onClick={() => join.mutate()}>
              {join.isPending && <Loader2 className="size-3.5 animate-spin" />}
              Join
            </Button>
          </div>
        )}

        {phase === 'lobby' && (
          <div className="rounded-sq border border-line bg-surface p-8 text-center">
            <Loader2 className="mx-auto size-5 animate-spin text-fg-muted" />
            <p className="mt-3 text-sm font-medium">You're in</p>
            <p className="mt-1 text-xs text-fg-muted">Waiting for the host to start…</p>
          </div>
        )}

        {phase === 'playing' && arena?.currentQuestion && (
          <AnimatePresence mode="wait">
            {!answered ? (
              <motion.div
                key={arena.currentQuestion.arenaQuestionId}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
              >
                <p className="legend mb-2 text-center">
                  {arena.currentQuestion.position} / {arena.currentQuestion.totalQuestions}
                </p>
                <div className="rounded-sq border border-line bg-surface p-4">
                  <p className="text-[15px] leading-relaxed">{arena.currentQuestion.stem}</p>
                </div>
                <div className="mt-3 space-y-2">
                  {arena.currentQuestion.options.map((o) => (
                    <button
                      key={o.id}
                      disabled={submitAnswer.isPending}
                      onClick={() => { setSelectedOption(o.id); submitAnswer.mutate(o.id) }}
                      className={`w-full rounded-sq border px-4 py-3 text-left text-sm transition-colors ${
                        selectedOption === o.id
                          ? 'border-available bg-available-bg'
                          : 'border-line bg-surface hover:border-line-strong'
                      }`}
                    >
                      {o.text}
                    </button>
                  ))}
                </div>
              </motion.div>
            ) : (
              <motion.div
                key="feedback"
                initial={{ opacity: 0, scale: 0.97 }}
                animate={{ opacity: 1, scale: 1 }}
                className="rounded-sq border p-8 text-center"
                style={{
                  borderColor: answered.correct ? 'var(--mastered)' : 'var(--locked)',
                  background: answered.correct ? 'var(--mastered-bg)' : 'var(--locked-bg)',
                }}
              >
                {answered.correct ? (
                  <CheckCircle2 className="mx-auto size-8 text-mastered" />
                ) : (
                  <XCircle className="mx-auto size-8 text-fg-subtle" />
                )}
                <p className="mt-3 text-sm font-medium">
                  {answered.correct ? `+${answered.pointsAwarded} points` : 'Not quite'}
                </p>
                <p className="num mt-1 text-2xl font-semibold">{answered.newScore}</p>
                <p className="legend mt-1">total score</p>
                <p className="mt-4 text-xs text-fg-subtle">Waiting for the next question…</p>
              </motion.div>
            )}
          </AnimatePresence>
        )}

        {phase === 'ended' && (
          <div className="text-center">
            <Trophy className="mx-auto size-8 text-mastered" />
            <p className="mt-3 text-lg font-semibold">
              {myRank >= 0 ? `You placed #${leaderboard[myRank].finalRank ?? myRank + 1}` : 'Arena finished'}
            </p>
            {myRank >= 0 && (
              <p className="num mt-1 text-2xl font-semibold">{leaderboard[myRank].score}</p>
            )}
            <div className="mt-6 space-y-1.5 text-left">
              {leaderboard.slice(0, 5).map((p, i) => (
                <div
                  key={p.id}
                  className={`flex items-center gap-3 rounded-sq border px-3 py-2 text-sm ${
                    p.id === participant?.id ? 'border-available bg-available-bg' : 'border-line bg-surface'
                  }`}
                >
                  <span className="num w-5 text-fg-subtle">{p.finalRank ?? i + 1}</span>
                  <span className="flex-1">{p.displayName}</span>
                  <span className="num font-semibold">{p.score}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
