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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.io.PrintStream;
import java.util.Collection;
import java.util.logging.Level;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.MainCPAStatistics;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;


public class UsageStatisticsRefiner extends BAMPredicateRefiner implements StatisticsProvider {

  private class Stats implements Statistics {

    public final Timer ComputePath = new Timer();
    public final Timer Refinement = new Timer();
    public final Timer UnsafeCheck = new Timer();

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
      pOut.println("Time for choosing target usage      " + UnsafeCheck);
      pOut.println("Time for computing path             " + ComputePath);
      pOut.println("Time for refinement                 " + Refinement);
    }

    @Override
    public String getName() {
      return "UsageStatisticsRefiner";
    }

  }

  final Stats pStat = new Stats();
  private final LogManager logger;

  public UsageStatisticsRefiner(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    super(pCpa);
    logger = CPAs.retrieveCPA(pCpa, UsageStatisticsCPA.class).getLogger();
  }

  public static Refiner create(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    return new UsageStatisticsRefiner(pCpa);
  }

  int i = 0;
  int findUnknown = 0;
  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    final UsageContainer container =
        AbstractStates.extractStateByType(pReached.getFirstState(), UsageStatisticsState.class).getContainer();
    final Collection<SingleIdentifier> unsafes = container.getUnsafes();

    final RefineableUsageComputer computer = new RefineableUsageComputer(container, logger);

    logger.log(Level.INFO, ("Perform US refinement: " + i++));
    int counter = 0;
    final StringBuilder sb = new StringBuilder();
    boolean refinementFinish = false;
    for (SingleIdentifier id : container.getStatistics().keySet()) {
      UsageList uset = container.getStatistics().get(id);
      if (uset.isTrueUnsafe()) {
        counter++;
        sb.append(id + ", ");
      }
    }
    logger.log(Level.INFO, "Unsafes: " + unsafes.size());
    logger.log(Level.INFO, "True refined: " + counter + ". " + sb.toString());
    logger.log(Level.INFO, "Time: " + MainCPAStatistics.programTime);
    UsageInfo target = null;
    pStat.UnsafeCheck.start();
    while ((target = computer.getNextRefineableUsage()) != null) {
      pStat.UnsafeCheck.stopIfRunning();
      pathStateToReachedState.clear();
      refinementFinish = true;
      pStat.ComputePath.start();
      ARGPath pPath = computePath((ARGState)target.getKeyState(), target.getCallStack());
      pStat.ComputePath.stopIfRunning();

      assert (pPath != null);
      try {
        pStat.Refinement.start();
        CounterexampleInfo counterexample = super.performRefinement0(
            new BAMReachedSet(transfer, new ARGReachedSet(pReached), pPath, pathStateToReachedState), pPath);
        computer.setResultOfRefinement(target, !counterexample.isSpurious());
      } catch (IllegalStateException e) {
        //msat_solver return -1 <=> unknown
        //consider its as true;
        logger.log(Level.WARNING, "Solver exception, consider " + target + " as true");
        computer.setResultOfRefinement(target, true);
      } finally {
        pStat.Refinement.stopIfRunning();
      }

      pStat.UnsafeCheck.start();
    }
    pStat.UnsafeCheck.stopIfRunning();
    return refinementFinish;
  }



  protected ARGPath computePath(ARGState pLastElement, CallstackState stack) throws InterruptedException, CPATransferException {
    if (pLastElement == null || pLastElement.isDestroyed()) {
      //we delete this state from other unsafe
      return null;
    }
    ARGState subgraph = transfer.findPath(pLastElement, pathStateToReachedState, stack);
    if (subgraph == null) {
      return null;
    }
    return computeCounterexample(subgraph);
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    super.collectStatistics(pStatsCollection);
    pStatsCollection.add(pStat);
  }
}
