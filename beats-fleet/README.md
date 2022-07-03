
## Fleet Beat

**FleetBeat** is a standalone Akka-based Http(s) service that monitors the Fleet platform's
event log directory and re-publishes log (change) events via FIWARE, MQTT or SSE.

*Fleet* is a device management platform on top of *Osquery* for 100,000+ devices (and Osquery
agents). Fleet is a TLS endpoint for Osquery agents and collects configured & adhoc query
results on the file system.

From an analytics perspective, Fleet serves as a query result aggregator that can be used to
derive meaningful insights from a large ensemble of devices at once.

**FleetBeat** is part of PredictiveWorks. Cyber Security Beats and offers a threat detection sensor
for enterprise endpoints or nodes. Due to its output channels, FIWARE, MQTT and SSE, FleetBeat is built
to complement (security) context recognition infrastructures with raw context.

Example:

In FIWARE-enabled context management, the context broker can be used a broker for security content.
With this context broker, threat intelligence platforms like OpenCTI and threat detection sensors
like Osquery and Zeek seamless integrate.

