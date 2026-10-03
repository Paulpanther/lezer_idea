<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Lezer Grammar Changelog

## [Unreleased]

### Added

- Lezer grammar file type (`*.grammar`) with JFlex lexer and Grammar-Kit parser
- Syntax highlighting with a color settings page
- Commenter, brace matcher, quote handler and code folding
- References with go to declaration, completion and find usages
- Rename and safe delete refactorings
- Structure view and navigation bar
- Readable syntax error messages, errors for unterminated literals and unexpected characters
- Code formatter with configurable code style
- lezer-generator integration: live errors and warnings, run configuration with gutter icon, installation banner and settings
- Quick documentation for declarations (from preceding comments) and for keywords and built-ins, with links to the Lezer guide
- Unused rule/token inspection with quick fix, keyword completion, precedence inlay hints
- Navigation and find usages from `styleTags` keys and terms imports in JS/TS (with the JavaScript plugin)
- Option to regenerate the parser when the grammar is saved
- Lezer Playground tool window: syntax tree of example text with selection sync
