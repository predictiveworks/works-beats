
# OPC-UA Beat

**OPC-UA Beat** is a standalone Akka-based Http(s) service that connects to an OPC-UA server
leveraging *Eclipse Milo*, listens to configured subscriptions and re-publishes subscription
results via MQTT or SSE.

**Milo** is an open-source implementation of OPC UA (currently targeting 1.03). It includes a
high-performance stack (channels, serialization, data structures, security), and client and
server SDKs.

