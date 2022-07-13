
# MQTT Output Channel

## Description
MQTT is one of the output channels of a certain Works Beat. Other channels are FIWARE and SSE.

## Implementation
The current implementation supports *Eclipse Paho* and *Hive* as MQTT client libraries. The selection
of the respective client library is controlled by the configuration of the Works Beat.

### *Fleet*
The **Fleet Beat** monitors an Osquery-based endpoint fleet management platform, transforms
Osquery results into NGSI-compliant entities and send them to an MQTT broker. This approach 
publishes endpoint security events to an IoT network and integrates operational and security 
data in a single network.

### *OPC-UA*
The **OPC-UA Beat** listens to an OPC-UA server, and transforms a set of events that refer
to configured subscriptions into NGSI-compliant entities and send them to an MQTT broker.

Note: A certain OPC-UA event specifies a single attribute of a certain entities (or object).

This approach integrates an OPC-UA based (industrial) environment with an MQTT IoT network. 

### *OpenCTI*
The **OpenCTI Beat** listens to the SSE from an OpenCTI instance, transforms the STIX v2
events into NGSI-compliant entities & relations, and send them to an MQTT broker.

This approach publishes threat intelligence events to an IoT network and integrates operational 
and threat intelligence data in a single network.

### *Zeek*
The **Zeek Beat** monitors a certain Zeek (network) sensor platform, transforms the logged
events into NGSI-compliant entities and send them to an MQTT broker. This approach publishes
network security events to an IoT network and integrates operational and security data in
a single network.