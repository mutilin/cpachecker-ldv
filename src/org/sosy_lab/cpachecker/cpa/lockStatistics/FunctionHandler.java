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

import java.util.Set;

import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;

/**Method of handling/searching lock functions
 *
 */
@Options(prefix="cpa.functionhandler")
public abstract class FunctionHandler {

  protected Set<String> lock;

  protected Set<String> unlock;

  public FunctionHandler(Set<String> pLock, Set<String> pUnlock) {
    lock = pLock;
    unlock = pUnlock;
  }

  /**
   * Handling statement expressions
   * @param element - state in CPA
   * @param expressionl
   * @return new state (successor)
   */
  public abstract LockStatisticsState handleStatement(LockStatisticsState element,
      CStatement expression);

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
   */
  public abstract LockStatisticsState handleFunctionCall(LockStatisticsState element, CFunctionCallEdge callEdge);


}
