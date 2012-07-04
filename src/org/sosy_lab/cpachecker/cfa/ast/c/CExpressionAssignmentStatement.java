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
package org.sosy_lab.cpachecker.cfa.ast.c;

/**
 * AST node for the expression "a = b".
 */
public class CExpressionAssignmentStatement extends CStatement implements CAssignment {

  private final CExpression leftHandSide;
  private final CExpression rightHandSide;

  public CExpressionAssignmentStatement(CFileLocation pFileLocation,
                                           CExpression pLeftHandSide,
                                           CExpression pRightHandSide) {
    super(pFileLocation);
    leftHandSide = pLeftHandSide;
    rightHandSide = pRightHandSide;
  }

  @Override
  public CExpression getLeftHandSide() {
    return leftHandSide;
  }

  @Override
  public CExpression getRightHandSide() {
    return rightHandSide;
  }

  @Override
  public CStatement asStatement() {
    return this;
  }

  @Override
  public <R, X extends Exception> R accept(CStatementVisitor<R, X> v) throws X {
    return v.visit(this);
  }

  @Override
  public String toASTString() {
    return leftHandSide.toASTString()
        + " = " + rightHandSide.toASTString() + ";";
  }
}
