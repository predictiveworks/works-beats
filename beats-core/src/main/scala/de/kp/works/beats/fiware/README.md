
# FIWARE Output Channel

## Description
The FIWARE NGSI-compliant context broker is one of the building blocks of modern
context management.

*Context* is defined as **any** information that is useful to characterize the 
situation of an entity, and that affects the interaction and relationship of 
entities.

Context combines three information levels, *raw*, *interpreted* and *situation*:

* *Raw context* is data provided by sensors or other data sources.

* *Interpreted context* is information that is derived from raw data. It represents information
in a higher level of abstraction (e.g., activities). Machine learning is an adequate means
to create interpretations.
  
* *Situation* defines a combination and association of multiple interpretations.

Context management and its process, with phases context acquisition, modeling, recognition
and dissemination, is an appropriate means to enable smart situational awareness in all
analytics dimensions. 

Smart situational awareness is the foundation of modern OODA decision support, and is based 
on a 3-layer model:

* Perception
* Comprehension
* Projection

## Implementation
The current implementation sends events from the following data sources to a FIWARE
context broker:

### *Fleet*
The **Fleet Beat** monitors an Osquery-based endpoint fleet management platform, transforms
Osquery results into NGSI-compliant entities and send them to a FIWARE context broker.

Transforming Endpoint-based cyber events into NGSI entities and their relationships aims
to integrate cyber defense into a common enterprise context management (system).

### *OPC-UA*
The **OPC-UA Beat** listens to an OPC-UA server, and transforms a set of events that refer
to configured subscriptions into NGSI-compliant entities and send them to a FIWARE context 
broker.

Note: A certain OPC-UA event specifies a single attribute of a certain entities (or object).

### *OpenCTI*
The **OpenCTI Beat** listens to the SSE from an OpenCTI instance, transforms the STIX v2
events into NGSI-compliant entities & relations, and send them to a FIWARE context broker.

This *Works Beat* is part of an enterprise sensing & perception layer, and disseminates
NGSI-formatted entities for smart interpretation and entity situation inference, to finally
contribute to common context-aware situational awareness.

### *Zeek*
The **Zeek Beat** monitors a Zeek (network) sensor platform, transforms the logged
events into NGSI-compliant entities and send them to a FIWARE context broker.