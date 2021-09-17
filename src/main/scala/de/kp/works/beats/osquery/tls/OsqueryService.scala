package de.kp.works.beats.osquery.tls
/*
 * Copyright (c) 2020 Dr. Krusche & Partner PartG. All rights reserved.
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

import akka.NotUsed
import akka.actor.Props
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import com.typesafe.config.Config
import de.kp.works.beats.osquery.tls.actor._
import de.kp.works.beats.{BeatsConf, BeatsService}

class OsqueryService extends BeatsService(BeatsConf.OSQUERY_CONF) {

  import OsqueryRoutes._

  override def onStart(queue: SourceQueueWithComplete[String], opcUaCfg:Config):Unit = {
    throw new Exception("not implemented yet")
  }

  override def buildRoute(queue: SourceQueueWithComplete[String], source: Source[ServerSentEvent, NotUsed]): Route = {
    /*
     * NODE MANAGEMENT
     *
     * The [OsqueryBeat] can also be used as a gate
     * to a fleet of machines that are equipped with
     * Osquery agents.
     */
    lazy val configActor = system
      .actorOf(Props(new ConfigActor()), CONFIG_ACTOR)

    lazy val enrollActor = system
      .actorOf(Props(new EnrollActor()), ENROLL_ACTOR)
    /*
     * INFORMATION MANAGEMENT
     */
    lazy val logActor = system
      .actorOf(Props(new LogActor(queue)), LOG_ACTOR)

    lazy val readActor = system
      .actorOf(Props(new ReadActor()), READ_ACTOR)

    lazy val writeActor = system
      .actorOf(Props(new WriteActor(queue)), WRITE_ACTOR)

    val actors = Map(
      CONFIG_ACTOR -> configActor,
      ENROLL_ACTOR -> enrollActor,
      LOG_ACTOR    -> logActor,
      READ_ACTOR   -> readActor,
      WRITE_ACTOR  -> writeActor
    )

    val routes = new OsqueryRoutes(actors, source)

    routes.event ~
    routes.config ~
    routes.enroll ~
    routes.log ~
    routes.read ~
    routes.write

  }

}
