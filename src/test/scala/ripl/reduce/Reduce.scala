package ripl.reduce

import scala.language.implicitConversions

import org.scalatest._

import ripl.util.{MultiMap => Multi}

import ripl.ast.common._
import ripl.ast.common.TypeAtom._
import ripl.ast.common.ImplicitConversions._
import ripl.ast.{untyped => a0, typed => a1}

import ripl.reduce.CustomMatchers.matchAst

class TestReduce extends FreeSpec with Matchers {

  def test(
      in: Multi[String, a0.Exp]
    )(out: Multi[String, a1.Node]
    )(errs: Error*
    ): Unit = Reduce(in) should matchAst((out, Set(errs: _*)))

  def test(in: (String, a0.Exp)*)(out: (String, a1.Node)*)(errs: Error*): Unit =
    test(Multi(in: _*))(Multi(out: _*))(errs: _*)

  def testErrs(in: Multi[String, a0.Exp])(errs: Error*): Unit =
    Reduce(in)._2.shouldBe(Set(errs: _*))

  def testErrs(in: (String, a0.Exp)*)(errs: Error*): Unit =
    testErrs(Multi(in: _*))(errs: _*)

  "constants" - {
    "4 + 5 is 9" in {
      test("a" -> a0.App(Name("+"), 4, 5))("a" -> 9)()
    }
    "a + b is 9 given a = 4 and b = 5" in {
      test(
        "a" -> 4,
        "b" -> 5,
        "c" -> a0.App(Name("+"), Name("a"), Name("b"))
      )("a" -> 4, "b" -> 5, "c" -> 9)()
    }
  }
  "named references" - {
    "are found" in {
      test("a" -> 4, "b" -> Name("a"))("a" -> 4, "b" -> 4)()
    }
    "are found in any order" in {
      test("a" -> Name("b"), "b" -> 4)("a" -> 4, "b" -> 4)()
    }
    "produce errors when they don't exist" in {
      test("a" -> Name("b"))("a" -> a1.Name("b"))(UnknownName("b"))
    }
    // What are your criteria, your true criteria going to be to detect cyclic variables?
    // Is it the depth of the cycle that are important or the contents? Contents, I think.
    // If the cycle includes more than 1 variable, it is verukt.
    // Might need to sit down with a pad of paper for this one.

    "produce errors when they form cycles" - {
      "at depth 0" in {
        val cycle = a1.Cycle(a1.Cycle.Node(Name("a")) :: Nil)
        test("a" -> Name("a"))("a" -> a1.Name("a", cycle))(
          RecursiveVariableDef(cycle)
        )
      }
      "at depth 1" in {
        val cycle = a1.Cycle(
          a1.Cycle.Node(Name("a")) :: a1.Cycle.Node(Name("b")) :: Nil
        )
        test("a" -> Name("b"), "b" -> Name("a"))(
          "a" -> a1.Name("b", a1.Name("a", cycle)),
          "b" -> a1.Name("a", cycle)
        )(RecursiveVariableDef(cycle))
      }
      "at depth 2" in {
        val cycle = a1.Cycle(
          a1.Cycle.Node(Name("a")) ::
            a1.Cycle.Node(Name("c")) ::
            a1.Cycle.Node(Name("b")) :: Nil
        )
        testErrs("a" -> Name("b"), "b" -> Name("c"), "c" -> Name("a"))(
          RecursiveVariableDef(cycle)
        )
      }
      // TODO: add similar tests to catch cycles in sub-expressions
    }
  }
  "namespaces" - {
    "are traversed during reduction" in {
      test("n" -> a0.Namespace("a" -> a0.Cons(Name("Bln"), 4)))(
        "n" -> a1.Namespace("a" -> a1.Cons(TBln, 4))
      )(TypeConflict(TBln, TInt))
    }
    "units are visible within their namespace" in {
      test("n" -> a0.Namespace("a" -> 4, "b" -> Name("a")))(
        "n" -> a1.Namespace("a" -> 4, "b" -> 4)
      )()
    }
    "units are visible from sub-namespaces" in {
      test(
        "n" -> a0.Namespace("a" -> 4, "m" -> a0.Namespace("b" -> Name("a")))
      )("n" -> a1.Namespace("a" -> 4, "m" -> a1.Namespace("b" -> VInt(4))))()
    }
    "units are not visible from outer namespaces" in {
      test("n" -> a0.Namespace("a" -> VInt(4)), "b" -> Name("a"))(
        "n" -> a1.Namespace("a" -> VInt(4)),
        "b" -> a1.Name("a")
      )(UnknownName("a"))
    }
    "units can be selected by name" in {
      test(
        "n" -> a0.Namespace("a" -> VInt(4)),
        "b" -> a0.Select(Name("n"), "a")
      )("n" -> a1.Namespace("a" -> VInt(4)), "b" -> 4)()
    }
    "units can be selected by name at depth" in {
      test(
        "n" -> a0.Namespace("a" -> 4, "m" -> a0.Namespace("b" -> VInt(7))),
        "c" -> a0.Select(a0.Select(Name("n"), "m"), "b")
      )(
        "n" -> a1.Namespace("a" -> 4, "m" -> a1.Namespace("b" -> VInt(7))),
        "c" -> 7
      )()
    }
  }
  "types" - {
    "are mapped correctly" - {
      "Bln" in {
        test("bool" -> Name("Bln"))("bool" -> TBln)()
      }
      "(Int, Int) -> Bln" in {
        test("f" -> a0.TFun(Name("Int"), Name("Int"))(Name("Bln")))(
          "f" -> a1.TFun(TInt, TInt)(TBln)
        )()
      }
    }
  }
  "type constraints" - {
    "produce no errors when they are met" in {
      test("x" -> a0.Cons(Name("Int"), 3))("x" -> a1.Cons(TInt, 3))()
    }
    "produce errors when they are not met" in {
      test("x" -> a0.Cons(Name("Int"), true))("x" -> a1.Cons(TInt, true))(
        TypeConflict(TInt, TBln)
      )
    }
  }

  "assignment" - {}
  "functions" - {
    "with one parameter" - {
      "bind parameter in body" in {
        test(
          "identity" -> a0.Fun(a0.Param("a", Name("Int")))(
            Some(Name("Int"))
          )(Name("a"))
        )(
          "identity" -> a1
            .Fun(a1.Param("a", TInt))(TInt)(a1.Name("a", a1.Param("a", TInt)))
        )()
      }
      "bind parameter in deep exp in body" in {
        test(
          "inc" -> a0.Fun(a0.Param("a", Name("Int")))(Some(Name("Int")))(
            a0.App(Name("+"), Name("a"), 1)
          )
        )(
          "inc" -> a1.Fun(a1.Param("a", TInt))(TInt)(
            a1.App(a1.Intrinsic.IAdd, a1.Name("a", a1.Param("a", TInt)), 1)
          )
        )()
      }
    }

    "with two parameters" - {
      val add =
        a0.Fun(a0.Param("a", Name("Int")), a0.Param("b", Name("Int")))(
          Some(Name("Int"))
        )(a0.App(Name("+"), Name("a"), Name("b")))

      val addPrime = a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(TInt)(
        a1.App(
          a1.Intrinsic.IAdd,
          a1.Name("a", a1.Param("a", TInt)),
          a1.Name("b", a1.Param("b", TInt))
        )
      )

      "bind parameters in body" in {
        test("add" -> add)("add" -> addPrime)()
      }
      "produce no errors when applied to right types" in {
        val x      = a0.App(Name("add"), 4, 5)
        val xPrime = a1.App(a1.Name("add", addPrime), 4, 5)
        test("add" -> add, "x" -> x)("add" -> addPrime, "x" -> xPrime)()
      }
      "produce errors when applied to too few args" in {
        val x      = a0.App(Name("add"), 4)
        val xPrime = a1.App(a1.Name("add", addPrime), 4)
        test("add" -> add, "x" -> x)("add" -> addPrime, "x" -> xPrime)(
          WrongNumArgs(2, 1)
        )
      }
      "produce errors when applied to too many args" in {
        val x      = a0.App(Name("add"), 4, 5, 6)
        val xPrime = a1.App(a1.Name("add", addPrime), 4, 5)
        test("add" -> add, "x" -> x)("add" -> addPrime, "x" -> xPrime)(
          WrongNumArgs(2, 3)
        )
      }
      "produce errors when applied to wrong types" in {
        val x      = a0.App(Name("add"), 4, true)
        val xPrime = a1.App(a1.Name("add", addPrime), 4, true)
        test("add" -> add, "x" -> x)("add" -> addPrime, "x" -> xPrime)(
          TypeConflict(TInt, TBln)
        )
      }
      "produce errors when non-applicable type applied" in {
        test("a" -> a0.App(4, 5))("a" -> 4)(ApplicationOfNonAppliableType(TInt))
      }
    }

    "enforce known return types" - {

      val _vector =
        a0.Struct("Vector", "x" -> Name("Flt"), "y" -> Name("Flt"))
      val vector = a1.Struct("Vector", "x" -> TFlt, "y" -> TFlt)

      val _point =
        a0.Struct("Point", "x" -> Name("Int"), "y" -> Name("Int"))
      val point = a1.Struct("Point", "x" -> TInt, "y" -> TInt)

      "produce errors when Bln is required and Int is returned" in {
        test(
          "f" -> a0.Fun(
            a0.Param("a", Name("Int")),
            a0.Param("b", Name("Int"))
          )(Some(Name("Bln"))) {
            a0.App(Name("+"), Name("a"), Name("b"))
          }
        )(
          "f" -> a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(TBln) {
            a1.App(
              a1.Intrinsic.IAdd,
              a1.Name("a", a1.Param("a", TInt)),
              a1.Name("b", a1.Param("b", TInt))
            )
          }
        )(TypeConflict(TBln, TInt))
      }
      "produce errors when Bln is required and Vector is returned" in {
        test(
          "Vector" -> _vector,
          "f" -> a0.Fun(
            a0.Param("a", Name("Int")),
            a0.Param("b", Name("Int"))
          )(Some(Name("Bln"))) {
            a0.VObj(Name("Vector"), "x" -> 1.f, "b" -> 2.f)
          }
        )(
          "Vector" -> vector,
          "f" -> a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(TBln) {
            a1.VObj(vector, "x" -> 1.f, "b" -> 2.f)
          }
        )(TypeConflict(TBln, vector))
      }
      "produce errors when Point is required and Int is returned" in {
        test(
          "Point" -> _point,
          "f" -> a0.Fun(
            a0.Param("a", Name("Int")),
            a0.Param("b", Name("Int"))
          )(Some(Name("Point"))) {
            a0.App(Name("+"), Name("a"), Name("b"))
          }
        )(
          "Point" -> point,
          "f" -> a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(point) {
            a1.App(
              a1.Intrinsic.IAdd,
              a1.Name("a", a1.Param("a", TInt)),
              a1.Name("b", a1.Param("b", TInt))
            )
          }
        )(TypeConflict(point, TInt))
      }
      "produce errors when Point is required and Vector is returned" in {
        test(
          "Point"  -> _point,
          "Vector" -> _vector,
          "f" -> a0.Fun(
            a0.Param("a", Name("Int")),
            a0.Param("b", Name("Int"))
          )(Some(Name("Point"))) {
            a0.VObj(Name("Vector"), "x" -> 1.f, "b" -> 2.f)
          }
        )(
          "Point"  -> point,
          "Vector" -> vector,
          "f" -> a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(point) {
            a1.VObj(vector, "x" -> 1.f, "b" -> 2.f)
          }
        )(TypeConflict(point, vector))
      }
    }

    "infer return type when no excplicit type is provided" - {
      "f(Int a, Int b) => a + b has return type Int" in {
        test(
          "f" -> a0.Fun(
            a0.Param("a", Name("Int")),
            a0.Param("b", Name("Int"))
          )(None) {
            a0.App(Name("+"), Name("a"), Name("b"))
          }
        )(
          "f" -> a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(TInt) {
            a1.App(
              a1.Intrinsic.IAdd,
              a1.Name("a", a1.Param("a", TInt)),
              a1.Name("b", a1.Param("b", TInt))
            )
          }
        )()
      }
      "f(Flt a, Flt b) => a + b has return type Flt" in {
        test(
          "f" -> a0.Fun(
            a0.Param("a", Name("Flt")),
            a0.Param("b", Name("Flt"))
          )(None) {
            a0.App(Name("+"), Name("a"), Name("b"))
          }
        )(
          "f" -> a1.Fun(a1.Param("a", TFlt), a1.Param("b", TFlt))(TFlt) {
            a1.App(
              a1.Intrinsic.FAdd,
              a1.Name("a", a1.Param("a", TFlt)),
              a1.Name("b", a1.Param("b", TFlt))
            )
          }
        )()
      }
      "f(Int a, Flt b) => a + b has return type Flt" in {
        test(
          "f" -> a0.Fun(
            a0.Param("a", Name("Int")),
            a0.Param("b", Name("Flt"))
          )(None) {
            a0.App(Name("+"), Name("a"), Name("b"))
          }
        )(
          "f" -> a1.Fun(a1.Param("a", TInt), a1.Param("b", TFlt))(TFlt) {
            a1.App(
              a1.Intrinsic.FAdd,
              a1.App(a1.Intrinsic.ItoF, a1.Name("a", a1.Param("a", TInt))),
              a1.Name("b", a1.Param("b", TFlt))
            )
          }
        )()
      }
      "f(Int a, Int b) => a == b has return type Bln" in {
        test(
          "f" -> a0.Fun(
            a0.Param("a", Name("Int")),
            a0.Param("b", Name("Int"))
          )(None) {
            a0.App(Name("=="), Name("a"), Name("b"))
          }
        )(
          "f" -> a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(TBln) {
            a1.App(
              a1.Intrinsic.IEql,
              a1.Name("a", a1.Param("a", TInt)),
              a1.Name("b", a1.Param("b", TInt))
            )
          }
        )()
      }
      // TODO: Do an example with a user-defined type when you have constructors on line
    }

    "can be defined recursively" - {
      "with explicit return type" in {
        val _fact = a0.Fun(a0.Param("n", Name("Int")))(Some(Name("Int")))(
          a0.If(
            a0.App(Name("<="), Name("n"), 1),
            1,
            a0.App(
              Name("*"),
              Name("n"),
              a0.App(Name("fact"), a0.App(Name("-"), Name("n"), 1))
            )
          )
        )

        val cycle = a1.Cycle(a1.Cycle.Fun(_fact, List(TInt), Some(TInt)) :: Nil)

        val fact = a1.Fun(a1.Param("n", TInt))(TInt)(
          a1.If(
            a1.App(a1.Intrinsic.ILeq, a1.Name("n", a1.Param("n", TInt)), 1),
            1,
            a1.App(
              a1.Intrinsic.IMul,
              a1.Name("n", a1.Param("n", TInt)),
              a1.App(
                a1.Name("fact", cycle :: Nil),
                a1.App(a1.Intrinsic.ISub, a1.Name("n", a1.Param("n", TInt)), 1)
              )
            )
          )
        )

        test("fact" -> _fact)("fact" -> fact)()
      }
      "will raise an error without an explicit return type" in {
        val _fact = a0.Fun(a0.Param("n", Name("Int")))(None)(
          a0.If(
            a0.App(Name("<="), Name("n"), 1),
            1,
            a0.App(
              Name("*"),
              Name("n"),
              a0.App(Name("fact"), a0.App(Name("-"), Name("n"), 1))
            )
          )
        )

        val cycle = a1.Cycle(a1.Cycle.Fun(_fact, List(TInt), None) :: Nil)

        val fact = a1.Fun(a1.Param("n", TInt))(TInt)(
          a1.If(
            a1.App(a1.Intrinsic.ILeq, a1.Name("n", a1.Param("n", TInt)), 1),
            1,
            a1.App(
              a1.Intrinsic.IMul,
              a1.Name("n", a1.Param("n", TInt)),
              // Application gets replaced with first exp in case of non-applicable type
              a1.Name("fact", cycle :: Nil)
            )
          )
        )

        test(
          "fact" -> _fact
        )(
          "fact" -> fact
        )(
          RecursiveFunctionLacksExplicitReturnType(cycle)
        )
      }
    }
  }
  "if exps" - {
    "produce no errors with correct types" in {
      val select = a0.Fun(
        a0.Param("a", Name("Bln")),
        a0.Param("b", Name("Int")),
        a0.Param("c", Name("Int"))
      )(Some(Name("Int")))(a0.If(Name("a"), Name("b"), Name("c")))
      val selectPrime =
        a1.Fun(a1.Param("a", TBln), a1.Param("b", TInt), a1.Param("c", TInt))(
          TInt
        )(
          a1.If(
            a1.Name("a", a1.Param("a", TBln)),
            a1.Name("b", a1.Param("b", TInt)),
            a1.Name("c", a1.Param("c", TInt))
          )
        )
      test("select" -> select)("select" -> selectPrime)()
    }
    "produces error with non-boolean condition" in {
      val select = a0.Fun(
        a0.Param("a", Name("Int")),
        a0.Param("b", Name("Int")),
        a0.Param("c", Name("Int"))
      )(Some(Name("Int")))(a0.If(Name("a"), Name("b"), Name("c")))
      val selectPrime =
        a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt), a1.Param("c", TInt))(
          TInt
        )(
          a1.If(
            a1.Name("a", a1.Param("a", TInt)),
            a1.Name("b", a1.Param("b", TInt)),
            a1.Name("c", a1.Param("c", TInt))
          )
        )

      test("select" -> select)("select" -> selectPrime)(
        TypeConflict(TBln, TInt)
      )
    }
    "branches must yield compatible types" in {
      val select = a0.Fun(
        a0.Param("a", Name("Bln")),
        a0.Param("b", Name("Int")),
        a0.Param("c", Name("Bln"))
      )(Some(Name("Int")))(a0.If(Name("a"), Name("b"), Name("c")))
      val selectPrime =
        a1.Fun(a1.Param("a", TBln), a1.Param("b", TInt), a1.Param("c", TBln))(
          TInt
        )(
          a1.If(
            a1.Name("a", a1.Param("a", TBln)),
            a1.Name("b", a1.Param("b", TInt)),
            a1.Name("c", a1.Param("c", TBln))
          )
        )

      test("select" -> select)("select" -> selectPrime)(
        TypeConflict(TInt, TBln)
      )
    }
  }
  "local variables" - {
    "in blocks" - {
      val _block = a0.Block(a0.Var("x", 4), Name("x"))
      val block  = a1.Block(a1.Var("x", 4), 4)
      "are bound correctly" in {
        test("b" -> _block)("b" -> block)()
      }
      "are not bound outside" in {
        test("b" -> _block, "y" -> Name("x"))(
          "b" -> block,
          "y" -> a1.Name("x")
        )(UnknownName("x"))
      }
    }
    "in functions" - {
      val _inc = a0.Fun(a0.Param("a", Name("Int")))(Some(Name("Int")))(
        a0.Block(
          a0.Var("result", a0.App(Name("+"), Name("a"), 1)),
          Name("result")
        )
      )

      val result =
        a1.App(a1.Intrinsic.IAdd, a1.Name("a", a1.Param("a", TInt)), 1)
      val inc = a1.Fun(a1.Param("a", TInt))(TInt)(
        a1.Block(a1.Var("result", result), a1.Name("result", result))
      )

      "are bound correctly" in {
        test("inc" -> _inc)("inc" -> inc)()
      }
      "are not bound outside" in {
        test("inc" -> _inc, "res" -> Name("result"))(
          "inc" -> inc,
          "res" -> a1.Name("result")
        )(UnknownName("result"))
      }
    }
  }
  "selection" - {
    "members can be selected from struct values" in {
      val _point =
        a0.Struct("Point", "x" -> Name("Int"), "y" -> Name("Int"))
      val point = a1.Struct("Point", "x" -> TInt, "y" -> TInt)
      test(
        "Point" -> _point,
        "a"     -> a0.VObj(Name("Point"), "x" -> 7, "y" -> 3),
        "b"     -> a0.Select(Name("a"), "y")
      )(
        "Point" -> point,
        "a"     -> a1.VObj(point, "x" -> 7, "y" -> 3),
        "b"     -> 3
      )()
    }
    "members can be selected from struct variables" in {
      val _point =
        a0.Struct("Point", "x" -> Name("Int"), "y" -> Name("Int"))
      val _getX = a0.Fun(a0.Param("point", Name("Point")))(
        Some(Name("Int"))
      )(a0.Select(Name("point"), "x"))

      val point = a1.Struct("Point", "x" -> TInt, "y" -> TInt)
      val getX = a1.Fun(a1.Param("point", point))(TInt)(
        a1.Select(a1.Name("point", a1.Param("point", point)), "x", TInt)
      )
      test("Point" -> _point, "getX" -> _getX)(
        "Point" -> point,
        "getX"  -> getX
      )()
    }
  }

  "overloads" - {
    "integer addition selected for ints" in {
      test(
        "add" -> a0
          .Fun(a0.Param("a", Name("Int")), a0.Param("b", Name("Int")))(
            Some(Name("Int"))
          )(a0.App(Name("+"), Name("a"), Name("b")))
      )(
        "add" -> a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(TInt)(
          a1.App(
            a1.Intrinsic.IAdd,
            a1.Name("a", a1.Param("a", TInt)),
            a1.Name("b", a1.Param("b", TInt))
          )
        )
      )()
    }
    "integer subtraction selected for ints" in {
      test(
        "sub" -> a0
          .Fun(a0.Param("a", Name("Int")), a0.Param("b", Name("Int")))(
            Some(Name("Int"))
          )(a0.App(Name("-"), Name("a"), Name("b")))
      )(
        "sub" -> a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(TInt)(
          a1.App(
            a1.Intrinsic.ISub,
            a1.Name("a", a1.Param("a", TInt)),
            a1.Name("b", a1.Param("b", TInt))
          )
        )
      )()
    }
    "integer multiplication selected for ints" in {
      test(
        "mul" -> a0
          .Fun(a0.Param("a", Name("Int")), a0.Param("b", Name("Int")))(
            Some(Name("Int"))
          )(a0.App(Name("*"), Name("a"), Name("b")))
      )(
        "mul" -> a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(TInt)(
          a1.App(
            a1.Intrinsic.IMul,
            a1.Name("a", a1.Param("a", TInt)),
            a1.Name("b", a1.Param("b", TInt))
          )
        )
      )()
    }
    "floating point addition selected for floats" in {
      test(
        "add" -> a0
          .Fun(a0.Param("a", Name("Flt")), a0.Param("b", Name("Flt")))(
            Some(Name("Flt"))
          )(a0.App(Name("+"), Name("a"), Name("b")))
      )(
        "add" -> a1.Fun(a1.Param("a", TFlt), a1.Param("b", TFlt))(TFlt)(
          a1.App(
            a1.Intrinsic.FAdd,
            a1.Name("a", a1.Param("a", TFlt)),
            a1.Name("b", a1.Param("b", TFlt))
          )
        )
      )()
    }
    "floating point subtraction selected for floats" in {
      test(
        "sub" -> a0
          .Fun(a0.Param("a", Name("Flt")), a0.Param("b", Name("Flt")))(
            Some(Name("Flt"))
          )(a0.App(Name("-"), Name("a"), Name("b")))
      )(
        "sub" -> a1.Fun(a1.Param("a", TFlt), a1.Param("b", TFlt))(TFlt)(
          a1.App(
            a1.Intrinsic.FSub,
            a1.Name("a", a1.Param("a", TFlt)),
            a1.Name("b", a1.Param("b", TFlt))
          )
        )
      )()
    }
    "floating point multiplication selected for floats" in {
      test(
        "mul" -> a0
          .Fun(a0.Param("a", Name("Flt")), a0.Param("b", Name("Flt")))(
            Some(Name("Flt"))
          )(a0.App(Name("*"), Name("a"), Name("b")))
      )(
        "mul" -> a1.Fun(a1.Param("a", TFlt), a1.Param("b", TFlt))(TFlt)(
          a1.App(
            a1.Intrinsic.FMul,
            a1.Name("a", a1.Param("a", TFlt)),
            a1.Name("b", a1.Param("b", TFlt))
          )
        )
      )()
    }
  }

  "implicit conversions" - {
    "built in" - {

      "integers can be assigned to floats" in {
        test(
          "x" -> a0.Cons(Name("Flt"), 4)
        )(
          "x" -> a1.Cons(TFlt, 4.f)
        )()
      }
      "floats cannot be assigned to ints" in {
        test(
          "x" -> a0.Cons(Name("Int"), 4.f)
        )(
          "x" -> a1.Cons(TInt, 4.f)
        )(TypeConflict(TInt, TFlt))
      }

      "floating point ops are selected for mixed floating point and integral ops" - {
        "4.f + 5 == 9.f" in {
          test(
            "x" -> a0.App(Name("+"), 4.f, 5)
          )(
            "x" -> 9.f
          )()
        }
        "4 + 5.f == 9.f" in {
          test(
            "x" -> a0.App(Name("+"), 4, 5.f)
          )(
            "x" -> 9.f
          )()
        }
      }
    }
  }

  "syntax extensions" - {
    "method call syntax" - {

      "4.+(5)" in {
        test("x" -> a0.App(a0.Select(4, "+"), 5))(
          "x" -> 9
        )()
      }

      "4.+(5).+(6)" in {
        test("x" -> a0.App(a0.Select(a0.App(a0.Select(4, "+"), 5), "+"), 6))(
          "x" -> 15
        )()
      }

      "vector length squared" in {
        val _vector =
          a0.Struct("Vector", "x" -> Name("Flt"), "y" -> Name("Flt"))

        val _vSelectX = a0.Select(Name("v"), "x")
        val _vSelectY = a0.Select(Name("v"), "y")

        val _lengthSquared =
          a0.Fun(a0.Param("v", Name("Vector")))(Some(Name("Flt")))(
            a0.App(
              Name("+"),
              a0.App(Name("*"), _vSelectX, _vSelectX),
              a0.App(Name("*"), _vSelectY, _vSelectY)
            )
          )

        val vector = a1.Struct("Vector", "x" -> TFlt, "y" -> TFlt)

        val vSelectX = a1.Select(a1.Name("v", a1.Param("v", vector)), "x", TFlt)
        val vSelectY = a1.Select(a1.Name("v", a1.Param("v", vector)), "y", TFlt)

        val lengthSquared = a1.Fun(a1.Param("v", vector))(TFlt)(
          a1.App(
            a1.Intrinsic.FAdd,
            a1.App(a1.Intrinsic.FMul, vSelectX, vSelectX),
            a1.App(a1.Intrinsic.FMul, vSelectY, vSelectY)
          )
        )

        val u = a1.VObj(vector, "x" -> 3.f, "y" -> 4.f)
        test(
          "Vector"        -> _vector,
          "lengthSquared" -> _lengthSquared,
          "u"             -> a0.VObj(Name("Vector"), "x" -> 3.f, "y" -> 4.f),
          "uLength"       -> a0.App(a0.Select(Name("u"), "lengthSquared"))
        )(
          "Vector"        -> vector,
          "lengthSquared" -> lengthSquared,
          "u"             -> u,
          "uLength"       -> a1.App(lengthSquared, u)
        )()
      }
    }
    "application syntax" - {

      "5(4)" in {
        val _apply =
          a0.Fun(a0.Param("a", Name("Int")), a0.Param("b", Name("Int")))(
            Some(Name("Int"))
          )(a0.App(Name("*"), Name("a"), Name("b")))

        val apply = a1.Fun(a1.Param("a", TInt), a1.Param("b", TInt))(TInt)(
          a1.App(
            a1.Intrinsic.IMul,
            a1.Name("a", a1.Param("a", TInt)),
            a1.Name("b", a1.Param("b", TInt))
          )
        )

        test("apply" -> _apply, "x" -> a0.App(5, 4))(
          "apply" -> apply,
          "x"     -> a1.App(apply, 5, 4)
        )()
      }

      "f(x) = a * x + b" in {

        val _line =
          a0.Struct("Line", "a" -> Name("Flt"), "b" -> Name("Flt"))
        val line = a1.Struct("Line", "a" -> TFlt, "b" -> TFlt)

        val _lSelectA = a0.Select(Name("l"), "a")
        val _lSelectB = a0.Select(Name("l"), "b")

        val lSelectA = a1.Select(a1.Name("l", a1.Param("l", line)), "a", TFlt)
        val lSelectB = a1.Select(a1.Name("l", a1.Param("l", line)), "b", TFlt)

        val _apply = a0.Fun(
          a0.Param("l", Name("Line")),
          a0.Param("x", Name("Flt"))
        )(Some(Name("Flt")))(
          a0.App(
            Name("+"),
            a0.App(Name("*"), _lSelectA, Name("x")),
            _lSelectB
          )
        )

        val apply = a1.Fun(a1.Param("l", line), a1.Param("x", TFlt))(TFlt)(
          a1.App(
            a1.Intrinsic.FAdd,
            a1.App(
              a1.Intrinsic.FMul,
              lSelectA,
              a1.Name("x", a1.Param("x", TFlt))
            ),
            lSelectB
          )
        )

        val _l = a0.VObj(Name("Line"), "a" -> 2.f, "b" -> 1.f)
        val l  = a1.VObj(line, "a"         -> 2.f, "b" -> 1.f)

        test(
          "Line"  -> _line,
          "apply" -> _apply,
          "y"     -> a0.App(_l, 4.f)
        )(
          "Line"  -> line,
          "apply" -> apply,
          "y"     -> a1.App(apply, l, 4.f)
        )()
      }
    }
  }
}
