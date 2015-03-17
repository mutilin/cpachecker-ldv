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
package org.sosy_lab.cpachecker.core.algorithm.invariants;

import static com.google.common.base.Preconditions.*;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.sosy_lab.common.Classes.UnexpectedCheckedException;
import org.sosy_lab.common.LazyFutureTask;
import org.sosy_lab.common.concurrency.Threads;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPABuilder;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.ShutdownNotifier.ShutdownRequestListener;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.conditions.AdjustableConditionCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSetWrapper;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.CPAs;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/**
 * Class that encapsulates invariant generation by using the CPAAlgorithm
 * with an appropriate configuration.
 * Supports synchronous and asynchronous execution.
 */
@Options(prefix="invariantGeneration")
public class CPAInvariantGenerator implements InvariantGenerator, StatisticsProvider {

  @Option(secure=true, name="config",
          required=true,
          description="configuration file for invariant generation")
  @FileOption(FileOption.Type.REQUIRED_INPUT_FILE)
  private Path configFile;

  @Option(secure=true, description="generate invariants in parallel to the normal analysis")
  private boolean async = false;

  @Option(secure=true, description="adjust invariant generation conditions if supported by the analysis")
  private boolean adjustConditions = false;

  private final Timer invariantGeneration = new Timer();

  private final LogManager logger;
  private final Algorithm invariantAlgorithm;
  private final ConfigurableProgramAnalysis invariantCPAs;
  private final ReachedSetFactory reachedSetFactory;
  private final ReachedSet reached;

  private final ShutdownNotifier shutdownNotifier;

  private Future<UnmodifiableReachedSet> invariantGenerationFuture = null;

  private final List<UpdateListener> updateListeners = new CopyOnWriteArrayList<>();

  private final ShutdownRequestListener shutdownListener = new ShutdownRequestListener() {

    @Override
    public void shutdownRequested(String pReason) {
      invariantGenerationFuture.cancel(true);
    }
  };

  public ConfigurableProgramAnalysis getCPAs() {
    return invariantCPAs;
  }

  public CPAInvariantGenerator(Configuration config, LogManager pLogger,
      ReachedSetFactory reachedSetFactory, ShutdownNotifier pShutdownNotifier, CFA cfa) throws InvalidConfigurationException, CPAException {
    config.inject(this);
    logger = pLogger.withComponentName("CPAInvariantGenerator");
    shutdownNotifier = ShutdownNotifier.createWithParent(pShutdownNotifier);

    Configuration invariantConfig;
    try {
      ConfigurationBuilder configBuilder = Configuration.builder().copyOptionFrom(config, "specification");
      configBuilder.loadFromFile(configFile);
      invariantConfig = configBuilder.build();
    } catch (IOException e) {
      throw new InvalidConfigurationException("could not read configuration file for invariant generation: " + e.getMessage(), e);
    }

    invariantCPAs = new CPABuilder(invariantConfig, logger, shutdownNotifier, reachedSetFactory).buildCPAWithSpecAutomatas(cfa);
    invariantAlgorithm = CPAAlgorithm.create(invariantCPAs, logger, invariantConfig, shutdownNotifier);
    this.reachedSetFactory = new ReachedSetFactory(invariantConfig, logger);
    reached = reachedSetFactory.create();
  }

  @Override
  public void start(final CFANode initialLocation) {
    checkNotNull(initialLocation);
    checkState(invariantGenerationFuture == null);
    checkState(!reached.hasWaitingState());

    if (async) {
      invariantGenerationFuture = new AdjustingInvariantGenerationFuture(reachedSetFactory, initialLocation);
    } else {
      Callable<UnmodifiableReachedSet> task = new Callable<UnmodifiableReachedSet>() {

        @Override
        public UnmodifiableReachedSet call() throws Exception {
          shutdownNotifier.shutdownIfNecessary();
          UnmodifiableReachedSet result = new InvariantGenerationTask(reachedSetFactory, initialLocation).call();
          CPAs.closeCpaIfPossible(invariantCPAs, logger);
          CPAs.closeIfPossible(invariantAlgorithm, logger);
          return result;
        }

      };
      invariantGenerationFuture = new LazyFutureTask<>(task);
    }

    shutdownNotifier.registerAndCheckImmediately(shutdownListener);
  }

  @Override
  public void cancel() {
    checkState(invariantGenerationFuture != null);
    shutdownNotifier.requestShutdown("Invariant generation cancel requested.");
  }

  @Override
  public UnmodifiableReachedSet get() throws CPAException, InterruptedException {
    checkState(invariantGenerationFuture != null);
    shutdownNotifier.shutdownIfNecessary();

    try {
      return invariantGenerationFuture.get();

    } catch (ExecutionException e) {
      Throwables.propagateIfPossible(e.getCause(), CPAException.class, InterruptedException.class);
      throw new UnexpectedCheckedException("invariant generation", e.getCause());
    } catch (CancellationException e) {
      InterruptedException ie = new InterruptedException();
      ie.initCause(e);
      throw ie;
    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (invariantCPAs instanceof StatisticsProvider) {
      ((StatisticsProvider)invariantCPAs).collectStatistics(pStatsCollection);
    }
    if (invariantAlgorithm instanceof StatisticsProvider) {
      ((StatisticsProvider)invariantAlgorithm).collectStatistics(pStatsCollection);
    }
  }

  @Override
  public Timer getTimeOfExecution() {
    return invariantGeneration;
  }

  @Override
  public void addUpdateListener(UpdateListener pUpdateListener) {
    Preconditions.checkNotNull(pUpdateListener);
    updateListeners.add(pUpdateListener);
  }

  @Override
  public void removeUpdateListener(UpdateListener pUpdateListener) {
    Preconditions.checkNotNull(pUpdateListener);
    updateListeners.remove(pUpdateListener);
  }

  private void notifyUpdateListeners() {
    for (UpdateListener updateListener : updateListeners) {
      updateListener.updated();
    }
  }

  private class InvariantGenerationTask implements Callable<UnmodifiableReachedSet> {

    private final ReachedSet taskReached;

    public InvariantGenerationTask(ReachedSetFactory pReachedSetFactory, CFANode pInitialLocation) {
      taskReached = pReachedSetFactory.create();
      synchronized (invariantCPAs) {
        taskReached.add(invariantCPAs.getInitialState(pInitialLocation, StateSpacePartition.getDefaultPartition()),
            invariantCPAs.getInitialPrecision(pInitialLocation, StateSpacePartition.getDefaultPartition()));
      }
    }

    @Override
    public UnmodifiableReachedSet call() throws CPAException, InterruptedException {
      checkState(taskReached.hasWaitingState());

      invariantGeneration.start();
      logger.log(Level.INFO, "Finding invariants");

      try {
        while (!taskReached.getWaitlist().isEmpty()) {
          invariantAlgorithm.run(taskReached);
        }

        return new UnmodifiableReachedSetWrapper(taskReached);

      } finally {
        invariantGeneration.stop();
      }
    }

  }

  private class AdjustingInvariantGenerationFuture implements Future<UnmodifiableReachedSet> {

    private final List<AdjustableConditionCPA> conditionCPAs;

    private final AtomicReference<Future<UnmodifiableReachedSet>> currentFuture = new AtomicReference<>();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(Threads.threadFactory());

    private boolean done = false;

    private boolean cancelledInner = false;

    public AdjustingInvariantGenerationFuture(final ReachedSetFactory pReachedSetFactory, CFANode pInitialLocation) {
      conditionCPAs = CPAs.asIterable(invariantCPAs).filter(AdjustableConditionCPA.class).toList();
      Callable<UnmodifiableReachedSet> initialTask = new Callable<UnmodifiableReachedSet>() {

        @Override
        public UnmodifiableReachedSet call() {
          return pReachedSetFactory.create();
        }

      };
      currentFuture.set(executorService.submit(initialTask));
      if (!shutdownNotifier.shouldShutdown()) {
        scheduleTask(pReachedSetFactory, pInitialLocation);
      }
    }

    @Override
    public boolean cancel(boolean pMayInterruptIfRunning) {
      cancelledInner = true;
      boolean wasDone = done;
      setDone();
      Future<UnmodifiableReachedSet> currentFuture = this.currentFuture.get();
      if (currentFuture != null) {
        return currentFuture.cancel(pMayInterruptIfRunning);
      }
      return !wasDone;
    }

    @Override
    public UnmodifiableReachedSet get() throws InterruptedException, ExecutionException {
      return currentFuture.get().get();
    }

    @Override
    public UnmodifiableReachedSet get(long pTimeout, TimeUnit pUnit) throws InterruptedException, ExecutionException,
        TimeoutException {
      return currentFuture.get().get(pTimeout, pUnit);
    }

    @Override
    public boolean isCancelled() {
      return cancelledInner;
    }

    @Override
    public boolean isDone() {
      return done;
    }

    private Future<UnmodifiableReachedSet> scheduleTask(final ReachedSetFactory pReachedSetFactory, final CFANode pInitialLocation) {
      final AtomicReference<Future<UnmodifiableReachedSet>> ref = new AtomicReference<>();
      final Future<UnmodifiableReachedSet> future = new LazyFutureTask<>(new InvariantGenerationTask(pReachedSetFactory, pInitialLocation) {

        @Override
        public UnmodifiableReachedSet call() throws CPAException, InterruptedException {
          UnmodifiableReachedSet result = super.call();
          // This accesses the future referenced by ref, which is a future
          // wrapping this call itself, so this function must not be called
          // before ref is set to the wrapping future
          currentFuture.set(ref.get());
          notifyUpdateListeners();
          if (adjustConditions() && !shutdownNotifier.shouldShutdown()) {
            scheduleTask(pReachedSetFactory, pInitialLocation);
          } else {
            setDone();
            cancelledInner |= ref.get().isCancelled();
          }
          return result;
        }

      });
      // Set the wrapping future as value of the reference
      ref.set(future);
      // From here on it is safe to call the task, so it is submit to a scheduler
      if (!shutdownNotifier.shouldShutdown() && !executorService.isShutdown()) {
        ref.set(executorService.submit(new Callable<UnmodifiableReachedSet>() {

          @Override
          public UnmodifiableReachedSet call() throws ExecutionException, InterruptedException {
            return future.get();
          }

        }));
      }
      return future;
    }

    private void setDone() {
      if (!done) {
        done = true;
        CPAs.closeCpaIfPossible(invariantCPAs, logger);
        CPAs.closeIfPossible(invariantAlgorithm, logger);
        executorService.shutdown();
      }
    }

    private boolean adjustConditions() {
      if (!adjustConditions) {
        return false;
      }
      if (conditionCPAs.isEmpty()) {
        logger.log(Level.INFO, "Cannot adjust invariant generation: No adjustable CPAs.");
        return false;
      }
      synchronized (invariantCPAs) {
        for (AdjustableConditionCPA cpa : conditionCPAs) {
          if (!cpa.adjustPrecision()) {
            logger.log(Level.INFO, "Further invariant generation adjustments denied by", cpa.getClass().getSimpleName());
            return false;
          }
        }
      }
      return true;
    }

  }

}