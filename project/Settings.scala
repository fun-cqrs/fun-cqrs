import scalariform.formatter.preferences._
import sbt.Keys._
import sbt._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform._

object Settings {

  import sbt.Keys._

  import sbt._

  import ScalariformKeys._

  val commonSettings = SbtScalariform.defaultScalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test    := formattingPreferences
  )

  import scalariform.formatter.preferences._
  def formattingPreferences =
    FormattingPreferences()
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(MultilineScaladocCommentsStartOnFirstLine, true)
      .setPreference(PreserveDanglingCloseParenthesis, true)
      .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true)
}