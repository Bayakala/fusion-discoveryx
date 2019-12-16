/*
 * Copyright 2019 akka-fusion.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fusion.discoveryx.server.config

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, Terminated }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityContext, EntityTypeKey }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, RetentionCriteria }
import fusion.discoveryx.model.{ ChangeType, ConfigReply }
import fusion.discoveryx.server.protocol._
import helloscala.common.IntStatus
import helloscala.common.exception.HSInternalErrorException

object ConfigEntity {
  trait Command
  trait ReplyCommand extends Command {
    val replyTo: ActorRef[ConfigReply]
    def withReplyTo(replyTo: ActorRef[ConfigReply]): ReplyCommand
  }
  trait Event

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey("configEntity")

  object ConfigKey {
    def unapply(entityId: String): Option[ConfigKey] = entityId.split(' ') match {
      case Array(namespace, dataId) => Some(new ConfigKey(namespace, dataId))
      case _                        => None
    }
  }

  def makeEntityId(key: fusion.discoveryx.server.protocol.ConfigKey) = s"${key.namespace} ${key.dataId}"

  def makeEntityId(namespace: String, dataId: String) = s"$namespace $dataId"

  def init(system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] =
    ClusterSharding(system).init(Entity(TypeKey)(entityContext => apply(entityContext)))

  def apply(entityContext: EntityContext[Command]): Behavior[Command] =
    Behaviors.setup(context =>
      ConfigEntity.ConfigKey.unapply(entityContext.entityId) match {
        case Some(configKey) =>
          new ConfigEntity(
            PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
            configKey.namespace,
            configKey.dataId,
            context).eventSourcedBehavior()
        case _ =>
          throw HSInternalErrorException(
            s"Invalid entityId, need '[namespace] [dataId]'，but is ${entityContext.entityId}")
      })
}

import fusion.discoveryx.server.config.ConfigEntity._
class ConfigEntity private (
    persistenceId: PersistenceId,
    namespace: String,
    dataId: String,
    context: ActorContext[Command]) {
  private implicit val system = context.system
  private var listeners = List.empty[ActorRef[ConfigEntity.Event]]
  context.log.info(s"Entity startup: persistenceId: $persistenceId")

  def eventSourcedBehavior(): EventSourcedBehavior[Command, ChangedConfigEvent, ConfigState] =
    EventSourcedBehavior[Command, ChangedConfigEvent, ConfigState](
      persistenceId,
      ConfigState.defaultInstance,
      commandHandler,
      eventHandler)
      .receiveSignal {
        case (_, Terminated(ref)) =>
          listeners = listeners.filterNot(_ == ref)
      }
      .withTagger(_ => Set(ConfigEntity.TypeKey.name, namespace))
      .withRetention(RetentionCriteria.snapshotEvery(100, 2).withDeleteEventsOnSnapshot)
      .snapshotWhen {
        case (_, ChangedConfigEvent(_, ChangeType.CHANGE_REMOVE), _) => true
        case _                                                       => false
      }

  def commandHandler(state: ConfigState, cmd: Command): Effect[ChangedConfigEvent, ConfigState] = cmd match {
    case GetConfig(_, replyTo) =>
      Effect.reply(replyTo)(state.configItem match {
        case Some(item) => ConfigReply(IntStatus.OK, data = ConfigReply.Data.Config(item))
        case _          => ConfigReply(IntStatus.NOT_FOUND)
      })

    case PublishConfig(in, replyTo) =>
      context.log.debug(s"PublishConfig($in, $replyTo)")

      if (state.configItem.contains(in)) {
        Effect.reply(replyTo)(ConfigReply(IntStatus.OK, "Not need update."))
      } else {
        val event =
          ChangedConfigEvent(Some(in), if (state.configItem.isEmpty) ChangeType.CHANGE_ADD else ChangeType.CHANGE_SAVE)
        Effect.persist(event).thenReply(replyTo) {
          case ConfigState(Some(item)) => ConfigReply(IntStatus.OK, data = ConfigReply.Data.Config(item))
          case _                       => ConfigReply(IntStatus.BAD_REQUEST)
        }
      }

    case RemoveConfig(_, replyTo) =>
      Effect
        .persist[ChangedConfigEvent, ConfigState](ChangedConfigEvent(`type` = ChangeType.CHANGE_REMOVE))
        .thenRun {
          case state if state.configItem.isEmpty => replyTo ! ConfigReply(IntStatus.OK)
          case _                                 => replyTo ! ConfigReply(IntStatus.INTERNAL_ERROR)
        }
        .thenStop()

    case RegisterChangeListener(replyTo, _) =>
      listeners ::= replyTo
      context.watch(replyTo)
      Effect.none
  }

  def eventHandler(state: ConfigState, evt: ChangedConfigEvent): ConfigState = {
    context.log.debug(s"eventHandler($state, $evt)")
    listeners.foreach { ref =>
      ref ! evt
      if (evt.`type` == ChangeType.CHANGE_REMOVE) {
        ref ! RemovedConfigEvent()
      }
    }
    ConfigState(configItem = evt.config)
  }
}