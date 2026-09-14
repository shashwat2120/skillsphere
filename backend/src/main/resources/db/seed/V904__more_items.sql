-- =============================================================================
-- V904 — More items, weighted toward Collections
--
-- Difficulty is spread deliberately rather than clustered. An adaptive selector
-- can only adapt if the bank contains items at a range of levels: with
-- everything pitched at the same difficulty it has nothing to choose between,
-- and the test degenerates into a fixed one served in random order.
-- =============================================================================

INSERT INTO items (skill_id, stem, type, explanation, declared_difficulty, difficulty_b,
                   discrimination_a, elo_rating, is_calibrated, times_seen, times_correct,
                   status, created_at, updated_at)
SELECT s.id, i.stem, 'MCQ', i.explanation, i.difficulty, 0, i.discrimination, 1200, false, 0, 0,
       'ACTIVE', now(), now()
FROM (VALUES
  -- ---- collections: the demo skill, taken deep ------------------------------
  ('collections', 'Which collection should you use when you need to look up whether a value is present, many times, in a large data set?',
   'A HashSet gives O(1) average membership testing. List.contains is a linear scan, so checking inside a loop turns O(n) into O(n squared).',
   -0.8, 1.3),
  ('collections', 'Two threads write to the same HashMap with no synchronisation. What is the risk?',
   'HashMap has no synchronisation, so concurrent writes can corrupt its internal structure — historically producing an infinite loop on a later read. ConcurrentHashMap locks per bucket instead.',
   0.9, 1.5),
  ('collections', 'A single thread removes elements from an ArrayList while iterating it with a for-each loop. What happens?',
   'ConcurrentModificationException. Despite the name it is usually a single-threaded bug: the iterator detects that the structure changed underneath it. Use Iterator.remove() or removeIf().',
   0.3, 1.4),
  ('collections', 'What does Collections.unmodifiableList return?',
   'A read-only view over the original, not a copy. Changes to the backing list still show through. List.copyOf gives an independent snapshot.',
   0.6, 1.2),
  ('collections', 'Can a HashMap store a null key?',
   'Yes — one null key and any number of null values. TreeMap rejects null keys because it must compare them, and ConcurrentHashMap rejects both.',
   0.4, 1.1),
  ('collections', 'An object is used as a HashMap key, then a field used by its hashCode is changed. What happens on lookup?',
   'The entry stays in the bucket chosen by the original hash, so a lookup computes a different bucket and finds nothing — while the entry is still there on iteration. Keys should be immutable.',
   1.4, 1.6),
  ('collections', 'You need to repeatedly insert and remove at the front of a sequence. Which is more appropriate?',
   'LinkedList inserts and removes at the ends in constant time. ArrayList must shift every element, making front operations O(n). This is one of the few cases where LinkedList genuinely wins.',
   0.2, 1.2),
  ('collections', 'What is the iteration order of a LinkedHashMap?',
   'Insertion order — that is its entire purpose. It is the answer whenever you want map behaviour and predictable ordering without the O(log n) cost of TreeMap.',
   -0.6, 1.0),
  ('collections', 'What is the average time complexity of get() on a HashMap?',
   'O(1) average. Worst case degrades to O(log n) in modern Java, which converts a heavily collided bucket into a tree rather than a linked list.',
   -0.9, 1.1),
  ('collections', 'Which interface should a method parameter use when it only needs to iterate the values?',
   'Collection, or Iterable if iteration is truly all that is needed. Accepting the narrowest interface that works lets callers pass whatever they already have.',
   0.5, 1.0),
  ('collections', 'What happens when you add an element to a HashSet that equals an existing one and shares its hashCode?',
   'The set is unchanged and add returns false. This is the correct behaviour, and it depends entirely on hashCode and equals agreeing.',
   -0.4, 1.2),
  ('collections', 'Which structure keeps its keys sorted and allows range queries?',
   'TreeMap, which maintains order by comparator. Operations cost O(log n) rather than HashMap''s O(1), which is the price of ordering.',
   0.1, 1.3),

  -- ---- oop -----------------------------------------------------------------
  ('oop', 'Can an interface contain method implementations?',
   'Yes — default and static methods since Java 8, private ones since 9. What interfaces still cannot hold is instance state, which remains the real distinction from an abstract class.',
   0.3, 1.2),
  ('oop', 'A subclass declares a static method with the same signature as the parent. Which runs when called through a parent-typed reference?',
   'The parent version. Static methods are hidden rather than overridden, so dispatch uses the reference type at compile time — not the object.',
   1.2, 1.5),
  ('oop', 'A class overrides equals but not hashCode. What breaks?',
   'Every hash-based collection. HashMap and HashSet find the bucket by hashCode first, so two equal objects with different hashes never meet and duplicates appear.',
   0.7, 1.6),
  ('oop', 'When should you prefer composition over inheritance?',
   'Almost always, unless the subtype genuinely is a kind of the supertype. Inheritance commits you to the parent permanently and leaks its API through yours.',
   0.5, 1.0),

  -- ---- java-syntax ---------------------------------------------------------
  ('java-syntax', 'A method reassigns its object parameter to a new instance. What does the caller see?',
   'Nothing changed. Java passes a copy of the reference, so reassigning the parameter rebinds only the local copy. Mutating the object would have been visible.',
   0.6, 1.4),
  ('java-syntax', 'A try block returns a value and the method has a finally block. Does finally run?',
   'Yes — after the return value is computed but before control leaves the method. Only System.exit or a JVM crash skips it.',
   0.4, 1.2),
  ('java-syntax', 'What is the cost of building a long string with += inside a loop?',
   'O(n squared). Strings are immutable, so each concatenation allocates a new string and copies everything accumulated so far. StringBuilder makes it linear.',
   0.1, 1.3),

  -- ---- streams -------------------------------------------------------------
  ('streams', 'When does adding parallel() to a stream reliably help?',
   'On large data sets with independent, CPU-bound work. Below roughly ten thousand elements the cost of splitting and merging usually exceeds the gain.',
   0.9, 1.3),
  ('streams', 'Does forEach on a parallel stream preserve the source order?',
   'No. forEach makes no ordering promise when parallel. forEachOrdered does, at the cost of most of the parallel benefit.',
   1.0, 1.2),
  ('streams', 'What is peek intended for?',
   'Debugging. It may be skipped entirely when the pipeline can optimise it away, so anything that must happen belongs in a terminal operation.',
   1.1, 1.1),

  -- ---- generics ------------------------------------------------------------
  ('generics', 'Why can you not write new T[] inside a generic class?',
   'Erasure removes T at runtime, so the array would have no component type to check stores against — defeating the guarantee arrays are supposed to provide.',
   1.2, 1.3),
  ('generics', 'Can a static method reference its class type parameter T?',
   'No. A class type parameter belongs to an instance and a static method has none. Declare the method generic with its own parameter.',
   1.3, 1.2),

  -- ---- concurrency ---------------------------------------------------------
  ('concurrency', 'How does ConcurrentHashMap differ from wrapping a HashMap in synchronized blocks?',
   'The wrapper locks the whole map for every operation; ConcurrentHashMap locks per bucket, so unrelated keys never contend. The wrapper is also unsafe for compound operations such as check-then-put.',
   1.3, 1.4),
  ('concurrency', 'Does Thread.sleep release the locks the thread holds?',
   'No. Only wait() releases the monitor. Sleeping inside a synchronized block blocks every other thread for the entire duration.',
   0.8, 1.3),

  -- ---- spring-boot ---------------------------------------------------------
  ('spring-boot', 'A singleton bean stores request data in an instance field. What is the problem?',
   'Singleton means one instance shared across every thread, so the field is shared mutable state and requests overwrite each other. Keep beans stateless.',
   0.9, 1.5),
  ('spring-boot', 'A @Transactional method throws a checked exception. Does the transaction roll back?',
   'No. Only unchecked exceptions and Errors roll back by default, so a failed operation can silently commit half its work. Declare rollbackFor to change it.',
   1.3, 1.4)
) AS i(skill_slug, stem, explanation, difficulty, discrimination)
JOIN skills s ON s.slug = i.skill_slug
ON CONFLICT DO NOTHING;
