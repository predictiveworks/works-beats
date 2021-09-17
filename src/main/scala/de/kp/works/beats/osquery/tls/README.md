
# Osquery TLS Beat

**Osquery TLS Beat** is a standalone Akka-based Http(s) service and provides a TLS endpoint
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


