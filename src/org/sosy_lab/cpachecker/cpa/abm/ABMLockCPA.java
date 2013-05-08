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
package org.sosy_lab.cpachecker.cpa.abm;

import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;

@Options(prefix="cpa.abm")
public class ABMLockCPA extends ABMCPA {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ABMLockCPA.class);
  }

  /*@Option(description="Type of partitioning (FunctionAndLoopPartitioning or DelayedFunctionAndLoopPartitioning)\n"
      + "or any class that implements a PartitioningHeuristic")
  @ClassOption(packagePrefix="org.sosy_lab.cpachecker.cfa.blocks.builder")
  protected Class<? extends PartitioningHeuristic> blockHeuristic = FunctionAndLoopPartitioning.class;*/

  public ABMLockCPA(ConfigurableProgramAnalysis pCpa, Configuration config, LogManager pLogger,
    ReachedSetFactory pReachedSetFactory, CFA pCfa) throws InvalidConfigurationException, CPAException {
    super(pCpa, config, pLogger, pReachedSetFactory, pCfa);
    config.inject(this);
  }

  @Override
  public AbstractState getInitialState(CFANode node)  {
    if (blockPartitioning == null) {
      blockPartitioning = heuristic.buildPartitioning(node);
      transfer.setBlockPartitioning(blockPartitioning);

      UsageStatisticsCPA usCpa = ((WrapperCPA) getWrappedCpa()).retrieveWrappedCpa(UsageStatisticsCPA.class);
      if (usCpa != null) {
        usCpa.getStats().setStackRestoration(transfer);
      }

      Map<AbstractState, Precision> forwardPrecisionToExpandedPrecision = new HashMap<>();
      transfer.setForwardPrecisionToExpandedPrecision(forwardPrecisionToExpandedPrecision);
      prec.setForwardPrecisionToExpandedPrecision(forwardPrecisionToExpandedPrecision);
    }
    return getWrappedCpa().getInitialState(node);
  }
}
