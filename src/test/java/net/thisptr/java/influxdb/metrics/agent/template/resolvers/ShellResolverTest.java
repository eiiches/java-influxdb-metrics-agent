package net.thisptr.java.influxdb.metrics.agent.template.resolvers;

import static org.junit.Assert.assertEquals;
import net.thisptr.java.influxdb.metrics.agent.template.resolvers.ShellResolver.CommandExecutionException;
import net.thisptr.java.influxdb.metrics.agent.template.resolvers.ShellResolver.CommandTimeoutException;

import org.junit.Test;

public class ShellResolverTest {
	@Test
	public void test() {
		assertEquals("foo", new ShellResolver().resolve("echo foo"));
		assertEquals("foo bar", new ShellResolver().resolve("echo foo\nbar"));
		assertEquals("", new ShellResolver().resolve("true"));
	}

	@Test(expected = CommandExecutionException.class)
	public void testFail() {
		new ShellResolver().resolve("false");
	}

	@Test(expected = CommandTimeoutException.class)
	public void testTimeout() {
		new ShellResolver(1).resolve("sleep 60");
	}
}
