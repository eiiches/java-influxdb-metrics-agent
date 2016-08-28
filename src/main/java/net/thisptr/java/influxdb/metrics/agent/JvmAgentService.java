package net.thisptr.java.influxdb.metrics.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class JvmAgentService {
	private static final Logger LOG = LoggerFactory.getLogger(JvmAgent.class);

	private final JvmAgentConfig config;
	private volatile ScheduledExecutorService executor;

	private static enum State {
		STARTING, RUNNING, STOPPING, STOPPED
	}

	private final AtomicReference<State> state = new AtomicReference<State>(State.STOPPED);

	public JvmAgentService(final JvmAgentConfig config) {
		this.config = config;
	}

	public void start() {
		if (!state.compareAndSet(State.STOPPED, State.STARTING))
			throw new IllegalStateException("InfluxDB Metrics Agent is expected to be in STOPPED state.");

		LOG.info("Starting InfluxDB Metrics Agent...");
		try {
			final List<InfluxDB> conns = new ArrayList<>(config.servers.size());
			for (final HostAndPort server : config.servers) {
				try {
					final InfluxDB conn = InfluxDBFactory.connect("http://" + server, config.user, config.password);
					conns.add(conn);
				} catch (Exception e) {
					LOG.warn("Failed to connect to InfluxDB server {}.", server, e);
				}
			}
			executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("InfluxDB Metrics Agent %d").setDaemon(true).build());
			executor.scheduleWithFixedDelay(new JvmAgentCommand(conns, config.database, config.tags, config.retention), 0, config.interval, TimeUnit.SECONDS);
			state.set(State.RUNNING);
		} catch (final Throwable th) {
			LOG.error("Failed to start InfluxDB Metrics Agent.", th);
			state.set(State.STOPPED);
			throw th;
		}
	}

	public void stop() {
		if (!state.compareAndSet(State.RUNNING, State.STOPPING))
			throw new IllegalStateException("InfluxDB Metrics Agent is expected to be in RUNNING state.");

		LOG.info("Stopping InfluxDB Metrics Agent...");
		try {
			executor.shutdown();
			while (!executor.isTerminated()) {
				try {
					LOG.info("Waiting for InfluxDB Metrics Agent to stop...");
					executor.awaitTermination(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					LOG.warn("Interrupted while waiting for InfluxDB Metrics Agent to shutdown.", e);
					executor.shutdownNow();
				}
			}
		} finally {
			state.set(State.STOPPED);
		}
	}
}
