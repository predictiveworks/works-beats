package de.kp.works.beats.plc

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

import de.kp.works.beats.BeatsLogging
import de.kp.works.beats.handler.OutputHandler
import org.apache.plc4x.java.PlcDriverManager
import org.apache.plc4x.java.api.PlcConnection
import org.apache.plc4x.java.api.messages.{PlcReadRequest, PlcReadResponse}

import java.util.function.BiConsumer

case class PlcField(fieldAddress:String, fieldName:String)

class PlcConnector(outputHandler:OutputHandler) extends BeatsLogging{

  private var connection:Option[PlcConnection] = None
  private val url = PlcOptions.getPlcUrl

  private val retryWait  = 5000
  private val maxRetries = 10

  def connectWithRetry():Boolean = {

    var success = false

    var retry = true
    var numRetry = 0

    while (retry) {

      try {

        if (connect()) {

          retry   = false
          success = true

        } else throw new Exception()

      } catch {
        case _:Throwable =>
          numRetry += 1
          if (numRetry < maxRetries)
            Thread.sleep(retryWait)

          else {
            retry   = false
            success = false
          }

      }

    }

    success

  }

  def buildReadRequest(fields:Seq[PlcField]):Option[PlcReadRequest] = {

    try {

      if (connection.isEmpty)
        throw new Exception("No connection available")

      val builder = connection.get.readRequestBuilder()
      /*
       * Add monitored fields (items) to the request builder.
       * Each node is specified by its field `name` and `address`.
       */
      fields.foreach(field => builder.addItem(field.fieldName, field.fieldAddress))
      Some(builder.build)

    } catch {
      case t:Throwable =>
        val message = s"Creating a new read request for `$url` failed: ${t.getLocalizedMessage}"
        error(message)

        None
    }
  }

  def connect():Boolean = {

    try {
      /*
       * The connection url determine the protocol to be
       * used, e.g., s7://10.10.64.20
       */
      val conn = new PlcDriverManager().getConnection(url)
      /*
       * Check whether this connection supports the reading
       * of data
       */
      if (!conn.getMetadata.canRead) {
        val message = s"The connection `$url` does not support reading"
        error(message)

        return false
      }

      connection = Some(conn)
      true

    } catch {
      case t:Throwable =>
        val message = s"Connecting to PLC `$url` failed: ${t.getLocalizedMessage}"
        error(message)

        false
    }
  }

  def readAndPublish(readRequest: PlcReadRequest):Unit = {

    try {

      val consumer = new BiConsumer[PlcReadResponse, Throwable] {
        override def accept(response: PlcReadResponse, throwable: Throwable): Unit = {

          if (response == null) {

            val message =
              if (throwable == null) "No message received"
              else
                s"No message received: ${throwable.getLocalizedMessage}"

            error(message)

          } else publish(response)

        }
      }
      /*
       * Leverage an asynchronous read request
       * to retrieve the values from the PLC.
       */
      readRequest
        .execute()
        .whenComplete(consumer)

    } catch {
      case t:Throwable =>
        val message = s"Reading from `$url` failed: ${t.getLocalizedMessage}"
        error(message)
    }

  }

  private def publish(response:PlcReadResponse):Unit = {

    val plcEvent = PlcTransform.transform(response)
    if (plcEvent.isEmpty) {
      val message = s"The PLC response does not contain data."
      error(message)

    } else {
      outputHandler.sendPlcEvent(plcEvent.get)
    }

  }
}
