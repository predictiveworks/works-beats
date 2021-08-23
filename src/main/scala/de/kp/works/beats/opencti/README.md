
# OpenCTI Beat

**OpenCTI Beat** is a standalone Akka-based Http(s) service that connects to the OpenCTI
SSE stream, transforms *create*, *update* and *delete* events from STIX 2.0 to NGSI and
re-publishes these events via SSE.

<p align="center">
  <img src="https://github.com/predictiveworks/works-beats/blob/main/images/opencti-beat-2021-08-22.png" width="600" alt="OpenCTI Beat">
</p>

**OpenCTI Beat** perfectly works with **Works Stream**, an Apache Spark Streaming based library. 
