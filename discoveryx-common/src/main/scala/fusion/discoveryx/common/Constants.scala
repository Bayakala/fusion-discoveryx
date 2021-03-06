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

package fusion.discoveryx.common

import fusion.discoveryx.model.HealthyCheckProtocol

object Constants {
  val DISCOVERYX = "discoveryx"
  val DEFAULT_GROUP_NAME = "default"
  val ENTITY_ID_SEPARATOR = ':'

  val MANAGEMENT = "management"
  val CONFIG = "config"
  val NAMING = "naming"

  val SESSION_TOKEN_NAME = "discoveryx-session-token"
}

object Headers {
  val NAMESPACE = "x-discoveryx-namespace"
  val SERVICE_NAME = "x-discoveryx-service-name"
  val IP = "x-discoveryx-ip"
  val PORT = "x-discoveryx-port"
  val INSTANCE_ID = "x-discoveryx-instance-id"
}

object Protocols {
  @inline def formatProtocol(protocol: HealthyCheckProtocol): HealthyCheckProtocol =
    if (protocol.isUnknown || protocol.isUnrecognized) HealthyCheckProtocol.HTTP else protocol
}
