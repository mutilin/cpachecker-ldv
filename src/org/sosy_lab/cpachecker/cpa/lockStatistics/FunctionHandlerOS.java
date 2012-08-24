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
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock.LockType;


public class FunctionHandlerOS extends FunctionHandler{
  public FunctionHandlerOS(Set<String> pLock, Set<String> pUnlock, Set<String> pExceptions) {
    super(pLock, pUnlock, pExceptions);
  }

  @Override
  public LockStatisticsState handleStatement(LockStatisticsState element, CStatement expression) {

    LockStatisticsState newElement = element.clone();

    /*
     * level = intLock();
     * intUnlock(level);
     */
    if (expression instanceof CAssignment) {
      CRightHandSide op2 = ((CAssignment)expression).getRightHandSide();

      if (op2 instanceof CFunctionCallExpression) {
        String functionName = ((CFunctionCallExpression) op2).getFunctionNameExpression().toASTString();

        //System.out.println("FunctionExpression: " + expression.toASTString());
        if (lock.contains(functionName)) {
          if (exceptions.contains(functionName)) {
            newElement.add("lock", op2.getFileLocation().getStartingLineNumber(), LockType.GLOBAL_LOCK);
          }
          else {
            List<CExpression> params = ((CFunctionCallExpression) op2).getParameterExpressions();

            assert (params.size() == 1);
            newElement.add(params.get(0).toASTString(), op2.getFileLocation().getStartingLineNumber(), LockType.LOCAL_LOCK);
          }
        }
        else if (unlock.contains(functionName)) {
          if (exceptions.contains(functionName)) {
            newElement.delete("lock");
          }
          else {
            List<CExpression> params = ((CFunctionCallExpression) op2).getParameterExpressions();

            assert (params.size() == 1);
            newElement.delete(params.get(0).toASTString());
          }
        }
      }
    }
    return newElement;
  }

  @Override
  public LockStatisticsState handleFunctionCall(LockStatisticsState element, CFunctionCallEdge callEdge) {

    CFunctionEntryNode functionEntryNode = callEdge.getSuccessor();
    String functionName = functionEntryNode.getFunctionName();
    Set<CStatement> expressions = callEdge.getRawAST().asSet();

    LockStatisticsState newElement = element.clone();

    if (expressions.size() > 0) {
      for (CStatement statement : expressions) {
        newElement = handleStatement(newElement, statement);
      }
    }
    else {
      if (lock.contains(functionName)) {
        if (exceptions.contains(functionName)) {
          newElement.add("lock", callEdge.getLineNumber(), LockType.GLOBAL_LOCK);
        }
        else {
          List<CExpression> params = callEdge.getArguments();

          assert (params.size() == 1);
          newElement.add(params.get(0).toASTString(), callEdge.getLineNumber(), LockType.LOCAL_LOCK);
        }
      }
      else if (unlock.contains(functionName)) {
        if (exceptions.contains(functionName)) {
          newElement.delete("lock");
        }
        else {
          List<CExpression> params = callEdge.getArguments();

          assert (params.size() == 1);
          newElement.delete(params.get(0).toASTString());
        }
      }
    }
    return newElement;
  }

}
