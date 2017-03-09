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
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsReducer;

public class UsageStatisticsReducer implements Reducer {
  private final Reducer wrappedReducer;
  private final LockStatisticsReducer lockReducer;

  public UsageStatisticsReducer(Reducer pWrappedReducer, LockStatisticsReducer lReducer) {
    wrappedReducer = pWrappedReducer;
    lockReducer = lReducer;
  }

  @Override
  public AbstractState getVariableReducedState(AbstractState pExpandedElement,
                                          Block pContext, Block outerContext, CFANode pLocation) throws InterruptedException {
    UsageStatisticsState funElement = (UsageStatisticsState)pExpandedElement;
    AbstractState red = wrappedReducer.getVariableReducedState(funElement.getWrappedState(), pContext, outerContext, pLocation);
    return funElement.reduce(red);

  }

  @Override
  public AbstractState getVariableExpandedState(AbstractState pRootElement,
                        Block pReducedContext, Block outerSubtree, AbstractState pReducedElement) throws InterruptedException {
    UsageStatisticsState funRootState = (UsageStatisticsState)pRootElement;
    UsageStatisticsState funReducedState = (UsageStatisticsState)pReducedElement;
    AbstractState exp = wrappedReducer.getVariableExpandedState(funRootState.getWrappedState(), pReducedContext, funReducedState.getWrappedState());
    return funReducedState.expand(funRootState, exp, pReducedContext, lockReducer);
  }

  @Override
  public AbstractState getVariableReducedState(AbstractState pExpandedElement,
                                          Block pContext, CFANode pLocation) throws InterruptedException {
    UsageStatisticsState funElement = (UsageStatisticsState)pExpandedElement;
    AbstractState red = wrappedReducer.getVariableReducedState(funElement.getWrappedState(), pContext, pLocation);
    return funElement.reduce(red);

  }

  @Override
  public AbstractState getVariableExpandedState(AbstractState pRootElement,
                        Block pReducedContext, AbstractState pReducedElement) throws InterruptedException {
    UsageStatisticsState funRootState = (UsageStatisticsState)pRootElement;
    UsageStatisticsState funReducedState = (UsageStatisticsState)pReducedElement;
    AbstractState exp = wrappedReducer.getVariableExpandedState(funRootState.getWrappedState(), pReducedContext, funReducedState.getWrappedState());
    return funReducedState.expand(funRootState, exp, pReducedContext, lockReducer);
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
  public AbstractState rebuildStateAfterFunctionCall(AbstractState pRootState, AbstractState pEntryState,
      AbstractState pExpandedState, FunctionExitNode pExitLocation) {
    return pExpandedState;
  }

}
