/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.Triple;
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
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ForwardingReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.local.LocalState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;

import com.google.common.base.Strings;
import com.google.common.collect.Iterators;

@Options(prefix="restartAlgorithm")
public class RestartLockAlgorithm implements Algorithm, StatisticsProvider {
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

  @Option(required=true, description = "list of files with configurations to use")
  @FileOption(FileOption.Type.REQUIRED_INPUT_FILE)
  private List<Path> configFiles;

  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final RestartAlgorithmStatistics stats;
  private final String filename;
  private final CFA cfa;
  private final Configuration globalConfig;
  private String outputFileName = "output/localsave";

  private Algorithm currentAlgorithm;

  public RestartLockAlgorithm(Configuration pConfig, LogManager pLogger, ShutdownNotifier pShutdownNotifier, String pFilename, CFA pCfa)
      throws InvalidConfigurationException {
    pConfig.inject(this);

    if (configFiles.isEmpty()) {
      throw new InvalidConfigurationException("Need at least one configuration for restart algorithm!");
    }

    this.stats = new RestartAlgorithmStatistics(configFiles.size());
    this.logger = pLogger;
    this.shutdownNotifier = pShutdownNotifier;
    this.cfa = pCfa;
    this.filename = pFilename;
    this.globalConfig = pConfig;
    String tmpString = pConfig.getProperty("precision.path");
    if (tmpString != null) {
      outputFileName = tmpString;
    }
  }

  @Override
  public boolean run(ReachedSet pReached) throws CPAException, InterruptedException {
    boolean isFirst = true;
    checkArgument(pReached instanceof ForwardingReachedSet, "RestartAlgorithm needs ForwardingReachedSet");
    checkArgument(pReached.size() <= 1, "RestartAlgorithm does not support being called several times with the same reached set");
    checkArgument(!pReached.isEmpty(), "RestartAlgorithm needs non-empty reached set");

    ForwardingReachedSet reached = (ForwardingReachedSet)pReached;

    CFANode mainFunction = AbstractStates.extractLocation(pReached.getFirstState());
    assert mainFunction != null : "Location information needed";

    @Nullable ConfigurableProgramAnalysis currentCpa = null;
    Iterator<Path> configFilesIterator = Iterators.peekingIterator(configFiles.iterator());
    ShutdownNotifier singleShutdownNotifier = ShutdownNotifier.createWithParent(shutdownNotifier);
    while (configFilesIterator.hasNext()) {
      stats.totalTime.start();
      ReachedSet currentReached;
      try {
        Path singleConfigFileName = configFilesIterator.next();

        try {
          Triple<Algorithm, ConfigurableProgramAnalysis, ReachedSet> currentAlg = createNextAlgorithm(singleConfigFileName, mainFunction, singleShutdownNotifier, isFirst);
          currentAlgorithm = currentAlg.getFirst();
          currentCpa = currentAlg.getSecond();
          currentReached = currentAlg.getThird();
        } catch (InvalidConfigurationException e) {
          logger.logUserException(Level.WARNING, e, "Skipping one analysis because its configuration is invalid");
          continue;
        } catch (IOException e) {
          logger.logUserException(Level.WARNING, e, "Skipping one analysis due to unreadable configuration file");
          continue;
        }

        reached.setDelegate(currentReached);

        if (currentAlgorithm instanceof StatisticsProvider) {
          ((StatisticsProvider)currentAlgorithm).collectStatistics(stats.getSubStatistics());
        }

        stats.noOfAlgorithmsUsed++;

        // run algorithm
        try {
          boolean sound = currentAlgorithm.run(currentReached);

          if (isFirst) {
            isFirst = false;
            assert (currentAlgorithm instanceof CPALocalSaveAlgorithm);
            printReachedSet(((CPALocalSaveAlgorithm)currentAlgorithm).getStatistics());
          }

          if (!sound) {
            // if the analysis is not sound and we can proceed with
            // another algorithm, continue with the next algorithm
            logger.log(Level.INFO, "Analysis result was unsound.");

          } else if (currentReached.hasWaitingState()) {
            // if there are still states in the waitlist, the result is unknown
            // continue with the next algorithm
            logger.log(Level.INFO, "Analysis not completed: There are still states to be processed.");

          } else {
            logger.log(Level.INFO, "Analysis is finished");
          }
        } catch (CPAException e) {
          if (configFilesIterator.hasNext()) {
            logger.logUserException(Level.WARNING, e, "Analysis not completed");
          } else {
            throw e;
          }
        }
      } finally {
        stats.totalTime.stop();
      }

      if (configFilesIterator.hasNext()) {
        stats.printIntermediateStatistics(System.out, Result.UNKNOWN, currentReached);
        stats.resetSubStatistics();
        logger.log(Level.INFO, "RestartAlgorithm switches to the next configuration...");
        if (currentCpa != null) {
          CPAs.closeCpaIfPossible(currentCpa, logger);
        }
      } else {
        return true;
      }
    }

    // no further configuration available, and analysis has not finished
    logger.log(Level.INFO, "No further configuration available.");
    return false;
  }

  private Triple<Algorithm, ConfigurableProgramAnalysis, ReachedSet> createNextAlgorithm(Path singleConfigFileName, CFANode mainFunction
      , ShutdownNotifier singleShutdownNotifier, boolean isFirst) throws InvalidConfigurationException, CPAException, InterruptedException, IOException {

    ReachedSet reached;
    Algorithm algorithm;

    ConfigurationBuilder singleConfigBuilder = Configuration.builder();
    singleConfigBuilder.copyFrom(globalConfig);
    singleConfigBuilder.clearOption("analysis.saveLocalResults");

    singleConfigBuilder.loadFromFile(singleConfigFileName);
    Configuration singleConfig = singleConfigBuilder.build();

    ReachedSetFactory singleReachedSetFactory = new ReachedSetFactory(singleConfig, logger);
    ConfigurableProgramAnalysis cpa = createCPA(singleReachedSetFactory, singleConfig, singleShutdownNotifier, stats);
    algorithm = createAlgorithm(cpa, singleConfig, singleShutdownNotifier, isFirst);
    reached = createInitialReachedSetForRestart(cpa, mainFunction, singleReachedSetFactory);

    return Triple.of(algorithm, cpa, reached);
  }

  private ReachedSet createInitialReachedSetForRestart(
      ConfigurableProgramAnalysis cpa,
      CFANode mainFunction,
      ReachedSetFactory pReachedSetFactory) {
    logger.log(Level.FINE, "Creating initial reached set");

    AbstractState initialState = cpa.getInitialState(mainFunction);
    Precision initialPrecision = cpa.getInitialPrecision(mainFunction);

    ReachedSet reached = pReachedSetFactory.create();
    reached.add(initialState, initialPrecision);
    return reached;
  }

  private ConfigurableProgramAnalysis createCPA(ReachedSetFactory pReachedSetFactory, Configuration pConfig,
      ShutdownNotifier pShutdownNotifier, RestartAlgorithmStatistics stats) throws InvalidConfigurationException, CPAException {
    logger.log(Level.FINE, "Creating CPAs");

    CPABuilder builder = new CPABuilder(pConfig, logger, pShutdownNotifier, pReachedSetFactory);
    ConfigurableProgramAnalysis cpa = builder.buildCPAs(cfa);

    if (cpa instanceof StatisticsProvider) {
      ((StatisticsProvider)cpa).collectStatistics(stats.getSubStatistics());
    }
    return cpa;
  }

  private Algorithm createAlgorithm(final ConfigurableProgramAnalysis cpa, Configuration pConfig,
      ShutdownNotifier pShutdownNotifier,
      boolean isFirst) throws InvalidConfigurationException, CPAException {
    logger.log(Level.FINE, "Creating algorithms");
    Algorithm algorithm;


    if (isFirst) {
      algorithm =  new CPALocalSaveAlgorithm(cpa, logger, pShutdownNotifier);
    } else {
      algorithm =  CPAAlgorithm.create(cpa, logger, pConfig, pShutdownNotifier);
    }
    String cegar = pConfig.getProperty("analysis.algorithm.CEGAR");
    if (cegar != null && cegar.equals("true")) {
      algorithm = new CEGARAlgorithm(algorithm, cpa, pConfig, logger);
    }
    return algorithm;
  }

  private void printReachedSet(Map<CFANode, LocalState> reachedStatistics) {
    PrintWriter writer = null;
    FileOutputStream file = null;
    try {
      file = new FileOutputStream (outputFileName);
      writer = new PrintWriter(file);
      logger.log(Level.FINE, "Write precision to " + outputFileName);
      for (CFANode node : reachedStatistics.keySet()) {
        writer.println(node.toString());
        writer.println(reachedStatistics.get(node).toLog());
      }
      writer.close();
    } catch(FileNotFoundException e) {
      System.out.println("Cannot open file " + outputFileName);
      return;
    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (currentAlgorithm instanceof StatisticsProvider) {
      ((StatisticsProvider)currentAlgorithm).collectStatistics(pStatsCollection);
    }
    pStatsCollection.add(stats);
  }
}
