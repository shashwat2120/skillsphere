import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { Loader2, Swords } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'

interface SkillOption {
  id: number
  name: string
}

interface GraphResponse {
  skills: SkillOption[]
}

interface ArenaView {
  id: number
  title: string
  skillName: string
  joinCode: string
  status: string
}

/**
 * Instructor side of the live arena — create a session, hand out the code,
 * watch the room play.
 */
export function ArenasPage() {
  const navigate = useNavigate()
  const [showCreate, setShowCreate] = useState(false)

  const { data: skills } = useQuery({
    queryKey: ['skills', 'active'],
    queryFn: async () => (await api.get<GraphResponse>('/skills/graph')).data.skills,
  })

  const [title, setTitle] = useState('')
  const [skillId, setSkillId] = useState('')
  const [questionCount, setQuestionCount] = useState(8);
  const [secondsPerQuestion, setSecondsPerQuestion] = useState(20)

  const create = useMutation({
    mutationFn: async () =>
      (
        await api.post<ArenaView>('/realtime/arenas', {
          title, skillId: Number(skillId), questionCount, secondsPerQuestion,
        })
      ).data,
    onSuccess: (arena) => {
      toast.success(`Arena created — code ${arena.joinCode}`)
      navigate(`/arenas/${arena.id}`)
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not create that arena.')),
  })

  return (
    <div className="mx-auto max-w-xl">
      <header className="mb-6 flex items-center justify-between border-b border-line pb-4">
        <div>
          <h1 className="text-[19px] font-semibold">Live arena</h1>
          <p className="mt-1 text-[13px] text-fg-muted">
            A room, a code, a shared set of questions — live scoring while
            everyone plays at once.
          </p>
        </div>
        <Button size="sm" onClick={() => setShowCreate(!showCreate)}>
          <Swords className="size-3.5" /> New arena
        </Button>
      </header>

      {showCreate && (
        <motion.div
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          className="mb-6 overflow-hidden rounded-sq border border-line bg-surface p-4"
        >
          <div className="space-y-3">
            <label className="block">
              <span className="legend mb-1 block">Title</span>
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g. Collections speed round"
                className="w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong"
              />
            </label>
            <label className="block">
              <span className="legend mb-1 block">Skill</span>
              <select
                value={skillId}
                onChange={(e) => setSkillId(e.target.value)}
                className="w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong"
              >
                <option value="">Choose a skill</option>
                {skills?.map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </label>
            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="legend mb-1 block">Questions</span>
                <input
                  type="number"
                  min={3}
                  max={30}
                  value={questionCount}
                  onChange={(e) => setQuestionCount(Number(e.target.value))}
                  className="w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong"
                />
              </label>
              <label className="block">
                <span className="legend mb-1 block">Seconds each</span>
                <input
                  type="number"
                  min={5}
                  max={120}
                  value={secondsPerQuestion}
                  onChange={(e) => setSecondsPerQuestion(Number(e.target.value))}
                  className="w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong"
                />
              </label>
            </div>
            <Button
              className="w-full"
              disabled={!title.trim() || !skillId || create.isPending}
              onClick={() => create.mutate()}
            >
              {create.isPending && <Loader2 className="size-3.5 animate-spin" />}
              Create arena
            </Button>
          </div>
        </motion.div>
      )}

      <p className="text-xs text-fg-subtle">
        Once created, you'll get a join code to share with the room.
      </p>
    </div>
  )
}
