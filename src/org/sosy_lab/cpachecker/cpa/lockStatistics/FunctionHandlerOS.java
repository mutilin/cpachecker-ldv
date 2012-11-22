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

import java.util.List;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageStatisticsCPA;
import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageStatisticsTransferRelation;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;


public class FunctionHandlerOS {
  private UsageStatisticsCPA stateGetter;
  public Set<LockInfo> locks;

  public FunctionHandlerOS(Set<LockInfo> l) {
    locks = l;
  }

  public LockStatisticsState handleStatement(LockStatisticsState element, CStatement expression, String currentFunction) throws HandleCodeException {

    LockStatisticsState newElement = element.clone();

    if (expression instanceof CAssignment) {
      /*
       * level = intLock();
       */
      CRightHandSide op2 = ((CAssignment)expression).getRightHandSide();

      if (op2 instanceof CFunctionCallExpression) {
        return CheckIsLock(newElement, ((CFunctionCallExpression) op2).getFunctionNameExpression().toASTString(),
                           op2.getFileLocation().getStartingLineNumber(), currentFunction, ((CFunctionCallExpression) op2).getParameterExpressions());
      }
      else
        return newElement;

    }
    else if (expression instanceof CFunctionCallStatement) {
      /*
       * queLock(que);
       */
      return CheckIsLock(newElement,
          ((CFunctionCallStatement) expression).getFunctionCallExpression().getFunctionNameExpression().toASTString(),
          ((CFunctionCallStatement) expression).getFileLocation().getStartingLineNumber(), currentFunction,
          ((CFunctionCallStatement) expression).getFunctionCallExpression().getParameterExpressions());
    }
    else {
      return newElement;
    }


  }

  public LockStatisticsState handleFunctionCall(LockStatisticsState element, CFunctionCallEdge callEdge) throws HandleCodeException {
    Set<CStatement> expressions = callEdge.getRawAST().asSet();

    LockStatisticsState newElement = element.clone();

    if (expressions.size() > 0) {
      for (CStatement statement : expressions) {
        newElement = handleStatement(newElement, statement, callEdge.getPredecessor().getFunctionName());
      }
    }
    else {
      return CheckIsLock(newElement, callEdge.getSuccessor().getFunctionName(), callEdge.getLineNumber(), callEdge.getPredecessor().getFunctionName(),
          callEdge.getArguments());
    }
    return newElement;
  }

  /*private boolean isGlobal(CExpression expression) throws HandleCodeException {
    if (expression instanceof CArraySubscriptExpression) {
      return isGlobal(((CArraySubscriptExpression)expression).getArrayExpression());

    } else if (expression instanceof CFieldReference) {
      return isGlobal(((CFieldReference)expression).getFieldOwner());

    } else if (expression instanceof CIdExpression) {
      CSimpleDeclaration decl = ((CIdExpression)expression).getDeclaration();
      if (decl instanceof CDeclaration)
        return (((CDeclaration)decl).isGlobal());
      else if (decl instanceof CParameterDeclaration) {
        CParameterDeclaration pDecl = (CParameterDeclaration) decl;
        if (pDecl.getType() instanceof CPointerType)
          return true;
        else
          return false;
      } else
        throw new HandleCodeException("Can't handle expression " + expression.toASTString() + " as lock parameter");

    } else if (expression instanceof CUnaryExpression) {
      return isGlobal(((CUnaryExpression)expression).getOperand());
    } else {
      throw new HandleCodeException("Can't handle expression " + expression.toASTString() + " as lock parameter");
    }
  }*/

  private LockStatisticsState CheckIsLock(LockStatisticsState newElement, String functionName, int lineNumber, String currentFunction,
      List<CExpression> params) throws HandleCodeException {
    CallstackState callstack =
        AbstractStates.extractStateByType(((UsageStatisticsTransferRelation)stateGetter.getTransferRelation()).getOldState(),
            CallstackState.class);
    callstack = stateGetter.getStats().createStack(callstack);

    for (LockInfo lock : locks) {
      if (lock.LockFunctions.containsKey(functionName)) {
        int p = lock.LockFunctions.get(functionName);
        int d = newElement.getCounter(lock.lockName);
        //CExpression param = params.get(0);
        //if (isGlobal(param))
        if (p == 0 && d < lock.maxLock)
          newElement.add(lock.lockName, lineNumber, callstack);
        else if (d < lock.maxLock)
          newElement.add(params.get(p - 1).toASTString(), lineNumber, callstack);
        else
          System.err.println("Try to lock " + lock.lockName + " more, than " + lock.maxLock);
      }
      else if (lock.UnlockFunctions.containsKey(functionName)) {
        int p = lock.UnlockFunctions.get(functionName);
        if (p == 0)
          newElement.delete(lock.lockName);
        else
          newElement.delete(params.get(p - 1).toASTString());
      }
      else if (lock.ResetFunctions != null && lock.ResetFunctions.containsKey(functionName)) {
        int p = lock.ResetFunctions.get(functionName);
        if (p == 0)
          newElement.reset(lock.lockName);
        else
          newElement.reset(params.get(p - 1).toASTString());
      } else if (lock.setLevel != null && lock.setLevel.equals(functionName)) {
        int p = Integer.parseInt(params.get(0).toASTString()); //new level
        newElement.set(lock.lockName, p, lineNumber, callstack);
      }
    }
    return newElement;
  }

  public void setUsCPA(UsageStatisticsCPA cpa) {
    stateGetter = cpa;
  }
}
