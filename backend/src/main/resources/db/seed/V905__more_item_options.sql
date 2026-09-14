-- =============================================================================
-- V905 — Options for the expanded bank, every distractor tagged
-- =============================================================================

INSERT INTO item_options (item_id, text, is_correct, misconception_id, position, times_chosen, created_at)
SELECT it.id, o.text, o.correct, m.id, o.position, 0, now()
FROM (VALUES
  -- ---- collections ---------------------------------------------------------
  ('look up whether a value is present, many times', 'HashSet',              true,  NULL,                          0),
  ('look up whether a value is present, many times', 'ArrayList',            false, 'Believes List.contains is fast', 1),
  ('look up whether a value is present, many times', 'LinkedList',           false, 'Believes List.contains is fast', 2),
  ('look up whether a value is present, many times', 'Any of them — the difference is negligible', false, 'Thinks ArrayList and LinkedList perform alike', 3),

  ('same HashMap with no synchronisation', 'The internal structure can be corrupted', true,  NULL,                            0),
  ('same HashMap with no synchronisation', 'None — HashMap is thread-safe',           false, 'Believes HashMap is thread-safe', 1),
  ('same HashMap with no synchronisation', 'Only that writes may be lost',            false, 'Believes HashMap is thread-safe', 2),
  ('same HashMap with no synchronisation', 'It throws ConcurrentModificationException', false, 'Thinks ConcurrentModificationException means a threading bug', 3),

  ('removes elements from an ArrayList while iterating', 'ConcurrentModificationException', true,  NULL,                                                        0),
  ('removes elements from an ArrayList while iterating', 'It works — one thread is safe',   false, 'Thinks ConcurrentModificationException means a threading bug', 1),
  ('removes elements from an ArrayList while iterating', 'Elements are silently skipped',   false, 'Thinks ConcurrentModificationException means a threading bug', 2),
  ('removes elements from an ArrayList while iterating', 'IndexOutOfBoundsException',       false, 'Thinks ConcurrentModificationException means a threading bug', 3),

  ('Collections.unmodifiableList return', 'A read-only view over the original list', true,  NULL,                                              0),
  ('Collections.unmodifiableList return', 'An independent immutable copy',           false, 'Thinks unmodifiableList copies the underlying list', 1),
  ('Collections.unmodifiableList return', 'The same list with a flag set',           false, 'Thinks unmodifiableList copies the underlying list', 2),
  ('Collections.unmodifiableList return', 'A deep copy of every element',            false, 'Thinks unmodifiableList copies the underlying list', 3),

  ('Can a HashMap store a null key', 'Yes — one null key is allowed', true,  NULL,                              0),
  ('Can a HashMap store a null key', 'No, it throws NullPointerException', false, 'Believes HashMap rejects null keys', 1),
  ('Can a HashMap store a null key', 'No, the entry is silently dropped',  false, 'Believes HashMap rejects null keys', 2),
  ('Can a HashMap store a null key', 'Yes, and any number of them',        false, 'Believes HashMap rejects null keys', 3),

  ('field used by its hashCode is changed', 'Lookup fails, though the entry is still there on iteration', true,  NULL,                                     0),
  ('field used by its hashCode is changed', 'The map rehashes automatically',                              false, 'Thinks mutating a key leaves it findable', 1),
  ('field used by its hashCode is changed', 'Lookup still works via equals',                               false, 'Thinks mutating a key leaves it findable', 2),
  ('field used by its hashCode is changed', 'The entry is removed',                                        false, 'Thinks mutating a key leaves it findable', 3),

  ('insert and remove at the front of a sequence', 'LinkedList',                       true,  NULL,                                          0),
  ('insert and remove at the front of a sequence', 'ArrayList',                        false, 'Thinks ArrayList and LinkedList perform alike', 1),
  ('insert and remove at the front of a sequence', 'Either — both are constant time',  false, 'Thinks ArrayList and LinkedList perform alike', 2),
  ('insert and remove at the front of a sequence', 'HashSet',                          false, 'Thinks ArrayList and LinkedList perform alike', 3),

  ('iteration order of a LinkedHashMap', 'Insertion order',            true,  NULL,                                    0),
  ('iteration order of a LinkedHashMap', 'Sorted by key',              false, 'Confuses HashMap with TreeMap ordering', 1),
  ('iteration order of a LinkedHashMap', 'Unspecified, like HashMap',  false, 'Thinks HashMap preserves insertion order', 2),
  ('iteration order of a LinkedHashMap', 'Reverse insertion order',    false, 'Thinks HashMap preserves insertion order', 3),

  ('average time complexity of get() on a HashMap', 'O(1)',        true,  NULL,                          0),
  ('average time complexity of get() on a HashMap', 'O(log n)',    false, 'Believes List.contains is fast', 1),
  ('average time complexity of get() on a HashMap', 'O(n)',        false, 'Believes List.contains is fast', 2),
  ('average time complexity of get() on a HashMap', 'O(n log n)',  false, 'Believes List.contains is fast', 3),

  ('parameter use when it only needs to iterate', 'Collection',  true,  NULL,                                          0),
  ('parameter use when it only needs to iterate', 'ArrayList',   false, 'Thinks ArrayList and LinkedList perform alike', 1),
  ('parameter use when it only needs to iterate', 'List',        false, 'Thinks ArrayList and LinkedList perform alike', 2),
  ('parameter use when it only needs to iterate', 'HashSet',     false, 'Thinks ArrayList and LinkedList perform alike', 3),

  ('HashSet that equals an existing one and shares its hashCode', 'Nothing changes and add returns false', true,  NULL,                                      0),
  ('HashSet that equals an existing one and shares its hashCode', 'Both are stored',                        false, 'Thinks HashSet deduplicates by equals alone', 1),
  ('HashSet that equals an existing one and shares its hashCode', 'The new one replaces the old',           false, 'Thinks HashSet deduplicates by equals alone', 2),
  ('HashSet that equals an existing one and shares its hashCode', 'It throws IllegalStateException',        false, 'Thinks HashSet deduplicates by equals alone', 3),

  ('keeps its keys sorted and allows range queries', 'TreeMap',        true,  NULL,                                    0),
  ('keeps its keys sorted and allows range queries', 'HashMap',        false, 'Confuses HashMap with TreeMap ordering', 1),
  ('keeps its keys sorted and allows range queries', 'LinkedHashMap',  false, 'Confuses HashMap with TreeMap ordering', 2),
  ('keeps its keys sorted and allows range queries', 'Hashtable',      false, 'Confuses HashMap with TreeMap ordering', 3),

  -- ---- oop -----------------------------------------------------------------
  ('interface contain method implementations', 'Yes — default, static and private methods', true,  NULL,                                       0),
  ('interface contain method implementations', 'No, interfaces are pure contracts',          false, 'Thinks an interface cannot hold implementation', 1),
  ('interface contain method implementations', 'Only static methods',                        false, 'Thinks an interface cannot hold implementation', 2),
  ('interface contain method implementations', 'Only if the class is abstract',              false, 'Thinks an interface cannot hold implementation', 3),

  ('static method with the same signature as the parent', 'The parent version — statics are hidden, not overridden', true,  NULL,                                     0),
  ('static method with the same signature as the parent', 'The subclass version, by polymorphism',                    false, 'Believes static methods can be overridden', 1),
  ('static method with the same signature as the parent', 'It fails to compile',                                      false, 'Believes static methods can be overridden', 2),
  ('static method with the same signature as the parent', 'Whichever was loaded first',                               false, 'Believes static methods can be overridden', 3),

  ('overrides equals but not hashCode', 'Hash-based collections stop working correctly', true,  NULL,                          0),
  ('overrides equals but not hashCode', 'Nothing — equals is what matters',              false, 'Thinks equals alone is enough', 1),
  ('overrides equals but not hashCode', 'Only sorting breaks',                           false, 'Thinks equals alone is enough', 2),
  ('overrides equals but not hashCode', 'It fails to compile',                           false, 'Thinks equals alone is enough', 3),

  ('prefer composition over inheritance', 'Almost always, unless it genuinely is a kind of the supertype', true,  NULL,                                                  0),
  ('prefer composition over inheritance', 'Only when the parent is final',                                   false, 'Believes composition and inheritance are interchangeable', 1),
  ('prefer composition over inheritance', 'They are interchangeable in practice',                            false, 'Believes composition and inheritance are interchangeable', 2),
  ('prefer composition over inheritance', 'Inheritance is preferred for reuse',                              false, 'Believes composition and inheritance are interchangeable', 3),

  -- ---- java-syntax ---------------------------------------------------------
  ('reassigns its object parameter to a new instance', 'Nothing — the caller still sees the original', true,  NULL,                                 0),
  ('reassigns its object parameter to a new instance', 'The caller sees the new instance',             false, 'Thinks Java passes objects by reference', 1),
  ('reassigns its object parameter to a new instance', 'It depends whether the class is final',        false, 'Thinks Java passes objects by reference', 2),
  ('reassigns its object parameter to a new instance', 'A compile error',                              false, 'Thinks Java passes objects by reference', 3),

  ('try block returns a value and the method has a finally', 'Yes, finally always runs',       true,  NULL,                                  0),
  ('try block returns a value and the method has a finally', 'No, the return skips it',         false, 'Believes finally can be skipped by return', 1),
  ('try block returns a value and the method has a finally', 'Only if no exception was thrown', false, 'Believes finally can be skipped by return', 2),
  ('try block returns a value and the method has a finally', 'Only for unchecked exceptions',   false, 'Believes finally can be skipped by return', 3),

  ('building a long string with += inside a loop', 'O(n squared)',  true,  NULL,                                       0),
  ('building a long string with += inside a loop', 'O(n)',          false, 'Thinks String concatenation in a loop is cheap', 1),
  ('building a long string with += inside a loop', 'O(log n)',      false, 'Thinks String concatenation in a loop is cheap', 2),
  ('building a long string with += inside a loop', 'Constant — the compiler optimises it', false, 'Thinks String concatenation in a loop is cheap', 3),

  -- ---- streams -------------------------------------------------------------
  ('adding parallel() to a stream reliably help', 'Large data sets with independent CPU-bound work', true,  NULL,                                  0),
  ('adding parallel() to a stream reliably help', 'Always — more threads is faster',                  false, 'Thinks parallel streams are always faster', 1),
  ('adding parallel() to a stream reliably help', 'Whenever the source is a List',                    false, 'Thinks parallel streams are always faster', 2),
  ('adding parallel() to a stream reliably help', 'For I/O-bound work',                               false, 'Thinks parallel streams are always faster', 3),

  ('forEach on a parallel stream preserve the source order', 'No — use forEachOrdered if order matters', true,  NULL,                                        0),
  ('forEach on a parallel stream preserve the source order', 'Yes, order is always preserved',            false, 'Believes forEach guarantees order in parallel', 1),
  ('forEach on a parallel stream preserve the source order', 'Only for sorted sources',                   false, 'Believes forEach guarantees order in parallel', 2),
  ('forEach on a parallel stream preserve the source order', 'Only for small streams',                    false, 'Believes forEach guarantees order in parallel', 3),

  ('What is peek intended for', 'Debugging',                        true,  NULL,                             0),
  ('What is peek intended for', 'Performing side effects',          false, 'Thinks peek is for side effects', 1),
  ('What is peek intended for', 'Logging in production pipelines',  false, 'Thinks peek is for side effects', 2),
  ('What is peek intended for', 'Mutating elements in place',       false, 'Thinks peek is for side effects', 3),

  -- ---- generics ------------------------------------------------------------
  ('not write new T[] inside a generic class', 'Erasure leaves no component type to check stores against', true,  NULL,                                  0),
  ('not write new T[] inside a generic class', 'Arrays cannot hold objects',                                false, 'Thinks a generic array can be created', 1),
  ('not write new T[] inside a generic class', 'You can, with a cast',                                       false, 'Thinks a generic array can be created', 2),
  ('not write new T[] inside a generic class', 'Only if T extends Object',                                   false, 'Thinks a generic array can be created', 3),

  ('static method reference its class type parameter', 'No — declare the method generic with its own parameter', true,  NULL,                                                     0),
  ('static method reference its class type parameter', 'Yes, T is available everywhere in the class',            false, 'Believes a static method can use the class type parameter', 1),
  ('static method reference its class type parameter', 'Only if the class is final',                             false, 'Believes a static method can use the class type parameter', 2),
  ('static method reference its class type parameter', 'Only inside a static initialiser',                       false, 'Believes a static method can use the class type parameter', 3),

  -- ---- concurrency ---------------------------------------------------------
  ('ConcurrentHashMap differ from wrapping a HashMap', 'It locks per bucket rather than the whole map', true,  NULL,                                                 0),
  ('ConcurrentHashMap differ from wrapping a HashMap', 'There is no practical difference',               false, 'Thinks HashMap plus synchronized equals ConcurrentHashMap', 1),
  ('ConcurrentHashMap differ from wrapping a HashMap', 'Only that it is more convenient',                false, 'Thinks HashMap plus synchronized equals ConcurrentHashMap', 2),
  ('ConcurrentHashMap differ from wrapping a HashMap', 'The wrapper is faster under contention',          false, 'Thinks HashMap plus synchronized equals ConcurrentHashMap', 3),

  ('Thread.sleep release the locks', 'No — only wait() releases the monitor', true,  NULL,                                   0),
  ('Thread.sleep release the locks', 'Yes, sleeping releases all locks',       false, 'Believes Thread.sleep releases the lock', 1),
  ('Thread.sleep release the locks', 'Yes, but only the outermost lock',       false, 'Believes Thread.sleep releases the lock', 2),
  ('Thread.sleep release the locks', 'Only if the sleep exceeds one second',   false, 'Believes Thread.sleep releases the lock', 3),

  -- ---- spring-boot ---------------------------------------------------------
  ('singleton bean stores request data in an instance field', 'One instance is shared across threads, so requests overwrite each other', true,  NULL,                                 0),
  ('singleton bean stores request data in an instance field', 'Nothing — Spring creates one bean per request',                            false, 'Thinks singleton beans are thread-safe', 1),
  ('singleton bean stores request data in an instance field', 'Nothing — Spring synchronises bean access',                                 false, 'Thinks singleton beans are thread-safe', 2),
  ('singleton bean stores request data in an instance field', 'Only a memory leak',                                                        false, 'Thinks singleton beans are thread-safe', 3),

  ('@Transactional method throws a checked exception', 'No — only unchecked exceptions roll back by default', true,  NULL,                                                       0),
  ('@Transactional method throws a checked exception', 'Yes, any exception rolls back',                        false, 'Believes @Transactional rolls back on checked exceptions', 1),
  ('@Transactional method throws a checked exception', 'Yes, if the method is public',                         false, 'Believes @Transactional rolls back on checked exceptions', 2),
  ('@Transactional method throws a checked exception', 'Only if it propagates out of the bean',                false, 'Believes @Transactional rolls back on checked exceptions', 3)
) AS o(stem_match, text, correct, misconception_name, position)
JOIN items it ON it.stem LIKE '%' || o.stem_match || '%'
LEFT JOIN misconceptions m
       ON m.name = o.misconception_name
      AND m.skill_id = it.skill_id
ON CONFLICT DO NOTHING;
