java-influxdb-metrics-agent
===========================

Java agent for collecting and reporting JMX metrics to InfluxDB

Installation
------------

#### Build from source

1. Clone this repository and `mvn clean package` to build the artifact.
2. Put `target/java-influxdb-metrics-agent-{version}.jar` somewhere on your server.

#### Download from Maven Central

```sh
curl -O 'http://central.maven.org/maven2/net/thisptr/java-influxdb-metrics-agent/0.0.5/java-influxdb-metrics-agent-0.0.5.jar'
```

### Runtime dependencies

#### slf4j-api (required)

Since the version of slf4j-api and its binding must match, slf4j-api is not included in the agent jar and must be provided at runtime. Usually, slf4j-api is already on your classpath (because many applications depend on it), but if it's not, you have to download it from [the official site](http://www.slf4j.org/download.html).

#### slf4j binding (recommended)

You will need one of slf4j bindings (logback, log4j + slf4j-log4j, etc.) for logging to work properly. As with the case of slf4j-api, it is probably on your classpath already. You will not see any helpful log messages, even in the case of connection failures to InfluxDB servers, if no slf4j binding is provided.

Usage
-----

Add `-javaagent` option to JVM arguments.

```
-javaagent:<PATH_TO_AGENT_JAR>=<CONF1>=<VALUE1>,...
```

#### Example

At least, `servers` and `database` must be specified.

```
-javaagent:/opt/java-influxdb-metrics-agent-0.0.5.jar=servers=influxdb.example.com,database=test
```

Configuration
-------------

### Global options

| Key | Default | Description |
|---------------|---------|-------------|
| **servers** | - | Comma-separated list of `hostname:port` of InfluxDB servers. |
| **database** | - | The name of the database to store metrics to. The database is automatically created if it does not exist. Currently, only accepts *unquoted identifier* described in the [spec](https://docs.influxdata.com/influxdb/v0.13/query_language/spec/#identifiers) (i.e. no hyphens, etc.). |
| interval | 30 | Time, in seconds, between consecutive reporting to InfluxDB servers. |
| user | root | The user name to use when connecting to InfluxDB servers. |
| password | root | The password to use when connecting to InfluxDB servers. |
| tags.*&lt;key&gt;* | - | Additional tags to set on each measurements. For example, to add a `host` tag, configuration should be: `tags.host = foo.example.com` |
| retention | - | The name of the RetentionPolicy to write metrics to. If not specified, `DEFAULT` policy is used. |

### Metric-specific options

Metric-specific options can be specified in `/MBEAN_REGEX/ { <KEY1> = <VALUE1>, ... }` form.

| Key | Default | Description |
|------|---------|-------------|
| namekeys | *&lt;empty&gt;*   | Comma-separated list of MBean key properties to append to measurement names. For example, `/java.lang/{namekeys=type}` will generate measurements such as `java.lang:type=MemoryPool` and `java.lang:type=GarbageCollector` instead of a single `java.lang` measurement containing mulitple series with different `type` tags. |
| exclude | false | Exclude metrics from being sent to InfluxDB. |

### Using configuration files

To read configurations from `agent.conf`, add `@agent.conf` somewhere in `-javaagent` option.

```sh
-javaagent:/opt/java-influxdb-metrics-agent-0.0.5.jar=tags.host=`hostname`,@agent.conf
```

Examples
--------

### Apache Flume

```sh
# FILE: flume-env.sh
export JAVA_OPTS="-javaagent:/opt/flume/java-influxdb-metrics-agent-0.0.5.jar=tags.host=`hostname`,@/opt/flume/agent.conf"
```

```cs
// FILE: agent.conf
servers = influxdb.example.com
database = myapp
interval = 10
namekeys = type

// We don't need JMImplementation recorded in InfluxDB.
/JMImplementation/ {
	exclude = true
}

/org.apache.flume.*/ {
	// It's convenient to have these channel metrics in a single measurement,
	// rather than separated into many unrelated measurements.
	//  - org.apache.flume.channel:type=foo-ch
	//  - org.apache.flume.channel:type=bar-ch
	//  - org.apache.flume.channel:type=baz-ch
	namekeys =
}
```

If you prefer, an equivalent configuration can be set without `agent.conf`:
```sh
export JAVA_OPTS="-javaagent:/opt/flume/java-influxdb-metrics-agent-0.0.5.jar=tags.host=`hostname`,servers=influxdb.example.com,database=myapp,interval=10,namekeys=type,/JMImplementation/{exclude=true},/org.apache.flume.*/{namekeys=}"
```

### Apache Tomcat 8

```sh
# FILE: setenv.sh
export CATALINA_OPTS="-javaagent:/opt/java-influxdb-metrics-agent-0.0.5.jar=tags.host=`hostname`,@/opt/tomcat/agent.conf"

# Tomcat does not have slf4j-api in SYSTEM classloader ( https://tomcat.apache.org/tomcat-8.0-doc/class-loader-howto.html ). Need to download manually.
#  - curl -o /opt/tomcat/slf4j-api-1.7.21.jar http://central.maven.org/maven2/org/slf4j/slf4j-api/1.7.21/slf4j-api-1.7.21.jar
#  - curl -o /opt/tomcat/slf4j-simple-1.7.21.jar http://central.maven.org/maven2/org/slf4j/slf4j-api/1.7.21/slf4j-simple-1.7.21.jar
export CLASSPATH="/opt/tomcat/slf4j-api-1.7.21.jar:/opt/tomcat/slf4j-simple-1.7.21.jar"
```

```cs
// FILE: agent.conf
servers = influxdb.example.com
database = myapp
interval = 10
namekeys = type
tags.env = production
tags.role = web

/JMImplementation/ { exclude = true }
/Users/ { exclude = true }
```

### Dropwizard Metrics

[Dropwizard Metrics](http://metrics.dropwizard.io/3.1.0/) is a Java library for measuring and publishing application metrics.
Because MBeans exposed by Metrics does not have a `type=` key property even through the MBeans sometimes differ in its attribute types, we have to separate measurements using a `name=` key property when storing into InfluxDB.

```cs
/metrics/ { namekeys = name }
```

### Apache HBase

```sh
# FILE: hbase-env.sh
export HBASE_JMX_BASE="-javaagent:/opt/java-influxdb-metrics-agent-0.0.5.jar=servers=localhost,tags.host=`hostname`,tags.active='\${jmx|Hadoop:service=HBase,name=Master,sub=Server:tag.isActiveMaster}',database=hbase,interval=10,namekeys=type,/JMImplementation/{exclude=true},/Hadoop/{namekeys='service,name,sub'},/Hadoop::tag\\\\..+/{exclude=true} -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"
export HBASE_MASTER_OPTS="$HBASE_MASTER_OPTS $HBASE_JMX_BASE -Dcom.sun.management.jmxremote.port=10101"
export HBASE_REGIONSERVER_OPTS="$HBASE_REGIONSERVER_OPTS $HBASE_JMX_BASE -Dcom.sun.management.jmxremote.port=10102"
```

```cs
// FILE: agent.conf
servers = localhost
database = hbase
interval = 10
namekeys = type

tags.host = "${sh|hostname}"
// Set 'active' tag based on HMaster is active or not.
tags.active = "${jmx|Hadoop:service=HBase,name=Master,sub=Server:tag.isActiveMaster}"

/JMImplementation/ {
	// Remove JMImplementation.
	exclude = true
}

/Hadoop/ {
	// Value contains commas, so we need quotes.
	namekeys = 'service,name,sub'
}
/Hadoop::tag\\..+/ {
	// Exclude MBean attributes with "tag." prefix in "Hadoop" domain.
	exclude = true
}
```

References
----------

 - [Java Management Extensions (JMX) - Best Practices](http://www.oracle.com/technetwork/articles/java/best-practices-jsp-136021.html)

License
-------

The MIT License.
