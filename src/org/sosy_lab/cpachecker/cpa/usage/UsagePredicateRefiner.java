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
package org.sosy_lab.cpachecker.cpa.usage;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractionManager;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateStaticRefiner;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;


public class UsagePredicateRefiner extends BAMPredicateRefiner {

  private UsageStatisticsRefinementStrategy strategy;
  private ARGReachedSet ARGReached;

  public UsagePredicateRefiner(ConfigurableProgramAnalysis pCpa,
      UsageStatisticsRefinementStrategy pStrategy, ReachedSet reached) throws InvalidConfigurationException {
    super(pCpa, pStrategy);
    strategy = pStrategy;
    if (reached != null) {
      ARGReached = new ARGReachedSet(reached);
    }
  }

  public static UsagePredicateRefiner create(ConfigurableProgramAnalysis pCpa, ReachedSet reached) throws  InvalidConfigurationException {
    if (!(pCpa instanceof WrapperCPA)) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " could not find the PredicateCPA");
    }

    BAMPredicateCPA predicateCpa = ((WrapperCPA)pCpa).retrieveWrappedCpa(BAMPredicateCPA.class);
    if (predicateCpa == null) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " needs an BAMPredicateCPA");
    }

    LogManager logger = predicateCpa.getLogger();

    UsageStatisticsRefinementStrategy strategy = new UsageStatisticsRefinementStrategy(
                                          predicateCpa.getConfiguration(),
                                          logger,
                                          predicateCpa,
                                          predicateCpa.getSolver(),
                                          predicateCpa.getPredicateManager(),
                                          predicateCpa.getStaticRefiner());

    return new UsagePredicateRefiner(pCpa, strategy, reached);
  }

  public CounterexampleInfo performRefinement(ARGPath path) throws CPAException, InterruptedException {
    rootOfSubgraph = path.getFirstState();
    return performRefinement(ARGReached, path);
  }

  public List<ARGState> getLastAffectedStates() {
    return strategy.lastAffectedStates;
  }

  public PredicatePrecision getLastPrecision() {
    return strategy.lastAddedPrecision;
  }

  public Precision getCurrentPrecision() {
    return ARGReached.asReachedSet().getPrecision(ARGReached.asReachedSet().getFirstState());
  }

  public void updateReachedSet(ReachedSet pReached) {
    ARGReached = new ARGReachedSet(pReached);
  }

  public Map<ARGState, ARGState> getInternalMapForStates() {
    return this.subgraphStatesToReachedState;
  }

  protected static class UsageStatisticsRefinementStrategy extends BAMPredicateAbstractionRefinementStrategy {

    private List<ARGState> lastAffectedStates = new LinkedList<>();
    private PredicatePrecision lastAddedPrecision;

    public UsageStatisticsRefinementStrategy(final Configuration config, final LogManager logger,
        final BAMPredicateCPA predicateCpa,
        final Solver pSolver,
        final PredicateAbstractionManager pPredAbsMgr,
        final PredicateStaticRefiner pStaticRefiner) throws InvalidConfigurationException {
      super(config, logger, predicateCpa, pSolver, pPredAbsMgr, pStaticRefiner);
    }

    @Override
    protected void finishRefinementOfPath(ARGState pUnreachableState,
        List<ARGState> pAffectedStates, ARGReachedSet pReached,
        boolean pRepeatedCounterexample)
        throws CPAException {

      super.finishRefinementOfPath(pUnreachableState, pAffectedStates, pReached, pRepeatedCounterexample);

      lastAddedPrecision = newPrecisionFromPredicates;

      lastAffectedStates.clear();
      for (ARGState backwardState : pAffectedStates) {
        lastAffectedStates.add(backwardState);
      }
    }
  }
}
