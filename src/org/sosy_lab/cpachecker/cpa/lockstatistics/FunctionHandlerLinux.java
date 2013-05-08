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



public class FunctionHandlerLinux{
/*
  public FunctionHandlerLinux(List<String> pLock, List<String> pUnlock, Set<String> pExceptions) {
    super(pLock, pUnlock, pExceptions);
  }

  @Override
  public LockStatisticsState handleStatement(LockStatisticsState element, CStatement expression) {

    LockStatisticsState newElement = element.clone();

    if (expression instanceof CAssignment) {
      CRightHandSide op2 = ((CAssignment)expression).getRightHandSide();

      if (op2 instanceof CFunctionCallExpression) {
        String functionName = ((CFunctionCallExpression) op2).getFunctionNameExpression().toASTString();
        List<CExpression> params = ((CFunctionCallExpression) op2).getParameterExpressions();

        //System.out.println("--" + functionName);
        if (lock != null && lock.contains(functionName)) {
          assert (params.size() == 1);
          newElement.add(params.get(0).toASTString(), op2.getFileLocation().getStartingLineNumber(), LockType.MUTEX);
        }
        else if (unlock != null && unlock.contains(functionName)) {
          assert (params.size() == 1);
          newElement.delete(params.get(0).toASTString());
        }
      }
    }
    else if (expression instanceof CFunctionCallStatement) {
      String functionName = ((CFunctionCallStatement) expression).getFunctionCallExpression().getFunctionNameExpression().toASTString();
      List <CExpression> params = ((CFunctionCallStatement) expression).getFunctionCallExpression().getParameterExpressions();

      if (lock != null && lock.contains(functionName)) {
        assert (params.size() == 1);
        newElement.add(params.get(0).toASTString(), expression.getFileLocation().getStartingLineNumber(), LockType.MUTEX);
      }
      else if (unlock != null && unlock.contains(functionName)) {
        assert (params.size() == 1);
        newElement.delete(params.get(0).toASTString());
      }
    }
    return element.clone();
  }

  @Override
  public LockStatisticsState handleFunctionCall(LockStatisticsState element, CFunctionCallEdge callEdge) {

    CFunctionEntryNode functionEntryNode = callEdge.getSuccessor();
    String functionName = functionEntryNode.getFunctionName();
    List<String> paramNames = functionEntryNode.getFunctionParameterNames();

    LockStatisticsState newElement = element.clone();

    //System.out.println("--" + functionName);
    if (lock.contains(functionName)) {
      assert (paramNames.size() == 1);
      newElement.addLocal(paramNames.get(0), callEdge.getLineNumber(), LockType.MUTEX);
    }
    else if (unlock.contains(functionName)) {
      assert (paramNames.size() == 1);
      newElement.delete(paramNames.get(0));
    }
    return newElement;
  }*/

}
