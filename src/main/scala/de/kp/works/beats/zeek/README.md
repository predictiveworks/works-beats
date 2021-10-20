
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
