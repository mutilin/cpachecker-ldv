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
package org.sosy_lab.cpachecker.cpa.thread;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackTransferRelation;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.cpa.thread.ThreadLabel.LabelStatus;
import org.sosy_lab.cpachecker.cpa.thread.ThreadState.ThreadStateBuilder;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;


public class ThreadTransferRelation extends SingleEdgeTransferRelation {
  private final TransferRelation locationTransfer;
  private final TransferRelation callstackTransfer;

  private static String CREATE = "ldv_thread_create";
  private static String JOIN = "ldv_thread_join";
  private static String CREATE_SELF_PARALLEL = "ldv_thread_create_N";
  private static String JOIN_SELF_PARALLEL = "ldv_thread_join_N";

  private boolean resetCallstacksFlag;

  public ThreadTransferRelation(TransferRelation l,
      TransferRelation c, Configuration pConfiguration) {
    locationTransfer = l;
    callstackTransfer = c;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState pState,
      Precision pPrecision, CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {

    ThreadState tState = (ThreadState)pState;
    LocationState oldLocationState = tState.getLocationState();
    CallstackState oldCallstackState = tState.getCallstackState();

    ThreadStateBuilder builder = tState.getBuilder();
    if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
      handleFunctionCall((CFunctionCallEdge)pCfaEdge, builder);
    } else if (pCfaEdge instanceof CFunctionSummaryStatementEdge) {
      String functionName = ((CFunctionSummaryStatementEdge)pCfaEdge).getFunctionName();
      if (functionName.equals(CREATE) || functionName.equals(CREATE_SELF_PARALLEL)) {
        List<CExpression> args = ((CFunctionSummaryStatementEdge)pCfaEdge).getFunctionCall().
            getFunctionCallExpression().getParameterExpressions();
        String createdFunctionName = args.get(0).toString();
        builder.addToThreadSet(new ThreadLabel(createdFunctionName, LabelStatus.PARENT_THREAD));
        resetCallstacksFlag = true;
        ((CallstackTransferRelation)callstackTransfer).enableRecursiveContext();
      }
    } else if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionReturnEdge) {
      if (((CFunctionReturnEdge)pCfaEdge).getFunctionEntry().getFunctionName().equals(CREATE) ||
          ((CFunctionReturnEdge)pCfaEdge).getFunctionEntry().getFunctionName().equals(CREATE_SELF_PARALLEL) ) {
        return Collections.emptySet();
      }
    }

    Collection<? extends AbstractState> newLocationStates = locationTransfer.getAbstractSuccessorsForEdge(oldLocationState,
        SingletonPrecision.getInstance(), pCfaEdge);
    Collection<? extends AbstractState> newCallstackStates = callstackTransfer.getAbstractSuccessorsForEdge(oldCallstackState,
        SingletonPrecision.getInstance(), pCfaEdge);


    Set<ThreadState> resultStates = new HashSet<>();
    for (AbstractState lState : newLocationStates) {
      for (AbstractState cState : newCallstackStates) {
        builder.setWrappedStates((LocationState)lState, (CallstackState)cState);
        resultStates.add(builder.build());
      }
    }
    if (resetCallstacksFlag) {
      ((CallstackTransferRelation)callstackTransfer).disableRecursiveContext();
      resetCallstacksFlag = false;
    }
    return resultStates;
  }

  private void handleFunctionCall(CFunctionCallEdge pCfaEdge,
      ThreadStateBuilder builder) {
    String functionName = pCfaEdge.getSuccessor().getFunctionName();

   if (functionName.equals(CREATE) || functionName.equals(CREATE_SELF_PARALLEL)) {
      List<CExpression> args = pCfaEdge.getArguments();
      String createdFunctionName = args.get(0).toString();
      LabelStatus status = functionName.equals(CREATE) ? LabelStatus.CREATED_THREAD : LabelStatus.SELF_PARALLEL_THREAD;
      builder.addToThreadSet(new ThreadLabel(createdFunctionName, status));
    } else if (functionName.equals(JOIN) || functionName.equals(JOIN_SELF_PARALLEL)) {
      List<CExpression> args = pCfaEdge.getArguments();
      String joinedFunctionName = args.get(0).toString();
      builder.removeFromThreadSet(new ThreadLabel(joinedFunctionName, LabelStatus.PARENT_THREAD));
    }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState pState,
      List<AbstractState> pOtherStates, @Nullable CFAEdge pCfaEdge, Precision pPrecision)
      throws CPATransferException, InterruptedException {
    // TODO Auto-generated method stub
    return null;
  }
}
