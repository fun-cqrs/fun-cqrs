package lottery.akka.persistence

//import akka.persistence.journal.{ Tagged, WriteEventAdapter }
// import lottery.domain.model.{ Lottery, LotteryProtocol }

// class TagWriteEventAdapter extends WriteEventAdapter {

//   def manifest(event: Any): String = ""

//   def toJournal(event: Any): Any = {
//     event match {
//       // all lottery events get tagged with lottery tag!
//       case evt: LotteryProtocol.LotteryEvent => Tagged(evt, Set(Lottery.tag.value))
//       case evt                               => evt
//     }
//   }
// }
