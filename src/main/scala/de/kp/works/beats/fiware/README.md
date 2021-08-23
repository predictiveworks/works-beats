
# Fiware Beat

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

