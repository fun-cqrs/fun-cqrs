import org.reactivestreams.{ Publisher, Subscriber }

def pub: Publisher[Int] = ???

def sub: Subscriber[Int] = ???

def flow = {
  val s = pub.subscribe(sub)
  ???
}