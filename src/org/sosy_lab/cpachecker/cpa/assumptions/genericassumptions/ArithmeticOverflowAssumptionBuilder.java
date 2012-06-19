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
package org.sosy_lab.cpachecker.cpa.assumptions.genericassumptions;

import java.util.List;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;

/**
 * Class to generate assumptions related to over/underflow
 * of integer arithmetic operations
 */
public class ArithmeticOverflowAssumptionBuilder
implements GenericAssumptionBuilder
{

  private static Pair<CIntegerLiteralExpression, CIntegerLiteralExpression> boundsForType(CType typ)
  {
    if (typ instanceof CSimpleType) {
      CSimpleType btyp = (CSimpleType) typ;

        switch (btyp.getType()) {
        case INT:
          // TODO not handled yet by mathsat so we assume all vars are signed integers for now
          // will enable later
          return Pair.of
          (CNumericTypes.INT_MIN, CNumericTypes.INT_MAX);
          //          if (btyp.isLong())
          //            if (btyp.isUnsigned())
          //              return new Pair<DummyASTNumericalLiteralExpression, DummyASTNumericalLiteralExpression>
          //          (DummyASTNumericalLiteralExpression.ULONG_MIN, DummyASTNumericalLiteralExpression.ULONG_MAX);
          //            else
          //              return new Pair<DummyASTNumericalLiteralExpression, DummyASTNumericalLiteralExpression>
          //          (DummyASTNumericalLiteralExpression.LONG_MIN, DummyASTNumericalLiteralExpression.LONG_MAX);
          //          else if (btyp.isShort())
          //            if (btyp.isUnsigned())
          //              return new Pair<DummyASTNumericalLiteralExpression, DummyASTNumericalLiteralExpression>
          //          (DummyASTNumericalLiteralExpression.USHRT_MIN, DummyASTNumericalLiteralExpression.USHRT_MAX);
          //            else
          //              return new Pair<DummyASTNumericalLiteralExpression, DummyASTNumericalLiteralExpression>
          //          (DummyASTNumericalLiteralExpression.SHRT_MIN, DummyASTNumericalLiteralExpression.SHRT_MAX);
          //          else
          //            if (btyp.isUnsigned())
          //              return new Pair<DummyASTNumericalLiteralExpression, DummyASTNumericalLiteralExpression>
          //          (DummyASTNumericalLiteralExpression.UINT_MIN, DummyASTNumericalLiteralExpression.UINT_MAX);
          //            else
          //              return new Pair<DummyASTNumericalLiteralExpression, DummyASTNumericalLiteralExpression>
          //          (DummyASTNumericalLiteralExpression.INT_MIN, DummyASTNumericalLiteralExpression.INT_MAX);
          //        case IBasicType.t_char:
          //          if (btyp.isUnsigned())
          //            return new Pair<DummyASTNumericalLiteralExpression, DummyASTNumericalLiteralExpression>
          //          (DummyASTNumericalLiteralExpression.UCHAR_MIN, DummyASTNumericalLiteralExpression.UCHAR_MAX);
          //          else
          //            return new Pair<DummyASTNumericalLiteralExpression, DummyASTNumericalLiteralExpression>
          //          (DummyASTNumericalLiteralExpression.CHAR_MIN, DummyASTNumericalLiteralExpression.CHAR_MAX);
      }
    }
    return Pair.of(null, null);
  }

    /**
     * Compute and conjunct the assumption for the given arithmetic
     * expression, ignoring bounds if applicable. The method does
     * not check that the expression is indeed an arithmetic expression.
     */
    private static CExpression conjunctPredicateForArithmeticExpression(CExpression exp, CExpression result)
    {
      return conjunctPredicateForArithmeticExpression(exp.getExpressionType(), exp, result);
    }

  /**
   * Compute and conjunct the assumption for the given arithmetic
   * expression, given as its type and its expression.
   * The two last, boolean arguments allow to avoid generating
   * lower and/or upper bounds predicates.
   */
  private static CExpression conjunctPredicateForArithmeticExpression(CType typ,
      CExpression exp, CExpression result) {

    Pair<CIntegerLiteralExpression, CIntegerLiteralExpression> bounds =
        boundsForType(typ);

    if (bounds.getFirst() != null) {

      final CBinaryExpression secondExp = new CBinaryExpression(null, null, exp,
              bounds.getFirst(), BinaryOperator.GREATER_EQUAL);
      result = new CBinaryExpression(null, null,
              result, secondExp, BinaryOperator.LOGICAL_AND);
    }

    if (bounds.getSecond() != null) {

      final CBinaryExpression secondExp = new CBinaryExpression(null, null, exp,
              bounds.getSecond(), BinaryOperator.LESS_EQUAL);
      result =new CBinaryExpression(null, null,
              result, secondExp, BinaryOperator.LOGICAL_AND);
    }
    return result;
  }

    private static CExpression visit(CExpression pExpression, CExpression result) {
      if(pExpression instanceof CIdExpression){
        result = conjunctPredicateForArithmeticExpression(pExpression, result);
      }
      else if (pExpression instanceof CBinaryExpression)
      {
        CBinaryExpression binexp = (CBinaryExpression)pExpression;
        CExpression op1 = binexp.getOperand1();
        // Only variables for now, ignoring * & operators
        if(op1 instanceof CIdExpression){
          result = conjunctPredicateForArithmeticExpression(op1, result);
        }
      }
      else if (pExpression instanceof CUnaryExpression)
      {
        CUnaryExpression unexp = (CUnaryExpression)pExpression;
        CExpression op1 = unexp.getOperand();
        // Only variables. Ignoring * & operators for now
        if(op1 instanceof CIdExpression){
          result = conjunctPredicateForArithmeticExpression(op1, result);
        }
      }
      else if (pExpression instanceof CCastExpression)
      {
        CCastExpression castexp = (CCastExpression)pExpression;
        CType toType = castexp.getExpressionType();
        result = conjunctPredicateForArithmeticExpression(toType, castexp.getOperand(), result);
      }
      return result;
    }

  @Override
  public CExpression assumptionsForEdge(CFAEdge pEdge) {
    CExpression result = CNumericTypes.TRUE;

    switch (pEdge.getEdgeType()) {
    case AssumeEdge:
      CAssumeEdge assumeEdge = (CAssumeEdge) pEdge;
      result = visit(assumeEdge.getExpression(), result);
      break;
    case FunctionCallEdge:
      CFunctionCallEdge fcallEdge = (CFunctionCallEdge) pEdge;
      if (!fcallEdge.getArguments().isEmpty()) {
        CFunctionEntryNode fdefnode = fcallEdge.getSuccessor();
        List<CParameterDeclaration> formalParams = fdefnode.getFunctionParameters();
        for (CParameterDeclaration paramdecl : formalParams)
        {
          String name = paramdecl.getName();
          CType type = paramdecl.getType();
          CExpression exp = new CIdExpression(paramdecl.getFileLocation(), type, name, paramdecl);
          result = visit(exp, result);
        }
      }
      break;
    case StatementEdge:
      CStatementEdge stmtEdge = (CStatementEdge) pEdge;

      CStatement stmt = stmtEdge.getStatement();
      if (stmt instanceof CAssignment) {
        result = visit(((CAssignment)stmt).getLeftHandSide(), result);
      }
      break;
    case ReturnStatementEdge:
      CReturnStatementEdge returnEdge = (CReturnStatementEdge) pEdge;

      if(returnEdge.getExpression() != null){
        result = visit(returnEdge.getExpression(), result);
      }
      break;
    }
    return result;
  }
}
