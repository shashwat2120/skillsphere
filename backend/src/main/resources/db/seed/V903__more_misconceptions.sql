-- =============================================================================
-- V903 — More misconceptions
--
-- The bank needed depth before the adaptive engine could demonstrate anything.
-- A diagnostic converges in roughly fifteen to eighteen responses (measured, not
-- assumed — see PsychometricsTest), and with three items per skill every run
-- ended on NO_ITEMS_AVAILABLE before the estimate stabilised. Exposure control
-- was working exactly as intended; there was simply nothing left to ask.
--
-- Collections is deepest because it is the demo skill: it is the one that has to
-- sustain a full adaptive run, and it is where Java learners hold the most
-- well-documented wrong beliefs.
-- =============================================================================

INSERT INTO misconceptions (skill_id, name, description, remediation_hint, times_observed, created_at, updated_at)
SELECT s.id, m.name, m.description, m.hint, 0, now(), now()
FROM (VALUES
  -- collections
  ('collections', 'Believes HashMap is thread-safe',
   'Shares a HashMap between threads without synchronisation.',
   'HashMap has no synchronisation at all. Concurrent writes can corrupt its internal structure — historically producing an infinite loop on read. Use ConcurrentHashMap, which locks per bucket rather than wholesale.'),
  ('collections', 'Thinks ConcurrentModificationException means a threading bug',
   'Assumes the exception only occurs with multiple threads.',
   'It usually happens on a single thread: removing from a collection while iterating it. The iterator notices the structure changed underneath it. Use Iterator.remove() or removeIf().'),
  ('collections', 'Thinks ArrayList and LinkedList perform alike',
   'Chooses between them without regard to access pattern.',
   'ArrayList indexes in constant time and inserts in the middle in linear time; LinkedList is the reverse. In practice ArrayList wins almost always, because CPU cache locality beats theoretical complexity at realistic sizes.'),
  ('collections', 'Believes List.contains is fast',
   'Calls contains inside a loop over a large list.',
   'contains on a List is a linear scan — O(n). Inside a loop that is O(n squared). If you are membership-testing, use a HashSet, which is O(1) average.'),
  ('collections', 'Thinks unmodifiableList copies the underlying list',
   'Expects the wrapper to be independent of the original.',
   'It is a read-only view, not a copy. Changes to the backing list still show through the wrapper. Use List.copyOf() for a genuinely independent snapshot.'),
  ('collections', 'Believes HashMap rejects null keys',
   'Assumes a null key throws.',
   'HashMap permits one null key and any number of null values. TreeMap rejects null keys, because it must compare them. Hashtable and ConcurrentHashMap reject both.'),
  ('collections', 'Thinks mutating a key leaves it findable',
   'Changes a field used by hashCode after inserting the object.',
   'The entry stays in the bucket chosen by its original hash, so lookups with the mutated key look in the wrong bucket and find nothing. Keys should be immutable.'),

  -- oop
  ('oop', 'Thinks an interface cannot hold implementation',
   'Assumes interfaces are pure contracts.',
   'Since Java 8 interfaces can carry default and static methods, and since 9 private ones. They still hold no instance state, which remains the real distinction from an abstract class.'),
  ('oop', 'Believes static methods can be overridden',
   'Expects polymorphism from a static method redeclared in a subclass.',
   'Static methods are hidden, not overridden. Dispatch is by the reference type at compile time, so the parent version runs when the reference is of the parent type.'),
  ('oop', 'Thinks equals alone is enough',
   'Overrides equals without hashCode.',
   'Every hash-based collection finds the bucket by hashCode before comparing with equals, so two equal objects with different hashes never meet. Override both or neither.'),
  ('oop', 'Believes composition and inheritance are interchangeable',
   'Extends a class to reuse its methods.',
   'Inheritance commits you to the parent for the life of the type and exposes its API through yours. Composition delegates and can be swapped. Prefer composition unless the subtype genuinely is a kind of the supertype.'),

  -- java-syntax
  ('java-syntax', 'Thinks Java passes objects by reference',
   'Expects reassigning a parameter to affect the caller.',
   'Java is always pass-by-value — the value passed is a copy of the reference. Mutating the object is visible to the caller; reassigning the parameter is not.'),
  ('java-syntax', 'Believes finally can be skipped by return',
   'Assumes returning from try bypasses finally.',
   'finally runs regardless — after the return value is computed but before control leaves. Only System.exit or a JVM crash skips it.'),
  ('java-syntax', 'Thinks String concatenation in a loop is cheap',
   'Builds a string with += inside a loop.',
   'Strings are immutable, so each += allocates a new one and copies everything so far, making the loop O(n squared). Use StringBuilder.'),

  -- streams
  ('streams', 'Thinks parallel streams are always faster',
   'Adds parallel() expecting a speed-up.',
   'Parallelism costs splitting, thread coordination and merging. Below roughly ten thousand elements the overhead usually exceeds the gain, and on an ordered pipeline it can be far slower.'),
  ('streams', 'Believes forEach guarantees order in parallel',
   'Uses forEach on a parallel stream and expects source order.',
   'forEach makes no ordering promise on a parallel stream. Use forEachOrdered when order matters — and accept that it removes most of the parallel benefit.'),
  ('streams', 'Thinks peek is for side effects',
   'Uses peek to mutate or log as a substitute for forEach.',
   'peek is a debugging hook and may be skipped entirely when the pipeline can optimise it away. Anything that must happen belongs in a terminal operation.'),

  -- generics
  ('generics', 'Thinks a generic array can be created',
   'Writes new T[] and expects it to compile.',
   'Erasure means T is unknown at runtime, so the array would have no component type to check against. Use an Object[] with casts, or a List.'),
  ('generics', 'Believes a static method can use the class type parameter',
   'References T from a static context.',
   'A class type parameter belongs to an instance, and a static method has none. Declare the method generic itself, with its own parameter.'),

  -- concurrency
  ('concurrency', 'Thinks HashMap plus synchronized equals ConcurrentHashMap',
   'Wraps a HashMap in synchronized blocks and expects equivalent behaviour.',
   'A synchronized wrapper locks the entire map for every operation; ConcurrentHashMap locks per bucket, so unrelated keys never contend. The wrapper is also unsafe for compound operations such as check-then-put.'),
  ('concurrency', 'Believes Thread.sleep releases the lock',
   'Calls sleep inside a synchronized block to let another thread proceed.',
   'sleep holds every lock it owns. Only wait() releases the monitor. A sleeping thread inside a synchronized block blocks everyone else for the whole duration.'),

  -- spring-boot
  ('spring-boot', 'Thinks singleton beans are thread-safe',
   'Stores request state in a field of a singleton bean.',
   'Singleton means one instance shared by every thread, which makes mutable fields shared mutable state. Keep beans stateless and pass state as parameters.'),
  ('spring-boot', 'Believes @Transactional rolls back on checked exceptions',
   'Expects a checked exception to trigger a rollback.',
   'By default only unchecked exceptions and Errors roll back. A checked exception commits unless you declare rollbackFor — which is why a failed operation can silently persist half its work.')
) AS m(skill_slug, name, description, hint)
JOIN skills s ON s.slug = m.skill_slug
ON CONFLICT DO NOTHING;
