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
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.ConfigurableRefinementBlock;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.RefinementBlockFactory;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class UsageStatisticsRefiner extends BAMPredicateRefiner implements StatisticsProvider {

  private class Stats implements Statistics {

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
      pOut.println("");
      startingBlock.printStatistics(pOut);
    }

    @Override
    public String getName() {
      return "UsageStatisticsRefiner";
    }

  }

  final Stats pStat = new Stats();

  private final ConfigurableRefinementBlock<ReachedSet> startingBlock;

  public UsageStatisticsRefiner(Configuration pConfig, ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    super(pCpa);
    RefinementBlockFactory factory = new RefinementBlockFactory(this.subgraphStatesToReachedState, pCpa, pConfig);
    startingBlock = factory.create();
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
    return result;
  }

  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    return startingBlock.performRefinement(pReached).isTrue();
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    super.collectStatistics(pStatsCollection);
    pStatsCollection.add(pStat);
  }

}
