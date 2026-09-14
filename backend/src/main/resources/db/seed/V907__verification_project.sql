-- =============================================================================
-- V907 — One real project to defend
--
-- Deliberately just one, published and tied to the two skills with the
-- richest existing learner data (Collections, Concurrency) rather than a
-- broad catalogue with nothing behind it — the viva is the feature being
-- demonstrated, not project variety.
-- =============================================================================

INSERT INTO projects (title, slug, description, brief, level_band, est_minutes, rubric, requires_viva, status, created_at)
VALUES (
  'Build a Bounded LRU Cache',
  'bounded-lru-cache',
  'Implement a fixed-capacity cache that evicts the least recently used entry when full, safe for concurrent access.',
  E'Write a class `BoundedCache<K, V>` with `get(K)`, `put(K, V)` and a fixed capacity set at construction. '
  'When the cache is full and a new key is inserted, evict whichever entry was least recently accessed — '
  'a `get` counts as an access, not just a `put`. The cache must behave correctly under concurrent access '
  'from multiple threads. Explain your choice of underlying data structure and how you made it thread-safe.',
  'INTERMEDIATE',
  90,
  '[
    {"key": "correctness", "label": "Eviction is correct under normal use", "weight": 0.35, "descriptors": ["evicts the right entry", "get updates recency", "capacity is respected"]},
    {"key": "concurrency", "label": "Genuinely safe under concurrent access", "weight": 0.35, "descriptors": ["no lost updates", "no torn reads", "reasoned choice of synchronisation"]},
    {"key": "design", "label": "Data structure choice is deliberate", "weight": 0.30, "descriptors": ["can explain the trade-off made", "complexity of get/put is understood"]}
  ]'::jsonb,
  true,
  'PUBLISHED',
  now()
)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO project_skills (project_id, skill_id, weight)
SELECT p.id, s.id, w.weight
FROM (VALUES ('collections', 0.60), ('concurrency', 0.40)) AS w(skill_slug, weight)
JOIN projects p ON p.slug = 'bounded-lru-cache'
JOIN skills s ON s.slug = w.skill_slug
ON CONFLICT DO NOTHING;
