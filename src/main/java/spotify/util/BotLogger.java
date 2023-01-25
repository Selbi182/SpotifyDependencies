package spotify.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BotLogger {
	private enum Level {
		DEBUG, INFO, WARNING, ERROR
	}

	private final static String LOG_FILE_PATH = "./log.txt";
	private final static int DEFAULT_LOG_READ_LINES = 50;

	private final static int MAX_LINE_LENGTH = 160;
	private final static String ELLIPSIS = "...";
	private final static String LINE_SYMBOL = "-";

	private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private final Logger log = LoggerFactory.getLogger(BotLogger.class);

	//////////////////////
	// Base Logging

	/**
	 * Log a debug message
	 *
	 * @param message the message to log
	 */
	public void debug(String message) {
		logAtLevel(message, Level.DEBUG);
	}

	/**
	 * Log an info message
	 *
	 * @param message the message to log
	 */
	public void info(String message) {
		logAtLevel(message, Level.INFO);
	}

	/**
	 * Log a warning message
	 *
	 * @param message the message to log
	 */
	public void warning(String message) {
		logAtLevel(message, Level.WARNING);
	}

	/**
	 * Log an error message
	 *
	 * @param message the message to log
	 */
	public void error(String message) {
		logAtLevel(message, Level.ERROR);
	}

	/**
	 * Log a message at the given log level (truncate enabled)
	 *
	 * @param msg the message to log
	 * @param level the level to log at
	 */
	public void logAtLevel(String msg, Level level) {
		logAtLevel(msg, level, true);
	}

	/**
	 * Log a message at the given log level (truncation optional). Also writes to an
	 * external log.txt file.
	 *
	 * @param msg the message to log
	 * @param level the level to log at
	 * @param truncate whether to truncate this message or not
	 */
	public void logAtLevel(String msg, Level level, boolean truncate) {
		if (truncate) {
			msg = truncateToEllipsis(msg);
		}
		switch (level) {
			case DEBUG:
				log.debug(msg);
				break;
			case INFO:
				log.info(msg);
				break;
			case WARNING:
				log.warn(msg);
				break;
			case ERROR:
				log.error(msg);
				break;
		}
		try {
			writeToExternalLog(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Print a line of hyphens (----) as INFO-level log message
	 */
	public void printLine() {
		info(Strings.repeat(LINE_SYMBOL, MAX_LINE_LENGTH - ELLIPSIS.length()));
	}

	/**
	 * Chop off the message if it exceeds the maximum line length of 160 characters
	 * 
	 * @param message the message
	 * @return the truncated message
	 */
	private String truncateToEllipsis(String message) {
		if (message.length() <= MAX_LINE_LENGTH - ELLIPSIS.length()) {
			return message;
		}
		return message.substring(0, message.length() - ELLIPSIS.length()) + ELLIPSIS;
	}

	///////////////////////

	public boolean clearLog() {
		File logFile = new File(LOG_FILE_PATH);
		return logFile.delete();
	}

	private void writeToExternalLog(String message) throws IOException {
		File logFile = new File(LOG_FILE_PATH);
		if (!logFile.exists()) {
			if (!logFile.createNewFile()) {
				throw new IOException("Couldn't create log file");
			}
		}
		String logMessage = String.format("[%s] %s", DATE_FORMAT.format(Date.from(Instant.now())), message);
		if (logFile.canWrite()) {
			BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true));
			bw.write(logMessage);
			bw.write('\n');
			bw.close();
		} else {
			throw new IOException("Log file is currently locked, likely because it is being written to. Try again.");
		}
	}

	/**
	 * Return the content of the default log file (<code>./spring.log</code>).
	 * 
	 * @param limit (optional) maximum number of lines to read from the top of the
	 *              log (default: 50); Use -1 to read the entire file
	 * @return a list of strings representing a line of logging
	 * @throws IOException on a read error
	 */
	public List<String> readLog(Integer limit) throws IOException {
		File logFile = new File(LOG_FILE_PATH);
		if (logFile.exists()) {
			if (logFile.canRead()) {
				if (limit == null) {
					limit = DEFAULT_LOG_READ_LINES;
				} else if (limit < 0) {
					limit = Integer.MAX_VALUE;
				}
				try {
					List<String> logFileLines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
					return logFileLines.subList(Math.max(0, logFileLines.size() - limit), logFileLines.size());
				} catch (IOException e) {
					throw new IOException("Failed to read log file (malformed encoding?): " + e);
				}
			} else {
				throw new IOException("Log file is currently locked, likely because it is being written to. Try again.");
			}
		} else {
			throw new IOException("Couldn't find log file under expected location " + logFile.getAbsolutePath());
		}
	}

	//////////////////////

	/**
	 * Log and print the given exception's stack trace
	 * 
	 * @param e the exception
	 */
	public void stackTrace(Exception e) {
		StringWriter stringWriter = new StringWriter();
		try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
			e.printStackTrace(printWriter);
		}
		log.error(stringWriter.toString());
	}
}
