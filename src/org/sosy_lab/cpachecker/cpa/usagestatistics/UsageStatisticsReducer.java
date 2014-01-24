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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;

public class UsageStatisticsReducer implements Reducer {
  private final Reducer wrappedReducer;

  public UsageStatisticsReducer(Reducer pWrappedReducer) {
    wrappedReducer = pWrappedReducer;
  }

  @Override
  public AbstractState getVariableReducedState(AbstractState pExpandedElement,
                                          Block pContext, CFANode pLocation) {
    UsageStatisticsState funElement = (UsageStatisticsState)pExpandedElement;
    AbstractState red = wrappedReducer.getVariableReducedState(funElement.getWrappedState(), pContext, pLocation);
    return new UsageStatisticsState(red);

  }

  @Override
  public AbstractState getVariableExpandedState(AbstractState pRootElement,
                        Block pReducedContext, AbstractState pReducedElement) {
    UsageStatisticsState funRootState = (UsageStatisticsState)pRootElement;
    UsageStatisticsState funReducedState = (UsageStatisticsState)pReducedElement;
    AbstractState exp = wrappedReducer.getVariableExpandedState(funRootState.getWrappedState(), pReducedContext, funReducedState.getWrappedState());
    UsageStatisticsState result = funRootState.clone(exp);
    if (funReducedState.isTarget()) {
      result.setTarget();
    }
    return result;
  }

  @Override
  public Object getHashCodeForState(AbstractState pElementKey, Precision pPrecisionKey) {
    UsageStatisticsState funElement = (UsageStatisticsState)pElementKey;
    UsageStatisticsPrecision precision = (UsageStatisticsPrecision) pPrecisionKey;
    return wrappedReducer.getHashCodeForState(funElement.getWrappedState(), precision.getWrappedPrecision());
  }

  @Override
  public Precision getVariableReducedPrecision(Precision pPrecision, Block pContext) {
    UsageStatisticsPrecision newPrecision = ((UsageStatisticsPrecision)pPrecision).clone(wrappedReducer.getVariableReducedPrecision(
        ((UsageStatisticsPrecision)pPrecision).getWrappedPrecision(), pContext));
    return newPrecision;
  }

  @Override
  public Precision getVariableExpandedPrecision(Precision rootPrecision, Block rootContext, Precision reducedPrecision) {
    UsageStatisticsPrecision redPrecision = (UsageStatisticsPrecision)reducedPrecision;
    UsageStatisticsPrecision newPrecision = ((UsageStatisticsPrecision)rootPrecision).clone(
        wrappedReducer.getVariableExpandedPrecision(((UsageStatisticsPrecision)rootPrecision).getWrappedPrecision()
        , rootContext, redPrecision.getWrappedPrecision()));
    return newPrecision;
  }

  @Override
  public int measurePrecisionDifference(Precision pPrecision, Precision pOtherPrecision) {
    UsageStatisticsPrecision first = (UsageStatisticsPrecision) pPrecision;
    UsageStatisticsPrecision second = (UsageStatisticsPrecision) pOtherPrecision;
    int wrapperDifference = wrappedReducer.measurePrecisionDifference(first.getWrappedPrecision(), second.getWrappedPrecision());
    return wrapperDifference + Math.abs(first.getTotalRecords() - second.getTotalRecords());
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
