
# Mitre Beat

## Description
**MitreBeat** is a standalone Akka-based Http(s) microservice that monitors MITRE ATT&CK 
domain knowledge bases and re-publishes change events via FIWARE, MQTT or SSE.

**MitreBeat** also offers a REST API to retrieve domain specific knowledge, such as
adversary tactics & techniques, courses of actions to mitigate attack patterns and more.

## MITRE ATT&CK
MITRE ATT&CK (Adversarial Tactics, Techniques & Common Knowledge) is an in-depth knowledge 
framework, based on real-world observations, that offers an analytical model to analyze and 
identify adversary activities. The following domains are supported:

* ENTERPRISE: Enterprise platforms & networks
* ICS: Industrial control systems & networks
* MOBILE: Mobile platforms

In addition to these MITRE knowledge domains, the **MitreBeat** also supports CAPEC, Common
Attack Pattern Enumeration and Classification. CAPEC is a comprehensive knowledge base of
known patterns of attack employed by adversaries to exploit weaknesses or vulnerabilities
in cyber-enabled capabilities.