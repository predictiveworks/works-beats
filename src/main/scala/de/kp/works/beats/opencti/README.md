
# OpenCTI Beat

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
and open threat intelligence events to be used in the Apache Spark ecosystem. 
