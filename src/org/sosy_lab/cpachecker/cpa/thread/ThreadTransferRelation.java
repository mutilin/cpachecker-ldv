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
import java.util.TreeSet;

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
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;


public class ThreadTransferRelation extends SingleEdgeTransferRelation {
  private final TransferRelation locationTransfer;
  private final TransferRelation callstackTransfer;

  private static String CREATE = "ldv_thread_create";
  private static String JOIN = "ldv_thread_join";

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

    Set<ThreadLabel> Tset = tState.getThreadSet();
    Set<ThreadLabel> Rset = tState.getRemovedSet();
    if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
      Pair<Set<ThreadLabel>, Set<ThreadLabel>> newSets = handleFunctionCall((CFunctionCallEdge)pCfaEdge, Tset, Rset);
      Tset = newSets.getFirst();
      Rset = newSets.getSecond();
    } else if (pCfaEdge instanceof CFunctionSummaryStatementEdge) {
      String functionName = ((CFunctionSummaryStatementEdge)pCfaEdge).getFunctionName();
      if (functionName.equals(CREATE)) {
        Tset = new TreeSet<>(Tset);
        List<CExpression> args = ((CFunctionSummaryStatementEdge)pCfaEdge).getFunctionCall().
            getFunctionCallExpression().getParameterExpressions();
        String createdFunctionName = args.get(0).toString();
        Tset.add(new ThreadLabel(createdFunctionName, false));
        resetCallstacksFlag = true;
        ((CallstackTransferRelation)callstackTransfer).enableRecursiveContext();
      }
    } else if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionReturnEdge) {
      if (((CFunctionReturnEdge)pCfaEdge).getFunctionEntry().getFunctionName().equals(CREATE)) {
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
        resultStates.add(new ThreadState((LocationState)lState, (CallstackState)cState, Tset, Rset));
      }
    }
    if (resetCallstacksFlag) {
      ((CallstackTransferRelation)callstackTransfer).disableRecursiveContext();
      resetCallstacksFlag = false;
    }
    return resultStates;
  }

  private Pair<Set<ThreadLabel>, Set<ThreadLabel>> handleFunctionCall(CFunctionCallEdge pCfaEdge,
      Set<ThreadLabel> pTset, Set<ThreadLabel> pRset) {
    String functionName = pCfaEdge.getSuccessor().getFunctionName();

    if (!functionName.equals(CREATE) && !functionName.equals(JOIN)) {
      return Pair.of(pTset, pRset);
    } else if (functionName.equals(CREATE)) {
      Set<ThreadLabel> newSet = new TreeSet<>(pTset);
      List<CExpression> args = pCfaEdge.getArguments();
      String createdFunctionName = args.get(0).toString();
      newSet.add(new ThreadLabel(createdFunctionName, true));
      return Pair.of(newSet, pRset);
    } else if (functionName.equals(JOIN)) {
      Set<ThreadLabel> newSet = new TreeSet<>(pTset);
      List<CExpression> args = pCfaEdge.getArguments();
      String joinedFunctionName = args.get(0).toString();
      assert newSet.remove(new ThreadLabel(joinedFunctionName, false)) : "Can not find the label " + joinedFunctionName;
      return Pair.of(newSet, pRset);
    }
    return null;
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState pState,
      List<AbstractState> pOtherStates, @Nullable CFAEdge pCfaEdge, Precision pPrecision)
      throws CPATransferException, InterruptedException {
    // TODO Auto-generated method stub
    return null;
  }
}
