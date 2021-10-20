
# Works Beats

A collection of standalone Akka-based Http(s) services to connect to Fiware, OpenCTI and ThingsBoard.

## FIWARE Beat

**Fiware Beat** is a standalone Akka-based Http(s) service that connects to the FIWARE
Context Broker, receives notifications that match custom subscriptions, and re-publishes
these notifications via MQTT or SSE.

**FIWARE** brings a curated framework of open source software components to accelerate and
ease the implementation of smart IoT platforms and solutions.

The main components comprise an information hub, the FIWARE Context Broker, and a set of
IoT Agents (IOTA) to interact with devices via widely used IoT protocols and bridge between
multiple message formats and a common *NGSI v2* and *NGSI-LD* based format.

From an analytics perspective, connecting to a (single) Context Broker and receiving
device notification in real-time and in NGSI format, is a lot easier than interacting 
with plenty of individual data sources.

Just to name a few IoT protocols supported by the FIWARE framework:
* ISOXML
* LoRaWAN
* LWM2M over CoaP
* MQTT
* OPC-UA
* Sigfox

FIWARE is not restricted to these protocols, and also provides an agent library to build
custom IoT Agents.

**Fiware Beat** perfectly works with **Works Stream**, an Apache Spark Streaming based library,
and events from multi-protocol IoT devices can be used in the Apache Spark ecosystem.

## Fleet Beat

**Fleet Beat** is a standalone Akka-based Http(s) service that monitors the Fleet platform's
event log directory and re-publishes log (change) events via MQTT or SSE.

*Fleet* is a device management platform on top of *Osquery* for 100,000+ devices (and Osquery
agents). Fleet is a TLS endpoint for Osquery agents and collects configured & adhoc query 
results on the file system.  

From an analytics perspective, Fleet serves as a query result aggregator that can be used to
derive meaningful insights from a large ensemble of devices at once.

## OPC-UA Beat

**OPC-UA Beat** is a standalone Akka-based Http(s) service that connects to an OPC-UA server
leveraging *Eclipse Milo*, listens to configured subscriptions and re-publishes subscription 
results via MQTT or SSE.

**Milo** is an open-source implementation of OPC UA (currently targeting 1.03). It includes a 
high-performance stack (channels, serialization, data structures, security), and client and 
server SDKs.

## OpenCTI Beat

**OpenCTI Beat** is a standalone Akka-based Http(s) service that connects to the OpenCTI
SSE stream, transforms *create*, *update* and *delete* events from STIX 2.0 to NGSI and
re-publishes these events via SSE.

**OpenCTI** is a unified open source platform for all levels of Cyber Threat Intelligence.
A major goal is to build and provide a powerful knowledge base for cyber threat intelligence
and cyber operations.

OpenCTI ships with a variety of connectors to widely known threat intelligence data sources
like AlienVault, CrowdStrike, FireEye and MISP, MITRE ATT&CK and more.

**OpenCTI Beat** perfectly works with **Works Stream**, an Apache Spark Streaming based library,
and open threat intelligence events can be used in the Apache Spark ecosystem. 

## Osquery Beat

**Osquery Beat** is a standalone Akka-based Http(s) service and provides a TLS endpoint
for Osquery node-based *query results* and *status messages*. Node and status information
is consumed and re-published via MQTT or SSE.

This service can also be used to configure and manage a fleet of Osquery nodes. To this end,
Osquery Beat is backed by Redis.

**Osquery** is an operating system instrumentation framework that exposes an operating system
as a high-performance relational database. This allows to write SQL queries to explore operating
system data.

**Osquery** is a perfect tool for *Host Intrusion Detection* once it is configured as it has the
power to monitor thousands of machines simultaneously. Adding analytics and alerts on top of osquery
logs will help in the easy setup of in-house EDR Solution.

**Osquery Beat** perfectly works with **Works Stream**, an Apache Spark Streaming based library,
and events from thousands of machines and endpoints can be used in the Apache Spark ecosystem.

## Things Beat

**Things Beat** is a standalone Akka-based Http(s) service that leverages
a Mqtt client based ThingsBoard connector. Retrieved events are transformed
into the NGSI format and re-published via MQTT or SSE.

**ThingsBoard** is an open-source IoT platform for data collection, processing, visualization, and device management.
It enables device connectivity via industry standard IoT protocols like MQTT, CoAP and HTTP and supports both cloud and
on-premises deployments.

**Things Beat** perfectly works with **Works Stream**, an Apache Spark Streaming based library,
and events from multi-protocol IoT devices can be used in the Apache Spark ecosystem:
* BACnet
* BLE
* CAN
* CoAP
* HTTP(S)
* OPC-UA
* Modbus
* MQTT

## Zeek Beat

**Zeek Beat** is a standalone Akka-based Http(s) service that monitors the Zeek sensor's
event log directory and re-publishes log (change) events via MQTT or SSE.

**Zeek** is a passive, open-source network traffic analyzer. It can be used as a network security 
monitor (NSM) to support investigations of suspicious or malicious activity. 

Zeek offers an extensive set of logs describing network activity, even beyond the security domain, 
including performance measurement and troubleshooting. 

These logs range from comprehensive records of every connection seen on the wire, to HTTP sessions
with their requested URIs, key headers, MIME types, and server responses, DNS requests with replies,
SSL certificates, SMTP sessions and much more. 
