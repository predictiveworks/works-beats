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
import scala.collection.mutable

/**
 * The [ProcessOpenFiles] object represents the
 * Osquery `process_open_files` table and is
 * responsible for turning the table rows into
 * a graph representation.
 */
object ProcessOpenFiles extends EventParser {
  /*
   * The `process_open_files` table assigns
   * file descriptors (fd) to a certain process.
   *
   * The table specifies relationships between
   * processes and tables.
   *
   * The [GFile] & [GProcess] nodes are placeholders
   * for the respective files and processes.
   *
   * Therefore, this method does not assign MITRE
   * domain specific data components to the nodes
   */
  override protected def parse(event: JsonObject): Option[GGraph] = {

    info("Parsing process open file event")
    try {

      val edges = mutable.ArrayBuffer.empty[Edge]
      val nodes = mutable.ArrayBuffer.empty[Node]

      val hostname = getHostname(event)
      if (hostname.isEmpty)
        throw new Exception(s"The process open file event does not contain a host identifier.")

      if (!event.has("columns"))
        throw new Exception(s"The process open file event does not contain columns.")

      val columns = event.get("columns").getAsJsonObject
      /*
       * STEP #1: Build asset
       */
      val asset = new GAsset()
      asset.setId(hostname.get)
      asset.setHostname(hostname.get)
      /*
       * STEP #2: Build process
       */
      val process = new GProcess()
      process.setNodeType(NodeTypes.LIGHT)

      process.setAssetId(asset.id.get)

      val unixTime = getUnixTime(event)
      process.setLastSeen(unixTime)

      val pid = getBigInt(columns, "pid", remove = true)
      if (pid.isEmpty)
        throw new Exception(s"The process open file event does not contain `pid`.")

      process.setId(pid.get)
      /*
       * STEP #3: Build file
       */
      val file = new GFile()
      file.setNodeType(NodeTypes.LIGHT)

      process.setAssetId(asset.id.get)
      file.setId(hostname.get)

      val path = columns.remove("path").getAsString
      file.setFilePath(path)

      file.setLastSeen(unixTime)

      val action = event.get("action").getAsString
      val osqueryAction = OsqueryActions.withName(action)

      osqueryAction match {
        case OsqueryActions.ADDED =>
          file.setCreated(unixTime)

          /* process(light)-[created-file]->file(light) */
          val processFile = new GEdge()
          processFile.setSrc(process.id)
          processFile.setDst(file.id)
          processFile.setName(Some("created-file"))

          edges += processFile

        case OsqueryActions.REMOVED =>
          file.setDeleted(unixTime)

          /* process(light)-[deleted-file]->file(light) */
          val edge = new GEdge()
          edge.setSrc(process.id)
          edge.setDst(file.id)
          edge.setName(Some("deleted-file"))

          edges += edge

        case _ => /* Do nothing */

      }
      /*
       * The current implementation does not
       */

      /*
       * STEP #3: Build other edges
       */
      val assetFile = new GEdge()
      assetFile.setSrc(asset.id)
      assetFile.setDst(file.id)
      assetFile.setName(Some("file-on-asset"))

      /* asset-[file-on-asset]->file(light) --- file(full) */
      edges += assetFile

      val assetProcess = new GEdge()
      assetProcess.setSrc(asset.id)
      assetProcess.setDst(process.id)
      assetProcess.setName(Some("process-on-asset"))

      /* asset-[process-on-asset]->process(light) --- process(full) */
      edges += assetProcess

      nodes += asset
      nodes += file
      nodes += process

      val graph = new GGraph(nodes, edges)
      Some(graph)

    } catch {
      case t:Throwable =>
        error(s"Parsing process open file event failed: ${t.getLocalizedMessage}")
        None
    }
  }

}
