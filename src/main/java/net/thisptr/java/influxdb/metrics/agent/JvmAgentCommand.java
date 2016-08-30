package net.thisptr.java.influxdb.metrics.agent;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class JvmAgentCommand implements Runnable {
	private static final Logger LOG = LoggerFactory.getLogger(JvmAgentCommand.class);
	private static final MBeanServer MBEANS = ManagementFactory.getPlatformMBeanServer();

	private final List<InfluxDB> conns;
	private final JvmAgentConfig config;

	public JvmAgentCommand(final List<InfluxDB> conns, final JvmAgentConfig config) {
		this.conns = conns;
		this.config = config;
	}

	public static enum SupportedType {
		NUMBER(Number.class, (builder, name, value) -> {
			builder.addField(name, (Number) value);
			return 1;
		}),
		STRING(String.class, (builder, name, value) -> {
			builder.addField(name, (String) value);
			return 1;
		}),
		DOUBLE(Double.class, (builder, name, value) -> {
			builder.addField(name, ((Double) value).doubleValue());
			return 1;
		}),
		FLOAT(Float.class, (builder, name, value) -> {
			builder.addField(name, ((Float) value).doubleValue());
			return 1;
		}),
		LONG(Long.class, (builder, name, value) -> {
			builder.addField(name, ((Long) value).longValue());
			return 1;
		}),
		INTEGER(Integer.class, (builder, name, value) -> {
			builder.addField(name, ((Integer) value).longValue());
			return 1;
		}),
		SHORT(Short.class, (builder, name, value) -> {
			builder.addField(name, ((Short) value).longValue());
			return 1;
		}),
		BYTE(Byte.class, (builder, name, value) -> {
			builder.addField(name, ((Byte) value).longValue());
			return 1;
		}),
		BOOLEAN(Boolean.class, (builder, name, value) -> {
			builder.addField(name, ((Boolean) value).booleanValue());
			return 1;
		}),
		CHARACTER(Character.class, (builder, name, value) -> {
			builder.addField(name, ((Character) value).toString());
			return 1;
		}),
		COMPOSITE(CompositeData.class, (builder, name, composite) -> {
			int fields = 0;
			final CompositeData data = (CompositeData) composite;
			for (final String key : data.getCompositeType().keySet()) {
				final Object value = data.get(key);
				if (value == null)
					continue;
				JvmAgentCommand.SupportedType stype = SupportedType.find(value.getClass());
				if (stype == null) {
					LOG.debug("Composite {} contains value of unsupported type {}.", name + "." + key, value.getClass().getName());
					continue;
				}
				fields += stype.action.add(builder, name + "." + key, value);
			}
			return fields;
		});

		public interface Action {
			int add(final Point.Builder builder, final String name, final Object value);
		}

		public final Class<?> clazz;
		public final SupportedType.Action action;

		private SupportedType(final Class<?> clazz, SupportedType.Action action) {
			this.clazz = clazz;
			this.action = action;
		}

		public static JvmAgentCommand.SupportedType find(final Class<?> clazz) {
			for (final JvmAgentCommand.SupportedType type : SupportedType.values()) {
				if (type.clazz.isAssignableFrom(clazz))
					return type;
			}
			return null;
		}

		public static JvmAgentCommand.SupportedType find(final String className) {
			switch (className) {
				case "double":
					return DOUBLE;
				case "float":
					return FLOAT;
				case "long":
					return LONG;
				case "short":
					return SHORT;
				case "int":
					return INTEGER;
				case "byte":
					return BYTE;
				case "boolean":
					return BOOLEAN;
				case "char":
					return CHARACTER;
			}
			try {
				final Class<?> clazz = Class.forName(className);
				return find(clazz);
			} catch (ClassNotFoundException e) {
				return null;
			}
		}
	}

	public static final String CONFIG_KEY_NAMEKEYS = "namekeys";
	public static final String CONFIG_KEY_EXCLUDE = "exclude";

	private static List<String> configNamekeys(final Map<String, String> config) {
		final String namekeys = config.get(CONFIG_KEY_NAMEKEYS);
		if (Strings.isNullOrEmpty(namekeys))
			return Collections.emptyList();
		return Lists.newArrayList(Splitter.on(",").trimResults().split(namekeys));
	}

	private static boolean configExclude(final Map<String, String> config) {
		final String exclude = config.get(CONFIG_KEY_EXCLUDE);
		if (Strings.isNullOrEmpty(exclude))
			return false;
		return Boolean.parseBoolean(exclude);
	}

	@Override
	public void run() {
		LOG.debug("Collecting metrics to InfluxDB...");
		final long startTime = System.currentTimeMillis();
		try {
			final BatchPoints.Builder batchPoints = BatchPoints.database(config.database);
			if (!Strings.isNullOrEmpty(config.retention))
				batchPoints.retentionPolicy(config.retention);
			config.tags.forEach((name, value) -> {
				if (Strings.isNullOrEmpty(value)) {
					LOG.debug("Omitted a tag {}, as its value is empty.", name);
					return;
				}
				batchPoints.tag(name, value);
			});
			int batchPointSize = 0;

			for (final ObjectInstance instance : MBEANS.queryMBeans(null, null)) {
				final ObjectName oname = instance.getObjectName();
				final Map<String, String> metricConfig = config.getConfigForMetric(oname, null);
				if (configExclude(metricConfig))
					continue;

				String sep = ":";
				final StringBuilder measurementName = new StringBuilder(oname.getDomain());
				final Map<String, String> measurementTags = oname.getKeyPropertyList();
				for (final String measurementKey : configNamekeys(metricConfig)) {
					final String value = measurementTags.remove(measurementKey);
					if (value != null) {
						measurementName.append(sep);
						measurementName.append(measurementKey);
						measurementName.append('=');
						measurementName.append(value);
					}
					sep = ",";
				}

				final Point.Builder point = Point.measurement(measurementName.toString())
						.tag(measurementTags);

				int fields = 0;
				for (final MBeanAttributeInfo attr : MBEANS.getMBeanInfo(oname).getAttributes()) {
					final Map<String, String> attrConfig = config.getConfigForMetric(oname, attr.getName());
					if (configExclude(attrConfig))
						continue;

					final String typeName = attr.getType();
					if (typeName == null) {
						LOG.debug("Ignored null type attribute {} in {}", attr.getName(), oname);
						continue;
					}
					final JvmAgentCommand.SupportedType type = SupportedType.find(typeName);
					if (type == null) {
						LOG.debug("Ignored unsupported type attribute {} in {} (type = {})", attr.getName(), oname, attr.getType());
						continue;
					}

					final Object value;
					try {
						value = MBEANS.getAttribute(oname, attr.getName());
						if (value == null)
							continue;
					} catch (Exception e) {
						LOG.debug("Failed to get value of the attribute " + attr.getName() + " in " + oname, e);
						continue;
					}

					try {
						fields += type.action.add(point, attr.getName(), value);
					} catch (Exception e) {
						LOG.debug("Failed to process value of attribute " + attr.getName() + " in " + oname, e);
					}
				}

				// Skip points with no field. InfluxDB cannot ingest such data.
				if (fields == 0)
					continue;

				batchPoints.point(point.build());
				++batchPointSize;
			}

			for (final InfluxDB conn : conns) {
				try {
					conn.createDatabase(config.database);
				} catch (Exception e) {
					LOG.warn("Failed to create a database " + config.database + " on {}.", conn, e);
					continue;
				}
				try {
					conn.write(batchPoints.build());
				} catch (Exception e) {
					LOG.warn("Sending metrics to {} failed.", conn, e);
				}
			}

			LOG.debug("Collected and sent {} batch points to InfluxDB in {} ms.", batchPointSize, System.currentTimeMillis() - startTime);
		} catch (Throwable th) {
			LOG.error("Unknown error", th);
		}
	}
}