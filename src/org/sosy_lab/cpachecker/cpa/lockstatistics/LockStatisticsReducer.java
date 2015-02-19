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

import static com.google.common.collect.FluentIterable.from;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.cpa.bam.BAMRestoreStack;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackReducer;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState.LockStatisticsStateBuilder;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

@Options(prefix="cpa.lockstatistics")
public class LockStatisticsReducer implements Reducer {
  //this field should be initialized by ABM
  private BAMRestoreStack restorator;
  private CallstackReducer cReducer;
  private final Set<String> restrictedFunctions;
  private final Set<String> restrictedLocks;

  @Option(description="reduce recursive locks to a single access")
  private boolean aggressiveReduction = false;

  public LockStatisticsReducer(Configuration config, Map<String, AnnotationInfo> annotations, Set<LockInfo> locks) throws InvalidConfigurationException {
    config.inject(this);
    restrictedFunctions = annotations.keySet();
    //Make a set of locks with nonempty reset functions
    restrictedLocks = from(locks).filter(new Predicate<LockInfo>() {
        @Override
        public boolean apply(@Nullable LockInfo pInput) {
          return !(pInput.ResetFunctions.isEmpty() && pInput.Variables.isEmpty());
        }
      }).transform(new Function<LockInfo, String>() {
        @Override
        @Nullable
        public String apply(@Nullable LockInfo pInput) {
            return pInput.lockName;
        }
      }).toSet();
  }

  @Override
  public AbstractState getVariableReducedState(AbstractState pExpandedElement, Block pContext, CFANode pCallNode) {
    LockStatisticsState lockState = (LockStatisticsState) pExpandedElement;
    LockStatisticsStateBuilder builder = lockState.builder();
    builder.reduce();
    if (aggressiveReduction && !restrictedFunctions.contains(pCallNode.getFunctionName())) {
      builder.reduceLocks(restrictedLocks);
    }
    return builder.build();
  }

  @Override
  public AbstractState getVariableExpandedState(AbstractState pRootElement, Block pReducedContext,
      AbstractState pReducedElement) {

    LockStatisticsState reducedState = (LockStatisticsState)pReducedElement;
    LockStatisticsState rootState = (LockStatisticsState) pRootElement;
    LockStatisticsStateBuilder builder = reducedState.builder();
    builder.expand(rootState, restorator, cReducer, pReducedContext.getCallNode());
    if (aggressiveReduction && !restrictedFunctions.contains(pReducedContext.getCallNode().getFunctionName())) {
      builder.expandLocks(rootState, restrictedLocks);
    }
    return builder.build();
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

    return elementKey.getHashCodeForState();
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

  public void setRestorator(BAMRestoreStack r) {
    restorator = r;
  }

  public void setCallstackReducer(CallstackReducer r) {
    cReducer = r;
  }

  @Override
  public AbstractState rebuildStateAfterFunctionCall(AbstractState pRootState, AbstractState pEntryState,
      AbstractState pExpandedState, CFANode pExitLocation) {
    return pExpandedState;
  }
}
