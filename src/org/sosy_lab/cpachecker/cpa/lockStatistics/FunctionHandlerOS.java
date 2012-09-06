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
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock.LockType;


public class FunctionHandlerOS extends FunctionHandler{
  public FunctionHandlerOS(List<String> pLock, List<String> pUnlock, Set<String> pExceptions) {
    super(pLock, pUnlock, pExceptions);
  }

  @Override
  public LockStatisticsState handleStatement(LockStatisticsState element, CStatement expression) {

    LockStatisticsState newElement = element.clone();

    if (expression instanceof CAssignment) {
      /*
       * level = intLock();
       */
      CRightHandSide op2 = ((CAssignment)expression).getRightHandSide();

      if (op2 instanceof CFunctionCallExpression) {
        return CheckIsLock(newElement, ((CFunctionCallExpression) op2).getFunctionNameExpression().toASTString(),
                           op2.getFileLocation().getStartingLineNumber(), ((CFunctionCallExpression) op2).getParameterExpressions());
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
          ((CFunctionCallStatement) expression).getFileLocation().getStartingLineNumber(),
          ((CFunctionCallStatement) expression).getFunctionCallExpression().getParameterExpressions());
    }
    else {
      return newElement;
    }


  }

  @Override
  public LockStatisticsState handleFunctionCall(LockStatisticsState element, CFunctionCallEdge callEdge) {
    Set<CStatement> expressions = callEdge.getRawAST().asSet();

    LockStatisticsState newElement = element.clone();

    if (expressions.size() > 0) {
      for (CStatement statement : expressions) {
        newElement = handleStatement(newElement, statement);
      }
    }
    else {
      return CheckIsLock(newElement, callEdge.getSuccessor().getFunctionName(), callEdge.getLineNumber(), callEdge.getArguments());
    }
    return newElement;
  }

  private LockStatisticsState CheckIsLock(LockStatisticsState newElement, String functionName, int lineNumber, List<CExpression> params) {
    if (lock != null && lock.contains(functionName)) {
      if (exceptions.contains(functionName)) {
        newElement.add(functionName, lineNumber, LockType.GLOBAL_LOCK);
      }
      else {
        assert (params.size() == 1);
        newElement.add(params.get(0).toASTString(), lineNumber, LockType.LOCAL_LOCK);
      }
    }
    else if (unlock != null && unlock.contains(functionName)) {
      if (exceptions.contains(functionName)) {
        String lockName = lock.get(unlock.indexOf(functionName));
        newElement.delete(lockName);
      }
      else {
        assert (params.size() == 1);
        newElement.delete(params.get(0).toASTString());
      }
    }
    return newElement;
  }
}
