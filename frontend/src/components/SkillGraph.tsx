import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'

export interface GraphSkill {
  id: number
  name: string
  levelBand: 'FOUNDATIONAL' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT'
  masteryProbability: number
  mastered: boolean
  available: boolean
  blockedBy: { id: number; name: string }[]
}

const BANDS: GraphSkill['levelBand'][] = ['FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']

const COL_W = 232
const ROW_H = 92
const NODE_W = 168
const NODE_H = 54
const PAD_X = 28
const PAD_Y = 44

/**
 * Shortens a string to fit its label. Paired with the per-node clipPath
 * below, not a substitute for it — this keeps normal-length names reading
 * cleanly with a plain ellipsis; the clip is what guarantees an unusually
 * long one (an admin-entered skill name, say) can never bleed past the node
 * boundary into whatever is drawn next to it.
 */
function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text
}

/**
 * The skill graph, drawn as a graph.
 *
 * <p>The earlier version rendered this as columns of cards, which is the wrong
 * shape for the data and the single clearest tell of a generated interface: the
 * product's central idea is a directed graph, and a list cannot show the thing
 * that makes it interesting — that mastering one node opens specific others.
 *
 * Edges are drawn as bezier curves between node anchors, and their colour is
 * information rather than decoration: a green edge is a dependency the learner
 * has already satisfied, a grey one is still outstanding. Reading which paths
 * are open becomes a glance instead of a comparison.
 *
 * Layout is deterministic — column by level band, row by index within it. A
 * force-directed simulation would look more organic and would also place the
 * same graph differently on every render, so a learner could never build a
 * spatial memory of where their skills live. Stability is worth more than
 * organic placement here.
 */
export function SkillGraph({
  skills,
  edges,
}: {
  skills: GraphSkill[]
  edges: { from: number; to: number; hard: boolean }[]
}) {
  const [hovered, setHovered] = useState<number | null>(null)
  const navigate = useNavigate()

  const positions = useMemo(() => {
    const map = new Map<number, { x: number; y: number }>()
    BANDS.forEach((band, column) => {
      skills
        .filter((skill) => skill.levelBand === band)
        .forEach((skill, row) => {
          map.set(skill.id, { x: PAD_X + column * COL_W, y: PAD_Y + row * ROW_H })
        })
    })
    return map
  }, [skills])

  const width = PAD_X * 2 + (BANDS.length - 1) * COL_W + NODE_W
  const height = useMemo(() => {
    const tallest = Math.max(
      ...BANDS.map((band) => skills.filter((s) => s.levelBand === band).length),
      1,
    )
    return PAD_Y * 2 + tallest * ROW_H
  }, [skills])

  const byId = useMemo(() => new Map(skills.map((s) => [s.id, s])), [skills])

  return (
    <div className="relative overflow-x-auto rounded-sq-lg border border-line bg-surface">
      <div className="grid-paper pointer-events-none absolute inset-0" aria-hidden />

      <svg width={width} height={height} className="relative block">
        {/* Edges first so nodes paint over them. */}
        <g>
          {edges.map(({ from, to, hard }) => {
            const a = positions.get(from)
            const b = positions.get(to)
            if (!a || !b) return null

            const source = byId.get(from)
            const satisfied = source?.mastered ?? false
            // An edge touching the hovered node is lifted; everything else
            // recedes. On a dense graph this is the difference between "I can
            // see the lines" and "I can trace this skill's dependencies".
            const active = hovered === from || hovered === to

            const x1 = a.x + NODE_W
            const y1 = a.y + NODE_H / 2
            const x2 = b.x
            const y2 = b.y + NODE_H / 2
            const mid = (x1 + x2) / 2

            return (
              <path
                key={`${from}-${to}`}
                d={`M ${x1} ${y1} C ${mid} ${y1}, ${mid} ${y2}, ${x2} ${y2}`}
                fill="none"
                stroke={satisfied ? 'var(--mastered)' : 'var(--line-strong)'}
                strokeWidth={active ? 2 : 1.25}
                // Soft prerequisites are dashed: they advise an order without
                // blocking, and the line should say so without a legend.
                strokeDasharray={hard ? undefined : '4 4'}
                opacity={hovered === null ? (satisfied ? 0.75 : 0.4) : active ? 1 : 0.12}
                style={{ transition: 'opacity 140ms, stroke-width 140ms' }}
              />
            )
          })}
        </g>

        {skills.map((skill, index) => {
          const pos = positions.get(skill.id)
          if (!pos) return null

          const state = skill.mastered ? 'mastered' : skill.available ? 'available' : 'locked'
          const stroke = `var(--${state})`
          const fill = `var(--${state}-bg)`
          const dim = hovered !== null && hovered !== skill.id
          // A locked skill has no diagnostic to take yet — its prerequisites
          // are not met, so there is nothing for the adaptive selector to
          // meaningfully target. Only mastered/available nodes launch one.
          const clickable = state !== 'locked'

          return (
            <motion.g
              key={skill.id}
              initial={{ opacity: 0 }}
              animate={{ opacity: dim ? 0.35 : 1 }}
              transition={{ duration: 0.18, delay: Math.min(index * 0.02, 0.2) }}
              onMouseEnter={() => setHovered(skill.id)}
              onMouseLeave={() => setHovered(null)}
              onClick={() => clickable && navigate(`/diagnostics/${skill.id}`)}
              role={clickable ? 'button' : undefined}
              tabIndex={clickable ? 0 : undefined}
              style={{ cursor: clickable ? 'pointer' : 'default' }}
            >
              <rect
                x={pos.x}
                y={pos.y}
                width={NODE_W}
                height={NODE_H}
                rx={4}
                fill={fill}
                stroke={stroke}
                strokeWidth={1.25}
              />

              {/* Mastery as a fill along the bottom edge of the node — the
                  progress lives on the thing it describes rather than in a
                  separate bar competing for space. */}
              <rect
                x={pos.x}
                y={pos.y + NODE_H - 3}
                width={NODE_W * skill.masteryProbability}
                height={3}
                fill={stroke}
              />

              {/* Text is clipped to the node's own rect as a hard guarantee,
                  not just truncated by character count. Truncation alone once
                  let "needs Object-Oriented Programming" run straight out of
                  a 168px box and across the next column — a name long enough
                  to survive the character budget below would do the same
                  again. The clip makes overflow physically impossible
                  regardless of what an admin later names a skill. */}
              <clipPath id={`node-clip-${skill.id}`}>
                <rect x={pos.x} y={pos.y} width={NODE_W} height={NODE_H} />
              </clipPath>
              <g clipPath={`url(#node-clip-${skill.id})`}>
                <text
                  x={pos.x + 12}
                  y={pos.y + 22}
                  fontSize={12.5}
                  fontWeight={500}
                  fill="var(--fg)"
                >
                  {truncate(skill.name, 21)}
                </text>

                <text
                  x={pos.x + 12}
                  y={pos.y + 39}
                  fontSize={10.5}
                  fill="var(--fg-subtle)"
                  style={{ fontFamily: 'var(--font-mono)' }}
                >
                  {(skill.masteryProbability * 100).toFixed(0).padStart(2, '0')}%
                  {state === 'locked' && skill.blockedBy.length
                    ? `  · needs ${truncate(skill.blockedBy[0].name, 12)}`
                    : ''}
                </text>
              </g>
            </motion.g>
          )
        })}

        {BANDS.map((band, column) => (
          <text
            key={band}
            x={PAD_X + column * COL_W}
            y={22}
            fontSize={9.5}
            fontWeight={500}
            letterSpacing="0.09em"
            fill="var(--fg-subtle)"
          >
            {band}
          </text>
        ))}
      </svg>
    </div>
  )
}
