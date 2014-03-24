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
  int lastArg = 0;
  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    //System.out.println("Run USRefiner");
    //Set<CallstackState> refinedPaths = new HashSet<>();
    Set<String> cachedResult = new HashSet<>();
    Set<Integer> cachedResult2 = new HashSet<>();
    Set<UsageInfo> toDelete = new HashSet<>();
    UsageContainer container =
        AbstractStates.extractStateByType(pReached.getFirstState(), UsageStatisticsState.class).getContainer();
    Collection<SingleIdentifier> unsafes = container.getUnsafes();

    SingleIdentifier refinementId = unsafes.isEmpty() ? null : unsafes.iterator().next();
    PairwiseUnsafeDetector detector = new PairwiseUnsafeDetector(null);

    System.out.println("Perform US refinement: " + i++);
    int originSize = 0;
    boolean refinementFinish = false;
    for (SingleIdentifier id : container.getStatistics().keySet()) {
      UsageSet uset = container.getStatistics().get(id);
      if (uset.isTrueUnsafe()) {
        continue;
      }
      for (UsageInfo uinfo : uset) {
        if (detector.isUnsafeCase(uset, uinfo) && !uinfo.isRefined()) {
          originSize++;
        }
      }
    }
    System.out.println("Before refinement: " + unsafes.size() + " unsafes");
    if (i == 5) {
      //System.out.println("This refinement: " + i);
      return false;
    }
    int iterationNum = 0;
    pStat.UnsafeCheck.start();
top:while ((refinementId = container.check(refinementId)) != null) {
      pStat.UnsafeCheck.stop();
      pathStateToReachedState.clear();
      //System.out.println("  Iteration " + iterationNum);
      pStat.DetectUnsafeCases.start();
      refinementFinish = true;
      //Stack<UsageInfo> toRefine = new Stack<>();
      UsageInfo target = null;

      //uset.removeAll(toDelete);
     /* if (target != null) {
        break;
      }*/
      UsageSet uset = container.getStatistics().get(refinementId);
      for (UsageInfo uinfo : uset) {
        if (detector.isUnsafeCase(uset, uinfo) && !uinfo.isRefined()
            && !cachedResult.contains(uinfo.getCallStack().getCurrentFunction())
            //!cachedResult2.contains(uinfo.getLine().getLine())
            ) {
          /*if (refinedPaths.contains(uinfo.getCallStack())) {
            toDelete.add(uinfo);
            continue;
          }*/
          target = uinfo;
          break;
        } else if (cachedResult.contains(uinfo.getCallStack().getCurrentFunction())
            //cachedResult2.contains(uinfo.getLine().getLine())
            ) {
          toDelete.add(uinfo);
        }
        /*if (uinfo.isRefined()) {
          System.out.println("Skip " + uinfo + " as refined");
        }*/
      }
      if (toDelete.size() > 0) {
        //for (UsageInfo uinfo : toDelete) {
          uset.removeAll(toDelete);
          //container.removeState(AbstractStates.extractStateByType(uinfo.getKeyState(), UsageStatisticsState.class));
          //System.out.println(uinfo + " is considered to be false, as cached");
        //}
        toDelete.clear();
      }
      pStat.DetectUnsafeCases.stop();
      if (target == null) {
        continue;
      }
      /*if (iterationNum == 20000) {
        pStat.UnsafeCheck.stop();
        return false;
      }*/
      iterationNum++;
     // while (!toRefine.empty()) {
     //   target = toRefine.pop();
      System.out.println("Refine " + refinementId);
        System.out.println("Refine " + iterationNum + " from " + originSize);
        ARGReachedSet ARGset = new ARGReachedSet(pReached);
        pStat.ComputePath.start();
        ARGPath pPath = computePath((ARGState)target.getKeyState(), ARGset);
        pStat.ComputePath.stop();
        if (pPath == null) {
          container.removeState(AbstractStates.extractStateByType(target.getKeyState(), UsageStatisticsState.class));
          //refinedPaths.add(target.getCallStack());
          System.out.println(target + " isn't found");
          pStat.UnsafeCheck.start();
          continue;
        }

        pStat.Refinement.start();
        CounterexampleInfo counterexample = super.performRefinement0(new ABMReachedSet(transfer, ARGset, pPath, pathStateToReachedState), pPath);
        pStat.Refinement.stop();
        if (!counterexample.isSpurious()) {
          System.out.println(target + " is true");
          target.setRefineFlag();
        } else {
          container.removeState(AbstractStates.extractStateByType(target.getKeyState(), UsageStatisticsState.class));
          System.out.println(target + " is false");
          //refinedPaths.add(target.getCallStack());
          //container.check();
          cachedResult.add(target.getCallStack().getCurrentFunction());
          //cachedResult2.add(target.getLine().getLine());
        }
        pStat.UnsafeCheck.start();
   //   }
    }
    pStat.UnsafeCheck.stop();
    return refinementFinish;
  }


  @Override
  protected ARGPath computePath(ARGState pLastElement, ARGReachedSet pReachedSet) throws InterruptedException, CPATransferException {
    //assert pLastElement.isTarget();
    if (pLastElement == null || pLastElement.isDestroyed()) {
      //we delete this state from other unsafe
      return null;
    }
    int startId = new ARGState(pLastElement, null).getStateId();
    ARGState subgraph = transfer.findPath(pLastElement, pReachedSet, pathStateToReachedState);
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
