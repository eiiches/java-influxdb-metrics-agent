package net.thisptr.java.influxdb.metrics.agent.template;

public interface PlaceholderResolver {
	String resolve(String expr);
}
