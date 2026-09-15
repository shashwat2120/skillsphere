import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, Loader2, Plus } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'

const inputClass =
  'w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong'

const LEVEL_BANDS = ['FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']
const LESSON_TYPES = ['TEXT', 'VIDEO', 'RESOURCE', 'INTERACTIVE']

interface CourseSummary {
  id: number
  title: string
  slug: string
  subtitle: string | null
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  levelBand: string | null
  moduleCount: number
}

interface SkillTag {
  skillId: number
  skillName: string
  weight: string
}

interface LessonResponse {
  id: number
  title: string
  type: string
  durationSeconds: number
  preview: boolean
  skills: SkillTag[]
}

interface ModuleResponse {
  id: number
  title: string
  summary: string | null
  lessons: LessonResponse[]
}

interface CourseDetail {
  course: CourseSummary
  modules: ModuleResponse[]
}

interface ItemOption {
  id: number
  text: string
  correct: boolean
  misconceptionId: number | null
  misconceptionName: string | null
}

interface ItemResponse {
  id: number
  skillId: number
  stem: string
  type: string
  status: 'DRAFT' | 'ACTIVE' | 'RETIRED'
  declaredDifficulty: string | null
  effectiveDifficulty: string | null
  calibrated: boolean
  timesSeen: number
  pValue: string | null
  options: ItemOption[]
}

interface ItemHealth {
  id: number
  stem: string
  skillId: number
  timesSeen: number
  pValue: string | null
  discrimination: string | null
  diagnosis: string
}

interface Misconception {
  id: number
  skillId: number
  name: string
  description: string | null
  remediationHint: string
  timesObserved: number
}

type Tab = 'courses' | 'items'
const ITEM_STATUS_TONE: Record<ItemResponse['status'], string> = {
  DRAFT: 'available',
  ACTIVE: 'mastered',
  RETIRED: 'locked',
}
const COURSE_STATUS_TONE: Record<CourseSummary['status'], string> = {
  DRAFT: 'available',
  PUBLISHED: 'mastered',
  ARCHIVED: 'locked',
}

/**
 * Course and item authoring — the surface that was missing entirely until
 * this session found it: InstructorCourseController and ItemBankController
 * have existed since Phases 3 and 4, gated to INSTRUCTOR/ADMIN, with no
 * frontend page calling either. An approved instructor could sign in and
 * have nothing to actually author.
 *
 * <p>Item authoring in particular matters more than it looks: every MCQ item
 * needs its misconceptions defined first for the wrong-answer feedback to
 * mean anything, so the item form pulls from this skill's misconception
 * library rather than asking the author to remember ids.
 */
export function InstructorStudioPage() {
  const [tab, setTab] = useState<Tab>('courses')

  return (
    <div className="mx-auto max-w-3xl">
      <header className="mb-6 border-b border-line pb-5">
        <h1 className="text-[19px] font-semibold">Studio</h1>
        <p className="mt-1 text-[13px] text-fg-muted">
          Build courses and author the items the adaptive engine actually asks.
        </p>
        <div className="mt-4 flex gap-1.5">
          <ToggleButton active={tab === 'courses'} onClick={() => setTab('courses')}>
            Courses
          </ToggleButton>
          <ToggleButton active={tab === 'items'} onClick={() => setTab('items')}>
            Item bank
          </ToggleButton>
        </div>
      </header>

      {tab === 'courses' ? <CoursesView /> : <ItemsView />}
    </div>
  )
}

function ToggleButton({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      className={
        'rounded-sq px-3 py-1.5 text-sm font-medium transition-colors ' +
        (active ? 'bg-bg-sunken text-fg' : 'text-fg-muted hover:text-fg')
      }
    >
      {children}
    </button>
  )
}

function Tone({ tone, children }: { tone: string; children: React.ReactNode }) {
  return (
    <span
      className="legend rounded-full border px-2 py-0.5"
      style={{ color: `var(--${tone})`, borderColor: `var(--${tone})`, background: `var(--${tone}-bg)` }}
    >
      {children}
    </span>
  )
}

// -----------------------------------------------------------------------
// Courses
// -----------------------------------------------------------------------

function CoursesView() {
  const queryClient = useQueryClient()
  const [creating, setCreating] = useState(false)

  const { data: courses, isLoading } = useQuery({
    queryKey: ['instructor', 'courses'],
    queryFn: async () => (await api.get<CourseSummary[]>('/instructor/courses')).data,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['instructor', 'courses'] })

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="legend">Your courses {courses ? `(${courses.length})` : ''}</h2>
        <Button size="sm" variant="outline" onClick={() => setCreating((c) => !c)}>
          <Plus className="size-3.5" /> New course
        </Button>
      </div>

      {creating && (
        <CreateCourseForm
          onDone={() => {
            setCreating(false)
            invalidate()
          }}
          onCancel={() => setCreating(false)}
        />
      )}

      {isLoading && <div className="skeleton h-40 w-full rounded-sq-lg" />}

      {!isLoading && !courses?.length && (
        <div className="rounded-sq border border-dashed border-line py-10 text-center text-sm text-fg-muted">
          No courses yet — create one to start building content.
        </div>
      )}

      <div className="space-y-2">
        {courses?.map((c) => (
          <CourseRow key={c.id} course={c} onChanged={invalidate} />
        ))}
      </div>
    </div>
  )
}

function CreateCourseForm({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const [title, setTitle] = useState('')
  const [slug, setSlug] = useState('')
  const [subtitle, setSubtitle] = useState('')
  const [levelBand, setLevelBand] = useState('FOUNDATIONAL')

  const create = useMutation({
    mutationFn: async () => api.post('/instructor/courses', { title, slug, subtitle, levelBand }),
    onSuccess: () => {
      toast.success('Course created.')
      onDone()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not create the course.')),
  })

  return (
    <div className="mb-3 grid grid-cols-2 gap-2 rounded-sq border border-line bg-surface p-3">
      <div>
        <label className="legend mb-1 block">Title</label>
        <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} placeholder="Java Collections Deep Dive" />
      </div>
      <div>
        <label className="legend mb-1 block">Slug</label>
        <input value={slug} onChange={(e) => setSlug(e.target.value)} className={inputClass} placeholder="java-collections-deep-dive" />
      </div>
      <div className="col-span-2">
        <label className="legend mb-1 block">Subtitle</label>
        <input value={subtitle} onChange={(e) => setSubtitle(e.target.value)} className={inputClass} />
      </div>
      <div>
        <label className="legend mb-1 block">Level</label>
        <select value={levelBand} onChange={(e) => setLevelBand(e.target.value)} className={inputClass}>
          {LEVEL_BANDS.map((b) => (
            <option key={b} value={b}>
              {b.toLowerCase()}
            </option>
          ))}
        </select>
      </div>
      <div className="col-span-2 flex justify-end gap-1.5">
        <Button size="sm" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={!title.trim() || !slug.trim() || create.isPending}
          onClick={() => create.mutate()}
        >
          {create.isPending && <Loader2 className="size-3.5 animate-spin" />} Create
        </Button>
      </div>
    </div>
  )
}

function CourseRow({ course, onChanged }: { course: CourseSummary; onChanged: () => void }) {
  const [expanded, setExpanded] = useState(false)

  const { data: detail, refetch, isLoading } = useQuery({
    queryKey: ['instructor', 'courses', course.id],
    queryFn: async () => (await api.get<CourseDetail>(`/instructor/courses/${course.id}`)).data,
    enabled: expanded,
  })

  const publish = useMutation({
    mutationFn: async () => api.post(`/instructor/courses/${course.id}/publish`),
    onSuccess: () => {
      toast.success('Course published.')
      onChanged()
      refetch()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not publish.')),
  })

  const archive = useMutation({
    mutationFn: async () => api.post(`/instructor/courses/${course.id}/archive`),
    onSuccess: () => {
      toast.success('Course archived.')
      onChanged()
      refetch()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not archive.')),
  })

  return (
    <div className="overflow-hidden rounded-sq border border-line bg-surface">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-bg-sunken"
      >
        <span className="flex-1 text-sm font-medium">{course.title}</span>
        <span className="text-xs text-fg-subtle">
          {course.moduleCount} module{course.moduleCount === 1 ? '' : 's'}
        </span>
        <Tone tone={COURSE_STATUS_TONE[course.status]}>{course.status.toLowerCase()}</Tone>
        <ChevronDown className={`size-3.5 text-fg-subtle transition-transform ${expanded ? 'rotate-180' : ''}`} />
      </button>

      {expanded && (
        <div className="space-y-4 border-t border-line px-4 py-3">
          {isLoading && <div className="skeleton h-20 w-full rounded-sq" />}

          {detail && (
            <>
              <div className="flex items-center gap-1.5">
                {course.status === 'DRAFT' && (
                  <Button size="sm" variant="outline" disabled={publish.isPending} onClick={() => publish.mutate()}>
                    {publish.isPending && <Loader2 className="size-3.5 animate-spin" />} Publish
                  </Button>
                )}
                {course.status !== 'ARCHIVED' && (
                  <Button size="sm" variant="ghost" disabled={archive.isPending} onClick={() => archive.mutate()}>
                    Archive
                  </Button>
                )}
              </div>

              <div className="space-y-3">
                {detail.modules.map((m) => (
                  <ModuleBlock key={m.id} module={m} onChanged={refetch} />
                ))}
              </div>

              <AddModuleForm courseId={course.id} onDone={refetch} />
            </>
          )}
        </div>
      )}
    </div>
  )
}

function AddModuleForm({ courseId, onDone }: { courseId: number; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')

  const add = useMutation({
    mutationFn: async () => api.post(`/instructor/courses/${courseId}/modules`, { title }),
    onSuccess: () => {
      toast.success('Module added.')
      setOpen(false)
      setTitle('')
      onDone()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not add the module.')),
  })

  if (!open) {
    return (
      <Button size="sm" variant="ghost" onClick={() => setOpen(true)}>
        <Plus className="size-3.5" /> Add module
      </Button>
    )
  }

  return (
    <div className="flex items-center gap-1.5">
      <input
        autoFocus
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Module title"
        className={inputClass}
      />
      <Button size="sm" variant="outline" disabled={!title.trim() || add.isPending} onClick={() => add.mutate()}>
        {add.isPending ? <Loader2 className="size-3.5 animate-spin" /> : 'Add'}
      </Button>
      <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>
        Cancel
      </Button>
    </div>
  )
}

function ModuleBlock({
  module: mod,
  onChanged,
}: {
  module: ModuleResponse
  onChanged: () => void
}) {
  return (
    <div className="rounded-sq border border-line bg-bg-sunken p-3">
      <p className="text-sm font-medium">{mod.title}</p>
      {mod.summary && <p className="mt-0.5 text-xs text-fg-muted">{mod.summary}</p>}

      <div className="mt-2 space-y-1.5">
        {mod.lessons.map((l) => (
          <LessonRow key={l.id} lesson={l} onChanged={onChanged} />
        ))}
      </div>

      <div className="mt-2">
        <AddLessonForm moduleId={mod.id} onDone={onChanged} />
      </div>
    </div>
  )
}

function AddLessonForm({ moduleId, onDone }: { moduleId: number; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [type, setType] = useState('TEXT')

  const add = useMutation({
    mutationFn: async () => api.post(`/instructor/courses/modules/${moduleId}/lessons`, { title, type }),
    onSuccess: () => {
      toast.success('Lesson added.')
      setOpen(false)
      setTitle('')
      onDone()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not add the lesson.')),
  })

  if (!open) {
    return (
      <Button size="sm" variant="ghost" onClick={() => setOpen(true)}>
        <Plus className="size-3.5" /> Add lesson
      </Button>
    )
  }

  return (
    <div className="flex items-center gap-1.5">
      <input
        autoFocus
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="Lesson title"
        className={inputClass}
      />
      <select value={type} onChange={(e) => setType(e.target.value)} className={inputClass + ' w-32'}>
        {LESSON_TYPES.map((t) => (
          <option key={t} value={t}>
            {t.toLowerCase()}
          </option>
        ))}
      </select>
      <Button size="sm" variant="outline" disabled={!title.trim() || add.isPending} onClick={() => add.mutate()}>
        {add.isPending ? <Loader2 className="size-3.5 animate-spin" /> : 'Add'}
      </Button>
      <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>
        Cancel
      </Button>
    </div>
  )
}

function LessonRow({ lesson, onChanged }: { lesson: LessonResponse; onChanged: () => void }) {
  const [tagging, setTagging] = useState(false)
  const [skillId, setSkillId] = useState('')
  const [weight, setWeight] = useState('1')

  const { data: skills } = useQuery({
    queryKey: ['skills', 'ready-list'],
    queryFn: async () => (await api.get<{ id: number; name: string }[]>('/skills')).data,
    enabled: tagging,
  })

  const tag = useMutation({
    mutationFn: async () =>
      api.post(`/instructor/courses/lessons/${lesson.id}/skills`, { skillId: Number(skillId), weight }),
    onSuccess: () => {
      toast.success('Skill tagged.')
      setTagging(false)
      setSkillId('')
      onChanged()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not tag the skill.')),
  })

  return (
    <div className="rounded-sq bg-surface px-2.5 py-2 text-sm">
      <div className="flex items-center gap-2">
        <span className="flex-1">{lesson.title}</span>
        <span className="text-xs text-fg-subtle">{lesson.type.toLowerCase()}</span>
        <button onClick={() => setTagging((t) => !t)} className="text-xs font-medium text-accent hover:underline">
          + tag skill
        </button>
      </div>
      {!!lesson.skills.length && (
        <div className="mt-1.5 flex flex-wrap gap-1">
          {lesson.skills.map((s) => (
            <span key={s.skillId} className="legend rounded-full border border-line-strong px-2 py-0.5">
              {s.skillName}
            </span>
          ))}
        </div>
      )}
      {tagging && (
        <div className="mt-2 flex items-center gap-1.5">
          <select value={skillId} onChange={(e) => setSkillId(e.target.value)} className={inputClass + ' flex-1'}>
            <option value="">Choose a skill…</option>
            {skills?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <input
            type="number"
            min={0.01}
            max={1}
            step="0.01"
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
            className={inputClass + ' w-20'}
            title="How much of the lesson is about this skill"
          />
          <Button size="sm" variant="outline" disabled={!skillId || tag.isPending} onClick={() => tag.mutate()}>
            {tag.isPending ? <Loader2 className="size-3.5 animate-spin" /> : 'Tag'}
          </Button>
        </div>
      )}
    </div>
  )
}

// -----------------------------------------------------------------------
// Item bank
// -----------------------------------------------------------------------

function ItemsView() {
  const [skillId, setSkillId] = useState('')
  const [creating, setCreating] = useState(false)
  const queryClient = useQueryClient()

  const { data: skills } = useQuery({
    queryKey: ['skills', 'ready-list'],
    queryFn: async () => (await api.get<{ id: number; name: string }[]>('/skills')).data,
  })

  const { data: items, isLoading } = useQuery({
    queryKey: ['instructor', 'items', skillId],
    queryFn: async () => (await api.get<ItemResponse[]>('/instructor/items', { params: { skillId } })).data,
    enabled: !!skillId,
  })

  const { data: misconceptions } = useQuery({
    queryKey: ['instructor', 'misconceptions', skillId],
    queryFn: async () =>
      (await api.get<Misconception[]>('/instructor/items/misconceptions', { params: { skillId } })).data,
    enabled: !!skillId,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['instructor', 'items', skillId] })
    queryClient.invalidateQueries({ queryKey: ['instructor', 'misconceptions', skillId] })
  }

  return (
    <div className="space-y-6">
      <div>
        <label className="legend mb-1 block">Skill</label>
        <select value={skillId} onChange={(e) => setSkillId(e.target.value)} className={inputClass}>
          <option value="">Choose a skill to author for…</option>
          {skills?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>

      {skillId && (
        <>
          <MisconceptionLibrary skillId={Number(skillId)} misconceptions={misconceptions ?? []} onChanged={invalidate} />

          <div>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="legend">Items {items ? `(${items.length})` : ''}</h2>
              <Button size="sm" variant="outline" onClick={() => setCreating((c) => !c)}>
                <Plus className="size-3.5" /> New item
              </Button>
            </div>

            {creating && (
              <CreateItemForm
                skillId={Number(skillId)}
                misconceptions={misconceptions ?? []}
                onDone={() => {
                  setCreating(false)
                  invalidate()
                }}
                onCancel={() => setCreating(false)}
              />
            )}

            {isLoading && <div className="skeleton h-40 w-full rounded-sq-lg" />}
            {!isLoading && !items?.length && (
              <div className="rounded-sq border border-dashed border-line py-10 text-center text-sm text-fg-muted">
                No items for this skill yet.
              </div>
            )}
            <div className="space-y-2">
              {items?.map((it) => (
                <ItemRow key={it.id} item={it} onChanged={invalidate} />
              ))}
            </div>
          </div>

          <ItemHealthPanel />
        </>
      )}
    </div>
  )
}

function MisconceptionLibrary({
  skillId,
  misconceptions,
  onChanged,
}: {
  skillId: number
  misconceptions: Misconception[]
  onChanged: () => void
}) {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [remediationHint, setRemediationHint] = useState('')

  const create = useMutation({
    mutationFn: async () => api.post('/instructor/items/misconceptions', { skillId, name, remediationHint }),
    onSuccess: () => {
      toast.success('Misconception added.')
      setOpen(false)
      setName('')
      setRemediationHint('')
      onChanged()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not add the misconception.')),
  })

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <h2 className="legend">Misconception library ({misconceptions.length})</h2>
        <Button size="sm" variant="ghost" onClick={() => setOpen((o) => !o)}>
          <Plus className="size-3.5" /> Add
        </Button>
      </div>

      {open && (
        <div className="mb-2 space-y-2 rounded-sq border border-line bg-surface p-3">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder='Phrased as a belief — "Thinks HashMap preserves insertion order"'
            className={inputClass}
          />
          <input
            value={remediationHint}
            onChange={(e) => setRemediationHint(e.target.value)}
            placeholder="What to tell someone who has this misconception"
            className={inputClass}
          />
          <div className="flex justify-end gap-1.5">
            <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={!name.trim() || !remediationHint.trim() || create.isPending}
              onClick={() => create.mutate()}
            >
              {create.isPending && <Loader2 className="size-3.5 animate-spin" />} Add
            </Button>
          </div>
        </div>
      )}

      {!misconceptions.length && (
        <p className="text-xs text-fg-subtle">
          None yet — items can still be created, but wrong answers won't carry a diagnosis.
        </p>
      )}
      <div className="flex flex-wrap gap-1.5">
        {misconceptions.map((m) => (
          <span
            key={m.id}
            className="legend rounded-full border border-line-strong px-2 py-0.5"
            title={m.remediationHint}
          >
            {m.name} · {m.timesObserved}
          </span>
        ))}
      </div>
    </div>
  )
}

interface DraftOption {
  text: string
  correct: boolean
  misconceptionId: string
}

function CreateItemForm({
  skillId,
  misconceptions,
  onDone,
  onCancel,
}: {
  skillId: number
  misconceptions: Misconception[]
  onDone: () => void
  onCancel: () => void
}) {
  const [stem, setStem] = useState('')
  const [explanation, setExplanation] = useState('')
  const [declaredDifficulty, setDeclaredDifficulty] = useState('0')
  const [options, setOptions] = useState<DraftOption[]>([
    { text: '', correct: true, misconceptionId: '' },
    { text: '', correct: false, misconceptionId: '' },
    { text: '', correct: false, misconceptionId: '' },
    { text: '', correct: false, misconceptionId: '' },
  ])

  const setOption = (i: number, patch: Partial<DraftOption>) =>
    setOptions((opts) => opts.map((o, idx) => (idx === i ? { ...o, ...patch } : o)))

  const validOptionCount = options.filter((o) => o.text.trim()).length
  const hasCorrect = options.some((o) => o.correct && o.text.trim())

  const create = useMutation({
    mutationFn: async () =>
      api.post('/instructor/items', {
        skillId,
        stem,
        type: 'MCQ',
        explanation: explanation || null,
        declaredDifficulty,
        options: options
          .filter((o) => o.text.trim())
          .map((o) => ({
            text: o.text,
            correct: o.correct,
            misconceptionId: o.correct || !o.misconceptionId ? null : Number(o.misconceptionId),
          })),
      }),
    onSuccess: () => {
      toast.success('Item created as a draft — activate it to put it in circulation.')
      onDone()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not create the item.')),
  })

  return (
    <div className="mb-3 space-y-3 rounded-sq border border-line bg-surface p-3">
      <div>
        <label className="legend mb-1 block">Question</label>
        <textarea
          value={stem}
          onChange={(e) => setStem(e.target.value)}
          rows={2}
          className={inputClass}
          placeholder="What happens when an Integer holding null is unboxed to an int?"
        />
      </div>

      <div>
        <label className="legend mb-1 block">Explanation (shown after answering)</label>
        <textarea
          value={explanation}
          onChange={(e) => setExplanation(e.target.value)}
          rows={2}
          className={inputClass}
        />
      </div>

      <div className="w-40">
        <label className="legend mb-1 block">Declared difficulty (-3 to 3)</label>
        <input
          type="number"
          min={-3}
          max={3}
          step="0.5"
          value={declaredDifficulty}
          onChange={(e) => setDeclaredDifficulty(e.target.value)}
          className={inputClass}
        />
      </div>

      <div>
        <label className="legend mb-1 block">
          Options — mark the correct one, tag the misconception behind each wrong one
        </label>
        <div className="space-y-1.5">
          {options.map((o, i) => (
            <div key={i} className="flex items-center gap-1.5">
              <input
                type="radio"
                name="correct-option"
                checked={o.correct}
                onChange={() => setOptions((opts) => opts.map((x, idx) => ({ ...x, correct: idx === i })))}
                aria-label={`Option ${i + 1} is correct`}
              />
              <input
                value={o.text}
                onChange={(e) => setOption(i, { text: e.target.value })}
                placeholder={`Option ${i + 1}`}
                className={inputClass + ' min-w-0 flex-1'}
              />
              {!o.correct && (
                <select
                  value={o.misconceptionId}
                  onChange={(e) => setOption(i, { misconceptionId: e.target.value })}
                  // Deliberately not `inputClass + ' w-48'`: inputClass bakes
                  // in `w-full`, and with flex-basis:auto that resolves to
                  // width:100% before w-48 (same specificity, losing utility
                  // order) can override it — so the select's flex-basis came
                  // out as "100% of the row" instead of 12rem, and it grabbed
                  // the whole row instead of leaving room for the option-text
                  // input beside it. A width utility that must actually win
                  // here can't share a class string with one it conflicts with.
                  className="shrink-0 rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong w-48"
                >
                  <option value="">No misconception tagged</option>
                  {misconceptions.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name}
                    </option>
                  ))}
                </select>
              )}
            </div>
          ))}
        </div>
        <button
          onClick={() => setOptions((opts) => [...opts, { text: '', correct: false, misconceptionId: '' }])}
          disabled={options.length >= 8}
          className="mt-1.5 text-xs font-medium text-accent hover:underline disabled:opacity-40"
        >
          + add option
        </button>
      </div>

      <div className="flex justify-end gap-1.5">
        <Button size="sm" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={!stem.trim() || validOptionCount < 2 || !hasCorrect || create.isPending}
          onClick={() => create.mutate()}
        >
          {create.isPending && <Loader2 className="size-3.5 animate-spin" />} Create
        </Button>
      </div>
    </div>
  )
}

function ItemRow({ item, onChanged }: { item: ItemResponse; onChanged: () => void }) {
  const [expanded, setExpanded] = useState(false)

  const activate = useMutation({
    mutationFn: async () => api.post(`/instructor/items/${item.id}/activate`),
    onSuccess: () => {
      toast.success('Item activated.')
      onChanged()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not activate.')),
  })

  const retire = useMutation({
    mutationFn: async () => api.post(`/instructor/items/${item.id}/retire`),
    onSuccess: () => {
      toast.success('Item retired.')
      onChanged()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not retire.')),
  })

  return (
    <div className="overflow-hidden rounded-sq border border-line bg-surface">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-bg-sunken"
      >
        <span className="flex-1 truncate text-sm">{item.stem}</span>
        {item.calibrated && item.pValue && (
          <span className="num text-xs text-fg-subtle">p={Math.round(Number(item.pValue) * 100)}%</span>
        )}
        <Tone tone={ITEM_STATUS_TONE[item.status]}>{item.status.toLowerCase()}</Tone>
        <ChevronDown className={`size-3.5 text-fg-subtle transition-transform ${expanded ? 'rotate-180' : ''}`} />
      </button>

      {expanded && (
        <div className="space-y-3 border-t border-line px-4 py-3 text-sm">
          <div className="space-y-1">
            {item.options.map((o) => (
              <div
                key={o.id}
                className={
                  'flex items-center justify-between rounded-sq px-2.5 py-1.5 text-xs ' +
                  (o.correct ? 'bg-mastered-bg text-mastered' : 'bg-bg-sunken text-fg-muted')
                }
              >
                <span>{o.text}</span>
                {o.misconceptionName && <span className="text-fg-subtle">{o.misconceptionName}</span>}
              </div>
            ))}
          </div>
          <div className="flex items-center gap-1.5">
            {item.status === 'DRAFT' && (
              <Button size="sm" variant="outline" disabled={activate.isPending} onClick={() => activate.mutate()}>
                {activate.isPending && <Loader2 className="size-3.5 animate-spin" />} Activate
              </Button>
            )}
            {item.status !== 'RETIRED' && (
              <Button size="sm" variant="ghost" disabled={retire.isPending} onClick={() => retire.mutate()}>
                Retire
              </Button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function ItemHealthPanel() {
  const { data: suspect } = useQuery({
    queryKey: ['instructor', 'items', 'health'],
    queryFn: async () => (await api.get<ItemHealth[]>('/instructor/items/health')).data,
  })

  if (!suspect?.length) return null

  return (
    <div>
      <h2 className="legend mb-2">Items that look broken</h2>
      <div className="space-y-1.5">
        {suspect.map((it) => (
          <div key={it.id} className="rounded-sq border border-decaying/30 bg-decaying-bg px-3 py-2 text-xs">
            <p className="truncate font-medium text-fg">{it.stem}</p>
            <p className="mt-0.5 text-fg-muted">{it.diagnosis}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
