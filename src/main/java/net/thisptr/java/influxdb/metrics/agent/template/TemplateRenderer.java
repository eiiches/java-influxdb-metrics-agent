package net.thisptr.java.influxdb.metrics.agent.template;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.thisptr.java.influxdb.metrics.agent.template.resolvers.JmxResolver;
import net.thisptr.java.influxdb.metrics.agent.template.resolvers.ShellResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TemplateRenderer {
	private static final Logger LOG = LoggerFactory.getLogger(TemplateRenderer.class);

	// e.g.
	// - ${jmx|com.example.app:type=Status:in_service}
	// - ${sh|hostname -f}
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{\\s*([a-zA-Z_]+)\\s*\\|\\s*((\\\\\\}|[^}])+)\\s*\\}");

	private final Map<String, PlaceholderResolver> resolvers = new HashMap<>();
	{
		resolvers.put("sh", new ShellResolver());
		resolvers.put("jmx", new JmxResolver());
	}

	public String render(final String text) {
		final StringBuffer result = new StringBuffer();
		final Matcher m = PLACEHOLDER.matcher(text);
		while (m.find()) {
			final String type = m.group(1).toLowerCase();
			final String expr = m.group(2);
			final PlaceholderResolver resolver = resolvers.get(type);
			if (resolver == null) {
				LOG.warn("Placeholder resolver '{}' does not exist: {}", type, m.group());
				m.appendReplacement(result, expr);
			} else {
				try {
					final String value = resolver.resolve(expr);
					m.appendReplacement(result, value);
				} catch (Exception e) {
					LOG.warn("Failed to resolve placeholder: {}", m.group(), e);
					m.appendReplacement(result, expr);
				}
			}
		}
		m.appendTail(result);
		return result.toString();
	}
}
