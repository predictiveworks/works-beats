package de.kp.works.beats.tls.redis

/*
 * Copyright (c) 2021 Dr. Krusche & Partner PartG. All rights reserved.
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

import com.google.gson._
import de.kp.works.beats.BeatsConf
import org.apache.spark.sql.{DataFrame, SparkSession}
/**
 * The Osquery TLS Beat is backed by a REDIS instance that contains
 * node configurations, query and task specifications and more.
 *
 * This REDIS instance must be shared and managed by (e.g.) an Osquery
 * device management application. This application, however, is out of
 * scope of this beat.
 */
object RedisDB {

  private var instance:Option[RedisDB] = None
  private var tables:Option[Boolean] = None

  def getInstance:RedisDB = {

    val session = buildSession
    getInstance(session)

  }

  def getInstance(session:SparkSession):RedisDB = {

    if (session == null) {
      val now = new java.util.Date().toString
      throw new Exception(s"[ERROR] $now - No Spark session available.")
    }
    /*
     * Build Spark Session to enable SQL support
     * when accessing REDIS caches as tables
     */
    if (instance.isEmpty)
      instance = Some(new RedisDB(session))
    /*
     * Build all REDIS tables needed to manage
     * configurations, nodes, queries, and tasks
     */
    if (tables.isEmpty) {

      instance.get.buildTables()
      tables = Some(true)

    }

    instance.get

  }

  private def buildSession:SparkSession = {
    /*
     * STEP #1: Retrieve Spark-Redis configuration
     */
    val osqueryCfg = BeatsConf.getBeatCfg(BeatsConf.OSQUERY_CONF)
    val sparkCfg = osqueryCfg.getConfig("spark")

    val name = sparkCfg.getString("name")
    val master = sparkCfg.getString("master")

    val host = sparkCfg.getString("host")
    val port = sparkCfg.getInt("port")

    val password = sparkCfg.getString("password")
    /*
     * Step #2: Initialize Spark Session with REDIS
     * access
     */
    if (password.isEmpty) {

      SparkSession
        .builder
        .appName(name)
        .master(master)
        .config("spark.redis.host",host)
        .config("spark.redis.port",port)
        .getOrCreate

    } else {

      SparkSession
        .builder
        .appName(name)
        .master(master)
        .config("spark.redis.host",host)
        .config("spark.redis.port",port)
        .config("spark.redis.auth", password)
        .getOrCreate

    }

  }
}

class RedisDB(session:SparkSession) {
  /*
   * SQL CREATE TABLE STATEMENTS
   */
  private val USING = "org.apache.spark.sql.redis"
  private val CREATE_TABLE = "create table if not exists %1(%2) using %3 options (table '%1')"

  /** NODE **/

  def createNode(values:Seq[String]):Unit = {
    /*
     * It is expected that the values are in the same order
     * as the defined columns:
     *
     * 0: uuid string
     * 1: timestamp long
     * 2: active boolean
     * 3: enrolled boolean
     * 4: secret string
     * 5: key string
     * 6: host string
     * 7: checkin long
     * 8: address string
     */
    val sqlTpl = "insert into nodes values ('%0', %1, %2, %3, '%4', '%5', '%6', %7, '%8')"
    insert(values, sqlTpl)

  }

  def updateNode(values:Seq[String]):Unit = {
    /*
     * It is expected that the values are in the same order
     * as the defined columns:
     *
     * 0: uuid string
     * 1: timestamp long
     * 2: active boolean
     * 3: enrolled boolean
     * 4: secret string
     * 5: key string
     * 6: host string
     * 7: checkin long
     * 8: address string
     */
    val sqlTpl = "insert into nodes values ('%0', %1, %2, %3, '%4', '%5', '%6', %7, '%8')"
    insert(values, sqlTpl)

  }

  /** QUERY **/

  def createQuery(values:Seq[String]):Unit = {
    /*
     * It is expected that the values are in the same order
     * as the defined columns:
     *
     * 0: uuid string
     * 1: timestamp long
     * 2: description string
     * 3: sql string
     * 4: notbefore long
     */
    val sqlTpl = "insert into queries values ('%0', %1, '%2', '%3', %4)"
    insert(values, sqlTpl)

  }

  def updateQuery(values: Seq[String]):Unit = {
    /*
     * It is expected that the values are in the same order
     * as the defined columns:
     *
     * 0: uuid string
     * 1: timestamp long
     * 2: description string
     * 3: sql string
     * 4: notbefore long
     */
    val sqlTpl = "insert overwrite queries values ('%0', %1, '%2', '%3', %4)"
    insert(values, sqlTpl)

  }

  /** TASK **/

  def createTask(values:Seq[String]):Unit = {
    /*
     * It is expected that the values are in the same order
     * as the defined columns:
     *
     * 0: uuid string
     * 1: timestamp long
     * 2: node string
     * 3: query string
     * 4: status string
     */
    val sqlTpl = "insert into tasks values ('%0', %1, '%2', '%3', '%4')"
    insert(values, sqlTpl)

  }

  def updateTask(task:OsqueryQueryTask):Unit = {
    /*
     * It is expected that the values are in the same order
     * as the defined columns:
     *
     * 0: uuid string
     * 1: timestamp long
     * 2: node string
     * 3: query string
     * 4: status string
     */
    val values = Seq(
      task.uuid,
      task.timestamp,
      task.node,
      task.query,
      task.status
    ).map(_.toString)

    updateTask(values)

  }
  def updateTask(values:Seq[String]):Unit = {
    /*
     * It is expected that the values are in the same order
     * as the defined columns:
     *
     * 0: uuid string
     * 1: timestamp long
     * 2: node string
     * 3: query string
     * 4: status string
     */
    val sqlTpl = "insert overwrite tasks values ('%0', %1, '%2', '%3', '%4')"
    insert(values, sqlTpl)

  }

  /** CONFIGURATION **/

  def createConfiguration(values:Seq[String]):Unit = {
    /*
     * It is expected that the values are in the same order
     * as the defined columns:
     *
     * 0: uuid string
     * 1: timestamp long
     * 2: node string
     * 3: config string
     */
    val sqlTpl = "insert into configurations values ('%0', %1, '%2', '%3', '%4')"
    insert(values, sqlTpl)

  }

  def updateConfiguration(values:Seq[String]):Unit = {
    /*
     * It is expected that the values are in the same order
     * as the defined columns:
     *
     * 0: uuid string
     * 1: timestamp long
     * 2: node string
     * 3: config string
     */
    val sqlTpl = "insert overwrite configurations values ('%0', %1, '%2', '%3', '%4')"
    insert(values, sqlTpl)

  }
  /**
   * This method leverages Apache Spark to create or
   * update data in Redis "tables"
   */
  private def insert(values:Seq[String], sqlTpl:String):Unit = {
    var insertSql = sqlTpl

    values.zipWithIndex.foreach{ case(value, index) =>
      insertSql = insertSql.replace(s"%$index", value)
    }

    session.sql(insertSql)

  }

  /** READ OPERATIONS - CONFIGURATION **/

  def readConfigByNode(value:String):String = {

    try {

      val sql = s"select config from configurations where node = '$value'"
      val result = session.sql(sql)

      val configs = result.collect.map(row => row.getString(0))
      configs(0)

    } catch {
      case _:Throwable => null
    }

  }

  /** READ OPERATIONS - NODE **/

  /**
   * This method retrieves a specific node instance
   * that is identified by its shared `key`
   */
  def readNodeByKey(value:String):OsqueryNode = {

    try {

      val sql = s"select * from nodes where key = '$value'"
      val result = session.sql(sql)

      val nodes = dataframe2Nodes(result)
      nodes(0)

    } catch {
      case _:Throwable => null
    }

  }
  /**
   * This method retrieves a specific node instance
   * that is identified by its shared `secret`
   */
  def readNodeBySecret(value:String):OsqueryNode = {

    try {

      val sql = s"select * from nodes where secret = '$value'"
      val result = session.sql(sql)

      val nodes = dataframe2Nodes(result)
      nodes(0)

    } catch {
      case _:Throwable => null
    }

  }
  /**
   * This method determines whether a certain node
   * is still alive
   *
   * interval: pre-defined time interval to describe
   * accepted inactivity
   */
  def readNodeHealth(uuid:String, interval:Long):String = {

    try {

      val sql = s"select checkin from nodes where uuid = '$uuid'"
      val result = session.sql(sql)

      val checkins = result.collect.map(row => row.getLong(0))
      val checkin = checkins(0)

      val delta = System.currentTimeMillis - checkin
      if (delta > interval) "danger" else ""

    } catch {
      case _:Throwable => null
    }

  }

  private def dataframe2Nodes(dataset:DataFrame):Array[OsqueryNode] = {

    val rows = dataset.collect()
    val nodes = rows.map(row => {

      val uuid = row.getString(0)
      val timestamp = row.getLong(1)

      val active = row.getBoolean(2)
      val enrolled = row.getBoolean(3)

      val secret = row.getString(4)
      val key = row.getString(5)

      val host = row.getString(6)
      val checkin = row.getLong(7)

      val address = row.getString(8)

      OsqueryNode(
        uuid           = uuid,
        timestamp      = timestamp,
        active         = active,
        enrolled       = enrolled,
        secret         = secret,
        hostIdentifier = host,
        lastCheckIn    = checkin,
        lastAddress    = address,
        nodeKey        = key)

    })

    nodes

  }

  /** READ OPERATIONS - QUERY **/

  /**
   * Retrieve all distributed queries assigned to a particular
   * node in the NEW state. This function will change the state
   * of the distributed query to PENDING, however will not commit
   * the change.
   *
   * It is the responsibility of the caller to commit or rollback
   * on the current database session.
    */
  def getNewQueries(uuid:String):JsonObject = {
    /*
     * STEP #1: Retrieve all distributed query tasks, that refer
     * to the provided node identifier and have status `NEW`, and
     * select all associated queries where 'notBefore < now`
     *
     * QueryTask.node == node AND QueryTask.status = 'NEW' AND Query.notBefore < now
     */
    val now = System.currentTimeMillis
    /*
     * The result is a `Map`that assigns the task `uuid` and
     * the `sql` field of the query
     */
    val result = readNewQueries(uuid, now)

    val queries = new JsonObject()
    result.foreach { case(uuid, sql) =>
      /*
       * The tasks, that refer to the database retrieval result have
       * to be updated; this should be done after the osquery node
       * received the queries, which, however, is not possible
       */
      var task = readTaskById(uuid)
      task = task.copy(status = "PENDING", timestamp = now)

      updateTask(task)
      /*
       * Assign (task uuid, sql) to the output
       */
      queries.addProperty(uuid, sql.asInstanceOf[String])
    }

    queries
  }

  /**
   * This method retrieves queries that have been assigned to
   * a certain node but not deployed to the respective osquery
   * daemon
   */
  private def readNewQueries(uuid:String, timestamp:Long):Map[String,String] = {

    try {

      val sql = s"select tasks.uuid, queries.sql from tasks inner join queries on tasks.query = queries.uuid where tasks.node = '$uuid' and tasks.status = 'NEW' and queries.notbefore < $timestamp"
      val result = session.sql(sql)

      val rows = result.collect
      rows.map(row => {

        val task_uuid = row.getString(0)
        val query_sql = row.getString(1)

        (task_uuid, query_sql)

      }).toMap

    } catch {
      case _:Throwable => null
    }

  }

  /** READ OPERATIONS - TASK **/

  def readTaskById(uuid:String):OsqueryQueryTask = {

    try {

      val sql = s"select * from tasks where uuid = '$uuid'"
      val result = session.sql(sql)

      val tasks = dataframe2Tasks(result)
      tasks(0)

    } catch {
      case _:Throwable => null
    }

  }

  private def dataframe2Tasks(dataset:DataFrame):Array[OsqueryQueryTask] = {

    val rows = dataset.collect()
    val tasks = rows.map(row => {

      val uuid = row.getString(0)
      val timestamp = row.getLong(1)

      val node = row.getString(2)
      val query = row.getString(3)

      val status = row.getString(4)

      OsqueryQueryTask(
        uuid      = uuid,
        timestamp = timestamp,
        node      = node,
        query     = query,
        status    = status)

    })

    tasks

  }

  /*
   * The actual REDIS database consists tables to manage
   * `nodes`, `queries` and `tasks`. The latter specifies
   * the relation between a certain node and query.
   */
  private def buildTables():Unit = {

    val nodes_sql = {

      val columns = "uuid string, timestamp long, active boolean, enrolled boolean, secret string, key string, host string, checkin long, address string"
      val createSql = CREATE_TABLE.replace("%1", "nodes").replace("%2", columns).replace("%3", USING)

      createSql

    }

    val queries_sql = {

      val columns = "uuid string, timestamp long, description string, sql string, notbefore long"
      val createSql = CREATE_TABLE.replace("%1", "queries").replace("%2", columns).replace("%3", USING)

      createSql

    }

    val tasks_sql = {

      val columns = "uuid string, timestamp long, node string, query string, status string"
      val createSql = CREATE_TABLE.replace("%1", "tasks").replace("%2", columns).replace("%3", USING)

      createSql

    }

    val configurations_sql = {

      val columns = "uuid string, timestamp long, node string, config string"
      val createSql = CREATE_TABLE.replace("%1", "configurations").replace("%2", columns).replace("%3", USING)

      createSql

    }

    session.sql(nodes_sql)
    session.sql(queries_sql)
    session.sql(tasks_sql)
    session.sql(configurations_sql)

  }

}
