java-influxdb-metrics-agent
===========================

Java agent for collecting and reporting metrics to InfluxDB

Installation
------------

1. Clone this repository and `mvn clean package` to build the artifact.
2. Put `target/java-influxdb-metrics-agent-{version}.jar` somewhere on your server.

#### Runtime dependency

##### slf4j-api (required)

Since the version of slf4j-api and its binding must match, slf4j-api is not included in the agent jar and must be provided at runtime. Usually, slf4j-api is already on your classpath (since many applications depends on it), but if it's not, you should download it from [the official site](http://www.slf4j.org/download.html).

##### slf4j binding (recommended)

You will need one of slf4j bindings (logback, log4j + slf4j-log4j, etc.) for logging to work properly. As with the case of slf4j-api, it is probably on your classpath already. You will not see any helpful log messages, even in the case of connection failures to InfluxDB servers, if no slf4j binding is provided.

Usage
-----

Add `-javaagent` option to JVM arguments.

```
-javaagent:<PATH_TO_AGENT_JAR>=<CONF1>=<VALUE1>,<CONF2>=<VALUE2>...
```

#### Example

```
-javaagent:/opt/java-influxdb-metrics-agent-0.0.1.jar=servers=influxdb.example.com,database=test,interval=10,tags.host=`hostname`
```

Configuration
-------------

| Property Name | Default | Description |
|---------------|---------|-------------|
| **type** | - | The component type name, has to be `net.thisptr.flume.reporter.influxdb.InfluxDBReporter` |
| **servers** | - | Comma-separated list `of hostname:port` of InfluxDB servers |
| **database** | - | The name of the database to store metrics. The database is automatically created if not exists. |
| interval | 30 | Time, in seconds, between consecutive reporting to InfluxDB server |
| user | root | The user name to use when connecting to InfluxDB server |
| password | root | The password to use when connecting to InfluxDB server |
| tags.*&lt;key&gt;* | - | Additional tags to set on each measurements. For example, to add a `hostname` tag, configuration will look like `tags.hostname = foo.example.com` |
| retention | - | The name of the RetentionPolicy to write metrics to. If not specified, `DEFAULT` policy is used. |

TODO
----

 - upload the artifact to the maven central
