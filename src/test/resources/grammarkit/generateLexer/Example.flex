%%

/* The next section is for options and macros. Things defined here can be used
 * in the next rules section.
 */

/* The class will be called GeneratedLexer */
%class GeneratedLexer

/* The yylex() method will return an instance of Token */
%type Token

/* The yylex() method will throw a LexicalException */
%scanerror LexicalException

%unicode

/* Code in the next section is copied into the generated lexer class.
 */
%{
%}

/* Here we define some macros, which are abbriviations for regular expressions.
 */

LineTerminator = \r|\n|\r\n
WhiteSpace     = {LineTerminator} | [ \t\f]

/* NOT VALID ASSIGNEMNT IDENTIFIERS!!!! CHANGE ME!!!! */
Identifier = [:jletter:] [:jletterdigit:]*

DecIntegerLiteral = [0-9]

%state STRING

%%

/* Now we define keywords in our grammer
 * When the input string matches the regex on the left the action on the right
 * is performed. The action is java code. The regex matches longest match by
 * default. The lexer starts in the <YYINITIAL> state.
 */

<YYINITIAL> "if"                 { return new T_If(); }
<YYINITIAL> "then"               { return new T_Then(); }
<YYINITIAL> "else"               { return new T_Else(); }

<YYINITIAL> {
  /* identifiers */ 
  {Identifier}                   { return new T_Identifier(yytext()); }
  /* use a macro to match integers */
  {DecIntegerLiteral}            { return new T_Integer(Integer.parseInt(yytext())); }

  /* operators */
  "=="                           { return new T_Equal(); }
  "="                            { return new T_EqualDefines(); }
  "+"                            { return new T_Plus(); }
  ";"                            { return new T_Semicolon(); }

  /* whitespace */
  {WhiteSpace}                   { /* ignore */ }
}

/* error fallback */
[^]                              { throw new LexicalException("Illegal character <" +
                                                    yytext() + ">"); }
