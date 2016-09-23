package net.thisptr.java.influxdb.metrics.agent.template.resolvers;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import net.thisptr.java.influxdb.metrics.agent.template.PlaceholderResolver;

import com.google.common.base.Joiner;

public class JmxResolver implements PlaceholderResolver {
	private static final MBeanServer MBEANS = ManagementFactory.getPlatformMBeanServer();

	@Override
	public String resolve(String expr) {
		try {
			return resolveInternal(expr);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String resolveInternal(final String expr) throws MalformedObjectNameException, NullPointerException, AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException {
		final String[] names = expr.split(":");
		if (names.length < 3)
			throw new IllegalArgumentException(expr);
		final String attribute = names[names.length - 1];
		final String domainWithKeyProperties = String.join(":", Arrays.copyOfRange(names, 0, names.length - 1));

		final List<String> values = new ArrayList<>();
		for (final ObjectInstance mbean : MBEANS.queryMBeans(ObjectName.getInstance(domainWithKeyProperties), null)) {
			values.add(MBEANS.getAttribute(mbean.getObjectName(), attribute).toString());
		}
		return Joiner.on(",").join(values);
	}
}
