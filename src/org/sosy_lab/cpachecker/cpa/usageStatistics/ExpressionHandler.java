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

import java.util.LinkedList;
import java.util.List;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.identifiers.Identifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureFieldIdentifier;


public class ExpressionHandler implements CExpressionVisitor<Void, HandleCodeException> {

  private class isLocalExpression implements CExpressionVisitor<Boolean, HandleCodeException> {
    @Override
    public Boolean visit(CArraySubscriptExpression expression) throws HandleCodeException {
      if (expression.getExpressionType() instanceof CPointerType)
        return false;
      return expression.getArrayExpression().accept(this);
    }

    @Override
    public Boolean visit(CBinaryExpression expression) throws HandleCodeException {
      if (expression.getExpressionType() instanceof CPointerType)
        return false;
      boolean result = expression.getOperand1().accept(this);
      if (!result)
        return false;
      result = expression.getOperand2().accept(this);
      return result;
    }

    @Override
    public Boolean visit(CCastExpression expression) throws HandleCodeException {
      if (expression.getExpressionType() instanceof CPointerType)
        return false;
      return expression.getOperand().accept(this);
    }

    @Override
    public Boolean visit(CFieldReference expression) throws HandleCodeException {
      if (expression.getExpressionType() instanceof CPointerType)
        return false;
      if (expression.isPointerDereference()) {
        return false;
      } else {
        return expression.getFieldOwner().accept(this);
      }
    }

    @Override
    public Boolean visit(CIdExpression expression) throws HandleCodeException {
      if (expression.getExpressionType() instanceof CPointerType)
        return false;
      CSimpleDeclaration decl = expression.getDeclaration();

      if (decl instanceof CDeclaration) {
        return (!(((CDeclaration)decl).isGlobal()));
      } else {
        return true;
      }
    }

    @Override
    public Boolean visit(CCharLiteralExpression expression) {return true;}

    @Override
    public Boolean visit(CFloatLiteralExpression expression) {return true;}

    @Override
    public Boolean visit(CIntegerLiteralExpression expression) {return true;}

    @Override
    public Boolean visit(CStringLiteralExpression expression){return true;}

    @Override
    public Boolean visit(CTypeIdExpression expression) {return true;}

    @Override
    public Boolean visit(CUnaryExpression expression) throws HandleCodeException {
      if (expression.getExpressionType() instanceof CPointerType)
        return false;
      if (expression.getOperator() == CUnaryExpression.UnaryOperator.STAR) {
        return false;
      } else {
        return expression.getOperand().accept(this);
      }
    }

  }


  public List<Pair<Identifier, Access>> result;
  protected Access accessMode;
  protected String function;
  protected int dereferenceCounter;

  public void setMode(String funcName, Access mode) {
    result = new LinkedList<Pair<Identifier, Access>>();
    function = funcName;
    dereferenceCounter = 0;
    accessMode = mode;
  }

  @Override
  public Void visit(CArraySubscriptExpression expression) throws HandleCodeException {
    expression.getArrayExpression().accept(this);
    return null;
  }

  @Override
  public Void visit(CBinaryExpression expression) throws HandleCodeException {
    if (accessMode == Access.READ) {
      expression.getOperand1().accept(this);
      expression.getOperand2().accept(this);
    } else {
      //TODO handle it
      // *(a + b) = ... -> here
      //throw new HandleCodeException(expression.toASTString() + " can't be in left side of statement");
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
    isLocalExpression localChecker = new isLocalExpression();
    //Checks, if this variable is local. If it is so, we don't need to save it in statistics
    if (!(expression.getFieldOwner().accept(localChecker))) {
      Identifier id = new StructureFieldIdentifier(expression.getFieldName(),
        expression.getExpressionType(), expression.getFieldOwner().getExpressionType(), dereferenceCounter);
      result.add(Pair.of(id, accessMode));
    }
    accessMode = Access.READ;
    dereferenceCounter = (expression.isPointerDereference() ? 1 : 0);
    expression.getFieldOwner().accept(this);
    return null;
  }

  @Override
  public Void visit(CIdExpression expression) throws HandleCodeException {
    Identifier id = Identifier.createIdentifier(expression, function, dereferenceCounter);
    result.add(Pair.of(id, accessMode));
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
    if (expression.getOperator() == CUnaryExpression.UnaryOperator.STAR) {
      //write: *s =
      ++dereferenceCounter;
      expression.getOperand().accept(this);
      //read: s
      dereferenceCounter = 0;
    } else if (expression.getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
      --dereferenceCounter;
      expression.getOperand().accept(this);
      dereferenceCounter = 0;
    }
    //In all other unary operation we only read the operand
    accessMode = Access.READ;
    expression.getOperand().accept(this);
    return null;
  }

}

