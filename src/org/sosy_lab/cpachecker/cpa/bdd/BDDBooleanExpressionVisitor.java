/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.bdd;

import org.sosy_lab.cpachecker.cfa.ast.c.*;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;
import org.sosy_lab.cpachecker.util.predicates.interfaces.RegionManager;

/** This Visitor implements evaluation of simply typed expressions.
 * This Visitor is specialized for boolean expressions. */
public class BDDBooleanExpressionVisitor
        extends DefaultCExpressionVisitor<Region, RuntimeException> {

  private final static int BOOLEAN_SIZE = 1;
  private final PredicateManager predMgr;
  private final BDDPrecision precision;
  protected final RegionManager rmgr;

  /** This Visitor returns the boolean value for an expression. */
  protected BDDBooleanExpressionVisitor(final PredicateManager pPredMgr, final RegionManager pRmgr, final BDDPrecision pPrecision) {
    this.predMgr = pPredMgr;
    this.rmgr = pRmgr;
    this.precision = pPrecision;
  }

  @Override
  protected Region visitDefault(CExpression pExp) {
    return null;
  }

  @Override
  public Region visit(final CBinaryExpression pE) {
    final Region lVal = pE.getOperand1().accept(this);
    final Region rVal = pE.getOperand2().accept(this);
    if (lVal == null || rVal == null) {
      return null;
    }
    return calculateBinaryOperation(lVal, rVal, rmgr, pE);
  }

  public static Region calculateBinaryOperation(Region l, Region r, final RegionManager rmgr,
                                                final CBinaryExpression binaryExpr) {

    final BinaryOperator binaryOperator = binaryExpr.getOperator();
    switch (binaryOperator) {
      case BINARY_AND:
        return rmgr.makeAnd(l, r);
      case BINARY_OR:
        return rmgr.makeOr(l, r);
      case BINARY_XOR:
      case NOT_EQUALS:
        return rmgr.makeUnequal(l, r);
      case EQUALS:
        return rmgr.makeEqual(l, r);
      default:
        throw new AssertionError("unhandled binary operator");
    }
  }

  @Override
  public Region visit(CCharLiteralExpression pE) {
    return getNum(pE.getCharacter());
  }

  @Override
  public Region visit(CIntegerLiteralExpression pE) {
    return getNum(pE.asLong());
  }

  @Override
  public Region visit(CImaginaryLiteralExpression pE) {
    return pE.getValue().accept(this);
  }

  @Override
  public Region visit(CIdExpression idExp) {
    if (idExp.getDeclaration() instanceof CEnumerator) {
      CEnumerator enumerator = (CEnumerator) idExp.getDeclaration();
      if (enumerator.hasValue()) {
        return getNum(enumerator.getValue());
      } else {
        return null;
      }
    }

    return predMgr.createPredicate(idExp.getDeclaration().getQualifiedName(), BOOLEAN_SIZE, precision)[0];
  }

  private Region getNum(long num) {
    if (num == 0) {
      return rmgr.makeFalse();
    } else if (num == 1) {
      return rmgr.makeTrue();
    } else {
      throw new AssertionError("no boolean value: " + num);
    }
  }
}