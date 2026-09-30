# Lezer Grammar for IntelliJ

<!-- Plugin description -->
Language support for [Lezer](https://lezer.codemirror.net/) grammar files (`*.grammar`) in IntelliJ-based IDEs.
<!-- Plugin description end -->

## Features

- Syntax highlighting, configurable under *Settings | Editor | Color Scheme | Lezer Grammar*
- Go to declaration, find usages and completion for rules, tokens, parameters, precedences, dialects and external props
- Rename (in place) and safe delete
- Structure view and navigation bar
- Line/block commenting, brace matching, quote auto-closing, code folding (blocks, comments, `// region`)
- Quick documentation (F1 / Ctrl+Q): comments directly before a declaration are its documentation (Markdown), shown with
  its signature, kind and the node it creates. Keywords, pseudo-props (`@name`, …), character classes and built-in props
  have short descriptions with links to the [Lezer guide](https://lezer.codemirror.net/docs/guide/)
- Errors and warnings of [`@lezer/generator`](https://github.com/lezer-parser/generator) (unused rules, conflicts, …) while typing
- *Lezer Generator* run configuration with all generator options, started from the gutter icon on `@top`
- Uses a local (`npm i -D @lezer/generator`) or global installation and offers to install it, configurable under
  *Settings | Languages & Frameworks | Lezer Grammar*. Node.js is taken from the JavaScript plugin's settings if available,
  otherwise it is detected automatically
- Code formatter (Reformat Code) with configurable indentation and spacing

## Development

- Lexer: [`src/main/grammar/Lezer.flex`](src/main/grammar/Lezer.flex) (JFlex), mirrors the tokenizer of
  [lezer-generator](https://github.com/lezer-parser/generator/blob/main/src/parse.ts).
- Parser/PSI: [`src/main/grammar/Lezer.bnf`](src/main/grammar/Lezer.bnf) (Grammar-Kit), ported from the official
  [Lezer grammar for Lezer](https://github.com/lezer-parser/lezer-grammar/blob/main/src/lezer.grammar).
- Name resolution: [`LezerResolver`](src/main/kotlin/de/paulmethfessel/lezer/resolve/LezerResolver.kt) follows the
  scoping of the lezer-generator (parameters shadow rules, token contexts only see tokens, inline rules aren't referenceable).
- Both are generated at build time by the `org.jetbrains.intellij.platform.grammarkit` Gradle plugin
  (`generateLexer`/`generateParser`) into `build/generated/sources`.

```sh
./gradlew runIde   # start a sandbox IDE with the plugin
./gradlew check    # lexer, parser and highlighting tests
```

`src/test/testData/parser/real` contains grammars from the lezer-parser repositories (MIT licensed) that must parse
without errors.

The tests that run the real generator are skipped unless `LEZER_GENERATOR_DIR` points to an installed
`@lezer/generator` package (and Node.js is found):

```sh
npm i --prefix /tmp/lezer @lezer/generator
LEZER_GENERATOR_DIR=/tmp/lezer/node_modules/@lezer/generator ./gradlew test
```
