# Lezer Grammar for IntelliJ

<!-- Plugin description -->
Language support for [Lezer](https://lezer.codemirror.net/) grammar files (`*.grammar`) in IntelliJ-based IDEs.
<!-- Plugin description end -->

## Features

- Syntax highlighting, configurable under *Settings | Editor | Color Scheme | Lezer Grammar*
- Go to declaration, find usages and completion for rules, tokens, parameters, precedences, dialects and external props
- Inspection for unused rules and tokens, with a quick fix that removes them (and their doc comment)
- Keyword completion by context: declarations, `@precedence`/`@conflict`/`@else` in token blocks, character classes,
  `@specialize`/`@extend`, associativities, pseudo-props and the words of `@external`/`@local` declarations
- Inlay hints with the rank and associativity of precedences after markers like `!times`
- With the JavaScript plugin: go to declaration and find usages from JS/TS code to the grammar, for node names in
  `styleTags({...})` keys and names imported from the generated `*.terms.js` file. Renaming a rule doesn't change the code,
  it refers to the generated parser
- Rename (in place) and safe delete
- Structure view and navigation bar
- Line/block commenting, brace matching, quote auto-closing, code folding (blocks, comments, `// region`)
- Quick documentation (F1 / Ctrl+Q): comments directly before a declaration are its documentation (Markdown), shown with
  its signature, kind and the node it creates. Keywords, pseudo-props (`@name`, …), character classes and built-in props
  have short descriptions with links to the [Lezer guide](https://lezer.codemirror.net/docs/guide/)
- Errors and warnings of [`@lezer/generator`](https://github.com/lezer-parser/generator) (unused rules, conflicts, …) while typing
- **Lezer Playground** tool window (or *Open in Lezer Playground* in a grammar's context menu): parses example text
  with the grammar of the selected editor, also while it's being edited, and shows the syntax tree. The caret in the
  text selects the innermost node, selecting a node highlights its range, double-clicking opens its declaration.
  `@top` rules and dialects can be chosen in the toolbar. `@external` implementations are imported from the modules
  named in the grammar
- *Lezer Generator* run configuration with all generator options, started from the gutter icon on `@top`,
  optionally regenerating the parser in the background whenever the grammar is saved
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
