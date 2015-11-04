
type StringToInt = PartialFunction[String, Int]

val receive: StringToInt = {
  case "abc" => 1
  case "def" => 2
}

val fallback: StringToInt =  {
  case _     => 0
}

val pf = receive //orElse fallback

val v = "ads"


if (pf.isDefinedAt(v)) {
  println(pf(v))
} else {
  println(s"can't handle msg: $v")
}