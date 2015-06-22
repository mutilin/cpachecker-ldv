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
import static com.google.common.collect.FluentIterable.from;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.MainCPAStatistics;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.bam.BAMMultipleCEXSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.bam.MultipleARGSubtreeRemover;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractionManager;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateStaticRefiner;
import org.sosy_lab.cpachecker.cpa.usagestatistics.caches.InterpolantCache;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.RefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageContainer;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Precisions;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;

import com.google.common.base.Function;
import com.google.common.base.Predicates;


public class UsageStatisticsRefiner extends BAMPredicateRefiner implements StatisticsProvider {

  private class Stats implements Statistics {

    public final Timer ComputePath = new Timer();
    public final Timer Refinement = new Timer();
    public final Timer UnsafeCheck = new Timer();
    public final Timer CacheTime = new Timer();
    public final Timer CacheInterpolantsTime = new Timer();
    public int totalUsagesToRefine = 0;
    public int skippedUsages = 0;

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
      pOut.println("Time for choosing target usage      " + UnsafeCheck);
      pOut.println("Time for computing path             " + ComputePath);
      pOut.println("Time for refinement                 " + Refinement);
      pOut.println("Time for formula cache              " + CacheTime);
      pOut.println("Time for interpolant cache          " + CacheInterpolantsTime);
      pOut.println("");
      pOut.println("Total number of refinable usages:               " + totalUsagesToRefine);
      pOut.println("Number of skipped usages due to repeated trace: " + skippedUsages);
    }

    @Override
    public String getName() {
      return "UsageStatisticsRefiner";
    }

  }

  final Stats pStat = new Stats();
  private final ConfigurableProgramAnalysis cpa;
  private final LogManager logger;
  private final int NUMBER_FOR_RESET_PRECISION;
  private UsageStatisticsRefinementStrategy strategy;
  private Set<List<Integer>> refinedStates;
  private final InterpolantCache iCache = new InterpolantCache();

  public UsageStatisticsRefiner(ConfigurableProgramAnalysis pCpa, UsageStatisticsRefinementStrategy pStrategy) throws CPAException, InvalidConfigurationException {
    super(pCpa, pStrategy);
    cpa = pCpa;
    UsageStatisticsCPA UScpa = CPAs.retrieveCPA(pCpa, UsageStatisticsCPA.class);
    NUMBER_FOR_RESET_PRECISION = UScpa.getThePrecisionCleaningLimit();
    logger = UScpa.getLogger();
    strategy = pStrategy;
  }

  public static Refiner create(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    if (!(pCpa instanceof WrapperCPA)) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " could not find the PredicateCPA");
    }

    BAMPredicateCPA predicateCpa = ((WrapperCPA)pCpa).retrieveWrappedCpa(BAMPredicateCPA.class);
    if (predicateCpa == null) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " needs an BAMPredicateCPA");
    }

    LogManager logger = predicateCpa.getLogger();

    final Set<List<Integer>> refinedStates = new HashSet<>();

    UsageStatisticsRefinementStrategy strategy = new UsageStatisticsRefinementStrategy(
                                          predicateCpa.getConfiguration(),
                                          logger,
                                          predicateCpa,
                                          predicateCpa.getSolver(),
                                          predicateCpa.getPredicateManager(),
                                          predicateCpa.getStaticRefiner(),
                                          refinedStates);

    UsageStatisticsRefiner result = new UsageStatisticsRefiner(pCpa, strategy);
    result.refinedStates = refinedStates;
    return result;
  }

  int i = 0;
  int lastFalseUnsafeSize = -1;
  int lastTrueUnsafes = -1;
  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    final UsageContainer container =
        AbstractStates.extractStateByType(pReached.getFirstState(), UsageStatisticsState.class).getContainer();

    iCache.initKeySet();
    final RefineableUsageComputer computer = new RefineableUsageComputer(container, logger);
    MultipleARGSubtreeRemover subtreesRemover = transfer.getMultipleARGSubtreeRemover();
    strategy.init(computer, subgraphStatesToReachedState);
    BAMPredicateCPA bamcpa = CPAs.retrieveCPA(cpa, BAMPredicateCPA.class);
    assert bamcpa != null;
    refinedStates.clear();
    ARGReachedSet argReached = new ARGReachedSet(pReached);

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
    UsageInfo target = null;
    pStat.UnsafeCheck.start();
    while ((target = computer.getNextRefineableUsage()) != null) {
      pStat.totalUsagesToRefine++;
      pStat.UnsafeCheck.stopIfRunning();
      subgraphStatesToReachedState.clear();

      pStat.ComputePath.start();
      ARGPath pPath = computePath((ARGState)target.getKeyState(), refinedStates);
      pStat.ComputePath.stopIfRunning();

      if (pPath == null) {
        logger.log(Level.FINE, "Skip " + target + " due to repeated chunk of the abstract state trace");
        computer.setResultOfRefinement(target, false, null);
        //Do not need to add state for predicates, because it has been already added the previous time
        subtreesRemover.addStateForRemoving((ARGState)target.getKeyState());
        pStat.skippedUsages++;
        pStat.UnsafeCheck.start();
        continue;
      }

      List<ARGState> abstractTrace = pPath.asStatesList();

      CounterexampleInfo counterexample = null;
      try {
        pStat.Refinement.start();
        counterexample = performRefinement(argReached, pPath);
        pStat.Refinement.stop();
      } catch (IllegalStateException e) {
        //msat_solver return -1 <=> unknown
        //consider its as true;
        logger.log(Level.WARNING, "Solver exception: " + e.getMessage());
        logger.log(Level.WARNING, "Consider " + target + " as true");
        computer.setResultOfRefinement(target, true, pPath.getInnerEdges());
        target.failureFlag = true;
        pStat.Refinement.stop();
        pStat.UnsafeCheck.start();
        continue;
      }
      refinementFinish |= counterexample.isSpurious();
      if (counterexample.isSpurious()) {
        pStat.CacheInterpolantsTime.start();
        Iterator<Pair<Object, PathTemplate>> pairIterator = counterexample.getAllFurtherInformation().iterator();
        List<BooleanFormula> formulas = (List<BooleanFormula>) pairIterator.next().getFirst();
        pStat.CacheInterpolantsTime.stop();

        pStat.CacheTime.start();
        if (iCache.contains(target, formulas, abstractTrace)) {
        	computer.setResultOfRefinement(target, true, pPath.getInnerEdges());
	        logger.log(Level.WARNING, "Interpolants are repeated, consider " + target + " as true");
          target.failureFlag = true;
        } else {
          iCache.add(target, formulas, abstractTrace);
        	computer.setResultOfRefinement(target, false, pPath.getInnerEdges());
        	subtreesRemover.addStateForRemoving((ARGState)target.getKeyState());
        	for (ARGState state : strategy.lastAffectedStates) {
        	  subtreesRemover.addStateForRemoving(state);
        	}
        }
        pStat.CacheTime.stop();
      } else {
        computer.setResultOfRefinement(target, !counterexample.isSpurious(), pPath.getInnerEdges());
      }
      pStat.UnsafeCheck.start();
    }
    int newTrueUnsafeSize = container.getTrueUnsafeSize();
    if (lastTrueUnsafes == -1) {
      //It's normal, if in the first iteration the true unsafes are not involved in counter
      lastTrueUnsafes = newTrueUnsafeSize;
    }
    counter += (newTrueUnsafeSize -lastTrueUnsafes);
    if (counter >= NUMBER_FOR_RESET_PRECISION) {
      Precision p = pReached.getPrecision(pReached.getFirstState());
      PredicatePrecision predicates = Precisions.extractPrecisionByType(p, PredicatePrecision.class);
      System.out.println("Clean: " + predicates.getLocalPredicates().size());
      pReached.updatePrecision(pReached.getFirstState(),
          Precisions.replaceByType(p, PredicatePrecision.empty(), Predicates.instanceOf(PredicatePrecision.class)));
      iCache.reset();
      lastFalseUnsafeSize = originUnsafeSize;
      lastTrueUnsafes = newTrueUnsafeSize;
    }
    if (refinementFinish) {
      iCache.removeUnusedCacheEntries();
      bamcpa.clearAllCaches();
      ARGState.clearIdGenerator();
      ARGState firstState = (ARGState) pReached.getFirstState();
      CFANode firstNode = AbstractStates.extractLocation(firstState);
      Precision precision = pReached.getPrecision(firstState);
      subtreesRemover.cleanCaches();
      pReached.clear();
      PredicatePrecision predicates = Precisions.extractPrecisionByType(precision, PredicatePrecision.class);
      for (SingleIdentifier id : container.getProcessedUnsafes()) {
        PredicatePrecision predicatesForId = strategy.precisionMap.get(id);
        if (predicatesForId != null) {
          predicates.subtract(predicatesForId);
        }
        strategy.precisionMap.remove(id);
      }
      pReached.add(cpa.getInitialState(firstNode, StateSpacePartition.getDefaultPartition()), precision);
      PredicatePrecision p = Precisions.extractPrecisionByType(pReached.getPrecision(pReached.getFirstState()),
          PredicatePrecision.class);

      System.out.println("Total number of predicates: " + p.getLocalPredicates().size());
    }
    pStat.UnsafeCheck.stopIfRunning();
    return refinementFinish;
  }

  ARGPath computePath(ARGState pLastElement, Set<List<Integer>> processedStates) throws InterruptedException, CPATransferException {
    assert (pLastElement != null && !pLastElement.isDestroyed());
      //we delete this state from other unsafe
    rootOfSubgraph = transfer.findPath(pLastElement, subgraphStatesToReachedState, processedStates);
    assert (rootOfSubgraph != null);
    if (rootOfSubgraph == BAMMultipleCEXSubgraphComputer.DUMMY_STATE_FOR_REPEATED_STATE) {
      return null;
    }
    return ARGUtils.getRandomPath(rootOfSubgraph);
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    super.collectStatistics(pStatsCollection);
    pStatsCollection.add(pStat);
  }

  protected static class UsageStatisticsRefinementStrategy extends BAMPredicateAbstractionRefinementStrategy {

    protected final Map<SingleIdentifier, PredicatePrecision> precisionMap = new HashMap<>();
    protected RefineableUsageComputer computer;
    private final Set<List<Integer>> refinedStates;
    private Map<ARGState, ARGState> subgraphStatesToReachedState;
    private Set<ARGState> lastAffectedStates = new HashSet<>();

    private final Function<ARGState, Integer> GET_ORIGIN_STATE_NUMBERS = new Function<ARGState, Integer>() {
      @Override
      @Nullable
      public Integer apply(@Nullable ARGState pInput) {
        assert subgraphStatesToReachedState.containsKey(pInput);
        return subgraphStatesToReachedState.get(pInput).getStateId();
      }
    };

    public UsageStatisticsRefinementStrategy(final Configuration config, final LogManager logger,
        final BAMPredicateCPA predicateCpa,
        final Solver pSolver,
        final PredicateAbstractionManager pPredAbsMgr,
        final PredicateStaticRefiner pStaticRefiner,
        final Set<List<Integer>> pRefinedStates)
            throws CPAException, InvalidConfigurationException {
      super(config, logger, predicateCpa, pSolver, pPredAbsMgr, pStaticRefiner);
      refinedStates = pRefinedStates;
    }

    @Override
    protected void finishRefinementOfPath(ARGState pUnreachableState,
        List<ARGState> pAffectedStates, ARGReachedSet pReached,
        boolean pRepeatedCounterexample)
        throws CPAException {

      super.finishRefinementOfPath(pUnreachableState, pAffectedStates, pReached, pRepeatedCounterexample);

      SingleIdentifier currentId = computer.getCurrentRefiningId();
      PredicatePrecision updatedPrecision;
      if (precisionMap.containsKey(currentId)) {
        updatedPrecision = precisionMap.get(currentId).mergeWith(newPrecisionFromPredicates);
      } else {
        updatedPrecision = newPrecisionFromPredicates;
      }
      precisionMap.put(currentId, updatedPrecision);

      List<Integer>changedStateNumbers = from(pAffectedStates).transform(GET_ORIGIN_STATE_NUMBERS).toList();
      refinedStates.add(changedStateNumbers);

      lastAffectedStates.clear();
      for (ARGState backwardState : pAffectedStates) {
        lastAffectedStates.add(subgraphStatesToReachedState.get(backwardState));
      }
    }

    private void init(RefineableUsageComputer pComputer, Map<ARGState, ARGState> map) {
      computer = pComputer;
      subgraphStatesToReachedState = map;
    }
  }
}
