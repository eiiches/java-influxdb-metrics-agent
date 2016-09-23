package net.thisptr.java.influxdb.metrics.agent.template.resolvers;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import net.thisptr.java.influxdb.metrics.agent.template.PlaceholderResolver;

import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;

public class ShellResolver implements PlaceholderResolver {
	public static class CommandTimeoutException extends RuntimeException {
		private static final long serialVersionUID = -7775070005207287558L;
		private final String command;

		public CommandTimeoutException(final String command) {
			this.command = command;
		}

		@Override
		public String getMessage() {
			return String.format("Command timed out: %s", command);
		}
	}

	public static class CommandExecutionException extends RuntimeException {
		private static final long serialVersionUID = -4796891652175732414L;
		private String command;
		private int exitCode;
		private String outputText;
		private String errorText;

		public CommandExecutionException(final String command, final int exitCode, final String outputText, final String errorText) {
			this.command = command;
			this.exitCode = exitCode;
			this.outputText = outputText;
			this.errorText = errorText;
		}

		@Override
		public String getMessage() {
			return String.format("Command failed (exit code = %d): %s\nstdout:\n%s\nstderr:\n%s\n", exitCode, command, outputText, errorText);
		}
	}

	private static final int DEFAULT_TIMEOUT = 5;
	private int timeout;

	public ShellResolver() {
		this(DEFAULT_TIMEOUT);
	}

	public ShellResolver(final int timeout) {
		this.timeout = timeout;
	}

	private String resolveInternal(final String expr) throws IOException, InterruptedException {
		final Process p = Runtime.getRuntime().exec(expr);
		try {
			if (!p.waitFor(timeout, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				throw new CommandTimeoutException(expr);
			}

			if (p.exitValue() != 0) {
				final String stdout = CharStreams.toString(new InputStreamReader(p.getInputStream()));
				final String stderr = CharStreams.toString(new InputStreamReader(p.getErrorStream()));
				throw new CommandExecutionException(expr, p.exitValue(), stdout, stderr);
			}

			return CharStreams.toString(new InputStreamReader(p.getInputStream())).trim().replace('\n', ' ');
		} finally {
			Closeables.closeQuietly(p.getInputStream());
			Closeables.closeQuietly(p.getErrorStream());
			Closeables.close(p.getOutputStream(), true);
		}
	}

	@Override
	public String resolve(final String expr) {
		try {
			return resolveInternal(expr);
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}