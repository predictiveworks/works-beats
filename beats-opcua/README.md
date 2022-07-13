
# OPC-UA Beat

**OPC-UA Beat** is a standalone Akka-based Http(s) service that connects to an OPC-UA server
leveraging *Eclipse Milo*, listens to configured subscriptions and re-publishes subscription
results via FIWARE, MQTT or SSE.

**Milo** is an open-source implementation of OPC UA (currently targeting 1.03). It includes a
high-performance stack (channels, serialization, data structures, security), and client and
server SDKs.

**OPC-UA Beat** serves as a data gateway that tracks, standardizes and exposes industrial
IoT data to FIWARE & MQTT networks, and via SSE to custom applications.

# OPC UA

**OPC UA** is a well-known client-server protocol used in the Industry. An OPC UA server is usually 
responsible for fetching sensor data from factory-level machinery making them available to an OPC UA 
client.

As a prerequisite, before clients can retrieve data, sensors are mapped to the OPC UA Server Address Space 
as variables (or attributes). Access to sensor values is provided through a subscription mechanism. 

For each sensor value, the OPC UA client needs access to, a subscription must be specified and created. 
At that point the server determines if the requests can be fulfilled, otherwise it will continue sending 
data in the best effort mode.

Parameters to configure subscriptions:

*Sampling interval* defines, for each Monitored Item, the interval used by the server to evaluate changes 
in the value of the variable. The actual value chosen by the server depends on the underlying hardware capabilities.

*Publishing Interval* defines the interval that has to elapse to notify possible changes to the client. 
Whenever the server discovers a change in the value of a variable, this new value is stored into the queue.
After the configured publishing interval has expired, the entire queue is sent to the client. In this way the 
client can receive even very fast changes that otherwise would not have been detected

*Queue size* defines, for each Monitored Item, the size of the queue within which to store changes in the variable.
The queue is emptied, after samples were sent to the client.

