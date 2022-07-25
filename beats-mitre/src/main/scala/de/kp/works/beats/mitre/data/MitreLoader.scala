package de.kp.works.beats.mitre.data

/**
 * Copyright (c) 2019 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * @author Stefan Krusche, Dr. Krusche & Partner PartG
 *
 */

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.RoundRobinPool
import akka.stream.ActorMaterializer
import akka.util.Timeout
import de.kp.works.beats.BeatsLogging
import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.mitre.{MitreActor, MitreClient, MitreDomains, MitreTransform}
import de.kp.works.beats.mitre.MitreDomains._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt

case class LoadReq()
case class LoadDomainReq(domain:MitreDomain)
/**
 * The [LoadActor] is the executor or worker
 * actor, which loads a domain specific knowledge
 * base, and publishes the respective delta objects
 * to multiple channels via the output handler.
 *
 * The current implementation supports FIWARE, MQTT
 * and SSE. This approach feeds multiple network
 * infrastructures with newest ATT&CK.
 */
class LoadActor(outputHandler:OutputHandler) extends Actor with BeatsLogging {

  override def receive: Receive = {
    case req:LoadDomainReq => try {

      var message = s"Loading MITRE ${req.domain.toString} started"
      info(message)
      /*
       * Retrieve all MITRE domain specific objects
       * from the local file system. Do this, also
       * the memory store is updated.
       */
      val domain = req.domain
      val objects = MitreClient.getObjects(domain = domain, load = true)
      /*
       * Determine those domain objects that either
       * have been created or updated since the last
       * scheduled retrieval.
       *
       * Note, a delta object contains an additional
       * field `action` with values `create` or `update`
       */
      val deltaObjects = MitreLogs.registerAndChanges(domain, objects)
      /*
       * Transform deltaObjects into an NGSI compliant
       * MITRE event and send to the output handler for
       * publication.
       */
      val mitreEvent = MitreTransform.transform(deltaObjects)
      outputHandler.sendMitreEvent(mitreEvent)

      message = s"Loading MITRE ${req.domain.toString} finished"
      info(message)

    } catch {
      case t:Throwable =>
        val message = s"Loading MITRE ${req.domain.toString} failed: ${t.getLocalizedMessage}"
        error(message)
    }

  }
}

/**
 * This actor is responsible for distributing
 * load requests to the respective workers
 */
class MitreRouter(outputHandler:OutputHandler) extends MitreActor {

  private val loadWorker =
    system
      .actorOf(RoundRobinPool(instances)
        .withResizer(resizer)
        .props(Props(new LoadActor(outputHandler))), "LoadWorker")

  override def receive: Receive = {

    case LoadReq => try {
      /*
       * Distribute load request to domain
       * specific workers
       */
      loadWorker ! LoadDomainReq(CAPEC)
      loadWorker ! LoadDomainReq(ENTERPRISE)

      loadWorker ! LoadDomainReq(ICS)
      loadWorker ! LoadDomainReq(MOBILE)

    } catch {
      case t:Throwable =>
        val message = s"Loading MITRE domain knowledge bases failed: ${t.getLocalizedMessage}"
        error(message)
    }
  }

}
/**
 * The [MitreLoad] responds to scheduled `start`
 * requests, and is responsible for distributing
 * knowledge base loading requests to the
 */
class MitreLoader(outputHandler:OutputHandler) {
  /**
   * Akka 2.6 provides a default materializer out of the box, i.e., for Scala
   * an implicit materializer is provided if there is an implicit ActorSystem
   * available. This avoids leaking materializers and simplifies most stream
   * use cases somewhat.
   */
  implicit val system: ActorSystem = ActorSystem("mitre-load")
  implicit lazy val context: ExecutionContextExecutor = system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  /**
   * Common timeout for all Akka connection
   */
  implicit val timeout: Timeout = Timeout(5.seconds)

  private val router = system.actorOf(Props(new MitreRouter(outputHandler)), "mitre_router")

  def start():Unit = {
    router ! LoadReq
  }

}
