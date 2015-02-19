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
package org.sosy_lab.cpachecker.cpa.lockstatistics;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;

@Options(prefix="cpa.lockstatistics")
public class LockStatisticsCPA extends AbstractCPA implements ConfigurableProgramAnalysisWithBAM{

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(LockStatisticsCPA.class);
  }

  private final LockStatisticsReducer reducer;

  private LockStatisticsCPA (Configuration config, LogManager logger) throws InvalidConfigurationException {
    super("sep", "sep", new LockStatisticsDomain(), new LockStatisticsTransferRelation(config, logger));
    LockStatisticsTransferRelation transfer = (LockStatisticsTransferRelation) getTransferRelation();
    reducer             = new LockStatisticsReducer(config, transfer.annotatedfunctions, transfer.locks);
  }

  @Override
  public AbstractState getInitialState(CFANode node, StateSpacePartition pPartition) {
    return new LockStatisticsState();
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode, StateSpacePartition pPartition) {
    return new LockStatisticsPrecision(null);
  }

  @Override
  public Reducer getReducer() {
    return reducer;
  }
}
