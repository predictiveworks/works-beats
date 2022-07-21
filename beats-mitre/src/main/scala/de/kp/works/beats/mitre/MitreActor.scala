package de.kp.works.beats.mitre

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
import akka.actor.SupervisorStrategy._
import akka.actor.{Actor, ActorSystem, OneForOneStrategy}
import akka.routing.DefaultResizer
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import de.kp.works.beats.{BeatsConf, BeatsLogging}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

abstract class MitreActor extends Actor with BeatsLogging {
  /**
   * The actor system is implicitly accompanied
   * by a materializer
   */
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val config = BeatsConf.getBeatCfg(BeatsConf.MITRE_CONF)
  val actorCfg: Config = config.getConfig("actor")

  implicit val timeout: Timeout = {
    val value = actorCfg.getInt("timeout")
    Timeout(value.seconds)
  }
  /**
   * Parameters to control the handling of failed child actors:
   * it is the number of retries within a certain time window.
   *
   * The supervisor strategy restarts a child up to 10 restarts
   * per minute. The child actor is stopped if the restart count
   * exceeds maxNrOfRetries during the withinTimeRange duration.
   */
  protected val maxRetries: Int = actorCfg.getInt("maxRetries")
  protected val timeRange: FiniteDuration = {
    val value = actorCfg.getInt("timeRange")
    value.minute
  }
  /**
   * Child actors are defined leveraging a RoundRobin pool with a
   * dynamic resizer. The boundaries of the resizer are defined
   * below
   */
  protected val lower: Int = actorCfg.getInt("lower")
  protected val upper: Int = actorCfg.getInt("upper")

  /* We define a common resizer for all children pools */
  protected val resizer = new DefaultResizer(lower, upper)

  /**
   * The number of instances for the RoundRobin pool
   */
  protected val instances: Int = actorCfg.getInt("instances")
  /**
   * Each actor is the supervisor of its children, and as such each
   * actor defines fault handling supervisor strategy. This strategy
   * cannot be changed afterwards as it is an integral part of the
   * actor system’s structure.
   */
  override val supervisorStrategy: OneForOneStrategy =
  /*
   * The implemented supervisor strategy treats each child separately
   * (one-for-one). Alternatives are, e.g. all-for-one.
   *
   */
    OneForOneStrategy(maxNrOfRetries = maxRetries, withinTimeRange = timeRange) {
      case _: ArithmeticException      => Resume
      case _: NullPointerException     => Restart
      case _: IllegalArgumentException => Stop
      case _: Exception                => Escalate
    }

}
