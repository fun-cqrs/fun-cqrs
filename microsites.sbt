import BuildSettings._

// format:off
lazy val micrositeSettings = Seq(
  micrositeName := "Fun.CQRS",
  micrositeDescription := "Scala CQRS/ES library ",
  micrositeBaseUrl := "fun-cqrs",
  micrositeDocumentationUrl := "/fun-cqrs/docs/",
  micrositeGithubOwner := "strongtyped",
  micrositeGithubRepo := "fun-cqrs",
  micrositeAuthor := "Strong[Typed]",
  micrositeHomepage := "http://www.strongtyped.io",
  micrositeHighlightTheme := "color-brewer",
  micrositePalette := Map(
    "brand-primary" -> "#1C4C65", // "#185965",
    "brand-secondary" -> "#1F5571", //#1B6471",
    "brand-tertiary" -> "#1C4C65", // "#185965",
    "gray-dark" -> "#49494B",
    "gray" -> "#7B7B7E",
    "gray-light" -> "#E5E5E6",
    "gray-lighter" -> "#F4F3F4",
    "white-color" -> "#FFFFFF"),
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.md"
)
// format:on


lazy val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "funcqrs"
)
// 252338 453F78 759AAB
// 252338 89B6A5 C9EDDC
// Documentation site =======================================
lazy val docs = (project in file("docs"))
  .settings(defaultSettings: _*)
  .settings(micrositeSettings: _*)
  .settings(buildInfoSettings: _*)
  .settings(moduleName := "docs")
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(BuildInfoPlugin)