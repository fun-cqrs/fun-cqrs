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
    "brand-primary" -> "#5B5988",
    "brand-secondary" -> "#292E53",
    "brand-tertiary" -> "#222749",
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

// Documentation site =======================================
lazy val docs = (project in file("docs"))
  .settings(defaultSettings: _*)
  .settings(micrositeSettings: _*)
  .settings(buildInfoSettings: _*)
  .settings(moduleName := "docs")
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(BuildInfoPlugin)