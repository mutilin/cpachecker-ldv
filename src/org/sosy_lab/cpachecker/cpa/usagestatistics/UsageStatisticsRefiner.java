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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.MainCPAStatistics;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.cpa.usagestatistics.caches.InterpolantCache;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageContainer;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Precisions;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;


public class UsageStatisticsRefiner extends BAMPredicateRefiner implements StatisticsProvider {

  private class Stats implements Statistics {

    public final Timer ComputePath = new Timer();
    public final Timer Refinement = new Timer();
    public final Timer UnsafeCheck = new Timer();
    public final Timer CacheTime = new Timer();
    public final Timer CacheInterpolantsTime = new Timer();

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
      pOut.println("Time for choosing target usage      " + UnsafeCheck);
      pOut.println("Time for computing path             " + ComputePath);
      pOut.println("Time for refinement                 " + Refinement);
      pOut.println("Time for formula cache              " + CacheTime);
      pOut.println("Time for interpolants cache         " + CacheInterpolantsTime);
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
  private InterpolantCache iCache = new InterpolantCache();

  public UsageStatisticsRefiner(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    super(pCpa);
    cpa = pCpa;
    UsageStatisticsCPA UScpa = CPAs.retrieveCPA(pCpa, UsageStatisticsCPA.class);
    NUMBER_FOR_RESET_PRECISION = UScpa.getThePrecisionCleaningLimit();
    logger = UScpa.getLogger();
  }

  public static Refiner create(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    return new UsageStatisticsRefiner(pCpa);
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
    BAMPredicateCPA bamcpa = CPAs.retrieveCPA(cpa, BAMPredicateCPA.class);
    assert bamcpa != null;
    Set<List<ARGState>> refinedStates = new HashSet<>();

    logger.log(Level.INFO, ("Perform US refinement: " + i++));
    int originUnsafeSize = container.getUnsafeSize();
    System.out.println("Time: " + MainCPAStatistics.programTime);
    System.out.println("Unsafes: " + originUnsafeSize);
    Iterator<SingleIdentifier> iterator = container.getUnsafeIterator();
    int trueU = 0;
    while (iterator.hasNext()) {
      SingleIdentifier id = iterator.next();
      if (container.getUsages(id).isTrueUnsafe()) {
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
top:while ((target = computer.getNextRefineableUsage()) != null) {
      pStat.UnsafeCheck.stopIfRunning();
      pathStateToReachedState.clear();
      pStat.ComputePath.start();
      ARGPath pPath = computePath((ARGState)target.getKeyState());
      pStat.ComputePath.stopIfRunning();
      assert (pPath != null);

      pStat.CacheInterpolantsTime.start();
      List<ARGState> abstractTrace = pPath.asStatesList();
      abstractTrace = from(abstractTrace)./*filter(new Predicate<ARGState>() {
        @Override
        public boolean apply(@Nullable ARGState pInput) {
          return AbstractStates.extractStateByType(pInput, PredicateAbstractState.class).isAbstractionState();
        }
      }).*/transform(new Function<ARGState, ARGState>() {
        @Override
        @Nullable
        public ARGState apply(@Nullable ARGState pInput) {
          assert pathStateToReachedState.containsKey(pInput);
          return pathStateToReachedState.get(pInput);
        }
      }).toList();
      for (List<ARGState> previousTrace : refinedStates) {
        if (abstractTrace.containsAll(previousTrace)) {
          logger.log(Level.INFO, "Hey! I found repeated trace " + target + ". I don't want to refine it");
          computer.setResultOfRefinement(target, false);
          pStat.CacheInterpolantsTime.stop();
          pStat.UnsafeCheck.start();
          continue top;
        }
      }
      pStat.CacheInterpolantsTime.stop();
      try {
        pStat.Refinement.start();
        CounterexampleInfo counterexample = super.performRefinement0(
            new BAMReachedSet(transfer, new ARGReachedSet(pReached), pPath, pathStateToReachedState), pPath);
        refinementFinish |= counterexample.isSpurious();
        if (counterexample.isSpurious()) {

          List<CFAEdge> edges = pPath.getInnerEdges();
          edges = from(edges).filter(new Predicate<CFAEdge>() {
            @Override
            public boolean apply(@Nullable CFAEdge pInput) {
              if (pInput instanceof CDeclarationEdge) {
                if (((CDeclarationEdge)pInput).getDeclaration().isGlobal() ||
                    pInput.getSuccessor().getFunctionName().equals("ldv_main")) {
                }
                return false;
              } else if (pInput.getSuccessor().getFunctionName().equals("ldv_main")
                  && pInput instanceof CAssumeEdge) {
                //Remove infinite switch, it's too long
                return false;
              } else {
                return true;
              }
            }
          }).toList();
          for (CFAEdge edge : edges) {
            System.out.println(edge);
          }
          Iterator<Pair<Object, PathTemplate>> pairIterator = counterexample.getAllFurtherInformation().iterator();
          List<BooleanFormula> formulas = (List<BooleanFormula>) pairIterator.next().getFirst();
          List<ARGState> interpolants = (List<ARGState>) pairIterator.next().getFirst();
          pStat.CacheInterpolantsTime.start();
          interpolants = from(interpolants).transform(new Function<ARGState, ARGState>() {
            @Override
            @Nullable
            public ARGState apply(@Nullable ARGState pInput) {
              assert pathStateToReachedState.containsKey(pInput);
              return pathStateToReachedState.get(pInput);
            }
          }).toList();
          refinedStates.add(interpolants);
          pStat.CacheInterpolantsTime.stop();
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
      transfer.clearCaches();
      bamcpa.clearAllCaches();
      ARGState firstState = (ARGState) pReached.getFirstState();
      CFANode firstNode = AbstractStates.extractLocation(firstState);
      ARGState.clearIdGenerator();
      Precision precision = pReached.getPrecision(firstState);
      pReached.clear();
      pReached.add(cpa.getInitialState(firstNode, StateSpacePartition.getDefaultPartition()), precision);
    }
    pStat.UnsafeCheck.stopIfRunning();
    return refinementFinish;
  }

  protected ARGPath computePath(ARGState pLastElement) throws InterruptedException, CPATransferException {
    assert (pLastElement != null && !pLastElement.isDestroyed());
      //we delete this state from other unsafe
    ARGState subgraph = transfer.findPath(pLastElement, pathStateToReachedState);
    assert (subgraph != null);
    return ARGUtils.getRandomPath(subgraph);
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    super.collectStatistics(pStatsCollection);
    pStatsCollection.add(pStat);
  }
}
