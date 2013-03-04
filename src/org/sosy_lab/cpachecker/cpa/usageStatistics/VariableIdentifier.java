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
package org.sosy_lab.cpachecker.cpa.usageStatistics;

import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;


public abstract class VariableIdentifier implements Identifier {

  public static enum Ref {
    VARIABLE,
    ADRESS,
    REFERENCE;

    public String toASTString() {
      return " is " + name().toLowerCase();
    }
  }

  public VariableIdentifier(String nm, CType tp, int dereference) {
    name = nm;
    type = tp;

    if (dereference < 0)
      status = Ref.ADRESS;
    else if (dereference == 0)
      status = Ref.VARIABLE;
    else /*if (dereference > 0)*/
      status = Ref.REFERENCE;
  }

  public VariableIdentifier(String nm, CType tp, Ref dereference) {
    name = nm;
    type = tp;
    status = dereference;
  }

  protected String name;
  protected CType type;
  protected Ref status;

  public Ref getStatus() {
    return status;
  }

  public CType getType() {
    return type;
  }

  public VariableIdentifier makeAdress() {
    VariableIdentifier newId = this.clone();
    newId.status = Ref.ADRESS;
    return newId;
  }

  public VariableIdentifier makeReference() {
    VariableIdentifier newId = this.clone();
    newId.status = Ref.REFERENCE;
    return newId;
  }

  public VariableIdentifier makeVariable() {
    VariableIdentifier newId = this.clone();
    newId.status = Ref.VARIABLE;
    return newId;
  }

  public String getSimpleName() {
    return name;
  }

  public String getName() {
    if (status == Ref.ADRESS)
      return "(&" + name + ")";
    else if (status == Ref.VARIABLE)
      return name;
    else if (status == Ref.REFERENCE)
      return "(*" + name + ")";
    else
      return name;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((status == null) ? 0 : status.hashCode());
    result = prime * result + ((type == null) ? 0 : type.toASTString("").hashCode());
    return result;
  }
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    VariableIdentifier other = (VariableIdentifier) obj;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (status != other.status)
      return false;
    if (type == null) {
      if (other.type != null)
        return false;
    } else if (!type.toASTString("").equals(other.type.toASTString("")))
      return false;
    return true;
  }

  @Override
  public abstract VariableIdentifier clone();

  public static VariableIdentifier createIdentifier(CExpression expression, String function, int dereference) throws HandleCodeException {
    if (expression instanceof CArraySubscriptExpression) {
      return createIdentifier(((CArraySubscriptExpression)expression).getArrayExpression(), function, dereference);

    } else if (expression instanceof CBinaryExpression) {
      System.err.println("Try to create identifier for binary expression");
      return null;

    } else if (expression instanceof CFieldReference) {
        return new StructureFieldIdentifier(((CFieldReference)expression).getFieldName(),
          ((CFieldReference)expression).getFieldOwner().getExpressionType(),
          ((CFieldReference)expression).getExpressionType().toASTString(""),
          (((CFieldReference)expression).isPointerDereference() ? ++dereference : dereference));

    } else if (expression instanceof CIdExpression) {
      return VariableIdentifier.createIdentifier((CIdExpression)expression, function, dereference);

    } else if (expression instanceof CUnaryExpression) {
      if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.STAR) {
        return createIdentifier(((CUnaryExpression)expression).getOperand(), function, ++dereference);
      } else if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
        return createIdentifier(((CUnaryExpression)expression).getOperand(), function, --dereference);
      } else {
        return createIdentifier(((CUnaryExpression)expression).getOperand(), function, dereference);
      }

    } else {
      //CLiteralExpression, CCastExpression), CTypeIdExpression - do nothing
      return null;
    }
  }

  public static VariableIdentifier createIdentifier(CIdExpression expression, String function, int dereference) throws HandleCodeException {

    CSimpleDeclaration decl = expression.getDeclaration();

    if (decl == null) {
        /*
         * It means, that we have function, but parser couldn't understand this:
         * int f();
         * int (*a)() = &f;
         * Skip it
         */
      return null;
    }

    return VariableIdentifier.createIdentifier(decl, function, dereference);
  }

  public static VariableIdentifier createIdentifier(CSimpleDeclaration decl, String function, int dereference) throws HandleCodeException
  {
    String name = decl.getName();
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
    }
  }

}
