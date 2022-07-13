package de.kp.works.beats.opcua

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

import de.kp.works.beats.opcua.OpcUaTopicTypes.OpcUaTopicType

object OpcUaTopicTypes extends Enumeration {

  type OpcUaTopicType = Value

  val NodeId: OpcUaTopicType = Value(1, "NodeId")
  val Path: OpcUaTopicType   = Value(2, "Path")

}

case class OpcUaTopic(
   address:String,
   browsePath:String = "",
   topicName:String,
   topicType: OpcUaTopicType,
   systemName:String) {

  def isValid:Boolean = {
    true
  }
}

case class OpcUaTopicValue(
  sourceTime: Long,
  sourcePicoseconds: Int = 0,
  serverTime: Long,
  serverPicoseconds: Int = 0,
  value: Any,
  statusCode: Long)
