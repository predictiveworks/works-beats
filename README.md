
# Works Beats

A collection of standalone Akka-based Http(s) services to connect to Fiware, OpenCTI and ThingsBoard.

## FIWARE Beat

**Fiware Beat** is a standalone Akka-based Http(s) service that connects to the FIWARE
Context Broker, receives notifications that match custom subscriptions, and re-publishes
these notifications via SSE.

<p align="center">
  <img src="https://github.com/predictiveworks/works-beats/blob/main/images/fiware-beat-2021-08-22.png" width="600" alt="Fiware Beat">
</p>

**FIWARE** brings a curated framework of open source software components to accelerate and
ease the implementation of smart IoT platforms and solutions.

The main components comprise and information hub, the FIWARE Context Broker, and a set of
IoT Agents (IOTA) to interact with devices via widely used IoT protocols and bridge between
multiple message formats and a common *NGSI v2* and *NGSI-LD* based format.

From an analytics perspective, it is to connect to a (single) Context Broker and receive
device notification in real-time and in NGSI format, instead of interacting with plenty of
individual data sources.

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

## OpenCTI Beat

**OpenCTI Beat** is a standalone Akka-based Http(s) service that connects to the OpenCTI
SSE stream, transforms *create*, *update* and *delete* events from STIX 2.0 to NGSI and
re-publishes these events via SSE.

<p align="center">
  <img src="https://github.com/predictiveworks/works-beats/blob/main/images/opencti-beat-2021-08-22.png" width="600" alt="OpenCTI Beat">
</p>

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
is consumed and re-published via SSE.

This service can also be used to configure and manage a fleet of Osquery nodes. To this end,
Osquery Beat is backed by Redis.

<p align="center">
  <img src="https://github.com/predictiveworks/works-beats/blob/main/images/osquery-beat-2021-09-10.png" width="600" alt="Osquery Beat">
</p>

**Osquery** is an operating system instrumentation framework that exposes an operating system
as a high-performance relational database. This allows to write SQL queries to explore operating
system data.

**Osquery** is a perfect tool for *Host Intrusion Detection* once it is configured as it has the
power to monitor thousands of machines simultaneously. Adding analytics and alerts on top of osquery
logs will help in the easy setup of in-house EDR Solution.

**Osquery Beat** perfectly works with **Works Stream**, an Apache Spark Streaming based library,
and events from thousands of machines and endpoints can be used in the Apache Spark ecosystem.

## ThingsBoard Beat

**Things Beat** is a standalone Akka-based Http(s) service that leverages
a Mqtt client based ThingsBoard connector. Retrieved events are transformed
into the NGSI format and re-published via SSE.

<p align="center">
  <img src="https://github.com/predictiveworks/works-beats/blob/main/images/thingsboard-beat-2021-08-22.png" width="600" alt="ThingsBoard Beat">
</p>

**ThingsBoard** is an open-source IoT platform for data collection, processing, visualization, and device management.
It enables device connectivity via industry standard IoT protocols like MQTT, CoAP and HTTP and supports both cloud and
on-premises deployments.


**ThingsBoard Beat** perfectly works with **Works Stream**, an Apache Spark Streaming based library,
and events from multi-protocol IoT devices can be used in the Apache Spark ecosystem:
* BACnet
* BLE
* CAN
* CoAP
* HTTP(S)
* OPC-UA
* Modbus
* MQTT
