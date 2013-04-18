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
package org.sosy_lab.cpachecker.cpa.lockStatistics;

import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;


public class LockStatisticsReducer implements Reducer {


  @Override
  public AbstractState getVariableReducedState(AbstractState pExpandedElement, Block pContext, CFANode pCallNode) {
    LockStatisticsState lockState = (LockStatisticsState) pExpandedElement;
    LockStatisticsState reducedState = lockState.clone();
    reducedState.initReplaceLabels();
    reducedState.setRestoreState(null);
    return reducedState;
  }

  @Override
  public AbstractState getVariableExpandedState(AbstractState pRootElement, Block pReducedContext,
      AbstractState pReducedElement) {

    LockStatisticsState expandedState = ((LockStatisticsState)pReducedElement).clone();
    LockStatisticsState rootState = (LockStatisticsState) pRootElement;
    for (LockStatisticsLock lock : expandedState.getLocks()) {
      for (LockStatisticsLock rootLock : rootState.getLocks()) {
        if (lock.hasEqualNameAndVariable(rootLock)) {
          try {
            lock.replace(rootLock);
          } catch (HandleCodeException e) {
            System.err.println(e.getMessage());
          }
          break;
        }
      }
    }
    expandedState.setRestoreState(rootState.getRestoreState());
    return expandedState;
  }

  @Override
  public Precision getVariableReducedPrecision(Precision pPrecision, Block pContext) {
    return pPrecision;
  }

  @Override
  public Precision getVariableExpandedPrecision(Precision pRootPrecision, Block pRootContext,
      Precision pReducedPrecision) {
    return pReducedPrecision;
  }

  @Override
  public Object getHashCodeForState(AbstractState pElementKey, Precision pPrecisionKey) {
    LockStatisticsState elementKey = (LockStatisticsState)pElementKey;

    return elementKey.getLocks();
  }

  @Override
  public int measurePrecisionDifference(Precision pPrecision, Precision pOtherPrecision) {
    return 0;
  }

  @Override
  public AbstractState getVariableReducedStateForProofChecking(AbstractState pExpandedState, Block pContext,
      CFANode pCallNode) {
    return getVariableReducedState(pExpandedState, pContext, pCallNode);
  }

  @Override
  public AbstractState getVariableExpandedStateForProofChecking(AbstractState pRootState, Block pReducedContext,
      AbstractState pReducedState) {
    return getVariableExpandedState(pRootState, pReducedContext, pReducedState);
  }

}
