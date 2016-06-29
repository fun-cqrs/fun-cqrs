
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform._
import ScalariformKeys._

  SbtScalariform.scalariformSettings  

  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 15)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(IndentLocalDefs, true)
    .setPreference(SpacesAroundMultiImports, false)
  // ==============================================================

addCommandAlias("format", ";scalariformFormat;test:scalariformFormat")
