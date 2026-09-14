import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import {
  ArrowLeft,
  CheckCircle2,
  Loader2,
  Sparkles,
  XCircle,
} from 'lucide-react'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'

// ---------------------------------------------------------------------------
// Wire types — mirrors DiagnosticDtos on the backend exactly. Kept as one
// block rather than scattered so the contract is legible in one place; if the
// backend record shape changes, this is the one file that needs to follow.
// ---------------------------------------------------------------------------

type TerminationReason =
  | 'CONFIDENCE_REACHED'
  | 'ITEM_CAP'
  | 'NO_ITEMS_AVAILABLE'
  | 'TIME_LIMIT'
  | 'ABANDONED'

interface OptionView {
  id: number
  text: string
}

interface ItemView {
  id: number
  stem: string
  type: string
  options: OptionView[]
}

interface DiagnosticResult {
  skillId: number
  skillName: string
  ability: number
  standardError: number
  mastery: number
  mastered: boolean
  itemsServed: number
  itemsCorrect: number
  reason: TerminationReason
  explanation: string
}

interface NextItemResponse {
  assessmentId: number
  finished: boolean
  item: ItemView | null
  itemsServed: number
  maxItems: number
  confidence: number
  result: DiagnosticResult | null
}

interface Feedback {
  correct: boolean
  explanation: string
  misconception: string | null
  remediation: string | null
}

interface Progress {
  masteryAfter: number
  masteryBefore: number
  masteryDelta: number
  ability: number
  standardError: number
  masteryJustReached: boolean
}

interface AnswerResponse {
  feedback: Feedback
  next: NextItemResponse
  progress: Progress
}

/**
 * The adaptive diagnostic, run end to end.
 *
 * <p>State is a small machine rather than a pile of booleans: `next` holds
 * whatever the server last returned (a question, or a finished result), and
 * `answered` holds the feedback for the question just submitted. Feedback and
 * the next question arrive in the same response — the backend cannot serve
 * item N+1 without scoring item N first — but they are shown one at a time on
 * purpose. Revealing the next question the instant an answer lands would
 * bury the misconception explanation, which is the one thing here a plain
 * quiz does not have.
 */
export function DiagnosticPage() {
  const { skillId } = useParams<{ skillId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [state, setState] = useState<NextItemResponse | null>(null)
  const [answered, setAnswered] = useState<AnswerResponse | null>(null)
  const [selected, setSelected] = useState<number | null>(null)
  const [startError, setStartError] = useState<string | null>(null)
  const questionStartedAt = useRef<number>(Date.now())

  const start = useMutation({
    mutationFn: async () =>
      (await api.post<NextItemResponse>(`/diagnostics/start?skillId=${skillId}`)).data,
    onSuccess: (data) => {
      setState(data)
      questionStartedAt.current = Date.now()
    },
    onError: (error) => setStartError(errorMessage(error, 'Could not start the diagnostic.')),
  })

  const answer = useMutation({
    mutationFn: async (optionId: number) => {
      if (!state?.item) throw new Error('No active question.')
      const responseTimeMs = Math.max(1, Date.now() - questionStartedAt.current)
      return (
        await api.post<AnswerResponse>(`/diagnostics/${state.assessmentId}/answer`, {
          itemId: state.item.id,
          optionId,
          responseTimeMs,
        })
      ).data
    },
    onSuccess: (data) => {
      setAnswered(data)
      // The skill graph's mastery fill and lock state are stale the moment
      // this lands — invalidate rather than leave the tree showing a number
      // this exact answer just changed.
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })

  // Guards against firing "start" twice for the same skill — StrictMode
  // intentionally double-invokes effects in development, and a POST that
  // creates-or-resumes is exactly the kind of call where that matters: two
  // concurrent starts can each pass "is anything in progress?" before either
  // commits and both try to create an assessment. The backend now rejects the
  // loser at the database (V13) rather than corrupting state, but the honest
  // fix is not sending the duplicate request in the first place.
  const startedForSkill = useRef<string | undefined>(undefined)
  useEffect(() => {
    if (startedForSkill.current === skillId) return
    startedForSkill.current = skillId
    start.mutate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [skillId])

  function submit() {
    if (selected === null) return
    answer.mutate(selected)
  }

  function continueToNext() {
    if (!answered) return
    setState(answered.next)
    setAnswered(null)
    setSelected(null)
    questionStartedAt.current = Date.now()
  }

  // ---- start failed (e.g. bank exhausted before an assessment could begin) --
  if (startError) {
    return (
      <div className="mx-auto max-w-lg py-20 text-center">
        <p className="text-sm font-medium">Could not start</p>
        <p className="mt-2 text-sm text-fg-muted">{startError}</p>
        <Button asChild variant="outline" size="sm" className="mt-6">
          <Link to="/skills">
            <ArrowLeft className="size-3.5" /> Back to skill tree
          </Link>
        </Button>
      </div>
    )
  }

  // ---- loading (initial start, or waiting on an answer to be scored) -------
  if (!state) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="size-5 animate-spin text-fg-muted" />
      </div>
    )
  }

  // ---- finished — the result screen -----------------------------------------
  if (state.finished && state.result) {
    return <ResultScreen result={state.result} onExit={() => navigate('/skills')} />
  }

  const item = state.item!
  const confidencePct = Math.round(state.confidence * 100)

  return (
    <div className="mx-auto max-w-2xl">
      <Link
        to="/skills"
        className="inline-flex items-center gap-1.5 text-xs text-fg-muted transition-colors hover:text-fg"
      >
        <ArrowLeft className="size-3.5" /> Exit diagnostic
      </Link>

      {/* Instrument readout, not a question counter. "Question 3" implies a
          fixed length this test does not have; a confidence bar says
          honestly how close the estimate is to done. */}
      <div className="mt-5 border-b border-line pb-4">
        <div className="flex items-center justify-between text-xs text-fg-muted">
          <span className="legend">Estimate confidence</span>
          <span className="num">{confidencePct}%</span>
        </div>
        <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-bg-sunken">
          <motion.div
            className="h-full rounded-full bg-available"
            initial={false}
            animate={{ width: `${confidencePct}%` }}
            transition={{ duration: 0.4, ease: 'easeOut' }}
          />
        </div>
        <div className="mt-2 flex items-center justify-between text-[11px] text-fg-subtle">
          <span>
            {state.itemsServed} of up to {state.maxItems} questions
          </span>
          {answered && (
            <span className="num">
              θ {answered.progress.ability.toFixed(2)} · SE{' '}
              {answered.progress.standardError.toFixed(2)}
            </span>
          )}
        </div>
      </div>

      <AnimatePresence mode="wait">
        {!answered ? (
          <motion.div
            key={item.id}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.18 }}
            className="py-8"
          >
            <p className="text-[17px] leading-relaxed">{item.stem}</p>

            <div className="mt-6 space-y-2">
              {item.options.map((option) => {
                const isSelected = selected === option.id
                return (
                  <button
                    key={option.id}
                    type="button"
                    onClick={() => setSelected(option.id)}
                    className={`w-full rounded-sq border px-4 py-3 text-left text-sm transition-colors ${
                      isSelected
                        ? 'border-available bg-available-bg text-fg'
                        : 'border-line bg-surface hover:border-line-strong'
                    }`}
                  >
                    {option.text}
                  </button>
                )
              })}
            </div>

            <Button
              className="mt-6"
              disabled={selected === null || answer.isPending}
              onClick={submit}
            >
              {answer.isPending && <Loader2 className="size-3.5 animate-spin" />}
              Submit answer
            </Button>
          </motion.div>
        ) : (
          <FeedbackPanel
            key="feedback"
            feedback={answered.feedback}
            progress={answered.progress}
            onContinue={continueToNext}
          />
        )}
      </AnimatePresence>
    </div>
  )
}

/**
 * What the learner is told after answering.
 *
 * <p>A quiz says "wrong". This names the specific belief behind the wrong
 * answer and what to do about it — the misconception tag is the whole reason
 * item authoring bothers to classify distractors instead of just marking one
 * option correct.
 */
function FeedbackPanel({
  feedback,
  progress,
  onContinue,
}: {
  feedback: Feedback
  progress: Progress
  onContinue: () => void
}) {
  const tone = feedback.correct ? 'mastered' : 'decaying'

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -6 }}
      transition={{ duration: 0.18 }}
      className="py-8"
    >
      <div
        className="flex items-start gap-3 rounded-sq border px-4 py-3.5"
        style={{ borderColor: `var(--${tone})`, background: `var(--${tone}-bg)` }}
      >
        {feedback.correct ? (
          <CheckCircle2 className="mt-0.5 size-4 shrink-0" style={{ color: `var(--${tone})` }} />
        ) : (
          <XCircle className="mt-0.5 size-4 shrink-0" style={{ color: `var(--${tone})` }} />
        )}
        <div>
          <p className="text-sm font-medium">{feedback.correct ? 'Correct' : 'Not quite'}</p>
          {feedback.misconception && (
            <p className="mt-1 text-sm">
              <span className="font-medium">Likely reason:</span> {feedback.misconception}
            </p>
          )}
          <p className="mt-1 text-sm text-fg-muted">{feedback.explanation}</p>
          {feedback.remediation && (
            <p className="mt-2 text-sm text-fg-muted">
              <span className="font-medium text-fg">Try this: </span>
              {feedback.remediation}
            </p>
          )}
        </div>
      </div>

      {/* The delta lands here, not as a separate toast — the point is to show
          the answer moving the number, not to interrupt with a notification. */}
      <div className="mt-4 flex items-center gap-4 text-xs text-fg-muted">
        <span className="num">
          mastery {(progress.masteryBefore * 100).toFixed(0)}% →{' '}
          <span className="font-medium text-fg">{(progress.masteryAfter * 100).toFixed(0)}%</span>
        </span>
        {progress.masteryJustReached && (
          <motion.span
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className="flex items-center gap-1 rounded-full bg-mastered-bg px-2 py-0.5 text-mastered"
          >
            <Sparkles className="size-3" /> Mastery reached
          </motion.span>
        )}
      </div>

      <Button className="mt-6" onClick={onContinue}>
        Continue
      </Button>
    </motion.div>
  )
}

/**
 * The end screen.
 *
 * <p>Leads with the stopping reason in plain language, because a test that
 * ends after eight questions looks broken unless the learner is told it
 * ended early on purpose — the whole point of showing confidence instead of
 * a question count.
 */
function ResultScreen({ result, onExit }: { result: DiagnosticResult; onExit: () => void }) {
  const reasonTone: Record<TerminationReason, string> = {
    CONFIDENCE_REACHED: 'mastered',
    ITEM_CAP: 'available',
    NO_ITEMS_AVAILABLE: 'decaying',
    TIME_LIMIT: 'decaying',
    ABANDONED: 'locked',
  }
  const tone = reasonTone[result.reason]

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2 }}
      className="mx-auto max-w-lg py-14 text-center"
    >
      <p className="legend" style={{ color: `var(--${tone})` }}>
        {result.reason.replaceAll('_', ' ')}
      </p>
      <h1 className="mt-2 text-2xl font-semibold tracking-tight">{result.skillName}</h1>
      <p className="mx-auto mt-2 max-w-sm text-sm text-fg-muted">{result.explanation}</p>

      <div className="mt-8 grid grid-cols-3 gap-4 border-y border-line py-5">
        <Metric label="Mastery" value={`${Math.round(result.mastery * 100)}%`} />
        <Metric label="Ability (θ)" value={result.ability.toFixed(2)} />
        <Metric
          label="Correct"
          value={`${result.itemsCorrect}/${result.itemsServed}`}
        />
      </div>

      <div className="mt-5 flex items-center justify-center gap-2 text-xs text-fg-subtle">
        <span className="num">standard error {result.standardError.toFixed(2)}</span>
        {result.mastered && (
          <span className="flex items-center gap-1 rounded-full bg-mastered-bg px-2 py-0.5 text-mastered">
            <CheckCircle2 className="size-3" /> Mastered
          </span>
        )}
      </div>

      <Button className="mt-8" onClick={onExit}>
        Back to skill tree
      </Button>
    </motion.div>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dd className="num text-lg leading-none">{value}</dd>
      <dt className="legend mt-1.5">{label}</dt>
    </div>
  )
}
