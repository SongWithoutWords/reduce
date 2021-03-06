package ripl.parse

trait Token // also extended by `Atom`
case object Token {
  case object Newline extends Token
  case object Indent  extends Token
  case object Dedent  extends Token
  case object LParen  extends Token
  case object RParen  extends Token
  case object Dot     extends Token

  sealed trait PrefixOperator extends Token
  case object Apostrophe      extends PrefixOperator
  case object Circumflex      extends PrefixOperator
  case object Tilda           extends PrefixOperator
}
