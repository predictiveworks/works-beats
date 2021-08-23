
# Works Beats

## FIWARE Beat

TBD

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
