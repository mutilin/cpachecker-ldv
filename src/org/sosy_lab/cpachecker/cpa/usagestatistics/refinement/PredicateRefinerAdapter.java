/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usagestatistics.refinement;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsPredicateRefiner;
import org.sosy_lab.cpachecker.exceptions.CPAException;

import com.google.common.collect.Sets;


public class PredicateRefinerAdapter extends WrappedConfigurableRefinementBlock<ARGPath, ARGPath> {
  UsageStatisticsPredicateRefiner refiner;
  LogManager logger;

  private final Map<Set<CFAEdge>, List<CFAEdge>> falseCache = new HashMap<>();
  private final Set<List<CFAEdge>> trueCache = new HashSet<>();

  //Statistics
  private Timer totalTimer = new Timer();
  private int solverFailures = 0;
  private int numberOfrepeatedPaths = 0;

  public PredicateRefinerAdapter(ConfigurableRefinementBlock<ARGPath> wrapper,
      ConfigurableProgramAnalysis pCpa, ReachedSet pReached) throws CPAException, InvalidConfigurationException {
    super(wrapper);

    if (!(pCpa instanceof WrapperCPA)) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " could not find the PredicateCPA");
    }

    BAMPredicateCPA predicateCpa = ((WrapperCPA)pCpa).retrieveWrappedCpa(BAMPredicateCPA.class);
    if (predicateCpa == null) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " needs an BAMPredicateCPA");
    }

    logger = predicateCpa.getLogger();

    refiner = UsageStatisticsPredicateRefiner.create(pCpa, pReached);
  }

  @Override
  public RefinementResult call(ARGPath pInput) throws CPAException, InterruptedException {
    totalTimer.start();
    RefinementResult result;

    try {
      List<CFAEdge> currentPath = pInput.getInnerEdges();
      if (trueCache.contains(currentPath)) {
        //Somewhen we have already refined this path as true
        result = RefinementResult.createTrue();
        totalTimer.stop();
        System.out.println(currentPath.hashCode() + " is in true cache");
        result = wrappedRefiner.call(pInput);
        totalTimer.start();
      } else {
        Set<CFAEdge> edgeSet = Sets.newHashSet(currentPath);
        if (falseCache.containsKey(edgeSet)) {
          numberOfrepeatedPaths++;
          System.out.println(currentPath.hashCode() + " is in false cache, previous is " + falseCache.get(edgeSet).hashCode());
          logger.log(Level.WARNING, "Path is repeated");
          //pInput.failureFlag = true;
          result = RefinementResult.createTrue();
        } else {
          /*if (!totalARGCleaning) {
            subtreesRemover.addStateForRemoving((ARGState)target.getKeyState());
            for (ARGState state : strategy.lastAffectedStates) {
              subtreesRemover.addStateForRemoving(state);
            }
          }*/
          CounterexampleInfo cex = refiner.performRefinement(pInput);

          if (!cex.isSpurious()) {
            trueCache.add(currentPath);
            totalTimer.stop();
            result = wrappedRefiner.call(pInput);
            totalTimer.start();
          } else {
            result = RefinementResult.createFalse();
            result.addInfo(PredicateRefinerAdapter.class, Pair.of(refiner.getLastAffectedStates(), refiner.getLastPrecision()));
            falseCache.put(edgeSet, currentPath);
          }
        }
      }
    } catch (IllegalStateException e) {
      //msat_solver return -1 <=> unknown
      //consider its as true;
      logger.log(Level.WARNING, "Solver exception: " + e.getMessage());
      solverFailures++;
    //  logger.log(Level.WARNING, "Consider " + target + " as true");
     // computer.setResultOfRefinement(target, true, pPath.getInnerEdges());
    //  target.failureFlag = true;
    //  pStat.Refinement.stop();
    //  pStat.ComputePath.start();
    //  continue;
      result = RefinementResult.createUnknown();
    }
    totalTimer.stop();
    return result;
  }

  @Override
  public void start(Map<Class<? extends RefinementInterface>, Object> pUpdateInfo) {
    if (pUpdateInfo.containsKey(PredicateRefinerAdapter.class)) {
      Object info = pUpdateInfo.get(PredicateRefinerAdapter.class);
      assert info instanceof ReachedSet;
      refiner.updateReachedSet((ReachedSet)info);
    }
    wrappedRefiner.start(pUpdateInfo);

  }

  @Override
  public void printStatistics(PrintStream pOut) {
    pOut.println("--PredicateRefinerAdapter--");
    pOut.println("Timer for block:                  " + totalTimer);
    pOut.println("Solver failures:                  " + solverFailures);
    pOut.println("Number of repeated paths:         " + numberOfrepeatedPaths);
    wrappedRefiner.printStatistics(pOut);
  }

  public Set<Set<CFAEdge>> getInterpolantCache() {
    //TODO rewrite using start and finish signals
    return falseCache.keySet();
  }

}
