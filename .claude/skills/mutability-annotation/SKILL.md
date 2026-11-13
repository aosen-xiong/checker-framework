---
name: mutability-annotation
description: Rules for adding Mutability Checker (GAIT) qualifiers to Java code and measuring the result. Covers the annotated JDK, target file sets, counting conventions, the client protocol (annotations and one-line-reason suppressions only), the edit patterns that pay, and known checker defects with annotation-only workarounds. Use when adding @Immutable/@Readonly/@Mutable/@ReceiverDependentMutable/@PolyMutable/@Assignable to the JDK, Guava, a client library or a test, or when interpreting Mutability Checker output.
---

# Adding Mutability Checker annotations

Hard-won rules from annotating the JDK (52 `java.util` files), Guava (411 files), the
client libraries Jimfs, gson and commons-io, and the Commons Lang tuples. Each rule cost a
wrong conclusion or a reverted pass.

## 1. Every number needs its toolchain stated

A diagnostic count means nothing without **which checker commit**, **which annotated
JDK**, **which `--patch-module` root** and **which file list**. Say all four whenever
reporting. Never compare a number across checkers or JDK configurations. Doing so once
produced four wrong conclusions in one session, two of them false claims that a build was
broken.

Reference results on branch `gait` at f4062696e, built against the annotated JDK at
96fbe6402d4:

| Target | Errors |
|---|---|
| the 52 JDK files in `java/util/TypecheckedList.txt` | 0 |
| the 411 claimed Guava files (tiers T1 to T3) | 0 |
| the whole Guava module | 64 |

A change that moves one of these is a checker change, not an annotation change.
Re-establish the baseline before measuring anything new.

The module root is `<jdk>/src/java.base/share/classes`, **not** `<jdk>/src/java.base`.
One level too high makes javac resolve `java.base` from the runtime image. The annotated
JDK is then silently ignored, and every `K extends @Immutable Object` fails with
`type.invalid.annotations.on.use`.

## 2. Check for stale `.class` files, and always pass `-d`

`.class` files inside a JDK source tree shadow the sources. They are usually gitignored,
so `git status` shows nothing. They once inflated a measurement from 154 to 1741.

```bash
find <jdk>/src/java.base -name '*.class' | wc -l   # expect 0 before measuring
```

Scope the check to `src/java.base`. Maven output under `src/utils/LogCompilation/target/`
shadows nothing and is not yours to delete.

Running the checker without `-d` writes `.class` files next to the sources. That is how
they get there. The tell is `type.invalid.annotations.on.use` against `java.util.*`
types, that is, the annotated JDK appearing to be bypassed. Always pass `-d` to a scratch
directory.

## 3. Report the crash count with every error count

A crashed file emits **no diagnostics**, so it silently lowers the total. Always print
both:

```bash
echo "errors=$(grep -cE 'error: \[' compile.log) crashes=$(grep -c 'The Checker Framework crashed' compile.log)"
```

An error count from a run with crashes is a lower bound, not a result. In Jimfs four files
still crash, so the lines after each crash point are unchecked. Say so next to the
number.

## 4. Measure against a target file set, never a module total

Model the set on `java/util/TypecheckedList.txt` in the annotated JDK. The Guava case study
uses `TypecheckedList.txt` with tiers. **T1** is the regression baseline of files already
clean, which must stay 0. **T2** is the deliverable. Score every pass as "T1 stays 0, T2
falls". A change that lowers the module total while breaking a T1 file is a regression the
total cannot show.

Traps:
- A crashed file reports zero diagnostics, so it looks clean. Subtract the crash set.
- Per-file results depend on scope. `-implicit:none` changes what the checker sees, so a
  file clean in a whole-module run can fail in a subset. Derive the baseline by running
  the target set and iterating to a fixed point.
- **A changed contract reaches files you never edited.** Changing a supertype signature
  (a receiver, a return, a type-variable bound) adds errors to every subclass, including
  files outside the target set. Before claiming "no regressions outside the target set",
  run the whole module at the old and new commit, in separate worktrees with separate
  `-d` directories, and compare per-file counts. In Guava this found 41 new errors in 18
  files that the edited-file comparisons had missed.
- A script that swaps old file versions in must save and restore the working copies. It
  must never run `git checkout -- <dir>`, which discards uncommitted edits.

To check a few files of a larger library, use `-sourcepath <src> -implicit:none` and list
only the target files. The rest of the library is read from source but not checked.

## 5. Counting conventions

These are the conventions the published numbers use. A different rule gives a different
number, so state the rule.

- **Qualifiers.** Count `@Immutable`, `@Readonly`, `@Mutable`, `@ReceiverDependentMutable`,
  `@PolyMutable` and `@MutabilityLost`, simple or fully qualified. Count `@Assignable`
  separately. Skip imports, comments and strings. A simple `@Immutable` in a file that does
  not import the checker's `Immutable` is Error Prone's, and does not count.
- **Suppression keys.** Count each `"mutability"` or `"mutability:<key>"` string. Strings
  inside `@AnnotatedFor` and `@CFComment` are not suppressions. A `@CFComment` string that
  starts with `mutability:` once inflated a count from 304 to 305.
- **Lines.** Use the non-comment lines of the **original** files, not the annotated ones.
  Added suppression lines inflate the annotated count. For one study the annotated count
  was 739 and the correct original count was 729.
- **Qualified type variables.** Count a qualifier on a *use* of a type variable, such as
  `ArrayList<@PolyMutable E> this` or `<N1 extends @Immutable N>`. Resolve the names with
  javac after attribution (a `com.sun.source.util.Plugin` on the `ANALYZE` event), and use
  a regex only as a cross-check. Count per (file, line, column): one line can hold two
  sites. When a figure mixes categories, such as "all qualifiers" for one library and
  "`@Immutable` only" for another, report both libraries with the same rule.

## 6. The client protocol: annotations only, every suppression justified

To annotate a library you do not own:

1. Add **only** qualifiers, receiver parameters, explicit `extends Object` bounds and
   `@SuppressWarnings` with a one-line reason. Change no code.
2. **Audit.** Diff the annotated files against the originals with qualifiers, suppressions,
   comments, qualifier imports and receiver parameters stripped. Expect 0 differing code
   lines. Run the audit over `<merge-base>..<branch>`, not against `HEAD`, which checks
   only the last step. Pair removed and added text per hunk, not per line, or inserted
   suppression lines look like code changes.
3. **Load-bearing check.** Remove each suppression key alone and re-run. An error must come
   back. If it does not, the key is dead weight hiding future errors.
4. **Classify** each suppression by its reason:
   - **A** JDK gap: the annotated JDK lacks a qualifier.
   - **B** Guava gap.
   - **C** library design, for example a collection that requires `@Immutable` keys.
   - **D** GAIT limitation, such as a class literal or a qualifier the system cannot
     express.
   - **E** checker defect.
   - **F** client problem, such as a raw type.

   A reason that names an unannotated helper library (for example "StringUtils is not
   annotated") belongs in its own group.

Use the narrow key, never a bare `"mutability"`, in the one-line house style:

```java
@SuppressWarnings("mutability:argument.type.incompatible") // annotated JDK: Normalizer.normalize takes a @Mutable CharSequence
```

Keep CRLF files CRLF. Read and write sources with `newline=''` in Python, or the tool
rewrites every line ending.

## 7. Never add a type annotation with a regex

`<K, V>` to `<K extends @Immutable Object, V>` also rewrites type-parameter *uses*
(`new HashBiMap<K, V>()`), which is not valid Java. Parse instead: find `<` only after
`class|interface|enum Name` or a run of method modifiers, scan to the **matching** `>`,
and split on top-level commas.

Related traps:
- Line-anchored regexes miss multi-line declarations (`class Foo<\n  K extends ...`).
- `^\s*` before a keyword lookahead backtracks into the whitespace and matches statements
  as declarations. Strip the line first.
- Read class qualifiers from the modifier region only. `class CompactHashMap<K extends
  @Immutable Object, V>` is an RDM class with an immutable key bound.
- Enclosing-class attribution is `depth == memberDepth - 1`. Looser tests attribute
  members declared after a nested class to that nested class.
- Type-parameter names are a heuristic. In `Invokable<T, R>`, `R` is a return type.

## 8. Prefer log-driven edits

Read the checker's `file:line` and parameter name, and touch exactly that site. Every pass
that guessed a pattern needed a revert. The log-driven ones did not.

## 9. Which edits pay

Measured yield on Guava:

1. **Class-level qualifiers.** 6 annotations, 116 fewer errors. Audit `Immutable*` classes
   for a missing class-level `@Immutable` first.
2. **Parameter qualifiers on overrides.** 60 annotations, 57 fewer. The
   `equals`/`contains(@Readonly Object)` family is the common gap.
3. **Key type-parameter bounds.** `K extends @Immutable Object` on map keys. 198
   annotations, 586 fewer.

**Return qualifiers do not pay on their own.** `override.return.invalid` falls and
`return.type.incompatible` rises by the same amount, because the body still produces the
old mutability. Fix the whole chain (method and its factory helper, receiver and return)
or nothing.

**Value-storing APIs need `@Readonly` parameters end to end.** If an API that stores
caller values (an exception context, a builder) takes `@Mutable` parameters, every caller
passing a `String` or another immutable value is rejected. Make the parameter, the stored
element type and the getter return `@Readonly`. In one Commons Lang study, the first pass
used 17 qualifiers with `@Mutable` value parameters, and callers passing a `String` were
rejected. The `@Readonly` version needed 40 qualifiers and accepts every value.

## 10. Qualifier semantics worth remembering

- `@Assignable` is needed **only** for a non-final **instance** field of an `@Immutable`
  or RDM class. Static non-final fields are already assignable. `@Assignable` on a
  `final` field is an error. It is a declaration annotation (`@Target(FIELD)`).
- A field cannot be `@PolyMutable` (`field.polymutable.forbidden`). For a cached view, keep
  the field unqualified and `@Assignable`, and put the poly on the method's receiver and
  return.
- `@PolyMutable` is meaningful only in an RDM class. In an `@Immutable` class the receiver
  is always immutable.
- An `@Immutable` class cannot override a `@PolyMutable`-returning method usefully. Method
  scope is invariant.
- `@Mutable X` is invalid when `X` is declared `@Immutable`
  (`type.invalid.annotations.on.use`).
- **Never write `@Readonly T` when `T` is declared `@Immutable`.** It is well-formed but
  strictly less precise, and the precision cannot be recovered downstream. `@Readonly
  Object` is fine. Before a sweep that removes a qualifier by type name, resolve the name:
  nested names such as `EntrySet`, `Values` and `KeySet` are `@Immutable` in some files and
  not others.
- **An `@ReceiverDependentMutable` type-parameter bound** makes an `@Immutable` instance
  have `@Immutable` elements, and an `@Immutable Pair<@Mutable X, ...>` is rejected. A
  `@Readonly` view of such a class is ill-formed, and a written `@MutabilityLost` type
  argument is forbidden. The cost: code that stores values in such a container cannot
  store a mutable value. A `@Readonly` bound accepts everything and guarantees nothing.
- Method modes (`@AbstractState`, the default, `@ConcreteState`, `@ReadonlyState`,
  `@TransitiveState`) are invariant under overriding (`method.mode.override.invalid`). A
  method may call methods of its own mode or a stronger one. `@TransitiveState` is the
  strongest, `@ConcreteState` and `@ReadonlyState` both refine `@AbstractState`, and those
  two are incomparable. `-AsideEffectFreeAsTransitiveState` treats
  `@SideEffectFree` and `@Pure` as transitive state, and overrides inherit them.

## 11. Library-specific rules

- **Guava** uses Error Prone's `@Immutable`. When a file needs both, keep the Error Prone
  import and write the checker's fully qualified
  (`@org.checkerframework.checker.mutability.qual.Immutable`), as in `collect/Range.java`.
  Never delete or comment out an Error Prone annotation. `@Immutable(containerOf=...)` and
  `@LazyInit` are independent ground truth.
- **The JDK.** Add a missing JDK qualifier to the annotated JDK on its own branch, and
  measure clients against both the pinned JDK and that branch. Report which one each
  number uses.

## 12. Do not revert on an error delta

An increase can mean the change is wrong, incomplete, or measured on a dirty tree.
Diagnose before reverting, and ask before discarding someone else's design decision. One
revert was based on a contaminated measurement 11 times too high.

## 13. Settle semantics questions in a five-line file

Questions about variance or defaulting are answered in minutes by a tiny test file, and
often against intuition. Two examples:

- Array component qualifiers are covariant (`@Mutable Object[]` widens to `@Readonly
  Object[]`).
- A class declared `@Immutable` defaults to `@Immutable` in a type-argument position, so
  `List<Imm>` already means `List<@Immutable Imm>`.

Keep the probe in a scratch directory, compile it with the same flags as the real run, and
pass `--patch-module java.base=<jdk>/src/java.base/share/classes`. Mark each line under
test with a comment and report each line as accepted or rejected with its error key. When
a probe line depends on an earlier rejected line, say that its verdict is moot.

## 14. Unneeded suppressions

`-AwarnUnneededSuppressions` works with `-AassumeInitialized` on any branch that contains
eisop commit d808a912c. Do **not** remove `-AassumeInitialized` to get the report: on
Guava that causes 149 `@Covariant` crashes, and a crashed file's suppressions all look
unneeded.

Two traps remain:
- `-nowarn` hides the report, because `unneeded.suppression` is a warning.
- The check is per annotation, not per key. If one key of `@SuppressWarnings({...})` was
  needed, the others are never reported. The count is a lower bound. Confirm with the
  load-bearing check of rule 6.

## 15. Checker defects with an annotation-only workaround

- `new @Q Generic<?>[n]` inside a method with a parameter crashes the framework. Use a
  `@Readonly` local and leave the creation bare.
- An unannotated anonymous class fails the override check against a `@Readonly` receiver,
  while `new @Mutable AbstractIterator<T>() {...}` passes.
- `@PolyMutable` varargs always fail (the array must be `@MutabilityBottom`). The same
  defect hits `Arrays.asList` (eisop issue 2293).
- A `@PolyMutable` receiver adapts a receiver-dependent result to `@MutabilityLost`, and
  `new @PolyMutable X(rdmArg)` adapts an RDM constructor parameter to `@MutabilityLost`.
  Prefer a `@Readonly` parameter with a return-only poly.
- A `@Mutable T` return is lost through a type variable. Revert those annotations rather
  than suppressing around them.
- Inference compares an element type with an RDM bound that was not adapted, for method
  references such as `Pair::getKey`. Suppress `type.arguments.not.inferred` as a checker
  defect.
- An implicit array initializer `{}` in a static context defaults to RDM, and static
  `throws` types default to RDM. Write the qualifier explicitly.
- A varargs override crashes the checker (Jimfs). There is no workaround, so report the
  crash.

## 16. Shell traps

- zsh does not word-split `$VAR`. A file list in a variable reaches the command as one
  argument. Run such loops with `bash -c '...'` or use arrays.
- macOS ships bash 3.2, which has no `mapfile`. Use `arr=($(cmd))` or a `while read` loop.
- `grep -c` exits 1 on zero matches. Under `set -e` this ends a script unless the grep is
  inside a command substitution used as an argument, or is followed by `|| true`.
- Guard every `rm -rf "$VAR"` as `rm -rf "${VAR:?}"`.
