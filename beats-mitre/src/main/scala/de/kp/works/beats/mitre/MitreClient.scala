package de.kp.works.beats.mitre
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

import com.google.gson.{JsonElement, JsonObject, JsonParser}
import de.kp.works.beats.BeatsConf.MITRE_CONF
import de.kp.works.beats.mitre.MitreDomains.MitreDomain
import de.kp.works.beats.{BeatsConf, BeatsLogging}

import java.io.File
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.io.Source

object MitreDomains extends Enumeration {
  type MitreDomain = Value
  /*
   * Understanding how the adversary operates is essential
   * to effective cyber security. CAPEC™ helps by providing
   * a comprehensive dictionary of known patterns of attacks
   * employed by adversaries to exploit known weaknesses in
   * cyber-enabled capabilities.
   *
   * It can be used by analysts, developers, testers, and educators
   * to advance community understanding and enhance defenses.
   *
   * - Focuses on application security
   * - Enumerates exploits against vulnerable systems
   * - Includes social engineering / supply chain
   * - Associated with Common Weakness Enumeration (CWE)
   */
  val CAPEC:MitreDomain      = Value(1, "CAPEC")
  val ENTERPRISE:MitreDomain = Value(2, "ENTERPRISE")
  /*
   * Industrial Control Systems (ICS)
   */
  val ICS:MitreDomain        = Value(3, "ICS")
  val MOBILE:MitreDomain     = Value(4, "MOBILE")
}

object MitreClient extends MitreConnect

abstract class MitreConnect extends BeatsLogging {

  private val cfg = BeatsConf.getBeatCfg(MITRE_CONF)
  private val base = cfg.getString("folder")

  private val STIXv20 = s"$base/stixv2.0"
  private val STIXv21 = s"$base/stixv2.1"

  private val CAPEC      = STIXv20 + "/capec/2.1"
  private val ENTERPRISE = STIXv21 + "/enterprise-attack"
  private val ICS        = STIXv21 + "/ics-attack"
  private val MOBILE     = STIXv21 + "/mobile-attack"

  val OBJECT_TYPES = List(
    "attack-pattern",
    "course-of-action",
    /*
     * "identity" is ignored
     */
    "intrusion-set",
    "malware",
    /* "marking-definition" is ignored */
    "relationship",
    "tool",
    "x-mitre-collection",
    "x-mitre-data-component",
    "x-mitre-data-source",
    "x-mitre-matrix",
    "x-mitre-tactic")

  /**
   * The list of properties is a collection
   * of all object type attributes in the
   * domains Enterprise, Ics and Mobile
   */
  val OBJECT_TYPE_PROPS = List(
    "aliases",
    "created",
    "created_by_ref",
    "description",
    "external_references",
    "id",
    "is_family",
    "kill_chain_phases",
    "modified",
    "name",
    "object_marking_refs",
    "spec_version",
    "type",
    "x_mitre_aliases",
    "x_mitre_attack_spec_version",
    "x_mitre_contributors",
    "x_mitre_data_sources",
    "x_mitre_defense_bypassed",
    "x_mitre_detection",
    "x_mitre_domains",
    "x_mitre_is_subtechnique",
    "x_mitre_modified_by_ref",
    "x_mitre_platforms",
    "x_mitre_tactic_type",
    "x_mitre_version")

  def getBundle(domain:MitreDomain):JsonObject = {

    domain match {
      case MitreDomains.CAPEC =>
        loadCapec()

      case MitreDomains.ENTERPRISE =>
        loadEnterprise()

      case MitreDomains.ICS =>
        loadIcs()

      case MitreDomains.MOBILE =>
        loadMobile()
    }

  }
  /**
   * The method extracts domain specific objects
   * from the MITRE knowledge base and thereby
   * excludes `identity` and `marking-definition`.
   */
  def getObjects(domain:MitreDomain, objectType:Option[String]=None):Seq[JsonElement] = {

    val bundle = getBundle(domain)
    if (!bundle.has("objects")) {
      val message = s"STIX bundles does not describe `objects`"
      error(message)

      return Seq.empty[JsonElement]
    }

    val objects = bundle.get("objects").getAsJsonArray
    objects.filter(obj => {

      val objJson = obj.getAsJsonObject
      val objType = objJson.get("type").getAsString

      val notIdentity          = objType != "identity"
      val notMarkingDefinition = objType != "marking-definition"

      if (objectType.isEmpty) {
        /*
         * For objects of the MITRE domain knowledge
         * basis, there is one `marking-definition`
         * object, that contains the copyright
         * statement for the respective entries.
         *
         * As this is no threat related information,
         * the `marking-definition`object is ignored.
         *
         * For objects of the MITRE domain knowledge
         * basis, there is one `identity` object,
         * i.e., the MITRE organization.
         *
         * As this is no threat related information,
         * the `identity`object is ignored.
         *
         */
        notMarkingDefinition && notIdentity
      }
      else {

        (objType == objectType.get) && notMarkingDefinition && notIdentity

      }

    }).toSeq

  }
  def getCoursesOfAction(domain:MitreDomain):Seq[JsonElement] = {
    getObjects(domain, Some("course-of-action"))
  }

  def getDataSources(domain:MitreDomain):Seq[JsonElement] = {
    getObjects(domain, Some("x-mitre-data-source"))
  }

  def getMalware(domain:MitreDomain):Seq[JsonElement] = {
    getObjects(domain, Some("malware"))
  }
  /**
   * Software is a generic term for custom or commercial code,
   * operating system utilities, open-source software, or other
   * tools used to conduct behavior modeled in ATT&CK.
   *
   * Some instances of software have multiple names associated
   * with the same instance due to various organizations tracking
   * the same set of software by different names.
   *
   * Software entries include publicly reported technique use or
   * capability to use a technique and may be mapped to Groups
   * who have been reported to use that Software.
   *
   * The information provided does not represent all possible technique
   * use by a piece of Software, but rather a subset that is available
   * solely through open source reporting.
   */
  def getSoftware(domain:MitreDomain):Seq[JsonElement] = {

    val malware = getMalware(domain)
    val tools = getTools(domain)

    malware ++ tools

  }

  /**
   * Extract object that refer to MITRE
   * tactics
   */
  def getTactics(domain:MitreDomain):Seq[JsonElement] = {
    getObjects(domain, Some("x-mitre-tactic"))
  }
  /**
   * This method queries all STIX objects of a certain
   * domain, restricts objects to `attack-pattern`, and,
   * if a specific `phase` is provided, further restricts
   * to those, where the tactic is part of the kill chain
   * phases.
   *
   * If the respective `source` is also provided, this method
   * also filters the external references with respect to the
   * source name.
   */
  def getTechniques(domain:MitreDomain, source:Option[String],
                    phase:Option[String]):Seq[JsonElement] = {

    val objects = getObjects(domain, Some("attack-pattern"))
    if (objects.isEmpty) return Seq.empty[JsonElement]

    if (source.isEmpty && phase.isEmpty) return objects

    objects.filter(obj => {
      val objJson = obj.getAsJsonObject
      hasSource(objJson, source) && hasPhase(objJson, phase)
    })

  }
  def getTools(domain:MitreDomain):Seq[JsonElement] = {
    getObjects(domain, Some("tool"))
  }

  /**
   * This method determines whether the provided STIX
   * object is a relationship of the specified type,
   * and, if an object identifier is defined, whether
   * is the respective source or target
   */
  def getRelationship(objJson:JsonObject, relationType:String,
                      objectId:Option[String], target:Option[Boolean]):Boolean = {

    val objType = objJson.get("type").getAsString
    if (objType == "relationship") {

      val isRelationType = objJson.get("relation_type").getAsString == relationType
      if (objectId.isEmpty) return isRelationType

      if (target.nonEmpty) {

        val isTarget = objJson.get("target_ref").getAsString == objectId.get
        isRelationType && isTarget && !isDeprecated(objJson)

      } else {

        val isSource = objJson.get("source_ref").getAsString == objectId.get
        isRelationType && isSource && !isDeprecated(objJson)

      }

    } else false

  }

  private def isDeprecated(objJson:JsonObject):Boolean = {

    var deprecated = false
    var revoked = false

    if (objJson.has("x_mitre_deprecated"))
      deprecated = objJson.get("x_mitre_deprecated").getAsBoolean

    if (objJson.has("revoked"))
      revoked = objJson.get("revoked").getAsBoolean

    deprecated && revoked

  }

  private def hasSource(objJson:JsonObject, source:Option[String]):Boolean = {

    if (source.isEmpty) return true
    if (objJson.has("external_references")) {

      val kcps = objJson.get("external_references").getAsJsonArray
      val filtered = kcps.filter(kcp => {
        val kcpJson = kcp.getAsJsonObject
        kcpJson.get("source_name").getAsString == source.get
      })

      filtered.nonEmpty

    } else false

  }
  /**
   * This method determines whether the kill chain phases
   * of a certain STIX object references the provided tactic.
   */
  private def hasPhase(objJson:JsonObject, phase:Option[String]):Boolean = {

    if (phase.isEmpty) return true
    if (objJson.has("kill_chain_phases")) {

      val kcps = objJson.get("kill_chain_phases").getAsJsonArray
      val filtered = kcps.filter(kcp => {
        val kcpJson = kcp.getAsJsonObject
        kcpJson.get("phase_name").getAsString == phase.get
      })

      filtered.nonEmpty

    } else false

  }
  /**
   * In CAPEC, the main object is the `Attack Pattern`. Most Attack Pattern
   * also have `Mitigations`.
   *
   * There are other types of objects in CAPEC (e.g, Category, View, etc.),
   * but these are not (currently) part of the repository.
   *
   * In STIX v2.x, the CAPEC attack pattern maps to `attack-pattern` and
   * `mitigation` is mapped to `course-of-action`.
   */
  def loadCapec():JsonObject = {
    val file = new File(CAPEC + "/stix-capec.json")
    loadFile(file)
  }


  def loadEnterprise():JsonObject = {
    val file = new File(ENTERPRISE + "/enterprise-attack.json")
    loadFile(file)
  }

  def loadIcs():JsonObject = {
    val file = new File(ICS + "/ics-attack.json")
    loadFile(file)
  }

  def loadMobile():JsonObject = {
    val file = new File(MOBILE + "/mobile-attack.json")
    loadFile(file)
  }

  def loadFile(file:File):JsonObject = {

    val source = Source.fromFile(file)

    val fileStr = source.getLines.mkString
    val json = JsonParser.parseString(fileStr).getAsJsonObject

    source.close
    json

  }
  /**
   * The method adds confidence to every object
   * in the respective bundle
   */
  def addConfidence(bundle:JsonObject):Unit = {
    /*
     * The list of object types for which the
     * confidence is added: marking-definition,
     * identity and external-reference-as-report
     * are skipped
     */
    val objectTypes = Seq(
      "attack-pattern",
      "course-of-action",
      "intrusion-set",
      "campaign",
      "malware",
      "tool",
      "report",
      "relationship"
    )

    val objects = bundle.get("objects").getAsJsonArray
    objects.foreach(obj => {

      val objJson = obj.getAsJsonObject
      val objType = objJson.get("type").getAsString

      if (objectTypes.contains(objType)) {
        objJson.addProperty("confidence", MitreOptions.getConfidenceLevel)
      }
    })

  }

}
