
package invoices.domain.model


import java.time.LocalDate

import fun.cqrs._
import fun.cqrs.dsl.BehaviorDsl._

case class Contract(title: Title,
                    hourlyRate: HourlyRate,
                    customerId: CustomerId,
                    startDate: StartDate,
                    number: ContractNumber) extends Aggregate {

  type Identifier = ContractNumber
  type Protocol = ContractProtocol.type

  // ContractNumber is the unique identifier for Contracts
  val identifier = number
}

case class Title(value: String) extends AnyVal

case class HourlyRate(value: Double) extends AnyVal

case class StartDate(value: LocalDate) extends AnyVal


case class ContractNumber(value: String) extends AggregateIdentifier

object ContractProtocol extends ProtocolDef.Protocol {

  case class AddNewContract(title: Title, hourlyRate: HourlyRate,
                            customerId: CustomerId, startDate: StartDate, number: ContractNumber) extends CreateCmd


  // Creation Event
  sealed trait ContractCreateEvent extends CreateEvent

  case class NewContractAdded(title: Title,
                              hourlyRate: HourlyRate,
                              customerId: CustomerId,
                              startDate: StartDate,
                              number: ContractNumber,
                              metadata: Metadata) extends ContractCreateEvent

  // Update Events
  sealed trait ContractUpdateEvent extends UpdateEvent

}

object Contract {

  import ContractProtocol._

  def behavior(number: ContractNumber): Behavior[Contract] = {
    behaviorFor[Contract].whenConstructing { it =>
      it.acceptsEvents {
        case e: NewContractAdded => Contract(e.title, e.hourlyRate, e.customerId, e.startDate, e.number)
      }
    }.whenUpdating { it =>
      it.rejectsCommands {
        case _ => new CommandException("This should never happen!! Contracts are immutable for the moment")
      }
    }
  }
}