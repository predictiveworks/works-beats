
# ThingsBoard Beat

**Things Beat** is a standalone Akka-based Http(s) service that leverages
a Mqtt client based ThingsBoard connector. Retrieved events are transformed 
into the NGSI format and re-published via SSE.