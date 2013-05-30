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
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.BinaryIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.ConstantIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;

import com.google.common.base.Preconditions;


public class IdentifierCreator implements CExpressionVisitor<AbstractIdentifier, HandleCodeException> {
  protected int dereference;
  protected String function;

  public void clear(String func) {
    dereference = 0;
    function = func;
  }

  public void clearDereference() {
    dereference = 0;
  }

  @Override
  public AbstractIdentifier visit(CArraySubscriptExpression expression) throws HandleCodeException {
    dereference++;
    return expression.getArrayExpression().accept(this);
  }

  @Override
  public AbstractIdentifier visit(CBinaryExpression expression) throws HandleCodeException {
    AbstractIdentifier resultId1, resultId2, result;
    int oldDereference = dereference;
    dereference = 0;
    resultId1 = expression.getOperand1().accept(this);
    dereference = 0;
    resultId2 = expression.getOperand2().accept(this);
    result = new BinaryIdentifier(resultId1, resultId2, oldDereference);
    dereference = oldDereference;
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

    if (decl == null)
      //In our cil-file it means, that we have function pointer
      //This data can't be shared (we wouldn't write)
      return new LocalVariableIdentifier(expression.getName(), expression.getExpressionType(), function, dereference);
    else
      return createIdentifier(decl, function, dereference);

    /*if (decl instanceof CDeclaration) {
      if (((CDeclaration)decl).isGlobal())
        return new GlobalVariableIdentifier(expression.getName(), expression.getExpressionType(), dereference);
      else
        return new LocalVariableIdentifier(expression.getName(), expression.getExpressionType(), function, dereference);
    } else if (decl instanceof CParameterDeclaration) {
      return new LocalVariableIdentifier(expression.getName(), expression.getExpressionType(), function, dereference);
    } else if (decl instanceof CEnumerator) {
      return new ConstantIdentifier(decl.getName(), dereference);
    } else if (decl == null) {
      //In our cil-file it means, that we have function pointer
      //This data can't be shared (we wouldn't write)
      return new LocalVariableIdentifier(expression.getName(), expression.getExpressionType(), function, dereference);
    } else {
      //Composite type
      return null;
    }*/
  }

  @Override
  public AbstractIdentifier visit(CCharLiteralExpression expression) throws HandleCodeException {
    return new ConstantIdentifier(expression.toASTString(), dereference);
  }

  @Override
  public AbstractIdentifier visit(CFloatLiteralExpression expression) throws HandleCodeException {
    return new ConstantIdentifier(expression.toASTString(), dereference);
  }

  @Override
  public AbstractIdentifier visit(CIntegerLiteralExpression expression) throws HandleCodeException {
    return new ConstantIdentifier(expression.toASTString(), dereference);
  }

  @Override
  public AbstractIdentifier visit(CStringLiteralExpression expression) throws HandleCodeException {
    return new ConstantIdentifier(expression.toASTString(), dereference);
  }

  @Override
  public AbstractIdentifier visit(CTypeIdExpression expression) throws HandleCodeException {
    return new ConstantIdentifier(expression.toASTString(), dereference);
  }

  @Override
  public AbstractIdentifier visit(CUnaryExpression expression) throws HandleCodeException {
    if (expression.getOperator() == CUnaryExpression.UnaryOperator.STAR) {
      ++dereference;
    } else if (expression.getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
      --dereference;
    }
    AbstractIdentifier result = expression.getOperand().accept(this);
    if (result instanceof BinaryIdentifier) {
      /* It is very strange, but CIL sometimes replace 'a[i]' to '*(a + i)'
       * So, if we see it, create other identifier: '*a'
       */
      AbstractIdentifier id1 = ((BinaryIdentifier)result).getIdentifier1();
      AbstractIdentifier id2 = ((BinaryIdentifier)result).getIdentifier2();
      AbstractIdentifier main = null;
      if (id1 instanceof SingleIdentifier && id2 instanceof ConstantIdentifier) {
        main = id1;
      } else if (id2 instanceof SingleIdentifier && id1 instanceof ConstantIdentifier) {
        main = id2;
      } else if (id1 instanceof SingleIdentifier && id2 instanceof SingleIdentifier) {
        SingleIdentifier s1 = (SingleIdentifier) id1;
        SingleIdentifier s2 = (SingleIdentifier) id2;
        if (s1.isPointer() && !s2.isPointer()) {
          main = s1;
        } else if (s1.isPointer() && !s2.isPointer()) {
          main = s2;
        } else if (s1.getType().getClass() == CSimpleType.class && s2.getType().getClass() != CSimpleType.class) {
          main = s2;
        } else if (s2.getType().getClass() == CSimpleType.class && s1.getType().getClass() != CSimpleType.class) {
          main = s1;
        }
      }
      if (main != null) {
        main.setDereference(main.getDereference() + result.getDereference());
        return main;
      } else {
        return result;
      }
    }
    return expression.getOperand().accept(this);
  }

  public void setDereference(int pDereference) {
    dereference = pDereference;
  }

  @Override
  public AbstractIdentifier visit(CTypeIdInitializerExpression pCTypeIdInitializerExpression)
      throws HandleCodeException {
    //TODO Think about later
    return null;
  }

  public static AbstractIdentifier createIdentifier(CSimpleDeclaration decl, String function, int dereference) throws HandleCodeException
  {
    /*String name = decl.getName();
    CType type = decl.getType();

    if (decl instanceof CDeclaration){
      if(((CDeclaration)decl).isGlobal())
        return new GlobalVariableIdentifier(name, type, dereference);
      else {
        return new LocalVariableIdentifier(name, type, function, dereference);
      }

    } else if (decl instanceof CParameterDeclaration) {
      return new LocalVariableIdentifier(name, type, function, dereference);

    } else {
      throw new HandleCodeException("Unrecognized declaration: " + decl.toASTString());
    }*/

    Preconditions.checkNotNull(decl);
    String name = decl.getName();
    CType type = decl.getType();

    if (decl instanceof CDeclaration) {
      if (((CDeclaration)decl).isGlobal())
        return new GlobalVariableIdentifier(name, type, dereference);
      else
        return new LocalVariableIdentifier(name, type, function, dereference);
    } else if (decl instanceof CParameterDeclaration) {
      return new LocalVariableIdentifier(name, type, function, dereference);
    } else if (decl instanceof CEnumerator) {
      return new ConstantIdentifier(name, dereference);
    } else {
      //Composite type
      return null;
    }
  }
}
