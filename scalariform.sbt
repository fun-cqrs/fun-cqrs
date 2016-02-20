
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform._
import ScalariformKeys._


  val autoFormat = sys.props.get("autoFormat")

  // scalariform formatting settigns ==============================
  if (autoFormat.isDefined) {
    println("Scalariform: using auto format")
    SbtScalariform.scalariformSettings  
  } else {
    println("Scalariform: NOT using auto format")
    SbtScalariform.defaultScalariformSettings    
  }


  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 15)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(IndentLocalDefs, true)
    .setPreference(SpacesAroundMultiImports, false)
  // ==============================================================

addCommandAlias("format", ";scalariformFormat;test:scalariformFormat")