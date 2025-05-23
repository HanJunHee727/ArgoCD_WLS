// Copyright (c) 2022, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import ch.qos.logback.classic.LoggerContext;
import com.meterware.simplestub.Memento;
import org.slf4j.LoggerFactory;

public class BaseTestUtils {

  protected static boolean isTestLogHandler(Handler handler) {
    return handler instanceof TestLogHandler;
  }

  protected static void rejectCall(Handler handler) {
    throw new IllegalStateException("silenceOperatorLogger may only called once");
  }

  /**
   * Restores the silenced logger handlers.
   *
   * @param logger a logger to restore
   * @param savedHandlers the handlers to restore
   */
  public static void restoreConsoleHandlers(Logger logger, List<Handler> savedHandlers) {
    for (Handler handler : savedHandlers) {
      logger.addHandler(handler);
    }
  }

  public static Memento silenceJsonPathLogger() {
    return new JsonPathLoggerMemento();
  }

  protected abstract static class TestLogHandler extends Handler {
    private static final List<String> ALL_MESSAGES = new ArrayList<>();
    private Throwable throwable;
    private final List<Throwable> ignoredExceptions = new ArrayList<>();
    private final List<Class<? extends Throwable>> ignoredClasses = new ArrayList<>();
    private Collection<LogRecord> logRecords = new ArrayList<>();
    private List<String> messagesToTrack = new ArrayList<>();

    @Override
    public void publish(LogRecord rec) {
      if (rec.getThrown() != null && !shouldIgnore(rec.getThrown())) {
        throwable = rec.getThrown();
      }
      if (shouldTrack(rec)) {
        logRecords.add(rec);
      }
    }

    private boolean shouldTrack(LogRecord rec) {
      return messagesToTrack == ALL_MESSAGES || messagesToTrack.contains(rec.getMessage());
    }

    boolean shouldIgnore(Throwable thrown) {
      return ignoredExceptions.contains(thrown) || ignoredClasses.contains(thrown.getClass());
    }

    void throwLoggedThrowable() {
      if (throwable == null) {
        return;
      }

      throwable.printStackTrace();
      if (throwable instanceof Error) {
        throw (Error) throwable;
      }
      if (throwable instanceof RuntimeException) {
        throw (RuntimeException) throwable;
      }
      throw new RuntimeException(throwable);
    }

    void ignoreLoggedException(Throwable t) {
      ignoredExceptions.add(t);
    }

    void ignoreLoggedException(Class<? extends Throwable> t) {
      ignoredClasses.add(t);
    }

    void collectLogMessages(Collection<LogRecord> collection, String[] messages) {
      this.logRecords = collection;
      this.messagesToTrack = new ArrayList<>();
      this.messagesToTrack.addAll(Arrays.asList(messages));
    }

    void collectAllLogMessages(Collection<LogRecord> collection) {
      this.logRecords = collection;
      this.messagesToTrack = ALL_MESSAGES;
    }

    void throwUncheckedLogMessages() {
      if (logRecords.isEmpty()) {
        return;
      }

      SimpleFormatter formatter = new SimpleFormatter();
      List<String> messageKeys = new ArrayList<>();
      for (LogRecord rec : logRecords) {
        messageKeys.add(formatter.format(rec));
      }

      throw new AssertionError("Unexpected log messages " + messageKeys);
    }
  }

  public static class ConsoleHandlerMemento implements Memento {
    private final Logger logger;
    private final TestLogHandler testHandler;
    private final List<Handler> savedHandlers;
    private Level savedLogLevel;
    // log level could be null, so need a boolean to indicate if we have saved it
    private boolean loggerLevelSaved;

    /**
     *
     * Constructs an instance of ConsoleHandlerMemento.
     */
    public ConsoleHandlerMemento(Logger logger, TestLogHandler testHandler, List<Handler> savedHandlers) {
      this.logger = logger;
      this.testHandler = testHandler;
      this.savedHandlers = savedHandlers;
    }

    /**
     * build with logged exceptions.
     * @param throwables throwables
     * @return memento
     */
    public ConsoleHandlerMemento ignoringLoggedExceptions(Throwable... throwables) {
      for (Throwable throwable : throwables) {
        testHandler.ignoreLoggedException(throwable);
      }
      return this;
    }

    /**
     * build with ignoring logged exceptions.
     * @param classes classes
     * @return memento
     */
    @SafeVarargs
    public final ConsoleHandlerMemento ignoringLoggedExceptions(
        Class<? extends Throwable>... classes) {
      for (Class<? extends Throwable> klass : classes) {
        testHandler.ignoreLoggedException(klass);
      }
      return this;
    }

    /**
     * Specifies the log messages to track during a test suite. To exclude a particular message
     * for a single test, invoke {@link #ignoreMessage(String)}.
     * @param collection a collection into which matching log messages will be recorded
     * @param messages the keys whose log records should be captured
     * @return this memento
     */
    public ConsoleHandlerMemento collectLogMessages(
        Collection<LogRecord> collection, String... messages) {
      testHandler.collectLogMessages(collection, messages);
      return this;
    }

    public ConsoleHandlerMemento collectAllLogMessages(Collection<LogRecord> collection) {
      testHandler.collectAllLogMessages(collection);
      return this;
    }

    /**
     * build with log level.
     * @param logLevel log level
     * @return memento
     */
    public ConsoleHandlerMemento withLogLevel(Level logLevel) {
      if (!loggerLevelSaved) {
        savedLogLevel = logger.getLevel();
        loggerLevelSaved = true;
      }
      logger.setLevel(logLevel);
      return this;
    }

    public void ignoreMessage(String message) {
      testHandler.messagesToTrack.remove(message);
    }

    public void trackMessage(String message) {
      testHandler.messagesToTrack.add(message);
    }

    @Override
    public void revert() {
      logger.removeHandler(testHandler);
      restoreConsoleHandlers(logger, savedHandlers);
      if (loggerLevelSaved) {
        logger.setLevel(savedLogLevel);
      }

      testHandler.throwLoggedThrowable();
      testHandler.throwUncheckedLogMessages();
    }

    @Override
    public <T> T getOriginalValue() {
      throw new UnsupportedOperationException();
    }
  }

  static class JsonPathLoggerMemento implements Memento {

    private final ch.qos.logback.classic.Logger log;
    private final ch.qos.logback.classic.Level originalLogLevel;

    private JsonPathLoggerMemento() {
      LoggerContext logContext = (LoggerContext) LoggerFactory.getILoggerFactory();
      log = logContext.getLogger("com.jayway.jsonpath.internal.path.CompiledPath");
      originalLogLevel = log.getLevel();
      log.setLevel(ch.qos.logback.classic.Level.INFO);
    }

    @Override
    public void revert() {
      log.setLevel(originalLogLevel);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOriginalValue() {
      return (T) originalLogLevel;
    }
  }
}