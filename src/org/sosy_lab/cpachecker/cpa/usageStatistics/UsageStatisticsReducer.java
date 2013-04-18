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
package org.sosy_lab.cpachecker.cpa.usageStatistics;

import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;

// TODO implements a no-op for the function pointer map and passes the wrapped abstract element to the wrappedReducer; could be improved
// TODO functions called through function pointers are yet not considered for the computation of relevant predicates of a block. Using ABM with FunctionPointerCPA might thus yield unsound verification results.
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
    //System.out.println("exp " + funElement);
    //System.out.println("red " + red);
    return new UsageStatisticsState(red);
    //return pExpandedElement;

  }

  @Override
  public AbstractState getVariableExpandedState(AbstractState pRootElement,
                        Block pReducedContext, AbstractState pReducedElement) {
    UsageStatisticsState funRootState = (UsageStatisticsState)pRootElement;
    UsageStatisticsState funReducedState = (UsageStatisticsState)pReducedElement;
    AbstractState exp = wrappedReducer.getVariableExpandedState(funRootState.getWrappedState(), pReducedContext, funReducedState.getWrappedState());
    //System.out.println("after:root " + funRootState);
    //System.out.println("after:red " + funReducedState);
    //System.out.println("after:exp " + exp);
    return funRootState.clone(exp);
    //return pReducedElement;
  }

  @Override
  public Object getHashCodeForState(AbstractState pElementKey, Precision pPrecisionKey) {
    UsageStatisticsState funElement = (UsageStatisticsState)pElementKey;
    //return wrappedReducer.getHashCodeForState(funElement.getWrappedState(), pPrecisionKey);
    return wrappedReducer.getHashCodeForState(funElement.getWrappedState(), pPrecisionKey);
  }

  @Override
  public Precision getVariableReducedPrecision(Precision pPrecision,
      Block pContext) {
    return wrappedReducer.getVariableReducedPrecision(pPrecision, pContext);
  }

  @Override
  public Precision getVariableExpandedPrecision(Precision rootPrecision, Block rootContext, Precision reducedPrecision) {
    return wrappedReducer.getVariableExpandedPrecision(rootPrecision, rootContext, reducedPrecision);
  }

  @Override
  public int measurePrecisionDifference(Precision pPrecision, Precision pOtherPrecision) {
    return wrappedReducer.measurePrecisionDifference(pPrecision, pOtherPrecision);
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
