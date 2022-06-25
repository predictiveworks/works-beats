package de.kp.works.beats.fiware
/**
 * Copyright (c) 2020 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
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

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import com.google.gson.JsonObject

/**
 * The [FiwareSubscriber] is used to connect to the
 * Fiware Context Broker and subscribe to certain
 * events.
 *
 * The Fiware implementation requires a (public)
 * endpoint, the Context Broker can send its
 * notifications to.
 */
object FiwareSubscriber extends Fiware {
  /*
   * {
   *		"description": "A subscription to get info about Room1",
   *  	"subject": {
   *    "entities": [
   *      {
   *        "id": "Room1",
   *        "type": "Room"
   *      }
   *    ],
   *    "condition": {
   *      "attrs": [
   *        "pressure"
   *      ]
   *    }
   *  },
   *  "notification": {
   *    "http": {
   *      "url": "http://localhost:9080/notifications"
   *    },
   *    "attrs": [
   *      "temperature"
   *    ]
   *  },
   *  "expires": "2040-01-01T14:00:00.00Z",
   *  "throttling": 5
   * }
   *
   */

  /**
   * This method creates a single Akka based Http(s) request
   * to send the provided subscription to the Context Broker.
   *
   * Each subscription must contain the endpoint of the Fiware
   * notification server.
   */
  def subscribe(subscription: JsonObject): Option[String] = {

    try {

      val headers = Map.empty[String, String]
      val endpoint = s"$getBrokerUrl/v2/subscriptions"

      val response = postHttp(endpoint, headers, subscription)
      Some(getSubscriptionId(response))

    } catch {
      case _: Throwable => None
    }

  }

  /**
   * This method validates that the response code of the Orion Context
   * Broker response is 201 (Created) and then the subscription ID is
   * extracted from the provided 'Location' header
   */
  def getSubscriptionId(response: HttpResponse): String = {

    var sid: Option[String] = None

    val statusCode = response._1
    if (statusCode == StatusCodes.Created) {
      /*
       * The Fiware Context Broker responds with a 201 Created response
       * code; the subscription identifier is provided through the
       * Location Header
       */
      val headers = response._2

      headers.foreach(header => {
        /* Akka HTTP requires header in lower cases */
        if (header.is("location")) {
          /*
           * Location: /v2/subscriptions/57458eb60962ef754e7c0998
           *
           * Subscription ID: a 24 digit hexadecimal number used
           * for updating and cancelling the subscription. It is
           * used to identify the notifications that refer to this
           * subscription
           */
          sid = Some(header.value().replace("/v2/subscriptions/", ""))
        }

      })
    }

    if (sid.isEmpty)
      throw new Exception("Fiware Context Broker did not respond with a subscription response.")

    sid.get

  }

}
