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
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.MainCPAStatistics;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.cpa.usagestatistics.caches.InterpolantCache;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageContainer;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Precisions;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.CToFormulaConverterWithPointerAliasing;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.CTypeUtils;


public class UsageStatisticsRefiner extends BAMPredicateRefiner implements StatisticsProvider {

  private class Stats implements Statistics {

    public final Timer ComputePath = new Timer();
    public final Timer Refinement = new Timer();
    public final Timer UnsafeCheck = new Timer();
    public final Timer CacheTime = new Timer();

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
      pOut.println("Time for choosing target usage      " + UnsafeCheck);
      pOut.println("Time for computing path             " + ComputePath);
      pOut.println("Time for refinement                 " + Refinement);
      pOut.println("Time for cache                      " + CacheTime);
    }

    @Override
    public String getName() {
      return "UsageStatisticsRefiner";
    }

  }

  final Stats pStat = new Stats();
  private final LogManager logger;
  private InterpolantCache iCache = new InterpolantCache();

  public UsageStatisticsRefiner(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    super(pCpa);
    logger = CPAs.retrieveCPA(pCpa, UsageStatisticsCPA.class).getLogger();
  }

  public static Refiner create(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    return new UsageStatisticsRefiner(pCpa);
  }

  int i = 0;
  int counter = 0;
  int lastFalseUnsafeSize = -1;
  int lastTrueUnsafes = -1;
  private static final int HARDCODED_NUMBER_FOR_START_CLEANING_PRECISION = Integer.MAX_VALUE;
  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    final UsageContainer container =
        AbstractStates.extractStateByType(pReached.getFirstState(), UsageStatisticsState.class).getContainer();

    iCache.initKeySet();
    final RefineableUsageComputer computer = new RefineableUsageComputer(container, logger);

    logger.log(Level.INFO, ("Perform US refinement: " + i++));
    System.out.println("Time: " + MainCPAStatistics.programTime);
    int originUnsafeSize = container.getUnsafeSize();
    System.out.println("Unsafes: " + originUnsafeSize); 
    if (lastFalseUnsafeSize == -1) {
      lastFalseUnsafeSize = originUnsafeSize;
    } else {
      counter = lastFalseUnsafeSize - originUnsafeSize;
    }
    Iterator<SingleIdentifier> iterator = container.getUnsafeIterator();
    int originTrueUnsafeSize = 0;
    //int currentFalseUnsafeSize = 0;
    StringBuilder sb = new StringBuilder();
    while (iterator.hasNext()) {
      SingleIdentifier id = iterator.next();
      sb.append(id.getName() + ", ");
      AbstractUsagePointSet list = container.getUsages(id);
      if (list.isTrueUnsafe()) {
    	  originTrueUnsafeSize++;
      }/* else if (list.isFalseUnsafe()) {
        currentFalseUnsafeSize++;
      }*/
    }
    System.out.println("Origine unsafe list: " + sb.toString());
    System.out.println("True unsafes:        " + originTrueUnsafeSize);
    //System.out.println("False unsafes:       " + currentFalseUnsafeSize);
    boolean refinementFinish = false;
    UsageInfo target = null;
    pStat.UnsafeCheck.start();
    while ((target = computer.getNextRefineableUsage()) != null) {
      pStat.UnsafeCheck.stopIfRunning();
      pathStateToReachedState.clear();
      pStat.ComputePath.start();
      ARGPath pPath = computePath((ARGState)target.getKeyState(), target.getCallStack());
      pStat.ComputePath.stopIfRunning();
      assert (pPath != null);
      try {
        pStat.Refinement.start();
        CounterexampleInfo counterexample = super.performRefinement0(
            new BAMReachedSet(transfer, new ARGReachedSet(pReached), pPath, pathStateToReachedState), pPath);
        refinementFinish |= counterexample.isSpurious();
        if (counterexample.isSpurious()) {
          List<BooleanFormula> formulas = (List<BooleanFormula>) counterexample.getAllFurtherInformation().iterator().next().getFirst();
          pStat.CacheTime.start();
          if (iCache.contains(target, formulas)) {
          	computer.setResultOfRefinement(target, true);
            target.failureFlag = true;
          } else {
            iCache.add(target, formulas);
          	computer.setResultOfRefinement(target, false);
          }
          pStat.CacheTime.stop();
        } else {
          computer.setResultOfRefinement(target, !counterexample.isSpurious());
        }
      } catch (IllegalStateException e) {
        //msat_solver return -1 <=> unknown
        //consider its as true;
        logger.log(Level.WARNING, "Solver exception, consider " + target + " as true");
        computer.setResultOfRefinement(target, true);
        target.failureFlag = true;
      } finally {
        pStat.Refinement.stopIfRunning();
      }

      pStat.UnsafeCheck.start();
    }
    int newTrueUnsafeSize = 0;
    iterator = container.getUnsafeIterator();
     while (iterator.hasNext()) {
      SingleIdentifier id = iterator.next();
      AbstractUsagePointSet list = container.getUsages(id);
      if (list.isTrueUnsafe()) {
        newTrueUnsafeSize++;
      }
    }
    counter += (newTrueUnsafeSize -lastTrueUnsafes);
    if (counter >= HARDCODED_NUMBER_FOR_START_CLEANING_PRECISION) {
      Precision p = pReached.getPrecision(pReached.getFirstState());
      PredicatePrecision predicates = Precisions.extractPrecisionByType(p, PredicatePrecision.class);
      System.out.println("Clean: " + predicates.getLocalPredicates().size());
      pReached.updatePrecision(pReached.getFirstState(),
          Precisions.replaceByType(p, PredicatePrecision.empty(), PredicatePrecision.class));
      iCache.reset();
      CTypeUtils.clear();
      //CToFormulaConverterWithPointerAliasing.ufNameCache.clear();
      System.out.println("ufNameCache size = " + CToFormulaConverterWithPointerAliasing.ufNameCache.size());
      lastFalseUnsafeSize = originUnsafeSize;
      lastTrueUnsafes = newTrueUnsafeSize;
    } 
    iCache.removeUnusedCacheEntries();
    pStat.UnsafeCheck.stopIfRunning();
    return refinementFinish;
  }

  protected ARGPath computePath(ARGState pLastElement, CallstackState stack) throws InterruptedException, CPATransferException {
    assert (pLastElement != null && !pLastElement.isDestroyed());
      //we delete this state from other unsafe
    ARGState subgraph = transfer.findPath(pLastElement, pathStateToReachedState, stack);
    assert (subgraph != null);
    return computeCounterexample(subgraph);
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    super.collectStatistics(pStatsCollection);
    pStatsCollection.add(pStat);
  }
}
