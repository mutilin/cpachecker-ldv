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
package org.sosy_lab.cpachecker.core.algorithm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.IS_TARGET_STATE;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CoreComponentsFactory;
import org.sosy_lab.cpachecker.core.CoreComponentsFactory.SpecAutomatonCompositionType;
import org.sosy_lab.cpachecker.core.algorithm.pcc.PartialARGsCombiner;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ForwardingReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.HistoryForwardingReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.resources.ResourceLimitChecker;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import javax.annotation.Nullable;

@Options(prefix="restartAlgorithm")
public class RestartAlgorithm implements Algorithm, StatisticsProvider {

  private static final Splitter CONFIG_FILE_CONDITION_SPLITTER = Splitter.on("::").trimResults().limit(2);

  private static class RestartAlgorithmStatistics implements Statistics {

    private final int noOfAlgorithms;
    private final Collection<Statistics> subStats;
    private int noOfAlgorithmsUsed = 0;
    private Timer totalTime = new Timer();

    public RestartAlgorithmStatistics(int pNoOfAlgorithms) {
      noOfAlgorithms = pNoOfAlgorithms;
      subStats = new ArrayList<>();
    }

    public Collection<Statistics> getSubStatistics() {
      return subStats;
    }

    public void resetSubStatistics() {
      subStats.clear();
      totalTime = new Timer();
    }

    @Override
    public String getName() {
      return "Restart Algorithm";
    }

    private void printIntermediateStatistics(PrintStream out, Result result,
        ReachedSet reached) {

      String text = "Statistics for algorithm " + noOfAlgorithmsUsed + " of " + noOfAlgorithms;
      out.println(text);
      out.println(Strings.repeat("=", text.length()));

      printSubStatistics(out, result, reached);
      out.println();
    }

    @Override
    public void printStatistics(PrintStream out, Result result,
        ReachedSet reached) {

      out.println("Number of algorithms provided:    " + noOfAlgorithms);
      out.println("Number of algorithms used:        " + noOfAlgorithmsUsed);

      printSubStatistics(out, result, reached);
    }

    private void printSubStatistics(PrintStream out, Result result, ReachedSet reached) {
      out.println("Total time for algorithm " + noOfAlgorithmsUsed + ": " + totalTime);

      for (Statistics s : subStats) {
        String name = s.getName();
        if (!isNullOrEmpty(name)) {
          name = name + " statistics";
          out.println("");
          out.println(name);
          out.println(Strings.repeat("-", name.length()));
        }
        s.printStatistics(out, result, reached);
      }
    }

  }

  @Option(secure=true, required=true, description = "List of files with configurations to use. "
      + "A filename can be suffixed with :if-interrupted, :if-failed, and :if-terminated "
      + "which means that this configuration will only be used if the previous configuration ended with a matching condition.")
  @FileOption(FileOption.Type.OPTIONAL_INPUT_FILE)
  private List<Path> configFiles;

  @Option(
    secure = true,
    name = "combineARGsAfterRestart",
    description =
        "combine (partial) ARGs obtained by restarts of the analysis after an unknown result with a different configuration"
  )
  private boolean useARGCombiningAlgorithm = false;

  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final RestartAlgorithmStatistics stats;
  private final String filename;
  private final CFA cfa;
  private final Configuration globalConfig;

  private Algorithm currentAlgorithm;

  private RestartAlgorithm(
      Configuration config,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      String pFilename,
      CFA pCfa)
      throws InvalidConfigurationException {
    config.inject(this);

    if (configFiles.isEmpty()) {
      throw new InvalidConfigurationException("Need at least one configuration for restart algorithm!");
    }

    this.stats = new RestartAlgorithmStatistics(configFiles.size());
    this.logger = pLogger;
    this.shutdownNotifier = pShutdownNotifier;
    this.filename = pFilename;
    this.cfa = pCfa;
    this.globalConfig = config;
  }

  public static Algorithm create(
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      String pFilename,
      CFA pCfa)
      throws InvalidConfigurationException {
    RestartAlgorithm algorithm =
        new RestartAlgorithm(pConfig, pLogger, pShutdownNotifier, pFilename, pCfa);
    if (algorithm.useARGCombiningAlgorithm) {
      return new PartialARGsCombiner(algorithm, pConfig, pLogger, pShutdownNotifier);
    }
    return algorithm;
  }

  @Override
  public AlgorithmStatus run(ReachedSet pReached) throws CPAException, InterruptedException {
    checkArgument(pReached instanceof ForwardingReachedSet, "RestartAlgorithm needs ForwardingReachedSet");
    checkArgument(pReached.size() <= 1, "RestartAlgorithm does not support being called several times with the same reached set");
    checkArgument(!pReached.isEmpty(), "RestartAlgorithm needs non-empty reached set");

    ForwardingReachedSet reached = (ForwardingReachedSet)pReached;

    Iterable<CFANode> initialNodes = AbstractStates.extractLocations(pReached.getFirstState());
    CFANode mainFunction = Iterables.getOnlyElement(initialNodes);

    PeekingIterator<Path> configFilesIterator = Iterators.peekingIterator(configFiles.iterator());

    AlgorithmStatus status = AlgorithmStatus.UNSOUND_AND_PRECISE;
    while (configFilesIterator.hasNext()) {
      stats.totalTime.start();
      @Nullable ConfigurableProgramAnalysis currentCpa = null;
      ReachedSet currentReached;
      ShutdownManager singleShutdownManager = ShutdownManager.createWithParent(shutdownNotifier);

      boolean lastAnalysisInterrupted = false;
      boolean lastAnalysisFailed = false;
      boolean lastAnalysisTerminated = false;
      boolean recursionFound = false;
      boolean concurrencyFound = false;

      try {
        Path singleConfigFileName = configFilesIterator.next();
        // extract first part out of file name
        singleConfigFileName = Paths.get(CONFIG_FILE_CONDITION_SPLITTER.split(singleConfigFileName.toString()).iterator().next());

        try {
          Triple<Algorithm, ConfigurableProgramAnalysis, ReachedSet> currentAlg = createNextAlgorithm(singleConfigFileName, mainFunction, singleShutdownManager);
          currentAlgorithm = currentAlg.getFirst();
          currentCpa = currentAlg.getSecond();
          currentReached = currentAlg.getThird();
        } catch (InvalidConfigurationException e) {
          logger.logUserException(Level.WARNING, e, "Skipping one analysis because the configuration file " + singleConfigFileName.toString() + " is invalid");
          continue;
        } catch (IOException e) {
          logger.logUserException(Level.WARNING, e, "Skipping one analysis because the configuration file " + singleConfigFileName.toString() + " could not be read");
          continue;
        }

        if (reached instanceof HistoryForwardingReachedSet) {
          ((HistoryForwardingReachedSet) reached).saveCPA(currentCpa);
        }
        reached.setDelegate(currentReached);

        if (currentAlgorithm instanceof StatisticsProvider) {
          ((StatisticsProvider)currentAlgorithm).collectStatistics(stats.getSubStatistics());
        }
        shutdownNotifier.shutdownIfNecessary();

        stats.noOfAlgorithmsUsed++;

        // run algorithm
        try {
          status = currentAlgorithm.run(currentReached);

          if (from(currentReached).anyMatch(IS_TARGET_STATE) && status.isPrecise()) {

            // If the algorithm is not _precise_, verdict "false" actually means "unknown".
            return status;
          }

          if (!status.isSound()) {
            // if the analysis is not sound and we can proceed with
            // another algorithm, continue with the next algorithm
            logger.log(Level.INFO, "Analysis result was unsound.");

          } else if (currentReached.hasWaitingState()) {
            // if there are still states in the waitlist, the result is unknown
            // continue with the next algorithm
            logger.log(Level.INFO, "Analysis not completed: There are still states to be processed.");

          } else {
            // sound analysis and completely finished, terminate
            return status;
          }
          lastAnalysisTerminated = true;

        } catch (CPAException e) {
          lastAnalysisFailed = true;
          if (e.getMessage().contains("Counterexample could not be analyzed")) {
            status = status.withPrecise(false);
          }
          if (configFilesIterator.hasNext()) {
            logger.logUserException(Level.WARNING, e, "Analysis not completed");
            if (e.getMessage().contains("recursion")) {
              recursionFound = true;
            }
            if (e.getMessage().contains("pthread_create")) {
              concurrencyFound = true;
            }
          } else {
            throw e;
          }
        } catch (InterruptedException e) {
          lastAnalysisInterrupted = true;
          shutdownNotifier.shutdownIfNecessary(); // check if we should also stop
          logger.logUserException(Level.WARNING, e, "Analysis " + stats.noOfAlgorithmsUsed + " stopped");
        }
      } finally {
        singleShutdownManager.requestShutdown("Analysis terminated"); // shutdown any remaining components
        stats.totalTime.stop();
      }

      shutdownNotifier.shutdownIfNecessary();

      if (configFilesIterator.hasNext()) {
        // Check if the next config file has a condition,
        // and if it has a condition, check if it matches.
        boolean foundConfig;
        do {
          foundConfig = true;
          String nextConfigFile = configFilesIterator.peek().toString();
          List<String> parts = CONFIG_FILE_CONDITION_SPLITTER.splitToList(nextConfigFile);
          if (parts.size() == 2) {
            String condition = parts.get(1);
            switch (condition) {
            case "if-interrupted":
              foundConfig = lastAnalysisInterrupted;
              break;
            case "if-failed":
              foundConfig = lastAnalysisFailed;
              break;
            case "if-terminated":
              foundConfig = lastAnalysisTerminated;
              break;
            case "if-recursive":
              foundConfig = recursionFound;
              break;
            case "if-concurrent":
              foundConfig = concurrencyFound;
              break;
            default:
              logger.logf(Level.WARNING, "Ignoring invalid restart condition '%s'.", condition);
              foundConfig = true;
            }
            if (!foundConfig) {
              logger.logf(Level.INFO, "Ignoring restart configuration '%s' because condition %s did not match.", parts.get(0), condition);
              configFilesIterator.next();
              stats.noOfAlgorithmsUsed++;
            }
          }
        } while (!foundConfig && configFilesIterator.hasNext());
      }

      if (configFilesIterator.hasNext()) {
        stats.printIntermediateStatistics(System.out, Result.UNKNOWN, currentReached);
        stats.resetSubStatistics();

        if (currentCpa != null) {
          CPAs.closeCpaIfPossible(currentCpa, logger);
        }
        CPAs.closeIfPossible(currentAlgorithm, logger);

        logger.log(Level.INFO, "RestartAlgorithm switches to the next configuration...");
      }
    }

    // no further configuration available, and analysis has not finished
    logger.log(Level.INFO, "No further configuration available.");
    return status;
  }

  private Triple<Algorithm, ConfigurableProgramAnalysis, ReachedSet> createNextAlgorithm(Path singleConfigFileName, CFANode mainFunction, ShutdownManager singleShutdownManager) throws InvalidConfigurationException, CPAException, IOException {

    ReachedSet reached;
    ConfigurableProgramAnalysis cpa;
    Algorithm algorithm;

    ConfigurationBuilder singleConfigBuilder = Configuration.builder();
    singleConfigBuilder.copyFrom(globalConfig);
    singleConfigBuilder.clearOption("restartAlgorithm.configFiles");
    singleConfigBuilder.clearOption("analysis.restartAfterUnknown");
    singleConfigBuilder.loadFromFile(singleConfigFileName);
    if (globalConfig.hasProperty("specification")) {
      singleConfigBuilder.copyOptionFrom(globalConfig, "specification");
    }

    Configuration singleConfig = singleConfigBuilder.build();
    if (singleConfig.hasProperty("analysis.restartAfterUnknown")) {
      throw new InvalidConfigurationException(
          "Sequential analysis parts may not be sequential analyses theirselves.");
    }

    LogManager singleLogger = logger.withComponentName("Analysis" + (stats.noOfAlgorithmsUsed + 1));

    ResourceLimitChecker singleLimits = ResourceLimitChecker.fromConfiguration(singleConfig, singleLogger, singleShutdownManager);
    singleLimits.start();

    CoreComponentsFactory coreComponents =
        new CoreComponentsFactory(singleConfig, singleLogger, singleShutdownManager.getNotifier());
    cpa = coreComponents.createCPA(cfa, null, SpecAutomatonCompositionType.TARGET_SPEC);

    if (cpa instanceof StatisticsProvider) {
      ((StatisticsProvider) cpa).collectStatistics(stats.getSubStatistics());
    }

    GlobalInfo.getInstance().setUpInfoFromCPA(cpa);

    algorithm = coreComponents.createAlgorithm(cpa, filename, cfa, null);
    reached =
        createInitialReachedSetForRestart(
            cpa, mainFunction, coreComponents.getReachedSetFactory(), singleLogger);
    return Triple.of(algorithm, cpa, reached);
  }

  private ReachedSet createInitialReachedSetForRestart(
      ConfigurableProgramAnalysis cpa,
      CFANode mainFunction,
      ReachedSetFactory pReachedSetFactory,
      LogManager singleLogger) {
    singleLogger.log(Level.FINE, "Creating initial reached set");

    AbstractState initialState = cpa.getInitialState(mainFunction, StateSpacePartition.getDefaultPartition());
    Precision initialPrecision = cpa.getInitialPrecision(mainFunction, StateSpacePartition.getDefaultPartition());

    ReachedSet reached = pReachedSetFactory.create();
    reached.add(initialState, initialPrecision);
    return reached;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (currentAlgorithm instanceof StatisticsProvider) {
      ((StatisticsProvider)currentAlgorithm).collectStatistics(pStatsCollection);
    }
    pStatsCollection.add(stats);
  }
}
