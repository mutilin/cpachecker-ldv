/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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

import org.sosy_lab.cpachecker.cfa.ast.AExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;


/**
 * AST node for the expression "a = b".
 */
public class CExpressionAssignmentStatement extends AExpressionAssignmentStatement implements CAssignment, CStatement {

  public CExpressionAssignmentStatement(FileLocation pFileLocation,
                                           CExpression pLeftHandSide,
                                           CExpression pRightHandSide) {
    super(pFileLocation, pLeftHandSide, pRightHandSide);

  }

  @Override
  public CExpression getLeftHandSide() {
    return (CExpression)super.getLeftHandSide();
  }

  @Override
  public CExpression getRightHandSide() {
    return (CExpression)super.getRightHandSide();
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
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) { return true; }
    if (obj == null) { return false; }
    if (!(obj instanceof CExpressionAssignmentStatement)) { return false; }
    return super.equals(obj);
  }
}
