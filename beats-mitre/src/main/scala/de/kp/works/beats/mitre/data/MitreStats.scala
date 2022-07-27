package de.kp.works.beats.mitre.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.google.gson.JsonElement
import de.kp.works.beats.mitre.MitreDomains

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.mutable

/**
 * [MitreStats] leverages the memory store [MitreStore]
 * and extracts and provides relevant statistics
 */
object MitreStats {

  protected val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  def extractStats():Unit = {

    val domains = Seq(
      MitreDomains.CAPEC, MitreDomains.ENTERPRISE, MitreDomains.ICS, MitreDomains.MOBILE)

    val stats = mutable.HashMap.empty[String, Map[String,Any]]

    domains.foreach(domain => {

      val domainStats = mutable.HashMap.empty[String, Map[String,Any]]
      val objects = MitreStore.get(domain)
      /*
       * Determine the total number of object type
       * occurrences within the domain specific
       * knowledge bases. E.g., this describes the
       * # of attack patterns (techniques) within
       * the ICS domain
       */
      domainStats += "objectStats" -> objectStats(objects)
      /*
       * Determine platform specific number of
       * object types, and, object statistics
       */
      domainStats += "platformStats" -> platformStats(objects)
      // TODO

      /*
       * Finally assign domain specific statistics
       * to the result data structure
       */
      stats += domain.toString -> domainStats.toMap

    })

  }

  private def objectStats(objects:Seq[JsonElement]):Map[String,Int] = {

    val stats = objects
      .map(obj => {
        val objType = obj.getAsJsonObject
          .get("type").getAsString

        (objType, 1)
      })
      /*
       * Sort object types in alphabetical order
       */
      .sortBy{case(t, _) => t}
      /*
       * Group by object types and count the
       * total number of occurrences.
       */
      .groupBy{case(t, _) => t}
      .map{case(t, v) => (t, v.size)}

    stats

  }

  private def platformStats(objects:Seq[JsonElement]):Map[String, Map[String,Any]] = {

    val stats = objects
      /*
       * Restrict to those objects that refer
       * to at least one platform
       */
      .filter(obj =>
        obj.getAsJsonObject.has("x_mitre_platforms"))
      .flatMap(obj => {
        val objJson = obj.getAsJsonObject

        val objType = objJson.get("type").getAsString
        val platforms = objJson.get("x_mitre_platforms").getAsJsonArray

        platforms.map(platform => {
          (platform.getAsString, objType, 1)
        })

      })
      /*
       * Sort platforms in alphabetical order
       */
      .sortBy{case(p, _, _) => p}
      /*
       * Group by platforms
       */
      .groupBy{case(p, _, _) => p}
      .map{case(p, v) =>

        val total = v.size
        /*
         * Extract platform specific
         * object stats
         */
        val objStats = v
          .map{case(_, t, c) => (t,c)}
          /*
           * Sort object types in alphabetical order
           */
          .sortBy{case(t, _) => t}
          /*
           * Group by object types and count the
           * total number of occurrences.
           */
          .groupBy{case(t, _) => t}
          .map{case(t, c) => (t, c.size)}

        (p, Map("total" -> total, "objectStats" -> objStats))
      }

    stats

  }
}
