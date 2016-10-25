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
package org.sosy_lab.cpachecker.cpa.usage;

import java.util.LinkedList;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo.Access;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.IdentifierCreator;

public class ExpressionHandler implements CExpressionVisitor<Void, HandleCodeException> {

  public List<Pair<AbstractIdentifier, Access>> result;
  protected Access accessMode;
  protected String function;
  private IdentifierCreator creator = new IdentifierCreator();
  private UsageState currentState;

  public void setMode(String funcName, Access mode, UsageState state) {
    result = new LinkedList<>();
    function = funcName;
    accessMode = mode;
    creator.clear(function);
    currentState = state;
  }

  @Override
  public Void visit(CArraySubscriptExpression expression) throws HandleCodeException {
    addNewId(expression);
    accessMode = Access.READ;
    expression.getArrayExpression().accept(this);
    return null;
  }

  @Override
  public Void visit(CBinaryExpression expression) throws HandleCodeException {
    if (accessMode == Access.READ) {
      expression.getOperand1().accept(this);
      expression.getOperand2().accept(this);
    } else {
      //We can't be here. This is error: a + b = ...
      throw new HandleCodeException("Writing to BinaryExpression: " + expression.toASTString());
    }
    return null;
  }

  @Override
  public Void visit(CCastExpression expression) throws HandleCodeException {
    expression.getOperand().accept(this);
    return null;
  }

  @Override
  public Void visit(CFieldReference expression) throws HandleCodeException {
    addNewId(expression);
    if (expression.isPointerDereference()) {
      accessMode = Access.READ;
    }
    expression.getFieldOwner().accept(this);
    return null;
  }

  @Override
  public Void visit(CIdExpression expression) throws HandleCodeException {
    addNewId(expression);
    return null;
  }

  @Override
  public Void visit(CCharLiteralExpression expression) {return null;}

  @Override
  public Void visit(CFloatLiteralExpression expression) {return null;}

  @Override
  public Void visit(CIntegerLiteralExpression expression) {return null;}

  @Override
  public Void visit(CStringLiteralExpression expression) {return null;}

  @Override
  public Void visit(CTypeIdExpression expression) {return null;}

  @Override
  public Void visit(CUnaryExpression expression) throws HandleCodeException {
    if (expression.getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
      addNewId(expression);
      return null;
    }
    //In all other unary operation we only read the operand
    accessMode = Access.READ;
    expression.getOperand().accept(this);
    return null;
  }

  @Override
  public Void visit(CPointerExpression pPointerExpression) throws HandleCodeException {
    //write: *s =
    addNewId(pPointerExpression);
    accessMode = Access.READ;
    pPointerExpression.getOperand().accept(this);
    return null;
  }

  @Override
  public Void visit(CComplexCastExpression pComplexCastExpression) throws HandleCodeException {
    pComplexCastExpression.getOperand().accept(this);
    return null;
  }

  @Override
  public Void visit(CImaginaryLiteralExpression PIastLiteralExpression) throws HandleCodeException { return null; }

  @Override
  public Void visit(CAddressOfLabelExpression pAddressOfLabelExpression) throws HandleCodeException { return null; }

  private void addNewId(CExpression e) throws HandleCodeException {
    creator.clearDereference();
    AbstractIdentifier id = e.accept(creator);
    id = currentState.getLinksIfNecessary(id);
    result.add(Pair.of(id, accessMode));
  }
}

