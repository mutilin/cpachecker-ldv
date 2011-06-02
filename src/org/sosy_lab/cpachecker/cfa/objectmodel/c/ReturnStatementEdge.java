/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.objectmodel.c;

import org.sosy_lab.cpachecker.cfa.ast.IASTExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTReturnStatement;
import org.sosy_lab.cpachecker.cfa.objectmodel.AbstractCFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionExitNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;

public class ReturnStatementEdge extends AbstractCFAEdge {

  private final IASTReturnStatement rawAST;

  public ReturnStatementEdge(IASTReturnStatement rawAST, int lineNumber, CFANode predecessor, CFAFunctionExitNode successor) {
      super(rawAST.getRawSignature(), lineNumber, predecessor, successor);
      this.rawAST = rawAST;
  }

  @Override
  public boolean isJumpEdge() {
    return true;
  }

  @Override
  public CFAEdgeType getEdgeType() {
    return CFAEdgeType.ReturnStatementEdge;
  }

  public IASTExpression getExpression() {
    return rawAST.getReturnValue();
  }

  @Override
  public IASTReturnStatement getRawAST() {
    return rawAST;
  }

  @Override
  public CFAFunctionExitNode getSuccessor() {
    // the constructor enforces that the successor is always a CFAFunctionExitNode
    return (CFAFunctionExitNode)super.getSuccessor();
  }
}
