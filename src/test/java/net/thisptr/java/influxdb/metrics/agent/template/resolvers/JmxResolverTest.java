package net.thisptr.java.influxdb.metrics.agent.template.resolvers;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class JmxResolverTest {
	@Test
	public void testSingle() {
		assertNotNull(new JmxResolver().resolve("java.lang:type=OperatingSystem:Version"));
	}

	@Test
	public void testMuliple() {
		assertNotNull(new JmxResolver().resolve("java.lang:type=MemoryPool,name=*:Name"));
	}
}