package net.thisptr.java.influxdb.metrics.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

public class JvmAgentConfig {
	private static final Logger LOG = LoggerFactory.getLogger(JvmAgent.class);

	public static final int DEFAULT_PORT = 8086;
	public static final int DEFAULT_INTERVAL = 30;
	public static final String DEFAULT_USER = "root";
	public static final String DEFAULT_PASSWORD = "root";

	public List<HostAndPort> servers;
	public int interval;
	public String database;
	public String user;
	public String password;
	public Map<String, String> tags;
	public String retention;

	public static class ConfigurationException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public ConfigurationException() {
			super();
		}

		public ConfigurationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
			super(message, cause, enableSuppression, writableStackTrace);
		}

		public ConfigurationException(String message, Throwable cause) {
			super(message, cause);
		}

		public ConfigurationException(String message) {
			super(message);
		}

		public ConfigurationException(Throwable cause) {
			super(cause);
		}
	}

	public static class Context {
		private Map<String, String> values;

		public Context(final Map<String, String> values) {
			this.values = new HashMap<>(values);
		}

		public String getString(final String key) {
			return values.get(key);
		}

		public int getInteger(final String key, final int defval) {
			final String val = values.get(key);
			return val == null ? defval : Integer.parseInt(val);
		}

		public String getString(final String key, final String defval) {
			return values.getOrDefault(key, defval);
		}

		public Map<String, String> getSubProperties(final String key) {
			final Map<String, String> result = new HashMap<>();
			values.forEach((k, v) -> {
				if (k.startsWith(key))
					result.put(k.substring(key.length()), v);
			});
			return result;
		}
	}

	public static JvmAgentConfig fromMap(final Map<String, String> values) {
		final Context context = new Context(values);

		final String servers = context.getString("servers");
		if (Strings.isNullOrEmpty(servers))
			throw new ConfigurationException("servers must not be null or empty.");
		final List<HostAndPort> hostAndPorts = StreamSupport.stream(Splitter.on(",").trimResults().split(servers).spliterator(), false)
				.map(server -> HostAndPort.fromString(server).withDefaultPort(DEFAULT_PORT))
				.collect(Collectors.toList());

		final int interval = context.getInteger("interval", DEFAULT_INTERVAL);
		if (interval <= 0)
			throw new ConfigurationException("interval must be positive.");

		final String database = context.getString("database");
		if (Strings.isNullOrEmpty(database))
			throw new ConfigurationException("database must not be null or empty.");

		final String user = context.getString("user", DEFAULT_USER);
		if (Strings.isNullOrEmpty(user))
			throw new ConfigurationException("user must not be null or empty.");

		final String password = context.getString("password", DEFAULT_PASSWORD);
		final String retention = context.getString("retention");

		final Map<String, String> tags = context.getSubProperties("tags.");

		final JvmAgentConfig config = new JvmAgentConfig();
		config.servers = hostAndPorts;
		config.interval = interval;
		config.database = database;
		config.user = user;
		config.password = password;
		config.tags = tags;
		config.retention = retention;
		return config;
	}

	public static JvmAgentConfig fromArgs(final String args) {
		final Map<String, String> result = new HashMap<>();
		for (final String arg : Splitter.on(",").trimResults().split(args)) {
			final List<String> kv = Lists.newArrayList(Splitter.on("=").split(arg));
			if (kv.size() != 2) {
				LOG.info("invalid argment: {}", arg);
				continue;
			}
			result.put(kv.get(0), kv.get(1));
		}
		return fromMap(result);
	}
}
