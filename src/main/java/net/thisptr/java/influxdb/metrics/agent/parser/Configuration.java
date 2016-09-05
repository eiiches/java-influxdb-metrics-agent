package net.thisptr.java.influxdb.metrics.agent.parser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.management.ObjectName;

public class Configuration {
	public static class Property implements Element {
		public final String key;
		public final String value;

		public Property(String key, String value) {
			this.key = key;
			this.value = value;
		}
	}

	public static class MetricPattern {
		public final Pattern domain;
		public final Map<String, Pattern> keys;
		public final Pattern attribute;

		public MetricPattern(final String domain, final Map<String, String> keys, final String attribute) {
			this.domain = domain != null ? Pattern.compile(domain) : null;
			final Map<String, Pattern> tmp = new LinkedHashMap<>();
			keys.forEach((k, v) -> {
				tmp.put(k, Pattern.compile(v));
			});
			this.keys = Collections.unmodifiableMap(tmp);
			this.attribute = attribute != null ? Pattern.compile(attribute) : null;
		}

		public boolean matches(final ObjectName name, final String attribute) {
			if (domain != null && !domain.matcher(name.getDomain()).matches())
				return false;

			final Map<String, String> target = name.getKeyPropertyList();
			for (final Map.Entry<String, Pattern> patternEntry : keys.entrySet()) {
				final String targetValue = target.get(patternEntry.getKey());
				if (targetValue == null || !patternEntry.getValue().matcher(targetValue).matches())
					return false;
			}

			if (this.attribute != null && !this.attribute.matcher(attribute).matches())
				return false;

			return true;
		}
	}

	public static class MatcherBlock implements Element {
		public final MetricPattern pattern;
		public final List<Property> properties;

		public MatcherBlock(final MetricPattern pattern, final List<Property> properties) {
			this.pattern = pattern;
			this.properties = Collections.unmodifiableList(new ArrayList<>(properties));
		}
	}

	public static interface Element {
	}

	public static class Import implements Element {
		public final String path;

		public Import(String path) {
			this.path = path;
		}
	}

	public final List<Element> elements;

	public Configuration(final List<Element> elements) {
		this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
	}

	public static class ResolvedConfiguration {
		public final Map<String, String> properties;
		public final List<Configuration.MatcherBlock> matchers;

		public ResolvedConfiguration(final Map<String, String> properties, final List<MatcherBlock> matchers) {
			this.properties = Collections.unmodifiableMap(new HashMap<>(properties));
			this.matchers = Collections.unmodifiableList(new ArrayList<>(matchers));
		}
	}

	private static void recursiveResolveImports(final List<Configuration.Element> resolved, final File cwd, final List<Configuration.Element> queue) throws Exception {
		for (final Configuration.Element element : queue) {
			if (!(element instanceof Configuration.Import)) {
				resolved.add(element);
				continue;
			}
			final String path = ((Configuration.Import) element).path;
			final File f = cwd == null ? new File(path) : new File(cwd, path);
			try (final InputStream is = new BufferedInputStream(new FileInputStream(f))) {
				final Configuration sub = new ConfigurationParser(is).Start();
				recursiveResolveImports(resolved, f.getParentFile(), sub.elements);
			}
		}
	}

	public ResolvedConfiguration resolve() {
		try {
			final List<Configuration.Element> resolved = new ArrayList<>();
			recursiveResolveImports(resolved, null, elements);

			final Map<String, String> properties = new HashMap<>();
			resolved.forEach(element -> {
				if (!(element instanceof Configuration.Property))
					return;
				final Configuration.Property property = (Configuration.Property) element;
				properties.putIfAbsent(property.key, property.value);
			});

			final List<MatcherBlock> matchers = new ArrayList<>();
			resolved.forEach(element -> {
				if (!(element instanceof Configuration.MatcherBlock))
					return;
				final Configuration.MatcherBlock matcher = (Configuration.MatcherBlock) element;
				matchers.add(matcher);
			});
			return new ResolvedConfiguration(properties, matchers);
		} catch (Throwable th) {
			throw new ConfigurationException(th);
		}
	}
}
