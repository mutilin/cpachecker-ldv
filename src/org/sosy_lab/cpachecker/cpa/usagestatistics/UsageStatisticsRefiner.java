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
import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.common.Timer;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.predicate.ABMPredicateRefiner;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;


public class UsageStatisticsRefiner extends ABMPredicateRefiner implements StatisticsProvider {

  private class Stats implements Statistics {

    public Timer DetectUnsafeCases = new Timer();
    public Timer ComputePath = new Timer();
    public Timer Refinement = new Timer();
    public Timer UnsafeCheck = new Timer();

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
      pOut.println("Time for detect unsafe cases        " + DetectUnsafeCases);
      pOut.println("Time for computing path             " + ComputePath);
      pOut.println("Time for refinement                 " + Refinement);
      pOut.println("Time for checks of unsafes          " + UnsafeCheck);

    }

    @Override
    public String getName() {
      return "UsageStatisticsRefiner";
    }

  }

  Stats pStat = new Stats();

  public UsageStatisticsRefiner(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
  //  internalRefiner = new ABMPredicateRefiner(pCpa);
    super(pCpa);
  }

  public static Refiner create(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    return new UsageStatisticsRefiner(pCpa);
  }

  int i = 0;
  int findUnknown = 0;
  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    UsageCache cache = new UsageCallstackCache();
    Set<UsageInfo> toDelete = new HashSet<>();
    UsageContainer container =
        AbstractStates.extractStateByType(pReached.getFirstState(), UsageStatisticsState.class).getContainer();
    Collection<SingleIdentifier> unsafes = container.getUnsafes();

    SingleIdentifier refinementId = unsafes.isEmpty() ? null : unsafes.iterator().next();
    PairwiseUnsafeDetector detector = new PairwiseUnsafeDetector(null);

    System.out.println("Perform US refinement: " + i);
    //int originSize = 0;
    boolean refinementFinish = false;
    /*for (SingleIdentifier id : container.getStatistics().keySet()) {
      UsageSet uset = container.getStatistics().get(id);
      if (uset.isTrueUnsafe()) {
        continue;
      }
      for (UsageInfo uinfo : uset) {
        if (detector.isUnsafeCase(uset, uinfo) && !uinfo.isRefined()) {
          originSize++;
        }
      }
    }*/
    //System.out.println("Before refinement: " + unsafes.size() + " unsafes");
    if (i++ == 50) {
      //System.out.println("This refinement: " + i);
      return false;
    }
    //int iterationNum = 0;
    pStat.UnsafeCheck.start();
    while ((refinementId = container.check(refinementId)) != null) {
      pStat.UnsafeCheck.stop();
      pathStateToReachedState.clear();
      pStat.DetectUnsafeCases.start();
      refinementFinish = true;
      UsageInfo target = null;

      UsageSet uset = container.getStatistics().get(refinementId);
      for (UsageInfo uinfo : uset) {
        if (detector.isUnsafeCase(uset, uinfo) && !uinfo.isRefined()) {
          if (cache.contains(uinfo)) {
            toDelete.add(uinfo);
          } else {
            target = uinfo;
            break;
          }
        }
      }
      if (toDelete.size() > 0) {
        uset.removeAll(toDelete);
        toDelete.clear();
      }
      pStat.DetectUnsafeCases.stop();
      if (target == null) {
        continue;
      }
      //iterationNum++;
      System.out.println("Refine " + refinementId);
      //System.out.println("Refine " + iterationNum + " from " + originSize);
      pStat.ComputePath.start();
      ARGPath pPath = computePath((ARGState)target.getKeyState(), target.getCallStack());
      pStat.ComputePath.stop();
      if (pPath == null) {
        container.removeState(AbstractStates.extractStateByType(target.getKeyState(), UsageStatisticsState.class));
        System.out.println(target + " isn't found");
        pStat.UnsafeCheck.start();
        continue;
      }
      try {
        pStat.Refinement.start();
        CounterexampleInfo counterexample = super.performRefinement0(
            new ABMReachedSet(transfer, new ARGReachedSet(pReached), pPath, pathStateToReachedState), pPath);
        if (!counterexample.isSpurious()) {
          //System.out.println(target + " is true");
          target.setRefineFlag();
        } else {
          container.removeState(AbstractStates.extractStateByType(target.getKeyState(), UsageStatisticsState.class));
          //System.out.println(target + " is false");
          cache.add(target);
        }
      } catch (IllegalStateException e) {
        //msat_solver return -1 <=> unknown
        //consider its as true;
        System.out.println(target + " is " + (findUnknown++) + " unknown");
        target.setRefineFlag();
      } finally {
        pStat.Refinement.stop();
      }

      pStat.UnsafeCheck.start();
    }
    pStat.UnsafeCheck.stop();
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
