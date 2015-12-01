package io.funcqrs.akka

import java.time.OffsetDateTime
import java.util.UUID

import io.funcqrs._

object TestModel {

  case class User(name: String, age: Int, id: UserId) extends AggregateLike {
    type Id = UserId
    type Protocol = UserProtocol.type
  }

  object User {

    def behavior(id: UserId): Behavior[User] = {
      import UserProtocol._
      import io.funcqrs.dsl.BehaviorDsl
      val dsl = new BehaviorDsl[User]
      import dsl.api._

      whenConstructing {
        _.processesCommands {
          case cmd: CreateUser if cmd.age >= 0 => UserCreated(cmd.name, cmd.age, metadata(id, cmd))
        } acceptsEvents {
          case evt: UserCreated => User(evt.name, evt.age, id)
        }
      } whenUpdating {
        _.processesCommands {
          case (_, cmd: ChangeName) => NameChanged(cmd.newName, metadata(id, cmd))
        } acceptsEvents {
          case (user, evt: NameChanged) => user.copy(name = evt.newName)
        }
      }
    }
  }
  case class UserId(value: String) extends AggregateID
  object UserId {
    def generate() = UserId(UUID.randomUUID().toString)
  }

  object UserProtocol extends ProtocolLike {

    case class UserMetadata(aggregateId: UserId,
                            commandId: CommandId,
                            eventId: EventId = EventId(),
                            date: OffsetDateTime = OffsetDateTime.now(),
                            tags: Set[Tag] = Set()) extends Metadata with JavaTime {

      type Id = UserId
    }

    def metadata(id: UserId, cmd: UserCmd) = {
      UserMetadata(id, cmd.id)
    }

    trait UserCmd extends ProtocolCommand
    trait UserEvt extends ProtocolEvent with MetadataFacet[UserMetadata]

    case class CreateUser(name: String, age: Int) extends UserCmd
    case class UserCreated(name: String, age: Int, metadata: UserMetadata) extends UserEvt

    case class ChangeName(newName: String) extends UserCmd
    case class NameChanged(newName: String, metadata: UserMetadata) extends UserEvt
  }

}
