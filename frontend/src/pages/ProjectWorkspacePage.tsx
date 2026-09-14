import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, HelpCircle, Loader2, Send, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'

// ---------------------------------------------------------------------------
// Wire types — mirror the verification module's DTOs.
// ---------------------------------------------------------------------------

interface ProjectSummary {
  id: number
  title: string
  description: string
  brief: string
  levelBand: string
  estMinutes: number
  rubric: string
  requiresViva: boolean
  skillNames: string[]
}

interface RubricCriterion {
  key: string
  label: string
  weight: number
  descriptors: string[]
}

interface SubmissionView {
  id: number
  projectId: number
  status: string
  attemptNo: number
  content: string | null
  draftCount: number
}

interface TurnView {
  id: number
  position: number
  question: string
  anchor: string | null
  intent: string | null
  timeLimitSec: number
  answered: boolean
}

interface VivaResult {
  vivaSessionId: number | null
  status: string
  verdict: string | null
  overallScore: number | null
  turnsCompleted: number
  turnsPlanned: number
  currentTurn: TurnView | null
}

const AI_PURPOSES = ['NONE', 'BRAINSTORMING', 'EXPLANATION', 'DEBUGGING', 'CODE_GENERATION', 'REVIEW', 'WRITING']
const AI_EXTENTS = ['NONE', 'MINOR', 'MODERATE', 'SUBSTANTIAL']

/**
 * Write the submission, then defend it live.
 *
 * <p>One page, three phases in sequence: workspace → viva → verdict. They
 * live together because the whole point is continuity — the questions in
 * phase two are generated from exactly the text written in phase one, and
 * showing that as one unbroken flow is what makes "this came from your own
 * work" legible rather than an assertion in a paragraph of copy.
 */
export function ProjectWorkspacePage() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()

  const { data: project, isLoading: projectLoading } = useQuery({
    queryKey: ['verification', 'project', projectId],
    queryFn: async () => (await api.get<ProjectSummary>(`/verification/projects/${projectId}`)).data,
  })

  const [submission, setSubmission] = useState<SubmissionView | null>(null)
  const [content, setContent] = useState('')
  const [viva, setViva] = useState<VivaResult | null>(null)
  const startedRef = useState(() => ({ current: false }))[0]

  const openDraft = useMutation({
    mutationFn: async () => (await api.post<SubmissionView>(`/verification/projects/${projectId}/draft`)).data,
    onSuccess: (data) => {
      setSubmission(data)
      setContent(data.content ?? '')
    },
  })

  useEffect(() => {
    if (!startedRef.current && projectId) {
      startedRef.current = true
      openDraft.mutate()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  const saveDraft = useMutation({
    mutationFn: async () => {
      if (!submission) throw new Error('No draft open.')
      return (
        await api.post<SubmissionView>(`/verification/submissions/${submission.id}/save`, { content })
      ).data
    },
    onSuccess: (data) => {
      setSubmission(data)
      toast.success('Draft saved.')
    },
  })

  const submitWork = useMutation({
    mutationFn: async (form: { toolUsed: string; purpose: string; extent: string; detail: string }) => {
      if (!submission) throw new Error('No draft open.')
      // The content typed on this page lives only in local state until it is
      // saved — submit must persist it first, in the same action, rather than
      // relying on the learner having clicked "Save draft" separately. The
      // server would otherwise see whatever was last saved (possibly
      // nothing), not what's actually on screen.
      await api.post<SubmissionView>(`/verification/submissions/${submission.id}/save`, { content })
      return (
        await api.post<VivaResult>(`/verification/submissions/${submission.id}/submit`, form)
      ).data
    },
    onSuccess: (data) => {
      setSubmission((s) => (s ? { ...s, status: 'IN_VIVA' } : s))
      setViva(data)
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not submit.')),
  })

  if (projectLoading || openDraft.isPending || !project) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <Loader2 className="size-5 animate-spin text-fg-muted" />
      </div>
    )
  }

  if (viva?.status === 'COMPLETED') {
    return <VerdictScreen viva={viva} onExit={() => navigate('/projects')} />
  }

  if (viva?.currentTurn) {
    return (
      <VivaChat
        viva={viva}
        onAnswered={(next) => setViva(next)}
      />
    )
  }

  return (
    <div className="mx-auto max-w-2xl">
      <header className="mb-5 border-b border-line pb-4">
        <h1 className="text-[19px] font-semibold">{project.title}</h1>
        <p className="mt-1 text-[13px] text-fg-muted">{project.description}</p>
      </header>

      <section className="mb-5">
        <p className="legend mb-1.5">Brief</p>
        <p className="whitespace-pre-wrap text-sm text-fg-muted">{project.brief}</p>
      </section>

      <RubricView rubricJson={project.rubric} />

      <section className="mt-6">
        <div className="mb-1.5 flex items-center justify-between">
          <label htmlFor="submission-content" className="legend">
            Your submission
          </label>
          <span className="legend">{submission?.draftCount ?? 0} draft saves</span>
        </div>
        <textarea
          id="submission-content"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={14}
          placeholder="Paste or write your solution here — code, explanation, whatever the brief calls for."
          className="w-full rounded-sq border border-line bg-surface p-3 font-mono text-[13px] leading-relaxed text-fg outline-none focus:border-line-strong"
        />
        <div className="mt-2 flex justify-end">
          <Button variant="outline" size="sm" disabled={saveDraft.isPending} onClick={() => saveDraft.mutate()}>
            {saveDraft.isPending && <Loader2 className="size-3.5 animate-spin" />}
            Save draft
          </Button>
        </div>
      </section>

      <SubmitPanel
        disabled={!content.trim()}
        submitting={submitWork.isPending}
        onSubmit={(form) => submitWork.mutate(form)}
      />
    </div>
  )
}

function RubricView({ rubricJson }: { rubricJson: string }) {
  const criteria: RubricCriterion[] = (() => {
    try {
      return JSON.parse(rubricJson)
    } catch {
      return []
    }
  })()
  if (!criteria.length) return null

  return (
    <section className="rounded-sq border border-line bg-surface p-4">
      <p className="legend mb-2.5">What's assessed</p>
      <div className="space-y-2.5">
        {criteria.map((c) => (
          <div key={c.key}>
            <div className="flex items-center justify-between text-xs">
              <span className="font-medium text-fg">{c.label}</span>
              <span className="num text-fg-subtle">{Math.round(c.weight * 100)}%</span>
            </div>
            <p className="mt-0.5 text-xs text-fg-subtle">{c.descriptors.join(' · ')}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

function SubmitPanel({
  disabled,
  submitting,
  onSubmit,
}: {
  disabled: boolean
  submitting: boolean
  onSubmit: (form: { toolUsed: string; purpose: string; extent: string; detail: string }) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const [toolUsed, setToolUsed] = useState('')
  const [purpose, setPurpose] = useState('NONE')
  const [extent, setExtent] = useState('NONE')
  const [detail, setDetail] = useState('')

  return (
    <section className="mt-6 rounded-sq border border-line bg-surface p-4">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-center justify-between text-left"
      >
        <span className="text-sm font-medium">AI-use declaration</span>
        <span className="legend">{expanded ? 'hide' : 'declare & submit'}</span>
      </button>
      <p className="mt-1 text-xs text-fg-subtle">
        Declared, not banned. What matters is your judgement about the output —
        that's what the viva probes, not whether you used a tool at all.
      </p>

      {expanded && (
        <div className="mt-4 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <Field label="Tool used (optional)">
              <input
                value={toolUsed}
                onChange={(e) => setToolUsed(e.target.value)}
                placeholder="e.g. ChatGPT, Copilot"
                className="w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong"
              />
            </Field>
            <Field label="Purpose">
              <select
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
                className="w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong"
              >
                {AI_PURPOSES.map((p) => (
                  <option key={p} value={p}>
                    {p.toLowerCase().replaceAll('_', ' ')}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <Field label="Extent">
            <select
              value={extent}
              onChange={(e) => setExtent(e.target.value)}
              className="w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong"
            >
              {AI_EXTENTS.map((ex) => (
                <option key={ex} value={ex}>
                  {ex.toLowerCase()}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Detail (optional)">
            <input
              value={detail}
              onChange={(e) => setDetail(e.target.value)}
              placeholder="e.g. asked it to explain LinkedHashMap's access-order mode"
              className="w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong"
            />
          </Field>

          <Button
            className="w-full"
            disabled={disabled || submitting}
            onClick={() => onSubmit({ toolUsed, purpose, extent, detail })}
          >
            {submitting ? <Loader2 className="size-3.5 animate-spin" /> : <Send className="size-3.5" />}
            Submit &amp; start viva
          </Button>
        </div>
      )}
    </section>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="legend mb-1 block">{label}</span>
      {children}
    </label>
  )
}

const INTENT_LABEL: Record<string, string> = {
  DESIGN_CHOICE: 'design choice',
  TRADE_OFF: 'trade-off',
  EDGE_CASE: 'edge case',
  ALTERNATIVE: 'alternative approach',
  FAILURE_MODE: 'failure mode',
  CONCEPT_CHECK: 'concept check',
}

function VivaChat({ viva, onAnswered }: { viva: VivaResult; onAnswered: (next: VivaResult) => void }) {
  const [answer, setAnswer] = useState('')
  const turn = viva.currentTurn!

  const submitAnswer = useMutation({
    mutationFn: async () =>
      (
        await api.post<VivaResult>(`/verification/viva/${viva.vivaSessionId}/answer`, {
          turnId: turn.id,
          answer,
        })
      ).data,
    onSuccess: (data) => {
      setAnswer('')
      onAnswered(data)
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not submit that answer.')),
  })

  return (
    <div className="mx-auto max-w-2xl">
      <header className="mb-5 border-b border-line pb-4">
        <p className="legend">
          Viva · question {turn.position} of up to {viva.turnsPlanned}
        </p>
        <p className="mt-1 text-xs text-fg-subtle">
          Answer in your own words. There's no going back once you submit.
        </p>
      </header>

      <AnimatePresence mode="wait">
        <motion.div
          key={turn.id}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.18 }}
        >
          <div className="rounded-sq border border-available/30 bg-available-bg px-4 py-3.5">
            {turn.intent && (
              <span className="legend text-available">{INTENT_LABEL[turn.intent] ?? turn.intent}</span>
            )}
            <p className="mt-1.5 text-[15px] leading-relaxed text-fg">{turn.question}</p>
            {turn.anchor && (
              <p className="mt-2 text-xs text-fg-subtle">re: {turn.anchor}</p>
            )}
          </div>

          <textarea
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            rows={6}
            placeholder="Your answer…"
            autoFocus
            className="mt-4 w-full rounded-sq border border-line bg-surface p-3 text-sm leading-relaxed outline-none focus:border-line-strong"
          />

          <Button
            className="mt-3"
            disabled={!answer.trim() || submitAnswer.isPending}
            onClick={() => submitAnswer.mutate()}
          >
            {submitAnswer.isPending ? (
              <>
                <Loader2 className="size-3.5 animate-spin" /> Grading…
              </>
            ) : (
              <>
                <Send className="size-3.5" /> Submit answer
              </>
            )}
          </Button>
        </motion.div>
      </AnimatePresence>
    </div>
  )
}

function VerdictScreen({ viva, onExit }: { viva: VivaResult; onExit: () => void }) {
  const verdict = viva.verdict
  const tone = verdict === 'VERIFIED' ? 'mastered' : verdict === 'INCONCLUSIVE' ? 'decaying' : 'locked'
  const Icon = verdict === 'VERIFIED' ? CheckCircle2 : verdict === 'INCONCLUSIVE' ? HelpCircle : XCircle

  const message = {
    VERIFIED: 'Submission accepted. Understanding verified — evidence has been added to your passport.',
    NOT_VERIFIED: 'Submission accepted. Understanding not verified — the defence did not hold up.',
    INCONCLUSIVE: 'The evaluation was not confident enough to trust either way. This has been sent for human review.',
  }[verdict ?? 'NOT_VERIFIED']

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className="mx-auto max-w-lg py-16 text-center"
    >
      <Icon className="mx-auto size-8" style={{ color: `var(--${tone})` }} />
      <p className="legend mt-4" style={{ color: `var(--${tone})` }}>
        {verdict?.replaceAll('_', ' ')}
      </p>
      <p className="mx-auto mt-2 max-w-sm text-sm text-fg-muted">{message}</p>

      {viva.overallScore !== null && (
        <p className="num mt-4 text-2xl font-semibold">{Math.round((viva.overallScore ?? 0) * 100)}%</p>
      )}

      <Button className="mt-8" onClick={onExit}>
        Back to projects
      </Button>
    </motion.div>
  )
}
