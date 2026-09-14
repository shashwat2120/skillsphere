-- =============================================================================
-- V902 — Answer options, each wrong one tied to a named misconception
--
-- This file is where the product's central claim becomes real data. In an
-- ordinary quiz the three wrong options are filler, chosen to look plausible.
-- Here each one is a hypothesis about how a learner could be reasoning, so the
-- option somebody picks carries information even though the answer is wrong.
--
-- Options are matched to items by a distinctive fragment of the stem rather than
-- by id, because ids are assigned by the database and this file must remain
-- readable and re-runnable. Misconceptions are matched by name within the same
-- skill, so a name collision across skills cannot mis-tag an option.
-- =============================================================================

INSERT INTO item_options (item_id, text, is_correct, misconception_id, position, times_chosen, created_at)
SELECT
    it.id,
    o.text,
    o.correct,
    m.id,
    o.position,
    0,
    now()
FROM (VALUES
  -- ---- java-syntax ----------------------------------------------------------
  ('s1 == s2 evaluate', 'false',                                    true,  NULL,                                        0),
  ('s1 == s2 evaluate', 'true, because the contents are identical', false, 'Thinks == compares String contents',        1),
  ('s1 == s2 evaluate', 'true, because Java interns all strings',   false, 'Thinks == compares String contents',        2),
  ('s1 == s2 evaluate', 'It does not compile',                      false, 'Believes primitives can hold null',         3),

  ('7 / 2 when both operands', '3',     true,  NULL,                                   0),
  ('7 / 2 when both operands', '3.5',   false, 'Thinks integer division yields a decimal', 1),
  ('7 / 2 when both operands', '4',     false, 'Thinks integer division yields a decimal', 2),
  ('7 / 2 when both operands', '3.0',   false, 'Thinks integer division yields a decimal', 3),

  ('Integer holding null is assigned', 'It throws NullPointerException at runtime', true,  NULL,                               0),
  ('Integer holding null is assigned', 'The int becomes 0',                          false, 'Believes primitives can hold null', 1),
  ('Integer holding null is assigned', 'The int becomes null',                       false, 'Believes primitives can hold null', 2),
  ('Integer holding null is assigned', 'It fails to compile',                        false, 'Believes primitives can hold null', 3),

  -- ---- oop -----------------------------------------------------------------
  ('same name as a parent method but a different parameter', 'The method is overloaded, not overridden', true,  NULL,                                     0),
  ('same name as a parent method but a different parameter', 'The parent method is overridden',          false, 'Confuses overloading with overriding',   1),
  ('same name as a parent method but a different parameter', 'The parent method is hidden',              false, 'Confuses overloading with overriding',   2),
  ('same name as a parent method but a different parameter', 'It will not compile without @Override',    false, 'Confuses overloading with overriding',   3),

  ('parent gains a constructor that takes arguments', 'Constructors are not inherited, so the implicit super() no longer matches', true,  NULL,                               0),
  ('parent gains a constructor that takes arguments', 'The subclass inherits the new constructor but not the old one',             false, 'Believes constructors are inherited', 1),
  ('parent gains a constructor that takes arguments', 'The subclass must be declared abstract',                                    false, 'Thinks an abstract class can be instantiated', 2),
  ('parent gains a constructor that takes arguments', 'Constructors are inherited, so nothing should break',                       false, 'Believes constructors are inherited', 3),

  ('read a private field declared in its parent', 'No — private members are accessible only inside the declaring class', true,  NULL,                                            0),
  ('read a private field declared in its parent', 'Yes, private members are inherited and accessible',                   false, 'Thinks private members are accessible in subclasses', 1),
  ('read a private field declared in its parent', 'Yes, but only from within a constructor',                             false, 'Thinks private members are accessible in subclasses', 2),
  ('read a private field declared in its parent', 'Yes, if the subclass is in the same package',                         false, 'Thinks private members are accessible in subclasses', 3),

  -- ---- collections ----------------------------------------------------------
  ('inserted into a HashMap in the order C, A, B', 'No order is guaranteed', true,  NULL,                                      0),
  ('inserted into a HashMap in the order C, A, B', 'C, A, B',                false, 'Thinks HashMap preserves insertion order', 1),
  ('inserted into a HashMap in the order C, A, B', 'A, B, C',                false, 'Confuses HashMap with TreeMap ordering',   2),
  ('inserted into a HashMap in the order C, A, B', 'B, A, C',                false, 'Thinks HashMap preserves insertion order', 3),

  ('What does list.remove(1) do', 'Removes the element at index 1, so 20', true,  NULL,                                  0),
  ('What does list.remove(1) do', 'Removes the value 1, changing nothing', false, 'Thinks List.remove(int) removes by value', 1),
  ('What does list.remove(1) do', 'Throws IndexOutOfBoundsException',      false, 'Thinks List.remove(int) removes by value', 2),
  ('What does list.remove(1) do', 'Removes the first element, so 10',      false, 'Thinks List.remove(int) removes by value', 3),

  ('add() is called on the List returned by Arrays.asList', 'It throws UnsupportedOperationException', true,  NULL,                                             0),
  ('add() is called on the List returned by Arrays.asList', 'The element is appended normally',        false, 'Believes Arrays.asList returns a modifiable list', 1),
  ('add() is called on the List returned by Arrays.asList', 'A new larger list is returned',           false, 'Believes Arrays.asList returns a modifiable list', 2),
  ('add() is called on the List returned by Arrays.asList', 'It throws ArrayIndexOutOfBoundsException', false, 'Believes Arrays.asList returns a modifiable list', 3),

  ('equal according to equals() but return different hashCode', 'Both are stored — the set contains a duplicate', true,  NULL,                                    0),
  ('equal according to equals() but return different hashCode', 'Only one is stored, because equals is checked',   false, 'Thinks HashSet deduplicates by equals alone', 1),
  ('equal according to equals() but return different hashCode', 'An IllegalStateException is thrown',              false, 'Thinks HashSet deduplicates by equals alone', 2),
  ('equal according to equals() but return different hashCode', 'The second replaces the first',                   false, 'Thinks HashSet deduplicates by equals alone', 3),

  -- ---- generics -------------------------------------------------------------
  ('determine whether a List holds Strings or Integers', 'You cannot — the type is erased at compile time', true,  NULL,                                       0),
  ('determine whether a List holds Strings or Integers', 'Call getGenericType() on the list',               false, 'Believes generic types survive at runtime', 1),
  ('determine whether a List holds Strings or Integers', 'Use instanceof List<String>',                     false, 'Believes generic types survive at runtime', 2),
  ('determine whether a List holds Strings or Integers', 'Read it from list.getClass()',                    false, 'Believes generic types survive at runtime', 3),

  ('assigning a List<String> to a List<Object> fail', 'Generics are invariant — it would allow unsafe writes', true,  NULL,                                    0),
  ('assigning a List<String> to a List<Object> fail', 'It should compile; String is an Object',               false, 'Thinks List<Object> accepts a List<String>', 1),
  ('assigning a List<String> to a List<Object> fail', 'Because of type erasure',                              false, 'Believes generic types survive at runtime',  2),
  ('assigning a List<String> to a List<Object> fail', 'It only fails if the list is non-empty',               false, 'Thinks List<Object> accepts a List<String>', 3),

  ('must add elements to a collection. Which wildcard', 'List<? super T>',        true,  NULL,                                  0),
  ('must add elements to a collection. Which wildcard', 'List<? extends T>',      false, 'Confuses extends and super in wildcards', 1),
  ('must add elements to a collection. Which wildcard', 'List<?>',                false, 'Confuses extends and super in wildcards', 2),
  ('must add elements to a collection. Which wildcard', 'Either works identically', false, 'Confuses extends and super in wildcards', 3),

  -- ---- streams --------------------------------------------------------------
  ('then filtered again. What happens on the second use', 'IllegalStateException — the stream is already consumed', true,  NULL,                                0),
  ('then filtered again. What happens on the second use', 'It works, producing the same result',                    false, 'Thinks a stream can be consumed twice', 1),
  ('then filtered again. What happens on the second use', 'It returns an empty stream',                             false, 'Thinks a stream can be consumed twice', 2),
  ('then filtered again. What happens on the second use', 'It silently recreates the stream',                       false, 'Thinks a stream can be consumed twice', 3),

  ('no terminal operation. What executes', 'Nothing — intermediate operations are lazy',       true,  NULL,                                        0),
  ('no terminal operation. What executes', 'Both map and filter run over every element',        false, 'Believes intermediate operations run immediately', 1),
  ('no terminal operation. What executes', 'map runs but filter does not',                      false, 'Believes intermediate operations run immediately', 2),
  ('no terminal operation. What executes', 'It throws because no terminal operation is present', false, 'Believes intermediate operations run immediately', 3),

  ('what is the state of the original list', 'Unchanged — streams never mutate their source', true,  NULL,                                    0),
  ('what is the state of the original list', 'Sorted in place',                                false, 'Thinks stream operations mutate the source', 1),
  ('what is the state of the original list', 'Emptied, because elements were consumed',        false, 'Thinks stream operations mutate the source', 2),
  ('what is the state of the original list', 'Replaced by the collected list',                 false, 'Thinks stream operations mutate the source', 3),

  -- ---- concurrency ----------------------------------------------------------
  ('two different instances of the same class. Do they exclude', 'No — each object has its own monitor', true,  NULL,                                            0),
  ('two different instances of the same class. Do they exclude', 'Yes, synchronized locks the class',     false, 'Thinks synchronized on different objects excludes', 1),
  ('two different instances of the same class. Do they exclude', 'Yes, synchronized locks the method',    false, 'Thinks synchronized on different objects excludes', 2),
  ('two different instances of the same class. Do they exclude', 'Only if the method is static',          false, 'Thinks synchronized on different objects excludes', 3),

  ('declared volatile. Several threads execute count++', 'No — ++ is read-modify-write and can interleave', true,  NULL,                                      0),
  ('declared volatile. Several threads execute count++', 'Yes, volatile makes the operation atomic',        false, 'Believes volatile makes operations atomic', 1),
  ('declared volatile. Several threads execute count++', 'Yes, provided there are only two threads',        false, 'Believes volatile makes operations atomic', 2),
  ('declared volatile. Several threads execute count++', 'Yes, volatile serialises access to the field',    false, 'Believes volatile makes operations atomic', 3),

  ('moved from platform threads to virtual threads', 'Little to none — parallelism is still bounded by cores', true,  NULL,                                  0),
  ('moved from platform threads to virtual threads', 'It scales with the number of virtual threads',          false, 'Thinks virtual threads add parallelism', 1),
  ('moved from platform threads to virtual threads', 'Roughly double, from cheaper scheduling',               false, 'Thinks virtual threads add parallelism', 2),
  ('moved from platform threads to virtual threads', 'It improves because they are pre-emptive',              false, 'Thinks virtual threads add parallelism', 3),

  -- ---- spring-boot ----------------------------------------------------------
  ('called from another method in the same class. Does a transaction start', 'No — the proxy is bypassed by the internal call', true,  NULL,                                        0),
  ('called from another method in the same class. Does a transaction start', 'Yes, the annotation applies regardless',           false, 'Thinks @Transactional works on self-invocation', 1),
  ('called from another method in the same class. Does a transaction start', 'Yes, and it joins the caller''s transaction',      false, 'Thinks @Transactional works on self-invocation', 2),
  ('called from another method in the same class. Does a transaction start', 'Only if the method is public',                     false, 'Thinks @Transactional works on self-invocation', 3),

  ('constructor injection preferred over @Autowired on fields', 'Dependencies are explicit, fields can be final, and testing needs no container', true,  NULL,                              0),
  ('constructor injection preferred over @Autowired on fields', 'It is faster at startup',                                                         false, 'Believes field injection is preferred', 1),
  ('constructor injection preferred over @Autowired on fields', 'There is no practical difference',                                                false, 'Believes field injection is preferred', 2),
  ('constructor injection preferred over @Autowired on fields', 'Field injection is preferred in modern Spring',                                   false, 'Believes field injection is preferred', 3),

  ('outside the one containing @SpringBootApplication. Is it registered', 'No — scanning starts at the application package', true,  NULL,                                          0),
  ('outside the one containing @SpringBootApplication. Is it registered', 'Yes, the whole classpath is scanned',              false, 'Thinks component scanning covers every package', 1),
  ('outside the one containing @SpringBootApplication. Is it registered', 'Yes, if it is annotated @Component',               false, 'Thinks component scanning covers every package', 2),
  ('outside the one containing @SpringBootApplication. Is it registered', 'Only if it is in a JAR on the classpath',          false, 'Thinks component scanning covers every package', 3),

  -- ---- microservices --------------------------------------------------------
  ('the request times out. What can be concluded', 'Nothing — the work may have succeeded with the response lost', true,  NULL,                                       0),
  ('the request times out. What can be concluded', 'The work did not happen',                                      false, 'Thinks distributed calls behave like local ones', 1),
  ('the request times out. What can be concluded', 'The work was rolled back automatically',                       false, 'Thinks distributed calls behave like local ones', 2),
  ('the request times out. What can be concluded', 'It is safe to retry without further thought',                  false, 'Thinks distributed calls behave like local ones', 3),

  ('extracts a module into its own service. What is the most likely effect', 'Latency increases — an in-process call becomes a network call', true,  NULL,                                          0),
  ('extracts a module into its own service. What is the most likely effect', 'Latency falls because the service scales independently',        false, 'Believes splitting services improves performance', 1),
  ('extracts a module into its own service. What is the most likely effect', 'No measurable change',                                          false, 'Believes splitting services improves performance', 2),
  ('extracts a module into its own service. What is the most likely effect', 'Latency falls because each service is smaller',                 false, 'Believes splitting services improves performance', 3)
) AS o(stem_match, text, correct, misconception_name, position)
JOIN items it ON it.stem LIKE '%' || o.stem_match || '%'
LEFT JOIN misconceptions m
       ON m.name = o.misconception_name
      AND m.skill_id = it.skill_id
ON CONFLICT DO NOTHING;
