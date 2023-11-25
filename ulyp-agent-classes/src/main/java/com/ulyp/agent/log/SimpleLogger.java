/**
 * Copyright (c) 2004-2012 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package com.ulyp.agent.log;

import java.io.PrintStream;
import java.util.Date;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.LocationAwareLogger;

import com.ulyp.agent.AgentContext;
import com.ulyp.agent.RecorderInstance;


/**
 * Forked from slf4j-simple
 *
 * Disables any recording when log is issued, primarily because System.out/err might be custom classes which
 * might be instrumented. If recording is started when System.out/err is called and logging is set to INFO or more,
 * there might arise infinite recursion
 */
public class SimpleLogger extends MarkerIgnoringBase {

    private static final long serialVersionUID = -632788891211436180L;

    private static long START_TIME = System.currentTimeMillis();

    protected static final int LOG_LEVEL_TRACE = LocationAwareLogger.TRACE_INT;
    protected static final int LOG_LEVEL_DEBUG = LocationAwareLogger.DEBUG_INT;
    protected static final int LOG_LEVEL_INFO = LocationAwareLogger.INFO_INT;
    protected static final int LOG_LEVEL_WARN = LocationAwareLogger.WARN_INT;
    protected static final int LOG_LEVEL_ERROR = LocationAwareLogger.ERROR_INT;
    // The OFF level can only be used in configuration files to disable logging.
    // It has
    // no printing method associated with it in o.s.Logger interface.
    protected static final int LOG_LEVEL_OFF = LOG_LEVEL_ERROR + 10;

    private static boolean INITIALIZED = false;
    static final private SimpleLoggerConfiguration CONFIG_PARAMS =  new SimpleLoggerConfiguration();

    static void lazyInit() {
        if (INITIALIZED) {
            return;
        }
        INITIALIZED = true;
        init();
    }

    // external software might be invoking this method directly. Do not rename
    // or change its semantics.
    static void init() {
        CONFIG_PARAMS.init();
    }

    /** The current log level */
    protected int currentLogLevel = LOG_LEVEL_INFO;
    /** The short name of this simple log instance */
    private transient String shortLogName = null;

    /**
     * All system properties used by <code>SimpleLogger</code> start with this
     * prefix
     */
    public static final String SYSTEM_PREFIX = "org.slf4j.simpleLogger.";

    public static final String LOG_KEY_PREFIX = SimpleLogger.SYSTEM_PREFIX + "log.";

    public static final String CACHE_OUTPUT_STREAM_STRING_KEY = SimpleLogger.SYSTEM_PREFIX + "cacheOutputStream";

    public static final String WARN_LEVEL_STRING_KEY = SimpleLogger.SYSTEM_PREFIX + "warnLevelString";

    public static final String LEVEL_IN_BRACKETS_KEY = SimpleLogger.SYSTEM_PREFIX + "levelInBrackets";

    public static final String LOG_FILE_KEY = SimpleLogger.SYSTEM_PREFIX + "logFile";

    public static final String SHOW_SHORT_LOG_NAME_KEY = SimpleLogger.SYSTEM_PREFIX + "showShortLogName";

    public static final String SHOW_LOG_NAME_KEY = SimpleLogger.SYSTEM_PREFIX + "showLogName";

    public static final String SHOW_THREAD_NAME_KEY = SimpleLogger.SYSTEM_PREFIX + "showThreadName";

    public static final String DATE_TIME_FORMAT_KEY = SimpleLogger.SYSTEM_PREFIX + "dateTimeFormat";

    public static final String SHOW_DATE_TIME_KEY = SimpleLogger.SYSTEM_PREFIX + "showDateTime";

    public static final String DEFAULT_LOG_LEVEL_KEY = SimpleLogger.SYSTEM_PREFIX + "defaultLogLevel";

    /**
     * Package access allows only {@link SimpleLoggerFactory} to instantiate
     * SimpleLogger instances.
     */
    SimpleLogger(String name) {
        this.name = name;

        String levelString = recursivelyComputeLevelString();
        if (levelString != null) {
            this.currentLogLevel = SimpleLoggerConfiguration.stringToLevel(levelString);
        } else {
            this.currentLogLevel = CONFIG_PARAMS.defaultLogLevel;
        }
    }

    String recursivelyComputeLevelString() {
        String tempName = name;
        String levelString = null;
        int indexOfLastDot = tempName.length();
        while ((levelString == null) && (indexOfLastDot > -1)) {
            tempName = tempName.substring(0, indexOfLastDot);
            levelString = CONFIG_PARAMS.getStringProperty(SimpleLogger.LOG_KEY_PREFIX + tempName, null);
            indexOfLastDot = String.valueOf(tempName).lastIndexOf(".");
        }
        return levelString;
    }

    private void log(int level, String message, Throwable t) {
        if (!isLevelEnabled(level)) {
            return;
        }
        if (AgentContext.isLoaded()) {
            // only for agent runtime
            RecorderInstance.instance.disableRecording();
        }
        try {
            StringBuilder buf = new StringBuilder(32);

            // Append date-time if so configured
            if (CONFIG_PARAMS.showDateTime) {
                if (CONFIG_PARAMS.dateFormatter != null) {
                    buf.append(getFormattedDate());
                    buf.append(' ');
                } else {
                    buf.append(System.currentTimeMillis() - START_TIME);
                    buf.append(' ');
                }
            }

            // Append current thread name if so configured
            if (CONFIG_PARAMS.showThreadName) {
                buf.append('[');
                buf.append(Thread.currentThread().getName());
                buf.append("] ");
            }

            if (CONFIG_PARAMS.levelInBrackets)
                buf.append('[');

            // Append a readable representation of the log level
            String levelStr = renderLevel(level);
            buf.append(levelStr);
            if (CONFIG_PARAMS.levelInBrackets)
                buf.append(']');
            buf.append(' ');

            // Append the name of the log instance if so configured
            if (CONFIG_PARAMS.showShortLogName) {
                if (shortLogName == null)
                    shortLogName = computeShortName();
                buf.append(String.valueOf(shortLogName)).append(" - ");
            } else if (CONFIG_PARAMS.showLogName) {
                buf.append(String.valueOf(name)).append(" - ");
            }

            // Append the message
            buf.append(message);

            write(buf, t);
        } finally {
            if (AgentContext.isLoaded()) {
                // only for agent runtime
                RecorderInstance.instance.enableRecording();
            }
        }
    }

    protected String renderLevel(int level) {
        switch (level) {
        case LOG_LEVEL_TRACE:
            return "TRACE";
        case LOG_LEVEL_DEBUG:
            return ("DEBUG");
        case LOG_LEVEL_INFO:
            return "INFO";
        case LOG_LEVEL_WARN:
            return CONFIG_PARAMS.warnLevelString;
        case LOG_LEVEL_ERROR:
            return "ERROR";
        }
        throw new IllegalStateException("Unrecognized level [" + level + "]");
    }

    /**
     * To avoid intermingling of log messages and associated stack traces, the two
     * operations are done in a synchronized block.
     * 
     * @param buf
     * @param t
     */
    void write(StringBuilder buf, Throwable t) {
        PrintStream targetStream = CONFIG_PARAMS.outputChoice.getTargetPrintStream();

        synchronized (CONFIG_PARAMS) {
            targetStream.println(buf.toString());
            writeThrowable(t, targetStream);
            targetStream.flush();
        }

    }

    protected void writeThrowable(Throwable t, PrintStream targetStream) {
        if (t != null) {
            t.printStackTrace(targetStream);
        }
    }

    private String getFormattedDate() {
        Date now = new Date();
        String dateText;
        synchronized (CONFIG_PARAMS.dateFormatter) {
            dateText = CONFIG_PARAMS.dateFormatter.format(now);
        }
        return dateText;
    }

    private String computeShortName() {
        return name.substring(name.lastIndexOf(".") + 1);
    }

    /**
     * For formatted messages, first substitute arguments and then log.
     *
     * @param level
     * @param format
     * @param arg1
     * @param arg2
     */
    private void formatAndLog(int level, String format, Object arg1, Object arg2) {
        if (!isLevelEnabled(level)) {
            return;
        }
        FormattingTuple tp = MessageFormatter.format(format, arg1, arg2);
        log(level, tp.getMessage(), tp.getThrowable());
    }

    /**
     * For formatted messages, first substitute arguments and then log.
     *
     * @param level
     * @param format
     * @param arguments
     *            a list of 3 ore more arguments
     */
    private void formatAndLog(int level, String format, Object... arguments) {
        if (!isLevelEnabled(level)) {
            return;
        }
        FormattingTuple tp = MessageFormatter.arrayFormat(format, arguments);
        log(level, tp.getMessage(), tp.getThrowable());
    }

    /**
     * Is the given log level currently enabled?
     *
     * @param logLevel
     *            is this level enabled?
     */
    protected boolean isLevelEnabled(int logLevel) {
        // log level are numerically ordered so can use simple numeric
        // comparison
        return (logLevel >= currentLogLevel);
    }

    /** Are {@code trace} messages currently enabled? */
    public boolean isTraceEnabled() {
        return isLevelEnabled(LOG_LEVEL_TRACE);
    }

    /**
     * A simple implementation which logs messages of level TRACE according to
     * the format outlined above.
     */
    public void trace(String msg) {
        log(LOG_LEVEL_TRACE, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * TRACE according to the format outlined above.
     */
    public void trace(String format, Object param1) {
        formatAndLog(LOG_LEVEL_TRACE, format, param1, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * TRACE according to the format outlined above.
     */
    public void trace(String format, Object param1, Object param2) {
        formatAndLog(LOG_LEVEL_TRACE, format, param1, param2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * TRACE according to the format outlined above.
     */
    public void trace(String format, Object... argArray) {
        formatAndLog(LOG_LEVEL_TRACE, format, argArray);
    }

    /** Log a message of level TRACE, including an exception. */
    public void trace(String msg, Throwable t) {
        log(LOG_LEVEL_TRACE, msg, t);
    }

    /** Are {@code debug} messages currently enabled? */
    public boolean isDebugEnabled() {
        return isLevelEnabled(LOG_LEVEL_DEBUG);
    }

    /**
     * A simple implementation which logs messages of level DEBUG according to
     * the format outlined above.
     */
    public void debug(String msg) {
        log(LOG_LEVEL_DEBUG, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * DEBUG according to the format outlined above.
     */
    public void debug(String format, Object param1) {
        formatAndLog(LOG_LEVEL_DEBUG, format, param1, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * DEBUG according to the format outlined above.
     */
    public void debug(String format, Object param1, Object param2) {
        formatAndLog(LOG_LEVEL_DEBUG, format, param1, param2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * DEBUG according to the format outlined above.
     */
    public void debug(String format, Object... argArray) {
        formatAndLog(LOG_LEVEL_DEBUG, format, argArray);
    }

    /** Log a message of level DEBUG, including an exception. */
    public void debug(String msg, Throwable t) {
        log(LOG_LEVEL_DEBUG, msg, t);
    }

    /** Are {@code info} messages currently enabled? */
    public boolean isInfoEnabled() {
        return isLevelEnabled(LOG_LEVEL_INFO);
    }

    /**
     * A simple implementation which logs messages of level INFO according to
     * the format outlined above.
     */
    public void info(String msg) {
        log(LOG_LEVEL_INFO, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * INFO according to the format outlined above.
     */
    public void info(String format, Object arg) {
        formatAndLog(LOG_LEVEL_INFO, format, arg, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * INFO according to the format outlined above.
     */
    public void info(String format, Object arg1, Object arg2) {
        formatAndLog(LOG_LEVEL_INFO, format, arg1, arg2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * INFO according to the format outlined above.
     */
    public void info(String format, Object... argArray) {
        formatAndLog(LOG_LEVEL_INFO, format, argArray);
    }

    /** Log a message of level INFO, including an exception. */
    public void info(String msg, Throwable t) {
        log(LOG_LEVEL_INFO, msg, t);
    }

    /** Are {@code warn} messages currently enabled? */
    public boolean isWarnEnabled() {
        return isLevelEnabled(LOG_LEVEL_WARN);
    }

    /**
     * A simple implementation which always logs messages of level WARN
     * according to the format outlined above.
     */
    public void warn(String msg) {
        log(LOG_LEVEL_WARN, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * WARN according to the format outlined above.
     */
    public void warn(String format, Object arg) {
        formatAndLog(LOG_LEVEL_WARN, format, arg, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * WARN according to the format outlined above.
     */
    public void warn(String format, Object arg1, Object arg2) {
        formatAndLog(LOG_LEVEL_WARN, format, arg1, arg2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * WARN according to the format outlined above.
     */
    public void warn(String format, Object... argArray) {
        formatAndLog(LOG_LEVEL_WARN, format, argArray);
    }

    /** Log a message of level WARN, including an exception. */
    public void warn(String msg, Throwable t) {
        log(LOG_LEVEL_WARN, msg, t);
    }

    /** Are {@code error} messages currently enabled? */
    public boolean isErrorEnabled() {
        return isLevelEnabled(LOG_LEVEL_ERROR);
    }

    /**
     * A simple implementation which always logs messages of level ERROR
     * according to the format outlined above.
     */
    public void error(String msg) {
        log(LOG_LEVEL_ERROR, msg, null);
    }

    /**
     * Perform single parameter substitution before logging the message of level
     * ERROR according to the format outlined above.
     */
    public void error(String format, Object arg) {
        formatAndLog(LOG_LEVEL_ERROR, format, arg, null);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * ERROR according to the format outlined above.
     */
    public void error(String format, Object arg1, Object arg2) {
        formatAndLog(LOG_LEVEL_ERROR, format, arg1, arg2);
    }

    /**
     * Perform double parameter substitution before logging the message of level
     * ERROR according to the format outlined above.
     */
    public void error(String format, Object... argArray) {
        formatAndLog(LOG_LEVEL_ERROR, format, argArray);
    }

    /** Log a message of level ERROR, including an exception. */
    public void error(String msg, Throwable t) {
        log(LOG_LEVEL_ERROR, msg, t);
    }

    public void setCurrentLogLeve(int level) {
        this.currentLogLevel = level;
    }
}
