package net.thisptr.java.influxdb.metrics.agent.parser;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class ConfigurationParserTest {
	@Test
	public void test() throws ParseException, IOException {
		for (int i = 0; i < 100; ++i) {
			final String path = String.format("confs/%03d.properties", i + 1);
			try (final InputStream is = ConfigurationParserTest.class.getClassLoader().getResourceAsStream(path)) {
				if (is == null)
					continue;
				System.out.printf("%n");
				System.out.printf("=============================================================================================%n");
				System.out.printf("Parsing %s.%n", path);
				final ConfigurationParser parser = new ConfigurationParser(is);
				parser.Start();
			}
		}
	}

	@Test
	public void testBad() throws ParseException, IOException {
		for (int i = 0; i < 100; ++i) {
			final String path = String.format("confs/%03d.bad.properties", i + 1);
			try (final InputStream is = ConfigurationParserTest.class.getClassLoader().getResourceAsStream(path)) {
				if (is == null)
					continue;
				System.out.printf("%n");
				System.out.printf("=============================================================================================%n");
				System.out.printf("Parsing %s.%n", path);
				try {
					final ConfigurationParser parser = new ConfigurationParser(is);
					parser.Start();
				} catch (Throwable e) {
					// ok
					continue;
				}
				fail("must fail: " + path);
			}
		}
	}
}