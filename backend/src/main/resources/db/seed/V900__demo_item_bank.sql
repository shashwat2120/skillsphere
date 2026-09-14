-- =============================================================================
-- V900 — Demo item bank (Java track)
--
-- WHY THIS LIVES IN db/seed RATHER THAN db/migration.
-- Flyway runs everything on its locations list in every environment. Schema
-- belongs in db/migration and ships everywhere; demonstration content does not.
-- Keeping it in a separate location means the local and dev profiles can load it
-- while production simply does not list the path — so there is no risk of a
-- fictional Java course appearing on a real deployment, and no "delete the demo
-- data" step that someone eventually forgets.
--
-- The V900 prefix keeps it ordered after every schema migration regardless of
-- how many are added later.
--
-- WHY THE CONTENT MATTERS AS MUCH AS THE SCHEMA.
-- The adaptive engine cannot demonstrate anything without a bank to select from,
-- and a bank of filler questions produces a demo that technically works and
-- proves nothing. Every distractor below encodes a misconception a real Java
-- learner actually holds, because that mapping is what lets the platform say
-- "you believe X" rather than "you are wrong" — the single capability that
-- separates this from a quiz engine.
--
-- Each item also carries a declared_difficulty on the IRT logit scale, roughly
-- -2 (most learners pass) to +2 (most fail). These are authored estimates acting
-- as Bayesian priors: the engine routes on them until real responses calibrate
-- the learned value. That is the cold-start answer made concrete.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Misconceptions. Phrased as beliefs — "thinks X" — never as topics. A topic
-- cannot be addressed; a belief can.
-- -----------------------------------------------------------------------------
INSERT INTO misconceptions (skill_id, name, description, remediation_hint, times_observed, created_at, updated_at)
SELECT s.id, m.name, m.description, m.hint, 0, now(), now()
FROM (VALUES
  -- java-syntax
  ('java-syntax', 'Thinks == compares String contents',
   'Uses == to compare strings and is surprised when equal-looking values differ.',
   '== compares references — whether two variables point at the same object. Two strings built at runtime can hold identical characters and still be different objects. Use .equals() for contents, and remember the literal pool is why == sometimes appears to work.'),
  ('java-syntax', 'Thinks integer division yields a decimal',
   'Expects 7/2 to produce 3.5.',
   'Division between two ints is integer division: the fractional part is discarded, not rounded. 7/2 is 3. Cast one operand to double when you want 3.5.'),
  ('java-syntax', 'Believes primitives can hold null',
   'Assigns or compares a primitive to null.',
   'Primitives are values, not references — an int is always some number. Only wrapper types like Integer can be null, which is also why unboxing a null Integer throws.'),

  -- oop
  ('oop', 'Confuses overloading with overriding',
   'Treats a method with different parameters as overriding a superclass method.',
   'Overloading is same name, different parameters, resolved at compile time within a class. Overriding is same signature in a subclass, resolved at runtime. Changing the parameter list means you have overloaded, not overridden — which is why @Override catches the mistake.'),
  ('oop', 'Thinks an abstract class can be instantiated',
   'Expects new on an abstract type to work.',
   'An abstract class is deliberately incomplete, so it cannot be instantiated directly. You instantiate a concrete subclass, or an anonymous subclass created inline.'),
  ('oop', 'Believes constructors are inherited',
   'Expects a subclass to expose its parent constructors automatically.',
   'Constructors are not inherited. A subclass declares its own and calls super(...) to initialise the parent part. That is why adding a parameterised constructor to a parent can break subclasses.'),
  ('oop', 'Thinks private members are accessible in subclasses',
   'Expects to read a private parent field directly from a child class.',
   'Private members belong to the declaring class alone. A subclass inherits them in the sense that they exist in the object, but cannot reference them. Use protected, or a getter, if a subclass genuinely needs access.'),

  -- collections
  ('collections', 'Thinks HashMap preserves insertion order',
   'Expects iteration to return entries in the order they were added.',
   'HashMap makes no ordering guarantee at all — iteration order depends on hashes and capacity and can change as the map grows. Use LinkedHashMap for insertion order, or TreeMap for sorted keys.'),
  ('collections', 'Confuses HashMap with TreeMap ordering',
   'Expects HashMap keys to come back sorted.',
   'Sorted iteration is TreeMap, which orders by the key comparator and costs O(log n) per operation. HashMap trades ordering away for O(1) average access.'),
  ('collections', 'Thinks List.remove(int) removes by value',
   'Calls remove(1) on a List<Integer> expecting the element 1 to be removed.',
   'remove(int) removes by index and remove(Object) removes by value. On a List<Integer> the literal 1 selects the index overload. Call remove(Integer.valueOf(1)) to remove by value.'),
  ('collections', 'Believes Arrays.asList returns a modifiable list',
   'Calls add on the result of Arrays.asList and hits UnsupportedOperationException.',
   'Arrays.asList returns a fixed-size view backed by the original array — set works, add and remove do not. Wrap it: new ArrayList<>(Arrays.asList(...)).'),
  ('collections', 'Thinks HashSet deduplicates by equals alone',
   'Adds objects that are equal but produce different hash codes and finds duplicates.',
   'HashSet finds the bucket by hashCode first, then compares with equals. Override both together, or two equal objects land in different buckets and never meet.'),

  -- generics
  ('generics', 'Believes generic types survive at runtime',
   'Expects to inspect a List''s element type at runtime.',
   'Generics are erased at compile time: List<String> and List<Integer> are both List at runtime. That is why you cannot write new T[] or check instanceof List<String>.'),
  ('generics', 'Thinks List<Object> accepts a List<String>',
   'Assumes generic types are covariant like arrays.',
   'Generics are invariant: List<String> is not a List<Object>, even though String is an Object. If it were allowed you could insert an Integer through the wider reference. Use List<? extends Object> to read from any list.'),
  ('generics', 'Confuses extends and super in wildcards',
   'Uses ? extends T when writing, or ? super T when reading.',
   'Producer extends, consumer super. ? extends T lets you read T safely but not write, because the actual type could be narrower. ? super T lets you write T but reads come back as Object.'),

  -- streams
  ('streams', 'Thinks a stream can be consumed twice',
   'Reuses a stream variable after a terminal operation.',
   'A stream is single-use. After a terminal operation it is closed, and touching it again throws IllegalStateException. Create a fresh stream from the source each time.'),
  ('streams', 'Believes intermediate operations run immediately',
   'Expects a map or filter to execute at the point it is written.',
   'Intermediate operations are lazy: they build a pipeline and run nothing. No element moves until a terminal operation like collect or forEach pulls it through — which is why a stream with no terminal operation does nothing at all.'),
  ('streams', 'Thinks stream operations mutate the source',
   'Expects filter or sorted to change the original collection.',
   'Stream operations produce new results and leave the source untouched. Collect into a new collection, or use removeIf if you genuinely want to modify in place.'),

  -- concurrency
  ('concurrency', 'Thinks synchronized on different objects excludes',
   'Expects two synchronized methods on different instances to be mutually exclusive.',
   'synchronized locks a specific object monitor. Two threads holding different objects never contend, so there is no mutual exclusion. Lock a shared object — or a static lock — if the state being protected is shared.'),
  ('concurrency', 'Believes volatile makes operations atomic',
   'Uses volatile on a counter and expects ++ to be safe.',
   'volatile guarantees visibility, not atomicity. count++ is read-modify-write — three steps, interleavable. Use AtomicInteger or synchronize the whole operation.'),
  ('concurrency', 'Thinks virtual threads add parallelism',
   'Expects virtual threads to make CPU-bound work faster.',
   'Virtual threads make blocking cheap, not computation faster. Parallelism is still bounded by cores. They win overwhelmingly for I/O-bound work and do nothing for a tight CPU loop.'),

  -- spring-boot
  ('spring-boot', 'Thinks @Transactional works on self-invocation',
   'Calls one @Transactional method from another in the same class and expects a new transaction.',
   'Spring wraps the bean in a proxy, and an internal call bypasses it entirely — the annotation is silently ignored. Move the method to another bean, which is exactly why REQUIRES_NEW appears not to work.'),
  ('spring-boot', 'Believes field injection is preferred',
   'Uses @Autowired on fields rather than constructor parameters.',
   'Constructor injection makes dependencies explicit, allows final fields, and lets the class be tested without a container. Field injection hides dependencies and only fails at runtime.'),
  ('spring-boot', 'Thinks component scanning covers every package',
   'Expects a bean outside the application package to be discovered.',
   'Scanning starts at the @SpringBootApplication class package and descends. Anything outside that tree is invisible unless you add it with scanBasePackages.'),

  -- microservices
  ('microservices', 'Thinks distributed calls behave like local ones',
   'Assumes a remote call either succeeds or throws, like a method call.',
   'A network call has a third outcome: no answer. The request may have succeeded with the response lost. That is why retries need idempotency and why timeouts are a design decision rather than a default.'),
  ('microservices', 'Believes splitting services improves performance',
   'Expects extracting a service to make the system faster.',
   'Splitting converts in-process calls into network calls, which are orders of magnitude slower. Services are split for independent deployment, scaling and failure isolation — performance is usually the cost, not the benefit.')
) AS m(skill_slug, name, description, hint)
JOIN skills s ON s.slug = m.skill_slug
ON CONFLICT DO NOTHING;
