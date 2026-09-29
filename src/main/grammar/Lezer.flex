// JFlex lexer for Lezer grammar files (*.grammar).
// Mirrors the tokenizer of the lezer-generator: https://github.com/lezer-parser/generator/blob/main/src/parse.ts
package de.paulmethfessel.lezer.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static de.paulmethfessel.lezer.psi.LezerTypes.*;

%%

%{
  public _LezerLexer() {
    this((java.io.Reader) null);
  }
%}

%public
%class _LezerLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

WHITE_SPACE=\s+

LINE_COMMENT="//"[^\r\n]*
BLOCK_COMMENT="/*" !([^]* "*/" [^]*) ("*/")?

// Strings and character sets may be unterminated, they end at the line end in that case
DQ_STRING=\"([^\\\"\r\n]|\\[^\r\n])*\"?
SQ_STRING='([^\\'\r\n]|\\[^\r\n])*'?
CHAR_SET_CONTENT=([^\\\]\r\n]|\\[^\r\n])*\]?

NAME=[\p{Alphabetic}0-9_\-]+

%%

<YYINITIAL> {
  {WHITE_SPACE}                 { return WHITE_SPACE; }

  {LINE_COMMENT}                { return LINE_COMMENT; }
  {BLOCK_COMMENT}               { return BLOCK_COMMENT; }

  {DQ_STRING}                   { return STRING; }
  {SQ_STRING}                   { return STRING; }
  "$[" {CHAR_SET_CONTENT}       { return CHAR_SET; }
  "![" {CHAR_SET_CONTENT}       { return INVERTED_CHAR_SET; }

  "@top"                        { return AT_TOP; }
  "@tokens"                     { return AT_TOKENS; }
  "@local"                      { return AT_LOCAL; }
  "@else"                       { return AT_ELSE; }
  "@skip"                       { return AT_SKIP; }
  "@precedence"                 { return AT_PRECEDENCE; }
  "@left"                       { return AT_LEFT; }
  "@right"                      { return AT_RIGHT; }
  "@cut"                        { return AT_CUT; }
  "@external"                   { return AT_EXTERNAL; }
  "@context"                    { return AT_CONTEXT; }
  "@dialects"                   { return AT_DIALECTS; }
  "@detectDelim"                { return AT_DETECT_DELIM; }
  "@conflict"                   { return AT_CONFLICT; }
  "@specialize"                 { return AT_SPECIALIZE; }
  "@extend"                     { return AT_EXTEND; }
  "@asciiLetter"
    | "@asciiLowercase"
    | "@asciiUppercase"
    | "@digit"
    | "@whitespace"
    | "@eof"                    { return CHAR_CLASS; }
  "@" {NAME}                    { return AT_NAME; }

  "tokens"                      { return KW_TOKENS; }
  "from"                        { return KW_FROM; }
  "as"                          { return KW_AS; }
  "prop"                        { return KW_PROP; }
  "propSource"                  { return KW_PROP_SOURCE; }
  "extend"                      { return KW_EXTEND; }
  "specialize"                  { return KW_SPECIALIZE; }

  "_"                           { return ANY_CHAR; }
  {NAME}                        { return NAME; }

  "{"                           { return LBRACE; }
  "}"                           { return RBRACE; }
  "("                           { return LPAREN; }
  ")"                           { return RPAREN; }
  "["                           { return LBRACKET; }
  "]"                           { return RBRACKET; }
  "<"                           { return LANGLE; }
  ">"                           { return RANGLE; }
  ","                           { return COMMA; }
  "."                           { return DOT; }
  "="                           { return EQ; }
  "|"                           { return PIPE; }
  "!"                           { return BANG; }
  "~"                           { return TILDE; }
  "*"                           { return STAR; }
  "+"                           { return PLUS; }
  "?"                           { return QUESTION; }
}

[^]                             { return BAD_CHARACTER; }
