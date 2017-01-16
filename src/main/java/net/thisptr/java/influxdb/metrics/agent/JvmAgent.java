package net.thisptr.java.influxdb.metrics.agent;

import org.slf4j.Logger;

import com.google.common.base.Strings;

import ch.qos.logback.classic.Level;

public class JvmAgent {
	private static final Logger LOG = LoggerFactory.getLogger(JvmAgent.class);
	static {
		LoggerFactory.configure(Level.INFO, null); // default
	}

	public static void premain(final String args) {
		try {
			final JvmAgentConfig config = JvmAgentConfig.fromString(Strings.nullToEmpty(args));
			LoggerFactory.configure(Level.valueOf(config.logLevel), config.logPath);
			final JvmAgentService service = new JvmAgentService(config);
			service.start();
		} catch (final Throwable th) {
			LOG.error("Failed to start InfluxDB metrics agent.", th);
		}
	}
}
