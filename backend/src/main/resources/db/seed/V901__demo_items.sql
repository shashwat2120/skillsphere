-- =============================================================================
-- V901 — Demo assessment items
--
-- Each item is written so that every wrong option is reachable by a specific,
-- nameable line of reasoning. That is the whole point: a distractor chosen at
-- random tells the platform nothing, while a distractor tied to a belief lets it
-- say "you are treating HashMap as ordered" instead of "review Collections".
--
-- declared_difficulty is the author's estimate on the IRT logit scale, roughly
-- -2 (nearly everyone passes) through 0 (about half) to +2 (nearly everyone
-- fails). It is a prior, not a label — the engine routes on it until real
-- responses calibrate difficulty_b, and the two are kept apart permanently so
-- the guess can later be compared against reality.
-- =============================================================================

-- Helper: items are inserted with a temporary marker in `explanation` so their
-- options can be attached by matching on stem, without needing returned ids.

-- ---------------------------------------------------------------------------
-- java-syntax
-- ---------------------------------------------------------------------------
INSERT INTO items (skill_id, stem, type, explanation, declared_difficulty, difficulty_b,
                   discrimination_a, elo_rating, is_calibrated, times_seen, times_correct,
                   status, created_at, updated_at)
SELECT s.id, i.stem, 'MCQ', i.explanation, i.difficulty, 0, 1.0, 1200, false, 0, 0,
       'ACTIVE', now(), now()
FROM (VALUES
  ('java-syntax',
   'Two Strings are built at runtime from user input and both contain "hello". What does s1 == s2 evaluate to?',
   'false — == compares references, and two separately constructed objects are never the same reference. Literals appear to work only because the compiler pools them.',
   -0.6),
  ('java-syntax',
   'What is the value of 7 / 2 when both operands are int?',
   'Integer division discards the fractional part rather than rounding, so the result is 3. Cast an operand to double for 3.5.',
   -1.2),
  ('java-syntax',
   'An Integer holding null is assigned to an int variable. What happens?',
   'Unboxing calls intValue() on null, so it throws NullPointerException at runtime — it is not a compile error, which is what makes it easy to miss.',
   0.7),

  -- oop
  ('oop',
   'A subclass declares a method with the same name as a parent method but a different parameter list. What has happened?',
   'The parameter list differs, so this is overloading — a new, unrelated method resolved at compile time. @Override would fail to compile here, which is precisely why it is worth writing.',
   0.1),
  ('oop',
   'Why does a subclass fail to compile when its parent gains a constructor that takes arguments?',
   'Constructors are not inherited. The implicit super() call no longer matches any constructor, so the subclass must declare one that calls super(...) explicitly.',
   0.9),
  ('oop',
   'Can a subclass read a private field declared in its parent class?',
   'No. The field exists in the object but is only accessible inside the declaring class. Use protected or a getter if the subclass genuinely needs it.',
   -0.3),

  -- collections
  ('collections',
   'Keys are inserted into a HashMap in the order C, A, B. In what order does iteration return them?',
   'HashMap makes no ordering guarantee — iteration order depends on hashing and capacity and may change as the map grows. LinkedHashMap preserves insertion order; TreeMap sorts.',
   -0.2),
  ('collections',
   'A List<Integer> contains [10, 20, 30]. What does list.remove(1) do?',
   'The int literal selects remove(int index), so element 20 is removed. To remove the value 1 you would call remove(Integer.valueOf(1)).',
   0.8),
  ('collections',
   'What happens when add() is called on the List returned by Arrays.asList("a", "b")?',
   'It throws UnsupportedOperationException. The returned list is a fixed-size view over the array — set works, add and remove do not.',
   0.5),
  ('collections',
   'Two objects are equal according to equals() but return different hashCode() values. What happens when both are added to a HashSet?',
   'Both are stored. HashSet locates a bucket by hash first and only then compares with equals, so objects with different hashes never meet. equals and hashCode must always be overridden together.',
   1.1),

  -- generics
  ('generics',
   'At runtime, how can you determine whether a List holds Strings or Integers?',
   'You cannot from the list alone. Generic types are erased at compile time, so both are simply List. Inspecting an element is the only option, and an empty list tells you nothing.',
   1.0),
  ('generics',
   'Why does assigning a List<String> to a List<Object> fail to compile?',
   'Generics are invariant. If it were allowed, an Integer could be added through the List<Object> reference and later read out as a String. Arrays are covariant and this is exactly why they can throw ArrayStoreException.',
   1.3),
  ('generics',
   'A method must add elements to a collection. Which wildcard is correct?',
   'Consumer super: List<? super T> accepts T and anything wider. With ? extends T the actual type could be narrower, so writing is unsafe and the compiler rejects it.',
   1.5),

  -- streams
  ('streams',
   'A Stream is assigned to a variable, collected to a List, then filtered again. What happens on the second use?',
   'IllegalStateException — a stream is single-use and is closed by its terminal operation. Create a new stream from the source.',
   0.4),
  ('streams',
   'A stream pipeline calls map() and filter() but no terminal operation. What executes?',
   'Nothing. Intermediate operations are lazy and only build a pipeline; no element moves until a terminal operation pulls it through.',
   0.6),
  ('streams',
   'After list.stream().sorted().collect(toList()), what is the state of the original list?',
   'Unchanged. Stream operations never mutate their source — they produce new results. Use Collections.sort or List.sort to sort in place.',
   -0.4),

  -- concurrency
  ('concurrency',
   'Two threads call a synchronized method on two different instances of the same class. Do they exclude each other?',
   'No. synchronized locks that specific object''s monitor, and different objects have different monitors. Shared state needs a shared lock, or a static one.',
   0.9),
  ('concurrency',
   'A counter field is declared volatile. Several threads execute count++. Is the final value correct?',
   'No. volatile guarantees visibility but not atomicity, and ++ is a read-modify-write that can interleave. AtomicInteger or synchronisation is required.',
   0.7),
  ('concurrency',
   'A CPU-bound calculation is moved from platform threads to virtual threads. What is the expected effect on throughput?',
   'Essentially none. Virtual threads make blocking cheap; they do not add cores. The gain appears with I/O-bound work, where threads spend their time waiting.',
   1.2),

  -- spring-boot
  ('spring-boot',
   'A @Transactional method is called from another method in the same class. Does a transaction start?',
   'No. Spring applies the annotation through a proxy, and an internal call never leaves the object, so the proxy is bypassed and the annotation is silently ignored. Move the method to a separate bean.',
   1.4),
  ('spring-boot',
   'Why is constructor injection preferred over @Autowired on fields?',
   'It makes dependencies explicit, allows final fields, and lets the class be instantiated in a test without a container. Field injection hides dependencies and fails only at runtime.',
   0.2),
  ('spring-boot',
   'A @Component sits in a package outside the one containing @SpringBootApplication. Is it registered?',
   'No. Scanning begins at the application class''s package and descends from there. Anything outside that tree needs scanBasePackages.',
   0.5),

  -- microservices
  ('microservices',
   'A service calls another over HTTP and the request times out. What can be concluded about whether the work happened?',
   'Nothing. A timeout is ambiguous — the call may have succeeded with the response lost. This is why retries require idempotency, and why a timeout is a design decision rather than an error.',
   1.1),
  ('microservices',
   'A team extracts a module into its own service. What is the most likely effect on request latency?',
   'It increases. An in-process call becomes a network call, which is orders of magnitude slower. Services are split for independent deployment and failure isolation; latency is the price.',
   0.8)
) AS i(skill_slug, stem, explanation, difficulty)
JOIN skills s ON s.slug = i.skill_slug
ON CONFLICT DO NOTHING;
