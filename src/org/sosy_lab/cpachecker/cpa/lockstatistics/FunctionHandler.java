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

import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;

/**Method of handling/searching lock functions
 *
 */
public abstract class FunctionHandler {

  protected List<String> lock;

  protected List<String> unlock;

  /**
   * There were those functions, that are called with a parameters, but
   * we don't need to use it. F.e, intUnlock(level).
   */
  protected Set<String> exceptions;

  public FunctionHandler(List<String> pLock, List<String> pUnlock, Set<String> pExceptions) {
    lock = pLock;
    unlock = pUnlock;
    exceptions = pExceptions;
  }

  /**
   * Handling statement expressions
   * @param element - state in CPA
   * @param expressionl
   * @return new state (successor)
   * @throws HandleCodeException
   */
  public abstract LockStatisticsState handleStatement(LockStatisticsState element,
      CStatement expression, String currentFunction) throws HandleCodeException;

  /**
   * Handling Function call in expression
   * @param element - state in CPA
   * @param expression - function call
   * @return new state (successor)
   */
  /*public abstract LockStatisticsState handleFunctionCall(LockStatisticsState element,
      CFunctionCallExpression expression);*/

  /**
   * Handling function call in edge
   * @param element - state in CPA
   * @param callEdge - function call
   * @return new state (successor)
   * @throws HandleCodeException
   */
  public abstract LockStatisticsState handleFunctionCall(LockStatisticsState element, CFunctionCallEdge callEdge) throws HandleCodeException;


}
