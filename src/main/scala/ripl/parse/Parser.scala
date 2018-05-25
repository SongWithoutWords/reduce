package ripl.parse

import org.antlr.v4.runtime._

import ripl.ast.untyped._
import ripl.parser.antlr._

case object Parser {
  def apply(input: String): Node = {
    val charStream = new ANTLRInputStream(input)
    val lexer = new riplLexer(charStream)
    val tokens = new CommonTokenStream(lexer)
    val parser = new riplParser(tokens)

    ParseTreeToAst(parser.exp1())
  }
}
