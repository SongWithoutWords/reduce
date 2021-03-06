package ripl.parse

import org.scalatest._

import ripl.ast.common._
import ripl.ast.parse._

import ripl.reduce.CustomMatchers.matchAst

class ParseTest extends FreeSpec with Matchers {

  def test(name: String, input: String)(out: Exp*): Unit = name in {
    Parse(input) should matchAst(out.toList)
  }

  def test(input: String)(out: Exp*): Unit = test(input, input)(out: _*)

  "whitespace" - {
    test("empty string", "")()
    test("spaces", "       ")()
    test("newlines", "\n\n\n\n")()
    test("newlines and spaces", "\n\n    \n      \n")()
  }

  "atoms" - {
    test("0")(VInt(0))
    test("195")(VInt(195))
    test("0.0")(VFlt(0))
    test("1.374")(VFlt(1.374f))
    test("unenclosed-atom")(Name("unenclosed-atom"))
  }

  "s-expressions" - {

    "non-nested" - {
      test("()")(SExp())
      test("(  )")(SExp())
      test("(     )")(SExp())

      test("(0)")(SExp(VInt(0)))
      test("(195)")(SExp(VInt(195)))
      test("(1.374)")(SExp(VFlt(1.374f)))

      test("(single-enclosed-atom)")(SExp(Name("single-enclosed-atom")))

      test("(  single-enclosed-atom-with-spaces )    ")(
        SExp(Name("single-enclosed-atom-with-spaces"))
      )

      test("(two enclosed-atoms)")(SExp(Name("two"), Name("enclosed-atoms")))

      test("(three enclosed atoms)")(
        SExp(Name("three"), Name("enclosed"), Name("atoms"))
      )

      test("(multiple 0.9 enclosed 1 atoms)")(
        SExp(
          Name("multiple"),
          VFlt(0.9f),
          Name("enclosed"),
          VInt(1),
          Name("atoms")
        )
      )

      test("(  many  0.9 enclosed 1 atoms 84.7 with spaces)")(
        SExp(
          Name("many"),
          VFlt(0.9f),
          Name("enclosed"),
          VInt(1),
          Name("atoms"),
          VFlt(84.7f),
          Name("with"),
          Name("spaces")
        )
      )
    }

    "nested" - {
      test("(())")(SExp(SExp()))
      test("(()())")(SExp(SExp(), SExp()))
      test("((()))")(SExp(SExp(SExp())))
      test("((())())")(SExp(SExp(SExp()), SExp()))

      test("((1))")(SExp(SExp(VInt(1))))

      test("((1) 2)")(SExp(SExp(VInt(1)), VInt(2)))

      test("(((1) 2) 3)")(SExp(SExp(SExp(VInt(1)), VInt(2)), VInt(3)))

      test("(a (i j (x)) b ((y z) k) c)")(
        SExp(
          Name("a"),
          SExp(Name("i"), Name("j"), SExp(Name("x"))),
          Name("b"),
          SExp(SExp(Name("y"), Name("z")), Name("k")),
          Name("c")
        )
      )
    }
  }

  "expressions grouped by line" - {
    test("fact n")(SExp(Name("fact"), Name("n")))
    test("if a b c")(SExp(Name("if"), Name("a"), Name("b"), Name("c")))

    "can occur on multiple lines" - {
      test("""|a
              |i j
              |
              |x y z""".stripMargin)(
        Name("a"),
        SExp(Name("i"), Name("j")),
        SExp(Name("x"), Name("y"), Name("z"))
      )
    }

    "can be continued via indentation" - {
      test("""|if a
              |  b
              |  c""".stripMargin)(
        SExp(Name("if"), Name("a"), Name("b"), Name("c"))
      )

      test("""|if a
              |  i j
              |  x""".stripMargin)(
        SExp(Name("if"), Name("a"), SExp(Name("i"), Name("j")), Name("x"))
      )

      test("""|a
              |  b
              |    c""".stripMargin)(
        SExp(Name("a"), SExp(Name("b"), Name("c")))
      )

      test("""|a
              |  b
              |c""".stripMargin)(SExp(Name("a"), Name("b")), Name("c"))

      test("""|a
              |  b
              |    c
              |      d""".stripMargin)(
        SExp(Name("a"), SExp(Name("b"), SExp(Name("c"), Name("d"))))
      )

      test("""|a
              |  b
              |    c
              |  d""".stripMargin)(
        SExp(Name("a"), SExp(Name("b"), Name("c")), Name("d"))
      )

      test("""|a
              |  b
              |    c
              |      d
              |  e""".stripMargin)(
        SExp(
          Name("a"),
          SExp(
            Name("b"),
            SExp(
              Name("c"),
              Name("d")
            )
          ),
          Name("e")
        )
      )

      test("""|a
              |  b
              |    c
              |  d
              |    e""".stripMargin)(
        SExp(
          Name("a"),
          SExp(Name("b"), Name("c")),
          SExp(Name("d"), Name("e"))
        )
      )

      test("""|a
              |  b
              |    c
              |      d
              |        e
              |    f
              |      g
              |  h
              |i
              |  j
              |k""".stripMargin)(
        SExp(
          Name("a"),
          SExp(
            Name("b"),
            SExp(
              Name("c"),
              SExp(
                Name("d"),
                Name("e")
              )
            ),
            SExp(Name("f"), Name("g"))
          ),
          Name("h")
        ),
        SExp(Name("i"), Name("j")),
        Name("k")
      )

      // test("(a (i j (x)) b ((y z) k) c)")
      test("""|a
              |  i j (x)
              |  b
              |  (y z)
              |    k
              |  c""".stripMargin)(
        SExp(
          Name("a"),
          SExp(Name("i"), Name("j"), SExp(Name("x"))),
          Name("b"),
          SExp(SExp(Name("y"), Name("z")), Name("k")),
          Name("c")
        )
      )

      test("""|define (fact n)
              |  if (<= n 1)
              |    1
              |    * n (fact (- n 1))""".stripMargin)(
        SExp(
          Name("define"),
          SExp(Name("fact"), Name("n")),
          SExp(
            Name("if"),
            SExp(Name("<="), Name("n"), VInt(1)),
            VInt(1),
            SExp(
              Name("*"),
              Name("n"),
              SExp(
                Name("fact"),
                SExp(
                  Name("-"),
                  Name("n"),
                  VInt(1)
                )
              )
            )
          )
        )
      )
    }
  }

  "unary operators" - {}

  "selection" - {
    test("a.b")(Select(Name("a"), Name("b")))
    test("a.b.c")(Select(Select(Name("a"), Name("b")), Name("c")))
    test("(a b).c")(Select(SExp(Name("a"), Name("b")), Name("c")))
    test("(a b c).(i j k)")(
      Select(
        SExp(Name("a"), Name("b"), Name("c")),
        SExp(Name("i"), Name("j"), Name("k"))
      )
    )
    test("a.b c")(
      SExp(
        Select(Name("a"), Name("b")),
        Name("c")
      )
    )
  }

}
