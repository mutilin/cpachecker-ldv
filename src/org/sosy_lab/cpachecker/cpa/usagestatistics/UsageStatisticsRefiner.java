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

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.IdentifierIterator;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.PathIterator;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.PredicateRefinerAdapter;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.RefinementResult;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.RefinementStub;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.UsageIterator;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.CPAs;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsRefiner extends BAMPredicateRefiner implements StatisticsProvider {

  private class Stats implements Statistics {

    public final Timer ComputePath = new Timer();
    public final Timer Refinement = new Timer();
    public final Timer UnsafeCheck = new Timer();
    public final Timer CacheTime = new Timer();
    public final Timer CacheInterpolantsTime = new Timer();
    public int totalUsagesToRefine = 0;
    public int totalPathsToRefine = 0;
    public int skippedUsages = 0;

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
      pOut.println("Time for choosing target usage      " + UnsafeCheck);
      //pOut.println("Time for computing path             " + ComputePath);
      //pOut.println("Time for refinement                 " + Refinement);
     // pOut.println("Time for formula cache              " + CacheTime);
     // pOut.println("Time for interpolant cache          " + CacheInterpolantsTime);
      pOut.println("");
     // pOut.println("Total number of refinable usages:               " + totalUsagesToRefine);
     // pOut.println("Number of skipped usages due to repeated trace: " + skippedUsages);
    //  pOut.println("Total number of refinable paths:                " + totalPathsToRefine);
      startingBlock.printStatistics(pOut);
    }

    @Override
    public String getName() {
      return "UsageStatisticsRefiner";
    }

  }

  final Stats pStat = new Stats();
  private final ConfigurableProgramAnalysis cpa;

  private final IdentifierIterator startingBlock;

  public UsageStatisticsRefiner(Configuration pConfig, ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    super(pCpa);
    pConfig.inject(this);
    cpa = pCpa;
    UsageStatisticsCPA UScpa = CPAs.retrieveCPA(pCpa, UsageStatisticsCPA.class);
    LogManager logger = UScpa.getLogger();
    RefinementStub stub = new RefinementStub();
    PredicateRefinerAdapter predicateRefinerAdapter = new PredicateRefinerAdapter(stub, cpa, null);
    PathIterator pIterator = new PathIterator(this.subgraphStatesToReachedState, transfer, predicateRefinerAdapter, logger);
    UsageIterator uIterator = new UsageIterator(pIterator, null, UScpa.getLogger());
    startingBlock = new IdentifierIterator(uIterator, pConfig, pCpa, transfer, subgraphStatesToReachedState);
    //iCache = predicateRefinerAdapter.getInterpolantCache();
  }

  public static Refiner create(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    if (!(pCpa instanceof WrapperCPA)) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " could not find the PredicateCPA");
    }

    BAMPredicateCPA predicateCpa = ((WrapperCPA)pCpa).retrieveWrappedCpa(BAMPredicateCPA.class);
    if (predicateCpa == null) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " needs an BAMPredicateCPA");
    }

    UsageStatisticsRefiner result = new UsageStatisticsRefiner(predicateCpa.getConfiguration(), pCpa);
    //result.refinedStates = refinedStates;
    return result;
  }

  int i = 0;
  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    /*CPAs.retrieveCPA(cpa, UsageStatisticsCPA.class).getStats().printUnsafeRawdata(pReached, true);

    //iCache.initKeySet();
    UsageContainer container = AbstractStates.extractStateByType(pReached.getFirstState(), UsageStatisticsState.class).getContainer();
    //final RefineableUsageComputer computer = new RefineableUsageComputer(container, logger);
    BAMPredicateCPA bamcpa = CPAs.retrieveCPA(cpa, BAMPredicateCPA.class);
    assert bamcpa != null;
    //refinedStates.clear();
    //ARGReachedSet argReached = new ARGReachedSet(pReached);

    //Map<Integer, Integer> pathsToNumber = new TreeMap<>();

    logger.log(Level.INFO, ("Perform US refinement: " + i++));
    int originUnsafeSize = container.getUnsafeSize();
    System.out.println("Time: " + MainCPAStatistics.programTime);
    System.out.println("Unsafes: " + originUnsafeSize);
    Iterator<SingleIdentifier> iterator = container.getUnsafeIterator();
    int trueU = 0;
    while (iterator.hasNext()) {
      if (container.getUsages(iterator.next()) instanceof RefinedUsagePointSet) {
        trueU++;
      }
    }
    System.out.println("True unsafes: " + trueU);
    if (lastFalseUnsafeSize == -1) {
      lastFalseUnsafeSize = originUnsafeSize;
    }
    int counter = lastFalseUnsafeSize - originUnsafeSize;
    boolean refinementFinish = false;
    //UsageInfo target = null;
    pStat.UnsafeCheck.start();

    Map<Class<? extends RefinementInterface>, Object> updatingMap = new HashMap<>();
    updatingMap.put(PredicateRefinerAdapter.class, pReached);
    updatingMap.put(UsageIterator.class, container);
    refinerBlock.start(getClass(), updatingMap);

    iterator = container.getUnsafeIterator();
    while (iterator.hasNext()) {
      SingleIdentifier currentId = iterator.next();

      AbstractUsagePointSet pointSet = container.getUsages(currentId);
      if (pointSet instanceof UnrefinedUsagePointSet) {
        RefinementResult result = refinerBlock.call((UnrefinedUsagePointSet)pointSet);
        refinementFinish |= result.isFalse();

        PredicatePrecision info = (PredicatePrecision) result.getInfo(UsageIterator.class);

        if (info != null && !info.getLocalPredicates().isEmpty()) {
          PredicatePrecision updatedPrecision;
          if (precisionMap.containsKey(currentId)) {
            updatedPrecision = precisionMap.get(currentId).mergeWith(info);
          } else {
            updatedPrecision = info;
          }
          precisionMap.put(currentId, updatedPrecision);
        }
      }
    }

    //int realGlobalCounter = 0;
    while ((target = computer.getNextRefineableUsage()) != null) {
      pStat.totalUsagesToRefine++;
      pStat.UnsafeCheck.stopIfRunning();
      subgraphStatesToReachedState.clear();

      int numberOfPathRefined = 0;

      pStat.ComputePath.start();
      ARGPathIterator pathIterator = new ARGPathIterator(target, refinedStates, this.subgraphStatesToReachedState, transfer);
      strategy.lastAffectedState = null;

      ARGPath pPath;
      boolean pathIsTrue = false;
      while (!pathIsTrue && ((pPath = pathIterator.next(strategy.lastAffectedState)) != null)) {
        pStat.ComputePath.stop();

        CounterexampleInfo counterexample = null;
        try {
          pStat.Refinement.start();
          rootOfSubgraph = pPath.getFirstState();
          counterexample = performRefinement(argReached, pPath);
          numberOfPathRefined++;
          pStat.Refinement.stop();
        } catch (IllegalStateException e) {
          //msat_solver return -1 <=> unknown
          //consider its as true;
          logger.log(Level.WARNING, "Solver exception: " + e.getMessage());
          logger.log(Level.WARNING, "Consider " + target + " as true");
          computer.setResultOfRefinement(target, true, pPath.getInnerEdges());
          target.failureFlag = true;
          pStat.Refinement.stop();
          pStat.ComputePath.start();
          continue;
        }
        refinementFinish |= counterexample.isSpurious();
        if (counterexample.isSpurious()) {
          pStat.CacheTime.start();

          if (iCache.contains(target, Sets.newHashSet(pPath.asEdgesList()))) {
          	computer.setResultOfRefinement(target, true, pPath.getInnerEdges());
  	        logger.log(Level.WARNING, "Interpolants are repeated, consider " + target + " as true");
            target.failureFlag = true;
            pathIsTrue = true;
          } else {
            iCache.add(target, Sets.newHashSet(pPath.asEdgesList()));
          	if (!totalARGCleaning) {
            	subtreesRemover.addStateForRemoving((ARGState)target.getKeyState());
            	for (ARGState state : strategy.lastAffectedStates) {
            	  subtreesRemover.addStateForRemoving(state);
            	}
          	}
          }
          pStat.CacheTime.stop();
        } else {
          pathIsTrue = true;
          computer.setResultOfRefinement(target, true, pPath.getInnerEdges());
        }
        pStat.ComputePath.start();
        if (numberOfPathRefined >= refinablePathLimitation) {
          break;
        }
      }
      pStat.ComputePath.stop();

      if (!pathIsTrue) {
        logger.log(Level.FINE, "Skip " + target + " due to repeated chunk of the abstract state trace");
        computer.setResultOfRefinement(target, false, null);
        //Do not need to add state for predicates, because it has been already added the previous time
        subtreesRemover.addStateForRemoving((ARGState)target.getKeyState());
      }
      if (numberOfPathRefined == 0) {
        //Usage is skipped due to optimization
        pStat.skippedUsages++;
      }
      totalNumberOfPathRefined+=numberOfPathRefined;
      pStat.UnsafeCheck.start();
    }
    pStat.totalPathsToRefine += totalNumberOfPathRefined;
    int newTrueUnsafeSize = container.getTrueUnsafeSize();
    if (lastTrueUnsafes == -1) {
      //It's normal, if in the first iteration the true unsafes are not involved in counter
      lastTrueUnsafes = newTrueUnsafeSize;
    }
    counter += (newTrueUnsafeSize -lastTrueUnsafes);
    if (counter >= precisionReset) {
      Precision p = pReached.getPrecision(pReached.getFirstState());
      PredicatePrecision predicates = Precisions.extractPrecisionByType(p, PredicatePrecision.class);
      System.out.println("Clean: " + predicates.getLocalPredicates().size());
      pReached.updatePrecision(pReached.getFirstState(),
          Precisions.replaceByType(p, PredicatePrecision.empty(), Predicates.instanceOf(PredicatePrecision.class)));
      iCache.clear();
      lastFalseUnsafeSize = originUnsafeSize;
      lastTrueUnsafes = newTrueUnsafeSize;
    }
    if (refinementFinish) {
      //iCache.removeUnusedCacheEntries();
      bamcpa.clearAllCaches();
      ARGState.clearIdGenerator();
      ARGState firstState = (ARGState) pReached.getFirstState();
      CFANode firstNode = AbstractStates.extractLocation(firstState);
      Precision precision = pReached.getPrecision(firstState);
      if (totalARGCleaning) {
        transfer.cleanCaches();
      } else {
        MultipleARGSubtreeRemover subtreesRemover = transfer.getMultipleARGSubtreeRemover();
        subtreesRemover.cleanCaches();
      }
      pReached.clear();
      PredicatePrecision predicates = Precisions.extractPrecisionByType(precision, PredicatePrecision.class);
      for (SingleIdentifier id : container.getProcessedUnsafes()) {
        PredicatePrecision predicatesForId = precisionMap.get(id);
        if (predicatesForId != null) {
          predicates.subtract(predicatesForId);
        }
        precisionMap.remove(id);
      }
      pReached.add(cpa.getInitialState(firstNode, StateSpacePartition.getDefaultPartition()), precision);
      PredicatePrecision p = Precisions.extractPrecisionByType(pReached.getPrecision(pReached.getFirstState()),
          PredicatePrecision.class);

      subgraphStatesToReachedState.clear();
      refinerBlock.finish(UsageStatisticsRefiner.class);
      System.out.println("Total number of predicates: " + p.getLocalPredicates().size());
    }
    pStat.UnsafeCheck.stopIfRunning();*/
    RefinementResult result = startingBlock.call(pReached);
    return result.isTrue();
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    super.collectStatistics(pStatsCollection);
    pStatsCollection.add(pStat);
  }

}
