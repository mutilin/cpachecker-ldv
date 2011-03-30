/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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

import org.sosy_lab.cpachecker.cfa.ast.IASTBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.IASTCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTName;
import org.sosy_lab.cpachecker.cfa.ast.IASTNode;
import org.sosy_lab.cpachecker.cfa.ast.IASTParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.IASTSimpleDeclSpecifier;
import org.sosy_lab.cpachecker.cfa.ast.IASTUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.IType;
import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.ReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;
import org.sosy_lab.cpachecker.util.assumptions.NumericTypes;

/**
 * Class to generate assumptions related to over/underflow
 * of integer arithmetic operations
 *
 * @author g.theoduloz
 */
public class ArithmeticOverflowAssumptionBuilder
implements GenericAssumptionBuilder
{

  public static boolean isDeclGlobal = false;

  private static Pair<IASTIntegerLiteralExpression, IASTIntegerLiteralExpression> boundsForType(IType typ)
  {
    if (typ instanceof IASTSimpleDeclSpecifier) {
      IASTSimpleDeclSpecifier btyp = (IASTSimpleDeclSpecifier) typ;

        switch (btyp.getType()) {
        case INT:
          // TODO not handled yet by mathsat so we assume all vars are signed integers for now
          // will enable later
          return Pair.of
          (NumericTypes.INT_MIN, NumericTypes.INT_MAX);
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
    private static IASTNode conjunctPredicateForArithmeticExpression(IASTExpression exp, IASTNode result)
    {
      return conjunctPredicateForArithmeticExpression(exp.getExpressionType(), exp, result);
    }

  /**
   * Compute and conjunct the assumption for the given arithmetic
   * expression, given as its type and its expression.
   * The two last, boolean arguments allow to avoid generating
   * lower and/or upper bounds predicates.
   */
  private static IASTNode conjunctPredicateForArithmeticExpression(IType typ,
      IASTExpression exp, IASTNode result) {

    Pair<IASTIntegerLiteralExpression, IASTIntegerLiteralExpression> bounds =
        boundsForType(typ);

    if (bounds.getFirst() != null) {

      final String rawStatementOfsecondExpr =
          "(" + exp.getRawSignature() + ">=" + bounds.getFirst().getRawSignature() + ")";
      final IASTBinaryExpression secondExp =
          new IASTBinaryExpression(rawStatementOfsecondExpr, null, null, exp,
              bounds.getFirst(), BinaryOperator.GREATER_EQUAL);
      final String rawStatementOfNewResult =
          "(" + result.getRawSignature() + "&&" + secondExp.getRawSignature()
              + ")";
      result =
          new IASTBinaryExpression(rawStatementOfNewResult, null, null,
              (IASTExpression) result, secondExp, BinaryOperator.LOGICAL_AND);
    }

    if (bounds.getSecond() != null) {

      final String rawStatementOfsecondExpr =
          "(" + exp.getRawSignature() + "<=" + bounds.getSecond().getRawSignature() + ")";
      final IASTBinaryExpression secondExp =
          new IASTBinaryExpression(rawStatementOfsecondExpr, null, null, exp,
              bounds.getSecond(), BinaryOperator.LESS_EQUAL);
      final String rawStatementOfNewResult =
          "(" + result.getRawSignature() + "&&" + secondExp.getRawSignature()
              + ")";
      result =
          new IASTBinaryExpression(rawStatementOfNewResult, null, null,
              (IASTExpression) result, secondExp, BinaryOperator.LOGICAL_AND);
    }
    return result;
  }

    private static IASTNode visit(IASTExpression pExpression, IASTNode result) {
      if(pExpression instanceof IASTIdExpression){
        result = conjunctPredicateForArithmeticExpression(pExpression, result);
      }
      else if (pExpression instanceof IASTBinaryExpression)
      {
        IASTBinaryExpression binexp = (IASTBinaryExpression)pExpression;
        IASTExpression op1 = binexp.getOperand1();
        // Only variables for now, ignoring * & operators
        if(op1 instanceof IASTIdExpression){
          result = conjunctPredicateForArithmeticExpression(op1, result);
        }
      }
      else if (pExpression instanceof IASTUnaryExpression)
      {
        IASTUnaryExpression unexp = (IASTUnaryExpression)pExpression;
        IASTExpression op1 = unexp.getOperand();
        // Only variables. Ignoring * & operators for now
        if(op1 instanceof IASTIdExpression){
          result = conjunctPredicateForArithmeticExpression(op1, result);
        }
      }
      else if (pExpression instanceof IASTCastExpression)
      {
        IASTCastExpression castexp = (IASTCastExpression)pExpression;
        IType toType = castexp.getExpressionType();
        result = conjunctPredicateForArithmeticExpression(toType, castexp.getOperand(), result);
      }
      return result;
    }

  @Override
  public IASTNode assumptionsForEdge(CFAEdge pEdge) {
    IASTNode result = NumericTypes.TRUE;
    
    switch (pEdge.getEdgeType()) {
    case DeclarationEdge:
      DeclarationEdge declarationEdge = (DeclarationEdge) pEdge;
      result = declarationEdge.getRawAST();
      isDeclGlobal = declarationEdge.isGlobal();
      break;
    case AssumeEdge:
      AssumeEdge assumeEdge = (AssumeEdge) pEdge;
      result = visit(assumeEdge.getExpression(), result);
      break;
    case FunctionCallEdge:
      FunctionCallEdge fcallEdge = (FunctionCallEdge) pEdge;
      if (!fcallEdge.getArguments().isEmpty()) {
        FunctionDefinitionNode fdefnode = fcallEdge.getSuccessor();
        List<IASTParameterDeclaration> formalParams = fdefnode.getFunctionParameters();
        for (IASTParameterDeclaration paramdecl : formalParams)
        {
          IASTName name = paramdecl.getName();
          IType type = name.getType();
          IASTExpression exp = new IASTIdExpression(paramdecl.getRawSignature(), paramdecl.getFileLocation(), type, name, paramdecl);
          result = visit(exp, result);
        }
      }
      break;
    case StatementEdge:
      StatementEdge stmtEdge = (StatementEdge) pEdge;

      IASTExpression iastExp = stmtEdge.getExpression();
      // TODO replace with a global nondet variable
      if(iastExp != null && iastExp.getRawSignature().contains("__BLAST_NONDET")){
        break;
      }

      if(stmtEdge.getExpression() != null){
        result = visit(stmtEdge.getExpression(), result);
      }
      break;
    case ReturnStatementEdge:
      ReturnStatementEdge returnEdge = (ReturnStatementEdge) pEdge;

      if(returnEdge.getExpression() != null){
        result = visit(returnEdge.getExpression(), result);
      }
      break;
    }
    return result;
  }
}
