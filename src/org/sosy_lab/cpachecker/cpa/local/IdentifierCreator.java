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
package org.sosy_lab.cpachecker.cpa.local;

import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.BinaryIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.ConstantIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;


public class IdentifierCreator implements CExpressionVisitor<AbstractIdentifier, HandleCodeException> {
  protected int dereference;

  public void clearDereference() {
    dereference = 0;
  }

  @Override
  public AbstractIdentifier visit(CArraySubscriptExpression expression) throws HandleCodeException {
    return expression.getArrayExpression().accept(this);
  }

  @Override
  public AbstractIdentifier visit(CBinaryExpression expression) throws HandleCodeException {
    AbstractIdentifier resultId1, resultId2, result;
    int oldDereference = dereference;
    resultId1 = expression.getOperand1().accept(this);
    resultId2 = expression.getOperand2().accept(this);
    result = new BinaryIdentifier(resultId1, resultId2, oldDereference);
    return result;
  }

  @Override
  public AbstractIdentifier visit(CCastExpression expression) throws HandleCodeException {
    return expression.getOperand().accept(this);
  }

  @Override
  public AbstractIdentifier visit(CFieldReference expression) throws HandleCodeException {
    CExpression owner = expression.getFieldOwner();
    int oldDeref = dereference;
    dereference = (expression.isPointerDereference() ? 1 : 0);
    AbstractIdentifier ownerId = owner.accept(this);
    dereference = oldDeref;
    StructureIdentifier fullId = new StructureIdentifier(expression.getFieldName(), expression.getExpressionType()
        , dereference, ownerId);
    return fullId;
  }

  @Override
  public AbstractIdentifier visit(CIdExpression expression) throws HandleCodeException {
    CSimpleDeclaration decl = expression.getDeclaration();

    if (decl instanceof CDeclaration) {
      if (((CDeclaration)decl).isGlobal())
        return new GlobalVariableIdentifier(expression.getName(), expression.getExpressionType(), dereference);
      else
        return new LocalVariableIdentifier(expression.getName(), expression.getExpressionType(), "", dereference);
    } else if (decl instanceof CParameterDeclaration) {
      return new LocalVariableIdentifier(expression.getName(), expression.getExpressionType(), "", dereference);
    } else if (decl instanceof CEnumerator) {
      return new ConstantIdentifier(decl.getName());
    } else {
      //Composite type
      return null;
    }
  }

  @Override
  public AbstractIdentifier visit(CCharLiteralExpression expression) throws HandleCodeException {
    return new ConstantIdentifier(expression.toASTString());
  }

  @Override
  public AbstractIdentifier visit(CFloatLiteralExpression expression) throws HandleCodeException {
    return new ConstantIdentifier(expression.toASTString());
  }

  @Override
  public AbstractIdentifier visit(CIntegerLiteralExpression expression) throws HandleCodeException {
    return new ConstantIdentifier(expression.toASTString());
  }

  @Override
  public AbstractIdentifier visit(CStringLiteralExpression expression) throws HandleCodeException {
    return new ConstantIdentifier(expression.toASTString());
  }

  @Override
  public AbstractIdentifier visit(CTypeIdExpression expression) throws HandleCodeException {
    return new ConstantIdentifier(expression.toASTString());
  }

  @Override
  public AbstractIdentifier visit(CUnaryExpression expression) throws HandleCodeException {
    if (expression.getOperator() == CUnaryExpression.UnaryOperator.STAR) {
      ++dereference;
    } else if (expression.getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
      --dereference;
    }
    return expression.getOperand().accept(this);
  }

  public void setDereference(int pDereference) {
    dereference = pDereference;
  }

}
