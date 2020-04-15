package encry.analyzer.event

import ExplorerEventMessage.ExplorerEventProtoMessage
import ExplorerEventMessage.ExplorerEventProtoMessage.EventMessage.{
  ExplorerCoreLogEventMessage => ECLEM,
  ExplorerObserverLogEventMessage => EOLEM,
  ForkOccurredEventMessage => FOEM,
  NewBlockReceivedEventMessage => NBREV,
  NewNodeEventMessage => NNEM,
  RollbackOccurredEventMessage => ROEM,
  UnavailableNodeEventMessage => UNVM
}
import ExplorerEventMessage.ExplorerEventProtoMessage.{
  EventMessage,
  ExplorerCoreLogEventMessage,
  ExplorerObserverLogEventMessage,
  ForkOccurredEventMessage,
  NewBlockReceivedEventMessage,
  NewNodeEventMessage,
  RollbackOccurredEventMessage,
  UnavailableNodeEventMessage
}
import cats.effect.Sync
import cats.syntax.applicative._
import cats.syntax.either._
import fs2.kafka._

package object event {

  sealed trait ExplorerEvent {
    def kafkaKey: String
    def kafkaTopic: String
  }
  final case class ExplorerObserverLogEvent(msg: String) extends ExplorerEvent {
    override def kafkaKey: String = "ExplorerObserverLogEvent"

    override def kafkaTopic: String = "ObserverLogEvent"
  }
  final case class ExplorerCoreLogEvent(msg: String) extends ExplorerEvent {
    override def kafkaKey: String = "ExplorerCoreLogEvent"

    override def kafkaTopic: String = "CoreLogEvent"
  }
  final case class UnavailableNode(url: String) extends ExplorerEvent {
    override def kafkaKey: String = "UnavailableNode"

    override def kafkaTopic: String = "ChainEvent"
  }
  final case class NewBlockReceived(id: String) extends ExplorerEvent {
    override def kafkaKey: String = "NewBlockReceived"

    override def kafkaTopic: String = "ChainEvent"
  }
  final case class RollbackOccurred(branchPoint: String, height: Int) extends ExplorerEvent {
    override def kafkaKey: String = "RollbackOccurred"

    override def kafkaTopic: String = "ChainEvent"
  }
  final case class NewNode(url: String) extends ExplorerEvent {
    override def kafkaKey: String = "NewNode"

    override def kafkaTopic: String = "ChainEvent"
  }
  final case class ForkOccurred(id: String, height: Int) extends ExplorerEvent {
    override def kafkaKey: String = "ForkOccurred"

    override def kafkaTopic: String = "ChainEvent"
  }

  object explorerEventKafkaSerializer {
    def createDeserializer[F[_]: Sync]: Deserializer[F, Option[ExplorerEvent]] =
      Deserializer.instance[F, Option[ExplorerEvent]]((_, _, v) => ExplorerEventProtoSerializer.fromProto(v).pure[F])

    def createSerializer[F[_]: Sync]: Serializer[F, ExplorerEvent] =
      Serializer.instance[F, ExplorerEvent]((_, _, v) => ExplorerEventProtoSerializer.toProto(v).toByteArray.pure[F])
  }

  object ExplorerEventProtoSerializer {
    def toProto(event: ExplorerEvent): ExplorerEventProtoMessage =
      ExplorerEventProtoMessage().withEventMessage {
        event match {
          case msg @ ExplorerObserverLogEvent(_) => explorerObserverLogEventExplorerEvent.toProto(msg)
          case msg @ ExplorerCoreLogEvent(_)     => explorerCoreLogEventExplorerEvent.toProto(msg)
          case msg @ UnavailableNode(_)          => unavailableNodeExplorerEvent.toProto(msg)
          case msg @ NewBlockReceived(_)         => newBlockReceivedExplorerEvent.toProto(msg)
          case msg @ RollbackOccurred(_, _)      => rollbackOccurredExplorerEvent.toProto(msg)
          case msg @ NewNode(_)                  => newNodeExplorerEvent.toProto(msg)
          case msg @ ForkOccurred(_, _)          => forkOccurredExplorerEvent.toProto(msg)
        }
      }

    def fromProto(msg: Array[Byte]): Option[ExplorerEvent] =
      Either.catchNonFatal {
        val eventMsg = ExplorerEventProtoMessage.parseFrom(msg)
        def processEventMsg: (EventMessage => Option[ExplorerEvent], EventMessage) => Option[ExplorerEvent] =
          (serializer: EventMessage => Option[ExplorerEvent], msg: EventMessage) => {
            val serialized = serializer(msg)
            require(serialized.isDefined, "Incorrect event message")
            serialized
          }
        eventMsg.eventMessage match {
          case EventMessage.Empty => throw new Exception("Empty event message")
          case msg @ UNVM(_)      => processEventMsg(unavailableNodeExplorerEvent.fromProto, msg)
          case msg @ NBREV(_)     => processEventMsg(newBlockReceivedExplorerEvent.fromProto, msg)
          case msg @ ROEM(_)      => processEventMsg(rollbackOccurredExplorerEvent.fromProto, msg)
          case msg @ NNEM(_)      => processEventMsg(newNodeExplorerEvent.fromProto, msg)
          case msg @ FOEM(_)      => processEventMsg(forkOccurredExplorerEvent.fromProto, msg)
          case msg @ EOLEM(_)     => processEventMsg(explorerObserverLogEventExplorerEvent.fromProto, msg)
          case msg @ ECLEM(_)     => processEventMsg(explorerCoreLogEventExplorerEvent.fromProto, msg)
        }
      }.toOption.flatten
  }

  sealed trait EventSerializer[T] {
    def toProto(t: T): EventMessage
    def fromProto(message: EventMessage): Option[T]
  }

  object explorerObserverLogEventExplorerEvent extends EventSerializer[ExplorerObserverLogEvent] {
    override def toProto(t: ExplorerObserverLogEvent): EventMessage =
      EOLEM(ExplorerObserverLogEventMessage().withLog(t.msg))

    override def fromProto(message: EventMessage): Option[ExplorerObserverLogEvent] =
      message.explorerObserverLogEventMessage.map(msg => ExplorerObserverLogEvent(msg.log))
  }

  object explorerCoreLogEventExplorerEvent extends EventSerializer[ExplorerCoreLogEvent] {
    override def toProto(t: ExplorerCoreLogEvent): EventMessage =
      ECLEM(ExplorerCoreLogEventMessage().withLog(t.msg))

    override def fromProto(message: EventMessage): Option[ExplorerCoreLogEvent] =
      message.explorerCoreLogEventMessage.map(msg => ExplorerCoreLogEvent(msg.log))
  }

  object unavailableNodeExplorerEvent extends EventSerializer[UnavailableNode] {
    override def toProto(t: UnavailableNode): EventMessage =
      UNVM(UnavailableNodeEventMessage().withUrl(t.url))

    override def fromProto(message: EventMessage): Option[UnavailableNode] =
      message.unavailableNodeEventMessage.map(msg => UnavailableNode(msg.url))
  }

  object newBlockReceivedExplorerEvent extends EventSerializer[NewBlockReceived] {
    override def toProto(t: NewBlockReceived): EventMessage =
      NBREV(NewBlockReceivedEventMessage().withId(t.id))

    override def fromProto(message: EventMessage): Option[NewBlockReceived] =
      message.newBlockReceivedEventMessage.map(msg => NewBlockReceived(msg.id))
  }

  object rollbackOccurredExplorerEvent extends EventSerializer[RollbackOccurred] {
    override def toProto(t: RollbackOccurred): EventMessage =
      ROEM(RollbackOccurredEventMessage().withBranchPoint(t.branchPoint).withHeight(t.height))

    override def fromProto(message: EventMessage): Option[RollbackOccurred] =
      message.rollbackOccurredEventMessage.map(msg => RollbackOccurred(msg.branchPoint, msg.height))
  }

  object newNodeExplorerEvent extends EventSerializer[NewNode] {
    override def toProto(t: NewNode): EventMessage =
      NNEM(NewNodeEventMessage().withUrl(t.url))

    override def fromProto(message: EventMessage): Option[NewNode] =
      message.newNodeEventMessage.map(msg => NewNode(msg.url))
  }

  object forkOccurredExplorerEvent extends EventSerializer[ForkOccurred] {
    override def toProto(t: ForkOccurred): EventMessage =
      FOEM(ForkOccurredEventMessage().withHeight(t.height).withId(t.id))

    override def fromProto(message: EventMessage): Option[ForkOccurred] =
      message.forkOccurredEventMessage.map(msg => ForkOccurred(msg.id, msg.height))
  }

}
