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

/**
 * [OpenUaEvent] integrates OPC-UA topic and data value
 * into a single data structure.
 */
case class OpcUaEvent(
  /*
   * TOPIC representation
   */
  address:String,
  browsePath:String = "",
  topicName:String,
  topicType:String,
  systemName:String,
  /*
   * VALUE representation
   */
  sourceTime: Long,
  sourcePicoseconds: Int,
  serverTime: Long,
  serverPicoseconds: Int,
  statusCode: Long,
  dataValue: Any)
