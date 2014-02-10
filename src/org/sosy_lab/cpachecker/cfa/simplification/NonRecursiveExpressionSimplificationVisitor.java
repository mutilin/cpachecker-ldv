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
package org.sosy_lab.cpachecker.cfa.simplification;

import java.math.BigInteger;
import java.util.Set;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression.TypeIdOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.cfa.types.c.CProblemType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cpa.explicit.ExplicitExpressionValueVisitor;

import com.google.common.collect.ImmutableSet;

/**
 * This visitor visits an expression and evaluates it.
 * It tries to evaluate only the outermost operator,
 * i.e., it evaluates 1+2 to 3, but not (1+1)+1.
 * If evaluation is successful, it returns a CIntegerLiteralExpression with the new value,
 * otherwise it returns the original expression.
 */
public class NonRecursiveExpressionSimplificationVisitor extends DefaultCExpressionVisitor
    <CExpression, RuntimeException> {

  private final Set<UnaryOperator> EVALUATABLE_UNARY_OPERATORS =
      ImmutableSet.of(UnaryOperator.PLUS, UnaryOperator.MINUS, UnaryOperator.NOT);

  private final MachineModel machineModel;
  private final LogManager logger;

  public NonRecursiveExpressionSimplificationVisitor(MachineModel mm, LogManager pLogger) {
    this.machineModel = mm;
    this.logger = pLogger;
  }

  @Override
  protected CExpression visitDefault(final CExpression expr) {
    return expr;
  }

  private Long getValue(CExpression expr) {
    if (expr instanceof CIntegerLiteralExpression) {
      return ((CIntegerLiteralExpression)expr).asLong();
    }
    // TODO CharLiteralExpression
    return null;
  }

  @Override
  public CExpression visit(CIdExpression expr) {
    if (expr.getDeclaration() instanceof CEnumerator) {
      // enum constant
      if (((CEnumerator)expr.getDeclaration()).hasValue()) {
        long v = ((CEnumerator)expr.getDeclaration()).getValue();
        return new CIntegerLiteralExpression(expr.getFileLocation(),
            expr.getExpressionType(), BigInteger.valueOf(v));
      }
    }

    if (!(expr.getExpressionType() instanceof CProblemType)
        && expr.getExpressionType().isConst()
        && expr.getDeclaration() instanceof CVariableDeclaration) {
      // const variable, inline initializer

      CInitializer init = ((CVariableDeclaration)expr.getDeclaration()).getInitializer();
      if (init instanceof CExpression) {
        Long v = getValue((CExpression)init);
        if (v != null) {
          return new CIntegerLiteralExpression(expr.getFileLocation(),
              expr.getExpressionType(), BigInteger.valueOf(v));
        }
      }
    }

    return visitDefault(expr);
  }

  @Override
  public CExpression visit(final CBinaryExpression expr) {
    final CExpression op1 = expr.getOperand1();
    final Long v1 = getValue(op1);
    if (v1 == null) {
      return expr;
    }

    final CExpression op2 = expr.getOperand2();
    final Long v2 = getValue(op2);
    if (v2 == null) {
      return expr;
    }

    long result = ExplicitExpressionValueVisitor.calculateBinaryOperation(
        v1, v2, expr, machineModel, logger, null);

    return new CIntegerLiteralExpression(expr.getFileLocation(),
            expr.getExpressionType(), BigInteger.valueOf(result));
  }

  @Override
  public CExpression visit(CCastExpression expr) {
    final CExpression op = expr.getOperand();
    final Long v = getValue(op);
    if (v == null) {
      return expr;
    }

    final long castedValue = ExplicitExpressionValueVisitor.castCValue(
        v, expr.getExpressionType(), machineModel, logger, null);

    return new CIntegerLiteralExpression(expr.getFileLocation(),
            expr.getExpressionType(), BigInteger.valueOf(castedValue));
  }

  @Override
  public CExpression visit(final CTypeIdExpression expr) {
    final TypeIdOperator idOperator = expr.getOperator();
    final CType innerType = expr.getType();

    switch (idOperator) {
    case SIZEOF:
      int size = machineModel.getSizeof(innerType);
      return new CIntegerLiteralExpression(expr.getFileLocation(),
              expr.getExpressionType(), BigInteger.valueOf(size));

    default: // TODO support more operators
      return visitDefault(expr);
    }
  }

  @Override
  public CExpression visit(final CUnaryExpression expr) {
    final UnaryOperator unaryOperator = expr.getOperator();
    final CExpression op = expr.getOperand();

    // in case of a SIZEOF we do not need to know the explicit value of the variable,
    // it is enough to know its type
    if (unaryOperator == UnaryOperator.SIZEOF) {
      final int result = machineModel.getSizeof(op.getExpressionType());
      return new CIntegerLiteralExpression(expr.getFileLocation(),
              expr.getExpressionType(), BigInteger.valueOf(result));
    }

    if (!EVALUATABLE_UNARY_OPERATORS.contains(unaryOperator)) {
      return expr;
    }

    final Long value = getValue(op);
    if (value == null) {
      return expr;
    }
    long result;

    // TODO machinemodel
    switch (unaryOperator) {
    case PLUS:
      result = value;
      break;

    case MINUS:
      result = -value;
      break;

    case NOT:
      result = (value == 0L) ? 1L : 0L;
      break;

    default:
      throw new AssertionError("unknown unary operation: " + unaryOperator);
    }

    return new CIntegerLiteralExpression(expr.getFileLocation(),
            expr.getExpressionType(), BigInteger.valueOf(result));
  }
}
