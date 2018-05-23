package reduce

import java.nio.file.Files
import java.nio.charset.StandardCharsets
import sys.process._

import org.scalatest._
import matchers._


import reduce.ast.typed._
import reduce.util.PrettyPrint

object CustomMatchers {

  class AstMatcher(expected: Product) extends Matcher[Product] {

    def apply(result: Product) = {
      val success = result == expected;

      val expectedPath = Files.createTempFile("ast", "expected")
      val resultPath = Files.createTempFile("ast", "result")

      val expectedString = PrettyPrint(expected)
      val resultString = PrettyPrint(result)

      Files.write(expectedPath, expectedString.getBytes(StandardCharsets.UTF_8))
      Files.write(resultPath, resultString.getBytes(StandardCharsets.UTF_8))

      // if (!success) {
      //   val command = Seq(
      //     "emacsclient",
      //     "--create-frame",
      //     "--alternate-editor", "",
      //     "--eval", s"""(ediff "${resultPath.toString}" "${expectedPath.toString}")"""
      //   )

      //   println("Invoking: " + command)

      //   command.!
      // }

      // Could consider trying to get git-diff to work in future
      // might be able to get it inline with the test results

      MatchResult(
        success,
        if (!success) {
          val process = Process(Seq("git", "diff", "--no-index", expectedPath.toString, resultPath.toString))
          process.lines_!.mkString("\n")
        } else "",
        ""
      )
    }
  }

  def matchAst(expected: Product) = new AstMatcher(expected)
}

