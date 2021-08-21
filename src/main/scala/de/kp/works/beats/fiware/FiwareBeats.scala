package de.kp.works.beats.fiware
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

import scopt.OptionParser

/**
 * The [FiwareBeat] is an Akka based Http(s) services that
 * sends subscriptions to a Fiware Context Broker and receives
 * NGSI notifications. These notifications are sent to the SSE
 * output queue.
 *
 * An SSE client like [Works. Stream] listens to the published
 * events and initiates subsequent data processing.
 */
object FiwareBeats {

  // TODO Subscription registration

  private case class CliConfig(
    /*
     * The command line interface supports the provisioning
     * of a typesafe config compliant configuration file
     */
    conf:String = null
  )

  def main(args:Array[String]):Unit = {

    /* Command line argument parser */
    val parser = new OptionParser[CliConfig]("FiwareBeat") {

      head("Fiware Beat: Publish Fiware notifications as SSE.")

      opt[String]("c")
        .text("The path to the configuration file.")
        .action((x, c) => c.copy(conf = x))

    }

    /* Parse the argument and then run */
    parser.parse(args, CliConfig()).map{c =>

      try {

        val service = new FiwareService()

        if (c.conf == null) {

          println("[INFO] ------------------------------------------------")
          println("[INFO] Launch Fiware Beat with internal configuration.")
          println("[INFO] ------------------------------------------------")

          service.start()

        } else {

          println("[INFO] ------------------------------------------------")
          println("[INFO] Launch Fiware Beat with external configuration.")
          println("[INFO] ------------------------------------------------")

          val source = scala.io.Source.fromFile(c.conf)
          val config = source.getLines.mkString("\n")

          source.close
          service.start(Option(config))

        }

        println("[INFO] ------------------------------------------------")
        println("[INFO] Fiware Beat service started.")
        println("[INFO] ------------------------------------------------")

      } catch {
        case t:Throwable =>
          t.printStackTrace()
          println("[ERROR] ------------------------------------------------")
          println("[ERROR] Fiware Beat cannot be started: " + t.getMessage)
          println("[ERROR] ------------------------------------------------")
      }
    }.getOrElse {
      /*
       * Sleep for 10 seconds so that one may see error messages
       * in Yarn clusters where logs are not stored.
       */
      Thread.sleep(10000)
      sys.exit(1)
    }

  }

}
