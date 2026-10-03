# Code review and fix plan

Review date: 2026-10-03, commit `f2744f7` ("Fix newest intellij version").

## How the review was done

- Every file under `src/main`, the Gradle build, the GitHub workflows and the test setup were read.
- The lexer and the Grammar-Kit grammar were compared against the generator's own parser
  (`lezer-parser/generator`, `src/parse.ts`) and the generator's exported API and CLI flags.
- The build was compared with the current JetBrains plugin template (`intellij-platform-plugin-template`, main)
  and the IntelliJ Platform Gradle Plugin 2.14 to 2.19 changelog, which moved several settings to conventions.
- CI history was checked: run 3 (`b6eb901`) failed `verifyPlugin` with internal and deprecated API usages, run 4
  (`f2744f7`) is green in all four jobs (build, test, verify against 253 to 263, release draft).

What is in good shape and needs no work: the build file matches the current template (the template dropped the
explicit `pluginConfiguration`, `pluginVerification`, `signing` and `publishing` blocks, the Gradle plugin now defaults
them, including `ides.recommended()` and the `PUBLISH_TOKEN`/`PRIVATE_KEY`/... environment variables); the BNF is a
faithful port of the generator's parser (name characters, props, markers, `@specialize` with one literal argument,
empty `()`, `@skip` scopes, `@local tokens`, `@else`, external declarations all match); the built-in prop list matches
`NodeProp` in `@lezer/common`; the CLI flags match `lezer-generator.cjs`; the Kotlin `jvmDefault = NO_COMPATIBILITY`
setting is the right fix for the verifier's "overrides deprecated/experimental method" reports.

## Findings

Severity: **High** = wrong behaviour users can hit, **Medium** = latent bug, waste or a trap for later versions,
**Low** = divergence, cleanup or convention.

### High

**H1. Local generator search and install can escape the project up to the filesystem root.**
`GeneratorLocator.searchDirectoriesInReadAction` (`src/main/kotlin/de/paulmethfessel/lezer/generator/GeneratorLocator.kt:130`)
walks from the grammar's directory upwards and stops only when it reaches the file's content root. For a grammar
outside every content root (a file opened from elsewhere, a scratch file, a temp directory) the content root is null,
the fallback is the project directory, which is never an ancestor, so the loop walks to `/`. Consequences:
`findLocal` probes `/node_modules/@lezer/generator`, `~/node_modules/...` etc., and
`localInstallDirectory` (`:87`) returns the first ancestor with a `package.json` (possibly the home directory) or
`/`. The "Install locally" links in the editor banner, the playground banner and the run configuration quick fix then
run `npm install --save-dev` there after a yes/no dialog that names the directory.

**H2. The live generator check can throw an unhandled `IOException`.**
`GeneratorCheck.run` (`GeneratorCheck.kt:38`) writes the whole grammar to the node process' stdin and only then waits
for it. If node exits before reading (a custom generator directory whose module fails to load, a generator too old to
export `buildParserFile`, a grammar larger than the pipe buffer with a crashing script), the write or close throws
`IOException`. `LezerGeneratorAnnotator.doAnnotate` (`LezerGeneratorAnnotator.kt:44`) catches only
`ExecutionException`, so the exception surfaces as a plugin error from the daemon instead of the "lezer-generator
failed" file-level warning the report type was designed for.

**H3. A syntax divergence silently disables all live generator diagnostics for a file.**
The lexer ends string literals and character sets at the end of the line
(`src/main/grammar/Lezer.flex:33-35`), the generator allows line breaks inside both (`parse.ts`: `/^(\\.|[^"\\])*"/`).
A grammar the generator accepts therefore gets a false "Unterminated string literal" error, and because
`LezerGeneratorAnnotator.collectInformation` skips files with PSI errors (`LezerGeneratorAnnotator.kt:34`), the
generator's real errors, warnings and conflicts disappear for that file as well. Rare in practice (the official grammars
never do it) but the failure mode is total rather than local.

### Medium

**M1. Dependabot is configured for a branch that does not exist.**
`.github/dependabot.yml:9,15` targets `next` (template convention); the repository only has `main`. Dependabot logs an
error and never opens an update PR, so the pinned IDE, Kotlin, Gradle plugin and action versions silently age.

**M2. The VFS save listener forces file type detection for every saved file.**
`GenerateOnSaveListener.after` (`generator/run/GenerateOnSave.kt:25`) evaluates `it.file.fileType` for every content
change event in the IDE. For files without a registered extension that is content-based detection, done on the EDT
inside the VFS event. `PlaygroundModuleListener` already does the cheap `extension` check first; this listener should
too.

**M3. Playground input is persisted forever, keyed by absolute path.**
`LezerPlaygroundState` (`playground/LezerPlaygroundState.kt:17,24`) stores the input text, top rule and dialects for
every grammar ever shown in `workspace.xml` and never removes entries. Deleted or moved grammars and large pasted inputs
stay in the workspace file indefinitely.

**M4. Any JavaScript/TypeScript save restarts the playground worker.**
`PlaygroundModuleListener.needsRestart` (`playground/LezerPlaygroundToolWindow.kt:84`) restarts the node worker for
every changed `.js/.ts/...` file in the project that is not under `node_modules` and not a generator output, as soon as
the grammar imports at least one `@external` module. In a project with an application next to the grammar, every save
kills and respawns node and rebuilds the parser. Restricting the check to the grammar's package root (the directory
`LezerGrammars.packageRoot` already computes) keeps the safety without the cost.

**M5. Disk and process work on the EDT without caching.**
`NodeLocator.find`/`detect` scan the PATH and version-manager directories, `GeneratorLocator.load` reads
`package.json`, and nothing is cached. They run on the EDT from `LezerGeneratorRunConfiguration.checkConfiguration`
(`generator/run/LezerGeneratorRunConfiguration.kt:39`, called while the run configuration dialog validates), from
`LezerConfigurable.detectedNodeComment` (`generator/LezerConfigurable.kt:52`, when the settings page opens) and under a
read action from the editor notification provider. On slow or network file systems these freeze the UI.

**M6. Floating toolbar is registered on every editor.**
`GenerateOnSaveFloatingToolbarProvider` (`generator/run/GenerateOnSaveFloatingToolbarProvider.kt:13`) no longer
overrides `isApplicable`, so the platform creates a toolbar component with a mouse-motion listener for every editor
(consoles, diffs, every language) and relies on the action hiding itself. The platform's replacement is
`isApplicableAsync(DataContext)`, present from 2026.2; the deprecated `isApplicable` still works in all supported
versions and only shows as a deprecation note in the verifier (it is not a failure level). This is a trade-off to
revisit when `since-build` moves to 262.

**M7. The generator's string escapes are not decoded.**
`LezerStrings.unquote` (`psi/LezerStrings.kt:4`) maps every `\x` to `x`, the generator's `readString` decodes `\n`,
`\t`, `\0`, `\xNN`, `\uNNNN` and `\u{...}`. `ConflictLocator.symbolKey` compares literals from generator messages with
literals from the PSI through this function, so conflicts involving escaped literals (`"\n"`, `"\\"`) are not located
and fall back to a file-level annotation. `@name="..."` values are unaffected in practice.

**M8. `GeneratorLocator.esModule` handles fewer `exports` shapes than the playground script.**
`GeneratorLocator.kt:113` reads `exports["."].import` only when it is a string; `lezer-playground.mjs` also accepts
`{import: {default: "..."}}`. The current `@lezer/generator` manifest uses plain strings, so this only matters for a
future generator release or a fork; the two resolvers should at least agree.

### Low

**L1. `std.digit` style character classes.** The generator accepts `std.<class>` as an alias of `@<class>`
(`parse.ts`, `parseExprInner`); the BNF parses it as a scoped name (`Lezer.bnf:204`) with no reference, highlighting
or documentation. Not an error, just no support.

**L2. `@conflict` accepted inside `@external specialize`/`extend` token sets.** `Lezer.bnf:159,161` reuses
`externalTokenSet`; the generator passes `allowConflicts = false` there and reports it. Lenient parse, the generator
annotator reports it anyway.

**L3. Run configuration identity compares path strings.** `LezerGeneratorRunConfigurationProducer`
(`generator/run/LezerGeneratorRunConfigurationProducer.kt:25`) matches `grammarFile(context)?.path == options.grammarFile`;
a configuration edited to a relative path or a different separator is treated as a different one and the gutter creates
a duplicate.

**L4. Keyword completion can insert a duplicate space.** `suffixHandler`
(`completion/LezerKeywordCompletionContributor.kt:103`) skips the suffix only when the trimmed suffix already follows;
for the blank suffix `" "` it always inserts, giving two spaces when the caret was already before a space.

**L5. Two plugin descriptions.** `README.md:3-5` still carries the template's `<!-- Plugin description -->` markers
although nothing reads them (the current template keeps the description in `plugin.xml` only). The README's feature
list and the `plugin.xml` description already differ and will keep drifting.

**L6. `pluginRepositoryUrl` is not set in `gradle.properties`.** The Gradle plugin's changelog convention (2.15+) takes
the changelog's `repositoryUrl` from it; without it the rendered change notes and the `[Unreleased]` link cannot point
at the repository.

**L7. Internationalisation is inconsistent.** `messages/LezerBundle.properties` exists but is used only by the inlay
hint registration; the inspection display name, the notification group, the configurable, actions, settings labels,
quick fix names and notification texts are inline strings. Plugin DevKit inspections flag the missing `key`
attributes, and the Marketplace guidelines ask for bundled display names.

**L8. Tool window icon.** `plugin.xml:108` reuses `AllIcons.Toolwindows.ToolWindowStructure`, so the playground is
indistinguishable from the Structure tool window in the tool window bar. A plugin-specific 13x13 (and new UI 16x16)
icon is expected here.

**L9. Older but still supported APIs.** `LezerDocumentationProvider` extends `AbstractDocumentationProvider`
(`documentation/LezerDocumentationProvider.kt:21`); the platform's current API is `DocumentationTargetProvider`
and `DocumentationTarget`. `Alarm` in the playground panel is the pre-coroutine debouncer. Neither is deprecated;
they are candidates when `since-build` moves up, not now.

**L10. Build file nits.** `tasks.compileKotlin { dependsOn(generateLexer, generateParser) }` (`build.gradle.kts:46`)
is redundant with `java.srcDir(task)`, which already carries the dependency. `.run/Run Verifications.run.xml` points
its log file at the 1.x sandbox path (inherited from the template, harmless).

## Plan

Each step is one commit with its own tests; steps inside a phase are independent.

### Phase 1: correctness (H1, H2, H3, M2)

1. **Bound the directory walk** (H1). In `searchDirectoriesInReadAction` stop at the content root, else at
   `project.guessProjectDir()` only if it is an ancestor, else at the user's home directory, and never add the
   filesystem root. Make `localInstallDirectory` return `null` when no ancestor inside those bounds has a `package.json`
   and the grammar has no content root. Disable the "Install locally" actions (banner, playground, run configuration
   quick fix) when it is null and say why in the notification. Test: `GeneratorLocatorTest` with a grammar in a temp
   directory outside the project (expect null, no `/` in the search list) and one under a content root (unchanged).
2. **Make the check script robust to early exit** (H2). Wrap the stdin write in `GeneratorCheck.run` with
   `try/catch (IOException)` that waits for the process and returns `GeneratorReport(failure = stderr)`; add
   `IOException` to the catch in `LezerGeneratorAnnotator.doAnnotate` as a second line of defence. Test:
   `LezerGeneratorIntegrationTest` with a custom generator directory whose `package.json` points at a module that
   throws on load; expect the file-level "lezer-generator failed" warning, no exception.
3. **Match the generator's string and set lexing** (H3). Allow line breaks in `DQ_STRING`, `SQ_STRING` and
   `CHAR_SET_CONTENT` in `Lezer.flex` (keep the "may be unterminated" form so recovery still works, but let an
   unterminated literal run to the end of the file like the generator does, or to the next unescaped quote). Update
   `LezerErrorAnnotator.isTerminated` accordingly and the lexer and annotator tests (`LezerLexerTest`,
   `LezerErrorAnnotatorTest`) with a multi-line string that must lex as one token and a truly unterminated one that
   must still be reported. Re-run the real-grammar parsing tests.
4. **Cheap file filter in the save listener** (M2). In `GenerateOnSaveListener.after` check
   `it.file.extension == LezerFileType.defaultExtension` before `fileType`. No new test needed beyond
   `testGenerateOnSave`.

### Phase 2: robustness and performance (M3 to M8)

5. **Trim playground state** (M3). Cap `inputs` at a fixed number of entries (least recently used, e.g. 20), drop
   entries longer than a few kilobytes on save, and remove entries whose grammar file no longer exists when the panel
   opens. Test in `LezerPlaygroundStateTest`.
6. **Scope worker restarts** (M4). Pass the grammar's package root (reuse the walk from `LezerGrammars.packageRoot`,
   move it to a shared helper) into `needsRestart` and only restart for files under that root or in `loaded`. Extend
   `PlaygroundUnitTest`.
7. **Cache Node and generator lookups** (M5). Add a small cache in `NodeLocator`/`GeneratorLocator` keyed on the
   settings state and the `package.json` modification time, invalidated by `LezerGeneratorRefresher.refresh` and the
   installer (which already calls `invalidate()`). Move the `checkConfiguration` lookups behind the cache. Keep
   `canRunProcess()` as is.
8. **Decode generator escapes** (M7). Port `readString` from `parse.ts` into `LezerStrings.unquote` and use it in
   `ConflictLocator.symbolKey` and `LezerNodeNames.explicitName`. Add cases to `ConflictLocatorTest` with `"\n"` and
   `"\\"` literals.
9. **Align module resolution** (M8). Accept nested condition objects (`import.default`, `default.default`) in
   `GeneratorLocator.esModule`, mirroring `resolveEsm` in `lezer-playground.mjs`; unit test with the `@lezer/generator`
   manifest shapes seen in versions 1.x.
10. **Floating toolbar applicability** (M6). Decide between (a) overriding the deprecated `isApplicable(DataContext)`
    now, which limits registration to grammar editors on every supported version at the cost of one deprecation note
    in the verifier report, or (b) keeping the current approach and switching to `isApplicableAsync` when
    `since-build` becomes 262. Record the decision in the class comment either way.

### Phase 3: project hygiene (M1, L3 to L8, L10)

11. **Dependabot** (M1). Either create and protect a `next` branch as in the template workflow, or set
    `target-branch` to `main`. The second is simpler for a single-maintainer repository.
12. **Single plugin description** (L5). Remove the README markers and keep `plugin.xml` as the source, or restore the
    template's extraction in `build.gradle.kts` (`pluginConfiguration.description` from the README section) and delete
    the duplicate in `plugin.xml`. Pick one; the first keeps the build file aligned with the current template.
13. **Changelog links** (L6). Add `pluginRepositoryUrl = https://github.com/Paulpanther/lezer_idea` to
    `gradle.properties`.
14. **Bundle user-facing strings** (L7). Add `<resource-bundle>messages.LezerBundle</resource-bundle>` to
    `plugin.xml`, a `LezerBundle` object (as in the template's `MyBundle`), move the inspection, notification group,
    configurable, action and settings texts to keys. Mechanical, large diff, do it in one commit without behaviour
    changes.
15. **Own tool window icon** (L8). Add `icons/toolWindowLezer.svg` (13x13) and the `expui` variant, reference it from
    `plugin.xml` and `LezerIcons`.
16. **Small fixes** (L3, L4, L10). Normalise paths in `isConfigurationFromContext` (compare `Path` objects after
    resolving against the working directory); make `suffixHandler` skip a blank suffix when the next character is
    already whitespace; drop the redundant `dependsOn`; fix the `.run` log path. Each with a one-line test where one
    exists (`LezerKeywordCompletionTest`, `LezerGeneratorIntegrationTest.testConfigurationFromContext`).

### Phase 4: tracked for later (L1, L2, L9)

17. **Grammar parity items** (L1, L2). Add `std.<class>` as a `charClassExpression` alternative (lexer rule
    `"std." {class}` or a parser rule on `NAME DOT NAME` with `std`), and split `externalTokenSet` into a variant
    without `@conflict` for specializers with a readable error. Add parser test data for both.
18. **API modernisation when `since-build` is raised** (L9, M6). Migrate the documentation provider to
    `DocumentationTarget`, replace `Alarm` with a coroutine debounce in the playground, switch the floating toolbar to
    `isApplicableAsync`, and review `ToolWindowFactory` overrides. Keep the verifier matrix (`recommended()`) as the
    gate; it currently covers 253 to 263.

### Verification for every phase

- `./gradlew check` with `LEZER_GENERATOR_DIR` set, as the CI test job does.
- `./gradlew verifyPlugin`; the build must stay free of internal API usages (the default failure levels) and should
  not add deprecated usages beyond the one accepted in step 10(a).
- The real-grammar corpus in `src/test/testData/parser/real` must keep parsing without errors and resolving without
  unresolved hard references (`LezerResolveTest.testRealGrammarsResolve`).
