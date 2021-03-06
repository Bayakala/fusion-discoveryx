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

package fusion.discoveryx

import fusion.discoveryx.common.Protocols
import fusion.discoveryx.model.{ HealthyCheckMethod, Instance, InstanceModify, InstanceRegister }
import helloscala.common.exception.HSBadRequestException
import helloscala.common.util.StringUtils

import scala.util.control.NonFatal

object DiscoveryXUtils {
  val VALID_NAMES: Set[Char] = Set('.', '-', '_', ':') ++ ('0' to '9') ++ ('a' to 'z') ++ ('A' to 'Z')

  private def checkString(v: String, field: String): Either[String, String] = {
    if (StringUtils.isEmpty(v)) Left(s"Field `$field` cannot be empty.")
    else if (v.forall(VALID_NAMES)) Right(v)
    else
      Left(
        s"Field `$field` allows only English letters, numbers, dots, underscores, dashes and colons, current value is [$v].")
  }

  @inline def requireString(v: String, field: String): String = checkString(v, field) match {
    case Left(value)  => throw HSBadRequestException(value)
    case Right(value) => value
  }

  @inline final def require(requirement: Boolean, message: => Any): Unit = {
    if (!requirement)
      throw HSBadRequestException(s"requirement failed: $message.")
  }

  private def makeInstanceId(ip: String, port: Int): String = {
    requireString(ip, "ip")
    require(port > 0, s"Field `port` must be greater than 0, current value is [$port].")
    //DigestUtils.sha1Hex(namespace + serviceName + ip + port)
    s"$ip:$port"
  }

  def toInstance(in: InstanceRegister): Either[String, Instance] =
    try {
      requireString(in.namespace, "namespace")
      requireString(in.serviceName, "serviceName")
      val inst = Instance(
        makeInstanceId(in.ip, in.port),
        in.ip,
        in.port,
        in.weight,
        in.health,
        in.enable,
        in.ephemeral,
        in.metadata,
        in.healthyCheckMethod,
        in.healthyCheckInterval,
        in.unhealthyCheckCount,
        in.protocol,
        in.useTls,
        in.httpPath)
      Right(formatInstance(inst))
    } catch {
      case NonFatal(e) => Left(e.getLocalizedMessage)
    }

  private def formatInstance(inst: Instance): Instance =
    inst.copy(
      healthyCheckMethod =
        if (inst.healthyCheckMethod == HealthyCheckMethod.NOT_SET) HealthyCheckMethod.CLIENT_REPORT
        else inst.healthyCheckMethod,
      unhealthyCheckCount = if (inst.unhealthyCheckCount < 1) 1 else inst.unhealthyCheckCount,
      protocol = Protocols.formatProtocol(inst.protocol),
      httpPath =
        if (StringUtils.isEmpty(inst.httpPath)) "/"
        else if (inst.httpPath.head != '/') s"/${inst.httpPath}"
        else inst.httpPath)

  def instanceModify(old: Instance, in: InstanceModify): Instance = {
    old.copy(
      ip = in.ip.getOrElse(old.ip),
      port = in.port.getOrElse(old.port),
      weight = in.weight.getOrElse(old.weight),
      healthy = in.health.getOrElse(old.healthy),
      enabled = in.enable.getOrElse(old.enabled),
      metadata = if (in.replaceMetadata) in.metadata else old.metadata ++ in.metadata,
      healthyCheckMethod =
        if (in.healthyCheckMethod == HealthyCheckMethod.NOT_SET || in.healthyCheckMethod.isUnrecognized)
          old.healthyCheckMethod
        else in.healthyCheckMethod,
      healthyCheckInterval = in.healthyCheckInterval.getOrElse(old.healthyCheckInterval),
      unhealthyCheckCount = in.unhealthyCheckCount.getOrElse(old.unhealthyCheckCount),
      protocol = if (in.protocol.isUnknown || in.protocol.isUnrecognized) old.protocol else in.protocol,
      useTls = in.useTls.getOrElse(old.useTls),
      httpPath = in.httpPath.getOrElse(old.httpPath))
  }

  def userHome: Option[String] = sys.env.get("HOME") orElse sys.props.get("user.home")
}
