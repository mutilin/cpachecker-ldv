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
import java.util.LinkedList;
import java.util.List;

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

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
      pOut.println("Time for detect unsafe cases        " + DetectUnsafeCases);
      pOut.println("Time for computing path             " + ComputePath);
      pOut.println("Time for refinement                 " + Refinement);

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
    UsageContainer container =
        AbstractStates.extractStateByType(pReached.getFirstState(), UsageStatisticsState.class).getContainer();

    PairwiseUnsafeDetector detector = new PairwiseUnsafeDetector(null);

    System.out.println("Perform US refinement: " + i++);
    boolean refinementFinish = false;
    if (i > 305) {
      return false;
    }
    //int iterationNum = 0;
top:while (container.check()) {
      //System.out.println("  Iteration " + iterationNum);
      pStat.DetectUnsafeCases.start();
      refinementFinish = true;
      List<UsageInfo> toRefine = new LinkedList<>();
      for (SingleIdentifier id : container.getStatistics().keySet()) {
        List<UsageInfo> uset = container.getStatistics().get(id);
        for (UsageInfo uinfo : uset) {
          if (detector.isUnsafeCase(uset, uinfo) && !uinfo.isRefined()) {
            /*if (uinfo.getLine().getLine() == 163213) {
              System.out.println("Invisible line");
            }*/
            toRefine.add(uinfo);
          }
          /*if (uinfo.isRefined()) {
            System.out.println("Skip " + uinfo + " as refined");
          }*/
        }
      }
      pStat.DetectUnsafeCases.stop();
      for (UsageInfo target : toRefine) {
        //System.out.println("Refine " + target);
        ARGReachedSet ARGset = new ARGReachedSet(pReached);
        pStat.ComputePath.start();
        ARGPath pPath = computePath((ARGState)target.getKeyState(), ARGset);
        pStat.ComputePath.stop();
        if (pPath == null) {
          container.removeState(AbstractStates.extractStateByType(target.getKeyState(), UsageStatisticsState.class));
          System.out.println(target + " isn't found");
          continue top;
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
          //container.check();
          continue top;
        }
      }
    }
    return true;
  }


  @Override
  protected ARGPath computePath(ARGState pLastElement, ARGReachedSet pReachedSet) throws InterruptedException, CPATransferException {
    //assert pLastElement.isTarget();
    if (pLastElement == null) {
      //we delete this state from other unsafe
      return null;
    }
    int startId = new ARGState(pLastElement, null).getStateId();
    //System.out.println("ARGs in analysis: " + (startId - lastArg));
    //lastArg = startId;
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
