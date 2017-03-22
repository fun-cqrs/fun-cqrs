package io.funcqrs.akka

import java.util.UUID

import io.funcqrs._
import io.funcqrs.behavior._
import io.funcqrs.behavior.handlers._
object TestModel {

  case class User(name: String, age: Int, id: UserId, deleted: Boolean = false) {
    def isDeleted: Boolean = deleted
  }

  object User extends Types[User] {

    type Id      = UserId
    type Command = UserCmd
    type Event   = UserEvt

    def behavior(id: UserId) = {
      Behavior
        .first {
          actions
            .rejectCommand {
              case cmd: CreateUser if cmd.age <= 0 => new IllegalArgumentException("age must be >= 0")
            }
            .commandHandler {
              OneEvent {
                case CreateUser(name, age) => UserCreated(name, age)
              }
            }
            .eventHandler {
              case UserCreated(name, age) => User(name, age, id)
            }
        }
        .andThen {

          case user =>
            actions
              .rejectCommand {
                case _ if user.isDeleted => new IllegalArgumentException("User is already deleted!")
              }
              .commandHandler {
                OneEvent {
                  case ChangeName(newName) => NameChanged(newName)
                }
              }
              .eventHandler {
                case NameChanged(newName) => user.copy(name = newName)
              }
              .commandHandler {
                OneEvent {
                  case DeleteUser => UserDeleted
                }
              }
              .eventHandler {
                case UserDeleted => user.copy(deleted = true)
              }
        }
    }
  }

  case class UserId(value: String) extends AggregateId
  object UserId {
    def generate() = UserId(UUID.randomUUID().toString)
  }

}

sealed trait UserCmd
case class CreateUser(name: String, age: Int) extends UserCmd
case class ChangeName(newName: String) extends UserCmd
case object DeleteUser extends UserCmd

sealed trait UserEvt
case object UserDeleted extends UserEvt
case class UserCreated(name: String, age: Int) extends UserEvt
case class NameChanged(newName: String) extends UserEvt
