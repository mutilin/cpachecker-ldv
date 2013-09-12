/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.predicate;

import java.util.Collection;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithABM;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.predicate.relevantpredicates.AuxiliaryComputer;
import org.sosy_lab.cpachecker.cpa.predicate.relevantpredicates.CachingRelevantPredicatesComputer;
import org.sosy_lab.cpachecker.cpa.predicate.relevantpredicates.RefineableOccurrenceComputer;
import org.sosy_lab.cpachecker.cpa.predicate.relevantpredicates.RelevantPredicatesComputer;
import org.sosy_lab.cpachecker.exceptions.CPAException;


/**
 * Implements an ABM-based predicate CPA.
 */
@Options(prefix="cpa.predicate.abm")
public class ABMPredicateCPA extends PredicateCPA implements ConfigurableProgramAnalysisWithABM {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ABMPredicateCPA.class).withOptions(ABMBlockOperator.class);
  }

  private final ABMPredicateReducer reducer;
  private final ABMBlockOperator blk;
  private final ABMPredicateCPAStatistics stats;
  private final RelevantPredicatesComputer relevantPredicatesComputer;

  @Option(description="whether to use auxiliary predidates for reduction")
  private boolean auxiliaryPredicateComputer = true;


  private ABMPredicateCPA(Configuration config, LogManager logger,
      ABMBlockOperator pBlk, CFA pCfa, ReachedSetFactory reachedSetFactory)
          throws InvalidConfigurationException, CPAException {
    super(config, logger, pBlk, pCfa, reachedSetFactory);

    config.inject(this, ABMPredicateCPA.class);

    RelevantPredicatesComputer relevantPredicatesComputer;
    if (auxiliaryPredicateComputer) {
      relevantPredicatesComputer = new AuxiliaryComputer();
    } else {
      relevantPredicatesComputer = new RefineableOccurrenceComputer();
    }
    relevantPredicatesComputer = new CachingRelevantPredicatesComputer(relevantPredicatesComputer);
    this.relevantPredicatesComputer = relevantPredicatesComputer;

    reducer = new ABMPredicateReducer(getFormulaManager().getBooleanFormulaManager(), this, relevantPredicatesComputer);
    blk = pBlk;
    stats = new ABMPredicateCPAStatistics(reducer);
  }

  RelevantPredicatesComputer getRelevantPredicatesComputer() {
    return relevantPredicatesComputer;
  }

  BlockPartitioning getPartitioning() {
    return blk.getPartitioning();
  }

  @Override
  public Reducer getReducer() {
    return reducer;
  }

  public void setPartitioning(BlockPartitioning partitioning) {
    blk.setPartitioning(partitioning);
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    super.collectStatistics(pStatsCollection);
    pStatsCollection.add(stats);
  }

  ABMPredicateCPAStatistics getABMStats() {
    return stats;
  }
}
