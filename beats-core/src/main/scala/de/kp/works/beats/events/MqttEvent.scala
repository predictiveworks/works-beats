package de.kp.works.beats.events

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

class MqttEvent (
  /* The timestamp in milli seconds
   * the message arrived
   */
  val timestamp: Long,
  /* The timestamp in seconds the
   * message arrived
   */
  val seconds: Long,
  /* The MQTT topic of the message
   */
  val topic: String,
  /* The quality of service of the message
   */
  val qos: Int,
  /* Indicates whether or not this message might be a
   * duplicate of one which has already been received.
   */
  val duplicate: Boolean,
  /* Indicates whether or not this message should be/was
   * retained by the server.
   */
  val retained: Boolean,
  /* The [String] representation of the
   * payload
   */
  val json: String) extends Serializable {

  def copy():MqttEvent = {

    new MqttEvent(
      timestamp,
      seconds,
      topic,
      qos,
      duplicate,
      retained,
      json)

  }
}