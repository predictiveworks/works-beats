package de.kp.works.beats.mitre.osquery
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

import com.google.gson.JsonObject
import de.kp.works.beats.BeatsLogging
import de.kp.works.beats.mitre.MitreDomains
import de.kp.works.beats.mitre.osquery.FileActions.FileAction

import scala.collection.JavaConversions.asScalaSet
import scala.collection.mutable

/**
 * Osquery defined file actions
 */
object FileActions extends Enumeration {
  type FileAction = Value
  val ACCESSED:FileAction            = Value(0, "ACCESSED")
  val ATTRIBUTES_MODIFIED:FileAction = Value(1, "ATTRIBUTES_MODIFIED")
  val UPDATED:FileAction             = Value(2, "UPDATED")
  val CREATED:FileAction             = Value(3, "CREATED")
  val DELETED:FileAction             = Value(4, "DELETED")
  val MOVED_FROM:FileAction          = Value(5, "MOVED_FROM")
  val MOVED_TO:FileAction            = Value(6, "MOVED_TO")
  val OPENED:FileAction              = Value(7, "OPENED")
}

object MitreBridge {
  /**
   * This method maps a certain Osquery defined
   * file action onto the respective MITRE data
   * components.
   *
   * The supported domain is ENTERPRISE.
   */
  def fromFileAction(fileAction:FileAction):(String,String) = {

    fileAction match {

      case FileActions.ACCESSED | FileActions.OPENED =>

        val mitreId = "x-mitre-data-component--235b7491-2d2b-4617-9a52-3c0783680f71"
        val mitreName = "File Access"

        (mitreId, mitreName)

      case FileActions.ATTRIBUTES_MODIFIED =>

        val mitreId = "x-mitre-data-component--639e87f3-acb6-448a-9645-258f20da4bc5"
        val mitreName = "File Metadata"

        (mitreId, mitreName)

      case FileActions.CREATED || FileActions.MOVED_FROM =>

        val mitreId = "x-mitre-data-component--2b3bfe19-d59a-460d-93bb-2f546adc2d2c"
        val mitreName = "File Creation"

        (mitreId, mitreName)

      case FileActions.DELETED || FileActions.MOVED_TO =>

        val mitreId = "x-mitre-data-component--e905dad2-00d6-477c-97e8-800427abd0e8"
        val mitreName = "File Deletion"

        (mitreId, mitreName)

      case FileActions.UPDATED =>

        val mitreId = "x-mitre-data-component--84572de3-9583-4c73-aabd-06ea88123dd8"
        val mitreName = "File Modification"

        (mitreId, mitreName)

    }

  }
}
/**
 * The [FileEvents] object represents the Osquery
 * `file_events` table and is responsible for turning
 * the table rows into a graph representation.
 */
object FileEvents extends EventParser {
  /**
   * This public method transforms a change event
   * of the `file_events` table into a sub-graph
   * that can be added to a cyber endpoint master
   * graph.
   */
  override def parse(event:JsonObject):Option[GGraph] = {

    info("Parsing file event")
    try {

      val edges = mutable.ArrayBuffer.empty[Edge]
      val nodes = mutable.ArrayBuffer.empty[Node]

      val hostname = getHostname(event)
      if (hostname.isEmpty)
        throw new Exception(s"The file event does not contain a host identifier.")

      if (!event.has("columns"))
        throw new Exception(s"The file event does not contain columns.")

      val columns = event.get("columns").getAsJsonObject
      /*
       * STEP #1: Build asset
       */
      val asset = new GAsset()
      asset.setId(hostname.get)
      asset.setHostname(hostname.get)

      /*
       * STEP #2: Build file
       */
      val file = new GFile()
      file.setNodeType(NodeTypes.FULL)

      file.setAssetId(asset.id.get)
      file.setId(hostname.get)

      val targetPath = columns.remove("target_path").getAsString
      file.setFilePath(targetPath)

      val time = columns.remove("time").getAsLong * 1000
      file.setLastSeen(time)

      val action = columns.remove("action").getAsString
      val fileAction = FileActions.withName(action)

      fileAction match {
        case FileActions.CREATED || FileActions.MOVED_FROM =>
          file.setCreated(time)

        case FileActions.DELETED || FileActions.MOVED_TO =>
          file.setDeleted(time)

        case _ => /* Do nothing */
      }

      file.setColumns(columns)

      /*
       * Assign MITRE domain and data component
       * to the file name. This enables to link
       * Osquery to MITRE ATT&CK.
       *
       * NOTE: The current implementation expects
       * that the Osquery domain is deployed to an
       * enterprise endpoint
       */
      file.setMitreDomain(MitreDomains.ENTERPRISE)

      val (mitreId, mitreName) = MitreBridge.fromFileAction(fileAction)

      file.setMitreId(mitreId)
      file.setMitreName(mitreName)
      /*
       * STEP #3: Build edge
       */
      val assetFile = new GEdge()
      assetFile.setSrc(asset.id)
      assetFile.setDst(file.id)
      assetFile.setName(Some("file-on-asset"))

      edges += assetFile

      nodes += asset
      nodes += file

      val graph = new GGraph(nodes, edges)
      Some(graph)

    } catch {
      case t:Throwable =>
        error(s"Parsing file event failed: ${t.getLocalizedMessage}")
        None
    }
  }

}
