/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.singleSuccessorCompactor;

import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;

public class SingleSuccessorCompactorCPA extends AbstractSingleWrapperCPA
implements ConfigurableProgramAnalysisWithBAM {

  /** if BAM is used, break chains of edges at block entry and exit. */
  @Nullable private BlockPartitioning partitioning = null;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(SingleSuccessorCompactorCPA.class);
  }

  public SingleSuccessorCompactorCPA(ConfigurableProgramAnalysis pCpa) {
    super(pCpa);
  }

  @Override
  public TransferRelation getTransferRelation() {
    return new SingleSuccessorCompactorTransferRelation(getWrappedCpa().getTransferRelation(), partitioning);
  }

  @Override
  public void setPartitioning(BlockPartitioning pPartitioning) {
    if (getWrappedCpa() instanceof ConfigurableProgramAnalysisWithBAM) {
      ((ConfigurableProgramAnalysisWithBAM)getWrappedCpa()).setPartitioning(pPartitioning);
    }
    partitioning = pPartitioning;
  }
}