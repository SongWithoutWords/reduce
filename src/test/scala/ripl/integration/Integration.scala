package ripl.integration

import java.nio.file.Paths

import org.scalatest._

import ripl.process._

class TestIntegration extends FreeSpec with Matchers {

  def test(name: String, riplSrc: String)(out: Either[Set[Error], Int]): Unit =
    Run(Paths.get("./target/test/llvm-ir/", name + ".ll"), riplSrc) shouldBe out

  test("lambda-style-main", """define main
                              |  lambda () 42""".stripMargin)(Right(42))

  test("function-style-main", """define (main) 42""".stripMargin)(Right(42))

  test(
    "simple-function-call",
    """define (main) (meaning-of-life)
      |define (meaning-of-life) 42""".stripMargin
  )(Right(42))

  test(
    "simple-add",
    """define (main) (add 37 5)
      |define (add (Int a) (Int b)) (+ a b)""".stripMargin
  )(Right(42))

  test(
    "nested-exps",
    """define (main) (multiply-add 8 5 2)
      |define (multiply-add (Int a) (Int b) (Int c))
      |  + (* a b) c
      """.stripMargin
  )(Right(42))

  test(
    "ternary-using-if-true",
    """define (main) (ternary true 42 7)
      |define (ternary (Bln a) (Int b) (Int c))
      |  if a b c
      """.stripMargin
  )(Right(42))

  test(
    "ternary-using-if-false",
    """define (main) (ternary false 42 7)
      |define (ternary (Bln a) (Int b) (Int c))
      |  if a b c
      """.stripMargin
  )(Right(7))

  test(
    "cascading-if",
    """define (main) (cascading-if false 7 true 8 9)
      |define (cascading-if (Bln c1) (Int e1) (Bln c2) (Int e2) (Int e3))
      |  if c1 e1 (if c2 e2 e3)
      """.stripMargin
  )(Right(8))
}
