/* Lexer for Terms and Function-Declarations */

package org.sosy_lab.cpachecker.util.predicates.smtInterpol;

import java.math.BigDecimal;
import java.math.BigInteger;
import java_cup.runtime.Symbol;
import java_cup.runtime.ComplexSymbolFactory;
import java_cup.runtime.ComplexSymbolFactory.Location;

/** This is a autogenerated lexer for the smtlib 2 script files.
 *  It is generated from smtlib.flex by JFlex.
 */

@javax.annotation.Generated("JFlex")
@SuppressWarnings(value = { "all", "unchecked", "fallthrough" })
%%

%class Lexer
%public
%cup
%line
%column

%{
  private ComplexSymbolFactory symbolFactory;

  public void setSymbolFactory(ComplexSymbolFactory sf) {
    symbolFactory = sf;
  }

  private Symbol symbol(int type) {
    return symbolFactory.newSymbol(yytext(), type, getStartLocation(), getEndLocation());
  }

  private Symbol symbol(int type, Object value) {
    return symbolFactory.newSymbol(yytext(), type, getStartLocation(), getEndLocation(), value);
  }

  private Location getStartLocation() {
    return new Location(yyline+1,yycolumn+1-yylength());
  }

  private Location getEndLocation() {
    return new Location(yyline+1,yycolumn+1);
  }
%}

LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
WhiteSpace     = {LineTerminator} | [ \t\f]

/* comments */
Comment = {EndOfLineComment}

EndOfLineComment     = ";" {InputCharacter}* {LineTerminator}?
Letter = [:letter:] | [:~!@$%\^&*_+\-=<>.?/] 
LetterDigit = {Letter} | [:digit:]

Numeral = 0 | [1-9][0-9]*
Decimal = {Numeral} "."  0* {Numeral}
HexaDecimal = "#x" [0-9a-fA-F]+
Binary = "#b" [01]+
QuotedString = "\""  [^\"]*  "\"" | "|"  [^|]*  "|"
String = {Letter} {LetterDigit}* | {QuotedString}

%%

<YYINITIAL>  {
  "("                    { return symbol(LexerSymbols.LPAR); }
  ")"                    { return symbol(LexerSymbols.RPAR); }

  /* Predefined Symbols */
  "!"                    { return symbol(LexerSymbols.BANG); }
  "declare-fun"          { return symbol(LexerSymbols.DECLAREFUN); }
  "exists"               { return symbol(LexerSymbols.EXISTS); }
  "forall"               { return symbol(LexerSymbols.FORALL); }
  "let"                  { return symbol(LexerSymbols.LET); }
  "set-info"             { return symbol(LexerSymbols.SETINFO); }

  /* Predefined Keywords */
  ":named"               { return symbol(LexerSymbols.NAMED); }
  ":source"              { return symbol(LexerSymbols.SOURCE); }

  /* Other Strings */
  {String}               { return symbol(LexerSymbols.STRING, yytext()); }
  
  /* Numbers */
  {Numeral}              { return symbol(LexerSymbols.NUMERAL, yytext()); }
  {Decimal}              { return symbol(LexerSymbols.DECIMAL, yytext()); }
  {HexaDecimal}          { return symbol(LexerSymbols.HEXADECIMAL, yytext()); }
  {Binary}               { return symbol(LexerSymbols.BINARY, yytext()); }
 
  /* comments */
  {Comment}              { /* ignore */ }
 
  /* whitespace */
  {WhiteSpace}           { /* ignore */ }
}

/* error fallback */
.|\n                     { return symbol(LexerSymbols.error, yytext()); }

<<EOF>>                  { return symbol(LexerSymbols.EOF); }