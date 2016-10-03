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

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsPredicateRefiner;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException.Reason;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Precisions;

import com.google.common.collect.Sets;


public class PredicateRefinerAdapter extends GenericSinglePathRefiner {
  UsageStatisticsPredicateRefiner refiner;
  LogManager logger;

  private final Map<Set<CFAEdge>, PredicatePrecision> falseCache = new HashMap<>();
  private final Map<Set<CFAEdge>, PredicatePrecision> falseCacheForCurrentIteration = new HashMap<>();
  //private final Multimap<SingleIdentifier, Set<CFAEdge>> idCached = LinkedHashMultimap.create();
  private final Set<Set<CFAEdge>> trueCache = new HashSet<>();

  //Statistics
  private int solverFailures = 0;
  private int numberOfrepeatedPaths = 0;
  private int numberOfrefinedPaths = 0;
  private int numberOfBAMupdates = 0;

  public PredicateRefinerAdapter(ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>> wrapper,
      ConfigurableProgramAnalysis pCpa, ReachedSet pReached) throws InvalidConfigurationException {
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
  public RefinementResult call(ExtendedARGPath pInput) throws CPAException, InterruptedException {
    RefinementResult result;

    List<CFAEdge> currentPath = pInput.getInnerEdges();
    if (trueCache.contains(currentPath)) {
      //Somewhen we have already refined this path as true
      result = RefinementResult.createTrue();
    } else {
      Set<CFAEdge> edgeSet = Sets.newHashSet(currentPath);
      if (falseCache.containsKey(edgeSet)) {
        PredicatePrecision previousPreds = falseCache.get(edgeSet);
        Precision currentPrecision = refiner.getCurrentPrecision();
        PredicatePrecision currentPreds = Precisions.extractPrecisionByType(currentPrecision, PredicatePrecision.class);

        if (previousPreds.calculateDifferenceTo(currentPreds) == 0) {
          try {
            result = performPredicateRefinement(pInput);
            logger.log(Level.WARNING, "Path is repeated, but BAM Refiner was successfully updated");
            //BAM can refine with updated predicate refiner, congratulate him.
            numberOfBAMupdates++;
          } catch (RefinementFailedException e) {
            assert e.getReason() == Reason.RepeatedCounterexample;
            //All old interpolants are present => we are looped
            numberOfrepeatedPaths++;
            logger.log(Level.WARNING, "Path is repeated, BAM is looped");
            pInput.getUsageInfo().failureFlag = true;
            result = RefinementResult.createUnknown();
          }
        } else {
          //rerefine it to obtain new states
          logger.log(Level.WARNING, "Path is repeated, but predicates are missed");
          result = performPredicateRefinement(pInput);
          //We expect the same result
          assert result.isFalse();
        }
        //pInput.failureFlag = true;
      } else {
        if (falseCacheForCurrentIteration.containsKey(edgeSet)) {
          //We refined it for other usage
          //just return the result;
          //PredicatePrecision previousPreds = falseCacheForCurrentIteration.get(edgeSet);
          return RefinementResult.createFalse();
        } else {
          /*if (!totalARGCleaning) {
            subtreesRemover.addStateForRemoving((ARGState)target.getKeyState());
            for (ARGState state : strategy.lastAffectedStates) {
              subtreesRemover.addStateForRemoving(state);
            }
          }*/
          result = performPredicateRefinement(pInput);
        }
      }
    }
    return result;
  }

  private RefinementResult performPredicateRefinement(ExtendedARGPath path) throws CPAException, InterruptedException {
    RefinementResult result;
    try {
      numberOfrefinedPaths++;
      CounterexampleInfo cex = refiner.performRefinement(path);
      Set<CFAEdge> edgeSet = Sets.newHashSet(path.getInnerEdges());

      if (!cex.isSpurious()) {
        trueCache.add(edgeSet);
        result = RefinementResult.createTrue();
      } else {
        result = RefinementResult.createFalse();
        result.addInfo(PredicateRefinerAdapter.class, refiner.getLastAffectedStates());
        result.addPrecision(refiner.getLastPrecision());
        falseCacheForCurrentIteration.put(edgeSet, refiner.getLastPrecision());
        //idCached.put(path.getUsageInfo().getId(), edgeSet);
      }

    } catch (IllegalStateException e) {
      //msat_solver return -1 <=> unknown
      //consider its as true;
      logger.log(Level.WARNING, "Solver exception: " + e.getMessage());
      solverFailures++;
      result = RefinementResult.createUnknown();
    }
    return result;
  }

  @Override
  protected void handleUpdateSignal(Class<? extends RefinementInterface> pCallerClass, Object pData) {
    if (pCallerClass.equals(IdentifierIterator.class)) {
      if (pData instanceof ReachedSet) {
        //Updating new reached set
        refiner.updateReachedSet((ReachedSet)pData);
      }
    }
  }

  @Override
  protected Object handleFinishSignal(Class<? extends RefinementInterface> pCallerClass) {
    if (pCallerClass.equals(IdentifierIterator.class)) {
      for (Set<CFAEdge> edges : falseCacheForCurrentIteration.keySet()) {
        PredicatePrecision precision = falseCacheForCurrentIteration.get(edges);
        //false cache may contain other precision
        //It happens if we clean it for other Id and rerefine it now
        //Just replace old precision
        falseCache.put(edges, precision);
      }
      falseCacheForCurrentIteration.clear();
    }
    return  null;
  }

  @Override
  public void printAdditionalStatistics(PrintStream pOut) {
    pOut.println("--PredicateRefinerAdapter--");
    pOut.println("Number of refined paths:          " + numberOfrefinedPaths);
    pOut.println("Solver failures:                  " + solverFailures);
    pOut.println("Number of repeated paths:         " + numberOfrepeatedPaths);
    pOut.println("Number of BAM updates:            " + numberOfBAMupdates);
    pOut.println("Size of false cache:              " + falseCache.size());
  }

  Map<ARGState, ARGState> getInternalMapForStates() {
    return refiner.getInternalMapForStates();
  }

}
