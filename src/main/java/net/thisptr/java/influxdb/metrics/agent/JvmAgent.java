package net.thisptr.java.influxdb.metrics.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JvmAgent {
	private static final Logger LOG = LoggerFactory.getLogger(JvmAgent.class);

	public static void premain(final String args) {
		try {
			final JvmAgentConfig config = JvmAgentConfig.fromString(args);
			final JvmAgentService service = new JvmAgentService(config);
			service.start();
		} catch (final Throwable th) {
			LOG.error("Failed to start InfluxDB metrics agent.", th);
		}
	}
}
