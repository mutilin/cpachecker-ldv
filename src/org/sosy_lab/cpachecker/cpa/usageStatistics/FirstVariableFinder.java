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
package org.sosy_lab.cpachecker.cpa.usageStatistics;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.identifiers.Identifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureFieldIdentifier;


public class FirstVariableFinder extends ExpressionHandler {
  @Override
  public Void visit(CBinaryExpression expression) throws HandleCodeException {
    expression.getOperand1().accept(this);
    if(result.isEmpty()) {
      expression.getOperand2().accept(this);
    }
    return null;
  }

  @Override
  public Void visit(CFieldReference expression) throws HandleCodeException {
    Identifier id = new StructureFieldIdentifier(expression.getFieldName(),
        expression.getExpressionType(), expression.getFieldOwner().getExpressionType(), dereferenceCounter);
      result.add(Pair.of(id, accessMode));
    return null;
  }

  @Override
  public Void visit(CUnaryExpression expression) throws HandleCodeException {
    if (expression.getOperator() == CUnaryExpression.UnaryOperator.STAR) {
      ++dereferenceCounter;
    } else if (expression.getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
      --dereferenceCounter;
    }
    expression.getOperand().accept(this);
    return null;
  }
}