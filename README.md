# Lezer Grammar for IntelliJ

<!-- Plugin description -->
Language support for [Lezer](https://lezer.codemirror.net/) grammar files (`*.grammar`) in IntelliJ-based IDEs.
<!-- Plugin description end -->

## Features

- Syntax highlighting, configurable under *Settings | Editor | Color Scheme | Lezer Grammar*
- Line/block commenting, brace matching, quote auto-closing

## Development

- Lexer: [`src/main/grammar/Lezer.flex`](src/main/grammar/Lezer.flex) (JFlex), mirrors the tokenizer of
  [lezer-generator](https://github.com/lezer-parser/generator/blob/main/src/parse.ts).
- Parser/PSI: [`src/main/grammar/Lezer.bnf`](src/main/grammar/Lezer.bnf) (Grammar-Kit), ported from the official
  [Lezer grammar for Lezer](https://github.com/lezer-parser/lezer-grammar/blob/main/src/lezer.grammar).
- Both are generated at build time by the `org.jetbrains.intellij.platform.grammarkit` Gradle plugin
  (`generateLexer`/`generateParser`) into `build/generated/sources`.

```sh
./gradlew runIde   # start a sandbox IDE with the plugin
./gradlew check    # lexer, parser and highlighting tests
```

`src/test/testData/parser/real` contains grammars from the lezer-parser repositories (MIT licensed) that must parse
without errors.
