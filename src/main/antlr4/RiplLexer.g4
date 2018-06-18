lexer grammar RiplLexer;

@header { package ripl.parser.antlr; }

tokens { Indent, Dedent }

@lexer::members {

  // A queue where extra tokens are pushed on (see the NEWLINE lexer rule).
  private java.util.LinkedList<Token> tokens = new java.util.LinkedList<>();
  // The stack that keeps track of the indentation level.
  private java.util.Stack<Integer> indents = new java.util.Stack<>();
  // The amount of opened braces, brackets and parenthesis.
  private int opened = 0;
  // The most recently produced token.
  private Token lastToken = null;
  @Override
  public void emit(Token t) {
    super.setToken(t);
    tokens.offer(t);
  }

  @Override
  public Token nextToken() {
    // Check if the end-of-file is ahead and there are still some DEDENTS expected.
    if (_input.LA(1) == EOF && !this.indents.isEmpty()) {
      // Remove any trailing EOF tokens from our buffer.
      for (int i = tokens.size() - 1; i >= 0; i--) {
        if (tokens.get(i).getType() == EOF) {
          tokens.remove(i);
        }
      }

      // First emit an extra line break that serves as the end of the statement.
      // this.emit(commonToken(Python3Parser.NEWLINE, "\n"));
      this.emit(commonToken(Newline, "\n"));

      // Now emit as much DEDENT tokens as needed.
      while (!indents.isEmpty()) {
        this.emit(createDedent());
        indents.pop();
      }

      // Put the EOF back on the token stream.
      // this.emit(commonToken(Python3Parser.EOF, "<EOF>"));
      this.emit(commonToken(EOF, "<EOF>"));
    }

    Token next = super.nextToken();

    if (next.getChannel() == Token.DEFAULT_CHANNEL) {
      // Keep track of the last token on the default channel.
      this.lastToken = next;
    }

    return tokens.isEmpty() ? next : tokens.poll();
  }

  private Token createDedent() {
    // CommonToken dedent = commonToken(Python3Parser.DEDENT, "");
    // CommonToken dedent = commonToken(Dedent, "<DEDENT>");
    CommonToken dedent = commonToken(Dedent, "");
    dedent.setLine(this.lastToken.getLine());
    return dedent;
  }

  private CommonToken commonToken(int type, String text) {
    int stop = this.getCharIndex() - 1;
    int start = text.isEmpty() ? stop : stop - text.length() + 1;
    return new CommonToken(this._tokenFactorySourcePair, type, DEFAULT_TOKEN_CHANNEL, start, stop);
  }

  // Calculates the indentation of the provided spaces, taking the
  // following rules into account:
  //
  // "Tabs are replaced (from left to right) by one to eight spaces
  //  such that the total number of characters up to and including
  //  the replacement is a multiple of eight [...]"
  //
  //  -- https://docs.python.org/3.1/reference/lexical_analysis.html#indentation
  static int getIndentationCount(String spaces) {
    int count = 0;
    for (char ch : spaces.toCharArray()) {
      switch (ch) {
        case '\t':
          count += 8 - (count % 8);
          break;
        default:
          // A normal space char.
          count++;
      }
    }

    return count;
  }

  boolean atStartOfInput() {
    return super.getCharPositionInLine() <= 0 && super.getLine() <= 1;
  }
  boolean atEndOfInput() {
    return _input.LA(1) == EOF;
  }
}

Newline
 : ( {atStartOfInput()}? ' '+
   | ( '\r'? '\n' ) ' '*
   )
   {
     String newLine = getText().replaceAll("[^\r\n\f]+", "");
     String spaces = getText().replaceAll("[\r\n\f]+", "");
     int next = _input.LA(1);
     if (opened > 0 || next == '\r' || next == '\n' || next == '\f' || next == '#') {
       // If we're inside a list or on a blank line, ignore all indents,
       // dedents and line breaks.
       skip();
     }
     else {
       emit(commonToken(Newline, newLine));
       int indent = getIndentationCount(spaces);
       int previous = indents.isEmpty() ? 0 : indents.peek();
       if (indent == previous) {
         // skip indents of the same size as the present indent-size
         skip();
       }
       else if (indent > previous) {
         indents.push(indent);
         // emit(commonToken(Python3Parser.INDENT, spaces));
         // emit(commonToken(Indent, "<INDENT" + spaces + ">"));
         // emit(commonToken(Indent, spaces));
         emit(commonToken(Indent, ""));
       }
       else {
         // Possibly emit more than 1 DEDENT token.
         while(!indents.isEmpty() && indents.peek() > indent) {
           this.emit(createDedent());
           this.emit(commonToken(Newline, "\n"));
           indents.pop();
         }
       }
     }
   }
 ;

Spaces
    : (' ' | '\n') -> skip
    ;

fragment Underscore
    : '_';
fragment Upper
    : 'A' .. 'Z';
fragment Lower
    : 'a' .. 'z';
fragment Letter
    : Upper | Lower;
fragment LetterOrUnderscore
    : Letter | Underscore;
fragment Digit
    : '0' .. '9';
fragment LetterOrUnderscoreOrDigit
    : LetterOrUnderscore | Digit;

fragment OpChar
    : '!'
    | '#'
    | '$'
    | '%'
    | '&'
    | '*'
    | '+'
    | '-'
    // | '.' // Not sure it can be used without ambiguity
    | '/'
    | ':'
    | '<'
    | '='
    | '>'
    | '?'
    // | '@' // Not sure it can be used without ambiguity
    | '^'
    // | '~' // Not sure it can be used without ambiguity
    ;

// keywords
If
    : 'if';
Then
    : 'then';
Else
    : 'else';
Data
    : 'data';
Union
    : 'union';
Namespace
    : 'namespace';

// punctuation
LParen
    : '(';
RParen
    : ')';
LBrace
    : '{';
RBrace
    : '}';
Semicolon
    : ';';
Comma
    : ',';
Period
    : '.';
Quote
    : '"';
Apostrophe
    : '\'';

// operators
Tilda
    : '~';
Plus
    : '+';
Minus
    : '-';
Star
    : '*';
Slash
    : '/';
Percent
    : '%';
Chevron
    : '^';
Ampersand
    : '&';
Equal
    : '=';
LT
    : '<';
LTEQ
    : '<=';
EQ
    : '==';
NEQ
    : '!=';
GTEQ
    : '>=';
GT
    : '>';
At
    : '@';

ThinArrow
    : '->';
FatArrow
    : '=>';

And
    : 'and';
Or
    : 'or';
Not
    : 'not';


// tokens with data
VInt
    : '0' | [1-9][0-9]*;

VFlt
    : VInt '.' [0-9]*;

VBln
    : 'true'
    | 'false';

VStr
    : '"' .*? '"'
    ;

Name
    : LetterOrUnderscore LetterOrUnderscoreOrDigit*
    | OpChar+
    ;

