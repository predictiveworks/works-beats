package de.kp.works.beats.mitre.api
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

/**
 * This request retrieves MITRE data sources
 * and components, either sources or components
 * or both
 */
case class DataReq(
  domain:String,
  /*
   * Supported values are `source`, `component`
   * and `both`.
   */
  dataType:String)
/**
 * This request retrieves MITRE tactic(s) of
 * a certain domain, either by identifier or
 * name, or the list of all registered tactics
 */
case class TacticsReq(
  domain:String,
  id:Option[String] = None,
  shortName:Option[String] = None)