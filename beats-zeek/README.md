
## Zeek Beat

**ZeekBeat** is a standalone Akka-based Http(s) service that monitors the Zeek sensor's
event log directory and re-publishes log (change) events via FIWARE, MQTT or SSE.

**Zeek** is a passive, open-source network traffic analyzer. It can be used as a network security
monitor (NSM) to support investigations of suspicious or malicious activity. 

Zeek offers an extensive set of logs describing network activity, even beyond the security domain,
including performance measurement and troubleshooting.

These logs range from comprehensive records of every connection seen on the wire, to HTTP sessions
with their requested URIs, key headers, MIME types, and server responses, DNS requests with replies,
SSL certificates, SMTP sessions and much more. 

**ZeekBeat** is part of PredictiveWorks. Cyber Security Beats and offers a threat detection sensor
for enterprise network traffic. Due to its output channels, FIWARE, MQTT and SSE, ZeekBeat is built 
to complement (security) context recognition infrastructures with raw context. 

Example:

In FIWARE-enabled context management, the context broker can be used a broker for security content.
With this context broker, threat intelligence platforms like OpenCTI and threat detection sensors 
like Osquery and Zeek seamless integrate.
