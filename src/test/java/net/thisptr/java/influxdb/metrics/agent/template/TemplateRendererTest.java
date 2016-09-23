package net.thisptr.java.influxdb.metrics.agent.template;

import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class TemplateRendererTest {
	@Test
	public void testJmx() {
		final String text = "${jmx|java.lang:type=OperatingSystem:Version}";
		final String value = new TemplateRenderer().render(text);
		assertNotEquals(value, text);
	}

	@Test
	public void testSh() {
		final String text = "${sh|hostname -f}";
		final String value = new TemplateRenderer().render(text);
		assertNotEquals(value, text);
	}
}
