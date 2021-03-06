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

package fusion.discoveryx.server.namespace

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.cluster.ddata.typed.scaladsl.{ DistributedData, Replicator }
import akka.cluster.ddata.{ ORSet, ORSetKey }

/**
 * 需要 Singleton actor Management() 已启动
 */
object NamespaceRef {
  sealed trait Command
  final case class ExistNamespace(namespace: String, replyTo: ActorRef[NamespaceExists]) extends Command
  private final case class InternalNamespaceExists(exists: Boolean, replyTo: ActorRef[NamespaceExists]) extends Command

  final case class NamespaceExists(exists: Boolean)

  val NAME = "Namespace"
  val Key: ORSetKey[String] = ORSetKey(NAME)

  def apply(): Behavior[Command] = DistributedData.withReplicatorMessageAdapter[Command, ORSet[String]] {
    replicatorAdapter =>
      Behaviors.receive[Command] {
        case (ctx, ExistNamespace(namespace, replyTo)) =>
          replicatorAdapter.askGet(Replicator.Get(Key, Replicator.ReadLocal), {
            case chg @ Replicator.GetSuccess(Key) =>
              InternalNamespaceExists(chg.get(Key).contains(namespace), replyTo)
            case _ =>
              ctx.log.warn(s"ORSet key is [$Key], it's not found.")
              InternalNamespaceExists(false, replyTo)
          })
          Behaviors.same

        case (_, InternalNamespaceExists(exists, replyTo)) =>
          replyTo ! NamespaceExists(exists)
          Behaviors.same
      }
  }
}
