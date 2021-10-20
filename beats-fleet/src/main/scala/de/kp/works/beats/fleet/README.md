
## Fleet Beat

**Fleet Beat** is a standalone Akka-based Http(s) service that monitors the Fleet platform's
event log directory and re-publishes log (change) events via MQTT or SSE.

*Fleet* is a device management platform on top of *Osquery* for 100,000+ devices (and Osquery
agents). Fleet is a TLS endpoint for Osquery agents and collects configured & adhoc query
results on the file system.

From an analytics perspective, Fleet serves as a query result aggregator that can be used to
derive meaningful insights from a large ensemble of devices at once.
