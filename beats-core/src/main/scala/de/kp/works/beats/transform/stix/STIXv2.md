
# STIX 2.1 Domain Objects

Reference: https://oasis-open.github.io/cti-documentation/stix/intro

## Attack Pattern
A type of TTP that describe ways that adversaries attempt to compromise targets.

## Campaign
A grouping of adversarial behaviors that describes a set of malicious activities or 
attacks (sometimes called waves) that occur over a period of time against a specific 
set of targets.

## Course of Action
A recommendation from a producer of intelligence to a consumer on the actions that they 
might take in response to that intelligence.

## Grouping
Explicitly asserts that the referenced STIX Objects have a shared context, unlike a 
STIX Bundle (which explicitly conveys no context).

## Identity
Actual individuals, organizations, or groups (e.g., ACME, Inc.) as well as classes of 
individuals, organizations, systems or groups (e.g., the finance sector).

## Indicator
Contains a pattern that can be used to detect suspicious or malicious cyber activity.

## Infrastructure
Represents a type of TTP and describes any systems, software services and any associated 
physical or virtual resources intended to support some purpose (e.g., C2 servers used as 
part of an attack, device or server that are part of defence, database servers targeted 
by an attack, etc.).

## Intrusion Set
A grouped set of adversarial behaviors and resources with common properties that is believed 
to be orchestrated by a single organization.

## Location
Represents a geographic location.

## Malware
A type of TTP that represents malicious code.

## Malware Analysis
The metadata and results of a particular static or dynamic analysis performed on a malware 
instance or family.

## Note
Conveys informative text to provide further context and/or to provide additional analysis not 
contained in the STIX Objects, Marking Definition objects, or Language Content objects which 
the Note relates to.

## Observed Data
Conveys information about cyber-security related entities such as files, systems, and networks 
using the STIX Cyber-observable Objects (SCOs).

## Opinion
An assessment of the correctness of the information in a STIX Object produced by a different 
entity.

## Report
Collections of threat intelligence focused on one or more topics, such as a description of a 
threat actor, malware, or attack technique, including context and related details.

## Threat Actor
Actual individuals, groups, or organizations believed to be operating with malicious intent.

## Tool
Legitimate software that can be used by threat actors to perform attacks.

## Vulnerability
A mistake in software that can be directly used by a hacker to gain access to a system or network.

# STIX 2.1 Relationship Objects

## Relationship
Used to link together two SDOs or SCOs in order to describe how they are related to each other.

## Sighting
Denotes the belief that something in CTI (e.g., an indicator, malware, tool, threat actor, etc.) 
was seen.