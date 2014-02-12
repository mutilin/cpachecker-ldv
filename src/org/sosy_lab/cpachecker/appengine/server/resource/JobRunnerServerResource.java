/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.appengine.server.resource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.restlet.data.Form;
import org.restlet.engine.header.Header;
import org.restlet.ext.wadl.WadlServerResource;
import org.restlet.representation.Representation;
import org.restlet.util.Series;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.concurrency.Threads;
import org.sosy_lab.common.configuration.AbstractConfigurationBuilderFactory;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.converters.FileTypeConverter;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.common.log.FileLogFormatter;
import org.sosy_lab.cpachecker.appengine.common.GAEConfigurationBuilder;
import org.sosy_lab.cpachecker.appengine.common.JobMappingThreadFactory;
import org.sosy_lab.cpachecker.appengine.dao.JobDAO;
import org.sosy_lab.cpachecker.appengine.entity.DefaultOptions;
import org.sosy_lab.cpachecker.appengine.entity.Job;
import org.sosy_lab.cpachecker.appengine.entity.Job.Status;
import org.sosy_lab.cpachecker.appengine.io.GAEPathFactory;
import org.sosy_lab.cpachecker.appengine.log.GAELogHandler;
import org.sosy_lab.cpachecker.appengine.log.GAELogManager;
import org.sosy_lab.cpachecker.appengine.server.common.JobRunnerResource;
import org.sosy_lab.cpachecker.core.CPAchecker;
import org.sosy_lab.cpachecker.core.CPAcheckerResult;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.ShutdownNotifier.ShutdownRequestListener;
import org.sosy_lab.cpachecker.util.resources.ResourceLimitChecker;

import com.google.appengine.api.LifecycleManager;
import com.google.appengine.api.LifecycleManager.ShutdownHook;
import com.google.apphosting.api.ApiProxy;
import com.google.common.base.Charsets;
import com.google.common.io.FileWriteMode;


public class JobRunnerServerResource extends WadlServerResource implements JobRunnerResource {

  private Job job;
  private Level logLevel;
  private Path errorPath;
  private Configuration config;
  private LogManager logManager;
  private GAELogHandler logHandler;
  private Thread cpaCheckerThread;
  private volatile CPAcheckerResult result;

  private boolean configDumped = false;
  private boolean statsDumped = false;
  private boolean logDumped = false;

  private boolean shutdownComplete = false;

  @Override
  public void runJob(Representation entity) throws Exception {
    Form requestValues = new Form(entity);
    job = JobDAO.load(requestValues.getFirstValue("jobKey"));

    if (job == null) { return; }

    JobMappingThreadFactory.registerJobWithThread(job, Thread.currentThread());
    Threads.setThreadFactory(new JobMappingThreadFactory());

    Paths.setFactory(new GAEPathFactory(JobMappingThreadFactory.getMap()));
    errorPath = Paths.get(ERROR_FILE_NAME);

    @SuppressWarnings("unchecked")
    Series<Header> headers = (Series<Header>) getRequestAttributes().get("org.restlet.http.headers");
    int retries = Integer.valueOf(headers.getFirstValue("X-AppEngine-TaskRetryCount"));
    JobDAO.reset(job); // clear for case of retry
    job.setRetries(retries);
    job.setRequestID((String) ApiProxy.getCurrentEnvironment().getAttributes()
        .get("com.google.appengine.runtime.request_log_id"));
    job.setExecutionDate(new Date());
    job.setStatus(Status.RUNNING);
    JobDAO.save(job);

    buildConfiguration();
    setupLogging();
    dumpConfiguration();

    ShutdownRequestListener listener = new ShutdownRequestListener() {

      @Override
      public void shutdownRequested(final String reason) {
        log(Level.WARNING, "Task timed out. Trying to rescue results.", reason);

        try {
          cpaCheckerThread.join(10000); // 10 seconds
        } catch (Exception e) {
          // Never mind. We are shutting down and only want to do so gracefully.
        }

        /*
         * Sometimes there are weird race conditions going on and saving might
         * fail therefore.In this case we try again a couple of times.
         */
        int retries = 0;
        do {
          try {
            setResult();
            job.setStatus(Status.TIMEOUT);
            job.setTerminationDate(new Date());
            job.setStatusMessage(reason);
            JobDAO.save(job);

            dumpStatistics();
            dumpLog();
            break;
          } catch (Exception e) {
            log(Level.WARNING, "Error while trying to rescue results.", e);
            retries++;
          }
        } while (retries < 3);

        shutdownComplete = true;
      }
    };

    final ShutdownNotifier shutdownNotifier = ShutdownNotifier.create();
    shutdownNotifier.register(listener);

    ShutdownHook shutdownHook = new ShutdownHook() {
      @Override
      public void shutdown() {
        shutdownNotifier.requestShutdown("The backend is shutting down.");
      }
    };
    LifecycleManager.getInstance().setShutdownHook(shutdownHook);

    final CPAchecker cpaChecker = new CPAchecker(config, logManager, shutdownNotifier);

    /*
     * To prevent the main thread (and therefore the complete request) to go
     * down if the run is interrupted the checker is run in its own thread. This
     * allows for setting the jobs status and potentially saving results.
     */
    cpaCheckerThread = Threads.newThread(new Runnable() {

      @Override
      public void run() {
        try {
          result = cpaChecker.run(job.getProgram().getPath());
        } catch (Exception e) {
          if (e.getClass().getSimpleName().equals("RuntimeException")) {
            /* RuntimeException might be thrown if the thread is interrupted and
             * data store operations are going on. The exception needs to be
             * ignored to be able to bail out sensible.
             */
            log(Level.WARNING, e);
          } else {
            log(Level.WARNING, e);
            throw new IllegalStateException(e);
          }
        }
      }
    });

    UncaughtExceptionHandler handler = new UncaughtExceptionHandler() {

      @Override
      public void uncaughtException(Thread t, Throwable e) {
        doCatch(e);
      }
    };
    cpaCheckerThread.setUncaughtExceptionHandler(handler);

    ResourceLimitChecker limits = ResourceLimitChecker.fromConfiguration(config, logManager, shutdownNotifier);
    limits.start();

    cpaCheckerThread.start();
    cpaCheckerThread.join();

    if (shutdownNotifier.shouldShutdown()) {
      while (!shutdownComplete) {
        Thread.sleep(100);
      }
    } else {
      shutdownNotifier.unregister(listener);
      limits.cancel();

      // do not overwrite any previous status
      if (job.getStatus() != Status.ERROR && job.getStatus() != Status.TIMEOUT) {
        dumpStatistics();
        dumpLog();

        setResult();
        job.setTerminationDate(new Date());
        job.setStatus(Status.DONE);
        JobDAO.save(job);
      }
    }
  }

  /*
   * Catches all exceptions that are thrown while running the job and are not
   * handled along the way.
   *
   * @see org.restlet.resource.ServerResource#doCatch(java.lang.Throwable)
   */
  @Override
  protected void doCatch(Throwable e) {
    // set status OK to pretend everything went fine so that the task will not be re-tried.
    getResponse().setStatus(org.restlet.data.Status.SUCCESS_OK);

    Throwable originalThrowable = e;

    if (e.getCause() != null) {
      e = e.getCause();
    }

    String message = (e.getMessage() == null) ? "" : e.getMessage();

    switch (e.getClass().getSimpleName()) {
    case "DeadlineExceededException":
      job.setStatus(Status.TIMEOUT);
      job.setStatusMessage("The task timed out. Results may be available however.");
      log(Level.WARNING, "Task timed out. Trying to rescue results.", e);
      break;
    case "InvalidConfigurationException":
      job.setStatusMessage("The given configuration is invalid.");
      log(Level.WARNING, "The given configuration is invalid.", e);
      break;
    case "IOException":
      job.setStatusMessage(String.format("An I/O error occurred: %s", message));
      log(Level.WARNING, "An I/O error occurred.", e);
      break;
    default:
      job.setStatusMessage(String.format("An error occured: %s", message));
      log(Level.WARNING, "There was an error", e);
    }

    if (job.getStatus() != Status.TIMEOUT) {
      job.setStatus(Status.ERROR);
    }

    try {
      saveStackTrace(originalThrowable);
      setResult();
      job.setTerminationDate(new Date());
      JobDAO.save(job);

      dumpConfiguration();
      dumpStatistics();
      dumpLog();
    } catch (IOException _) {
      // we are already in an error state so ignore any further one
    }
  }

  private void log(Level level, Object... args) {
    if (logManager != null) {
      logManager.log(level, args);
    }
  }

  private void setResult() {
    if (result != null) {
      job.setResultMessage(result.getResultString());
      job.setResultOutcome(result.getResult());
    }
  }

  private void saveStackTrace(Throwable e) throws IOException {
    try (OutputStream out = errorPath.asByteSink(FileWriteMode.APPEND).openStream()) {
      PrintStream ps = new PrintStream(out);
      e.printStackTrace(ps);
      ps.flush();
    }
  }

  private void dumpLog() {
    if (logDumped) { return; }

    if (logHandler != null && logLevel != null && logLevel != Level.OFF) {
      logDumped = true;
      logHandler.flushAndClose();
    }
  }

  private void dumpConfiguration() throws IOException {
    if (configDumped) { return; }

    if (config != null && config.getProperty("configuration.dumpFile") != null && !config.getProperty("configuration.dumpFile").equals("")) {
      Path configurationDumpFile = Paths.get(config.getProperty("configuration.dumpFile"));
      if (configurationDumpFile != null) {
        configurationDumpFile.asCharSink(Charsets.UTF_8).write(config.asPropertiesString());
        configDumped = true;
      }
    }
  }

  private void dumpStatistics() throws IOException {
    if (statsDumped) { return; }

    if (config == null || config.getProperty("statistics.export").equals("false") || result == null) { return; }

    Path statisticsDumpFile = Paths.get(config.getProperty("statistics.file"));
    try (OutputStream out = statisticsDumpFile.asByteSink().openBufferedStream()) {
      PrintStream stream = new PrintStream(out);
      result.printStatistics(stream);

      if (result != null) {
        result.printResult(stream);
      }

      stream.flush();
      statsDumped = true;
    }
  }

  private void setupLogging() throws IOException, InvalidConfigurationException {
    if (config == null || config.getProperty("log.level") == null || config.getProperty("log.level").equals("")) {
      logLevel = Level.parse(DefaultOptions.getDefault("log.level"));
    } else {
      logLevel = Level.parse(config.getProperty("log.level"));
    }

    if (logLevel != Level.OFF) {
      Formatter fileLogFormatter = new FileLogFormatter();
      OutputStream logFileStream = Paths.get("CPALog.txt").asByteSink().openStream();
      logHandler = new GAELogHandler(logFileStream, fileLogFormatter, logLevel);
      logManager = new GAELogManager(config, new DummyHandler(), logHandler);
    } else {
      logManager = new GAELogManager(config, new DummyHandler(), new DummyHandler());
    }
  }

  private void buildConfiguration() throws IOException, InvalidConfigurationException {
    Configuration.setBuilderFactory(new AbstractConfigurationBuilderFactory() {
      @Override
      public ConfigurationBuilder getBuilder() {
        return new GAEConfigurationBuilder();
      }
    });

    ConfigurationBuilder configurationBuilder = Configuration.builder();
    configurationBuilder.setOptions(DefaultOptions.getDefaultOptions());

    if (job.getConfiguration() != null) {
      configurationBuilder.loadFromFile(Paths.get("WEB-INF", "configurations", job.getConfiguration()));
    }
    configurationBuilder
        .setOption("analysis.programNames", job.getProgram().getName())
        .setOptions(job.getOptions());
    if (job.getSpecification() != null) {
      configurationBuilder.setOption("specification", "WEB-INF/specifications/" + job.getSpecification());
    }

    Configuration configuration = configurationBuilder.build();

    FileTypeConverter fileTypeConverter = new FileTypeConverter(configuration);

    config = Configuration.builder()
        .copyFrom(configuration)
        .addConverter(FileOption.class, fileTypeConverter)
        .build();

    Configuration.getDefaultConverters().put(FileOption.class, fileTypeConverter);
  }

  private class DummyHandler extends Handler {

    @Override
    public void publish(LogRecord pRecord) {}

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
  }
}
