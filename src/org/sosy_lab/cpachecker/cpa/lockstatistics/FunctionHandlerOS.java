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

import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsCPA;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsTransferRelation;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;


public class FunctionHandlerOS {
  private UsageStatisticsCPA stateGetter;
  private final Set<LockInfo> locks;
  private LogManager logger;

  public FunctionHandlerOS(Set<LockInfo> l, LogManager logger) {
    locks = l;
    this.logger = logger;
  }

  public LockStatisticsState handleStatement(LockStatisticsState element, CStatement expression, String currentFunction) throws HandleCodeException {

    LockStatisticsState newElement = element.clone();

    if (expression instanceof CAssignment) {
      /*
       * level = intLock();
       */
      CRightHandSide op2 = ((CAssignment)expression).getRightHandSide();

      if (op2 instanceof CFunctionCallExpression) {
        CFunctionCallExpression function = (CFunctionCallExpression) op2;
        String functionName = function.getFunctionNameExpression().toASTString();
        if (needToChangeState(functionName)) {
          changeState(newElement, functionName, op2.getFileLocation().getStartingLineNumber(), currentFunction,
                           function.getParameterExpressions());
        }
      }

    } else if (expression instanceof CFunctionCallStatement) {
      /*
       * queLock(que);
       */
      CFunctionCallStatement statement = (CFunctionCallStatement) expression;
      String functionName = statement.getFunctionCallExpression().getFunctionNameExpression().toASTString();
      if (needToChangeState(functionName)) {
       changeState(newElement, functionName, statement.getFileLocation().getStartingLineNumber(), currentFunction,
          statement.getFunctionCallExpression().getParameterExpressions());
      }
    }
    return newElement;
  }

  public LockStatisticsState handleFunctionCall(LockStatisticsState element, CFunctionCallEdge callEdge) throws HandleCodeException {
    Set<CFunctionCall> expressions = callEdge.getRawAST().asSet();
    LockStatisticsState newElement = element.clone();

    if (expressions.size() > 0) {
      for (CStatement statement : expressions) {
        newElement = handleStatement(newElement, statement, callEdge.getPredecessor().getFunctionName());
      }
    } else {
      String functionName = callEdge.getSuccessor().getFunctionName();
      if (needToChangeState(functionName)) {
        changeState(newElement, functionName, callEdge.getLineNumber(), callEdge.getPredecessor().getFunctionName(),
          callEdge.getArguments());
      }
    }
    return newElement;
  }

  private boolean needToChangeState(String functionName) {
    for (LockInfo lock : locks) {
      if (lock.LockFunctions.containsKey(functionName)
          || lock.UnlockFunctions.containsKey(functionName)
          || (lock.ResetFunctions != null && lock.ResetFunctions.containsKey(functionName))
          || (lock.setLevel != null && lock.setLevel.equals(functionName))
          ) {
        return true;
      }
    }
    return false;
  }

  private void changeState(LockStatisticsState newElement, String functionName, int lineNumber, String currentFunction,
      List<CExpression> params) throws HandleCodeException {
    CallstackState reducedCallstack =
        AbstractStates.extractStateByType(((UsageStatisticsTransferRelation)stateGetter.getTransferRelation()).getOldState(),
            CallstackState.class);
    CallstackState callstack = stateGetter.getStats().createStack(reducedCallstack);

    for (LockInfo lock : locks) {
      if (lock.LockFunctions.containsKey(functionName)) {
    	  logger.log(Level.FINER, "Lock at line " + lineNumber + ", Callstack: " + callstack);
    	  int p = lock.LockFunctions.get(functionName);
    	  String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
        int d = newElement.getCounter(lock.lockName, variable);

        if (d < lock.maxLock) {
          newElement.add(lock.lockName, lineNumber, callstack, reducedCallstack, variable, logger);
        } else {
          Stack<AccessPoint> access = newElement.findLock(lock.lockName, variable).getAccessPoints();
          StringBuilder message = new StringBuilder();
          message.append("Try to lock " + lock.lockName + " more, than " + lock.maxLock + " in " + lineNumber + " line. Previous were in ");
          for (AccessPoint point : access) {
            message.append(point.line.getLine() + ", ");
          }
          message.delete(message.length() - 2, message.length());
          System.err.println(message.toString());
        }
        return;

      } else if (lock.UnlockFunctions.containsKey(functionName)) {
    	  logger.log(Level.FINER, "Unlock at line " + lineNumber + ", Callstack: " + callstack);
        int p = lock.UnlockFunctions.get(functionName);
        String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
        newElement.free(lock.lockName, variable, logger);
        return;

      } else if (lock.ResetFunctions != null && lock.ResetFunctions.containsKey(functionName)) {
    	  logger.log(Level.FINER, "Reset at line " + lineNumber + ", Callstack: " + callstack);
        int p = lock.ResetFunctions.get(functionName);
        String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
        newElement.reset(lock.lockName, variable, logger);
        return;

      } else if (lock.setLevel != null && lock.setLevel.equals(functionName)) {
        int p = Integer.parseInt(params.get(0).toASTString()); //new level
        newElement.set(lock.lockName, p - 1, lineNumber, callstack, reducedCallstack, ""); //they count from 1
        return;
      }
    }
  }

  public void setUsCPA(UsageStatisticsCPA cpa) {
    stateGetter = cpa;
  }
}
