
# Things Beat

**Things Beat** is a standalone Akka-based Http(s) service that leverages
a Mqtt client based ThingsBoard connector. Retrieved events are transformed 
into the NGSI format and re-published via SSE.

**ThingsBoard** is an open-source IoT platform for data collection, processing, visualization, and device management.
It enables device connectivity via industry standard IoT protocols like MQTT, CoAP and HTTP and supports both cloud and 
on-premises deployments. 


**ThingsBoard Beat** perfectly works with **Works Stream**, an Apache Spark Streaming based library,
and events from multi-protocol IoT devices can be used in the Apache Spark ecosystem:
* BACnet
* BLE
* CAN
* CoAP
* HTTP(S)  
* OPC-UA
* Modbus
* MQTT

