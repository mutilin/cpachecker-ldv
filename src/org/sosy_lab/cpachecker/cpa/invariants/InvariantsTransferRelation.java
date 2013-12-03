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
package org.sosy_lab.cpachecker.cpa.invariants;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.invariants.formula.CompoundStateFormulaManager;
import org.sosy_lab.cpachecker.cpa.invariants.formula.ExpressionToFormulaVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.ExpressionToFormulaVisitor.VariableNameExtractor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.FormulaEvaluationVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.InvariantsFormula;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

import com.google.common.collect.FluentIterable;

public enum InvariantsTransferRelation implements TransferRelation {

  INSTANCE;

  /**
   * Base name of the variable that is introduced to pass results from
   * returning function calls.
   */
  static final String RETURN_VARIABLE_BASE_NAME = "___cpa_temp_result_var_";

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState pElement, Precision pPrecision, CFAEdge pEdge)
      throws CPATransferException {

    InvariantsState element = (InvariantsState)pElement;

    element = getSuccessor(pEdge, element);

    if (element == null) {
      return Collections.emptySet();
    } else {
      return Collections.singleton(element);
    }
  }

  private InvariantsState getSuccessor(CFAEdge pEdge, InvariantsState pState) throws UnrecognizedCFAEdgeException, UnrecognizedCCodeException {
    InvariantsState element = pState;
    if (!element.isRelevant(pEdge)) {
      element = pState.clear();
    } else {
      switch (pEdge.getEdgeType()) {
      case BlankEdge:
        break;
      case FunctionReturnEdge:
        element = handleFunctionReturn(element, (CFunctionReturnEdge) pEdge);
        break;
      case ReturnStatementEdge:
        element = handleReturnStatement(element, (CReturnStatementEdge) pEdge);
        break;
      case AssumeEdge:
        element = handleAssume(element, (CAssumeEdge)pEdge);
        break;

      case DeclarationEdge:
        element = handleDeclaration(element, (CDeclarationEdge)pEdge);
        break;

      case FunctionCallEdge:
        element = handleFunctionCall(element, (CFunctionCallEdge)pEdge);
        break;

      case StatementEdge:
        element = handleStatement(element, (CStatementEdge)pEdge);
        break;
      case MultiEdge:
        Iterator<CFAEdge> edgeIterator = ((MultiEdge) pEdge).iterator();
        while (element != null && edgeIterator.hasNext()) {
          element = getSuccessor(edgeIterator.next(), element);
        }
        break;
      default:
        throw new UnrecognizedCFAEdgeException(pEdge);
      }
    }
    return element;
  }

  private InvariantsState handleAssume(InvariantsState pElement, CAssumeEdge pEdge) throws UnrecognizedCCodeException {
    FormulaEvaluationVisitor<CompoundInterval> resolver = pElement.getFormulaResolver(pEdge);
    CExpression expression = pEdge.getExpression();
    // Create a formula representing the edge expression
    InvariantsFormula<CompoundInterval> expressionFormula = expression.accept(getExpressionToFormulaVisitor(pEdge));

    // Evaluate the state of the assume edge expression
    CompoundInterval expressionState = expressionFormula.accept(resolver, pElement.getEnvironment());
    /*
     * If the expression definitely evaluates to false when truth is assumed or
     * the expression definitely evaluates to true when falsehood is assumed,
     * the state is unreachable.
     */
    if (pEdge.getTruthAssumption() && expressionState.isDefinitelyFalse()
        || !pEdge.getTruthAssumption() && expressionState.isDefinitelyTrue()) {
      return null;
    }
    /*
     * Assume the state of the expression:
     * If truth is assumed, any non-zero value, otherwise zero.
     */
    if (!pEdge.getTruthAssumption()) {
      expressionFormula = CompoundStateFormulaManager.INSTANCE.logicalNot(expressionFormula);
    }
    InvariantsState result = pElement.assume(expressionFormula, pEdge);
    return result;
  }

  private InvariantsState handleDeclaration(InvariantsState pElement, CDeclarationEdge pEdge) throws UnrecognizedCCodeException {
    if (!(pEdge.getDeclaration() instanceof CVariableDeclaration)) {
      return pElement;
    }

    CVariableDeclaration decl = (CVariableDeclaration) pEdge.getDeclaration();

    String varName = decl.getName();
    if (!decl.isGlobal()) {
      varName = scope(varName, pEdge.getSuccessor().getFunctionName());
    }

    final InvariantsFormula<CompoundInterval> value;
    if (decl.getInitializer() != null && decl.getInitializer() instanceof CInitializerExpression) {
      CExpression init = ((CInitializerExpression)decl.getInitializer()).getExpression();
      value = init.accept(getExpressionToFormulaVisitor(pEdge));
    } else {
      value = CompoundStateFormulaManager.INSTANCE.asConstant(CompoundInterval.top());
    }

    return pElement.assign(false, varName, value, pEdge).putType(varName,
        decl.getType());
  }

  private InvariantsState handleFunctionCall(InvariantsState pElement, final CFunctionCallEdge pEdge) throws UnrecognizedCCodeException {

    InvariantsState newElement = pElement;
    List<String> formalParams = pEdge.getSuccessor().getFunctionParameterNames();
    List<CExpression> actualParams = pEdge.getArguments();
    int limit = Math.min(formalParams.size(), actualParams.size());
    formalParams = FluentIterable.from(formalParams).limit(limit).toList();
    actualParams = FluentIterable.from(actualParams).limit(limit).toList();

    for (Pair<String, CExpression> param : Pair.zipList(formalParams, actualParams)) {
      CExpression actualParam = param.getSecond();

      InvariantsFormula<CompoundInterval> value = actualParam.accept(getExpressionToFormulaVisitor(new VariableNameExtractor() {

        @Override
        public String extract(CExpression pCExpression) throws UnrecognizedCCodeException {
          return getVarName(pCExpression, pEdge, pEdge.getPredecessor().getFunctionName());
        }
      }));

      String formalParam = scope(param.getFirst(), pEdge.getSuccessor().getFunctionName());
      newElement = newElement.assign(false, formalParam, value, pEdge);
    }

    return newElement;
  }

  private InvariantsState handleStatement(InvariantsState pElement, CStatementEdge pEdge) throws UnrecognizedCCodeException {

    if (pEdge.getStatement() instanceof CAssignment) {
      CAssignment assignment = (CAssignment)pEdge.getStatement();
      ExpressionToFormulaVisitor etfv = getExpressionToFormulaVisitor(pEdge);
      CExpression leftHandSide = assignment.getLeftHandSide();
      InvariantsFormula<CompoundInterval> value = assignment.getRightHandSide().accept(etfv);
      return handleAssignment(pElement, pEdge.getPredecessor().getFunctionName(), pEdge, leftHandSide, value);
    }

    return pElement;
  }

  private InvariantsState handleAssignment(InvariantsState pElement, String pFunctionName, CFAEdge pEdge, CExpression pLeftHandSide, InvariantsFormula<CompoundInterval> pValue) throws UnrecognizedCCodeException {
    ExpressionToFormulaVisitor etfv = getExpressionToFormulaVisitor(pEdge);
    boolean isUnknownPointerDereference = pLeftHandSide instanceof CPointerExpression;
    if (pLeftHandSide instanceof CArraySubscriptExpression) {
      CArraySubscriptExpression arraySubscriptExpression = (CArraySubscriptExpression) pLeftHandSide;
      String array = getVarName(arraySubscriptExpression.getArrayExpression(), pEdge, pFunctionName);
      InvariantsFormula<CompoundInterval> subscript = arraySubscriptExpression.getSubscriptExpression().accept(etfv);
      return pElement.assignArray(array, subscript, pValue, pEdge);
    } else {
      String varName = getVarName(pLeftHandSide, pEdge, pFunctionName);
      return pElement.assign(isUnknownPointerDereference, varName, pValue, pEdge);
    }
  }

  private InvariantsState handleReturnStatement(InvariantsState pElement, CReturnStatementEdge pEdge) throws UnrecognizedCCodeException {
    String calledFunctionName = pEdge.getPredecessor().getFunctionName();
    CExpression returnedExpression = pEdge.getExpression();
    // If the return edge has no statement, no return value is passed: "return;"
    if (returnedExpression == null) {
      return pElement;
    }
    ExpressionToFormulaVisitor etfv = getExpressionToFormulaVisitor(pEdge);
    InvariantsFormula<CompoundInterval> returnedState = returnedExpression.accept(etfv);
    String returnValueName = scope(RETURN_VARIABLE_BASE_NAME, calledFunctionName);
    return pElement.assign(false, returnValueName, returnedState, pEdge);
  }

  private InvariantsState handleFunctionReturn(InvariantsState pElement, CFunctionReturnEdge pFunctionReturnEdge)
      throws UnrecognizedCCodeException {
      CFunctionSummaryEdge summaryEdge = pFunctionReturnEdge.getSummaryEdge();

      CFunctionCall expression = summaryEdge.getExpression();

      String calledFunctionName = pFunctionReturnEdge.getPredecessor().getFunctionName();

      String returnValueName = scope(RETURN_VARIABLE_BASE_NAME, calledFunctionName);

      InvariantsFormula<CompoundInterval> value = CompoundStateFormulaManager.INSTANCE.asVariable(returnValueName);

      // expression is an assignment operation, e.g. a = g(b);
      if (expression instanceof CFunctionCallAssignmentStatement) {
        CFunctionCallAssignmentStatement funcExp = (CFunctionCallAssignmentStatement)expression;

        return handleAssignment(pElement, pFunctionReturnEdge.getSuccessor().getFunctionName(), pFunctionReturnEdge, funcExp.getLeftHandSide(), value);
      }
      return pElement;
  }

  public static String getVarName(CExpression pLhs, CFAEdge pEdge) throws UnrecognizedCCodeException {
    return getVarName(pLhs, pEdge, pEdge.getSuccessor().getFunctionName());
  }

  public static String getVarName(CExpression pLhs, CFAEdge pEdge, String pFunctionName) throws UnrecognizedCCodeException {
    if (pLhs instanceof CIdExpression) {
      CIdExpression var = (CIdExpression) pLhs;
      String varName = var.getName();
      if (var.getDeclaration() != null) {
        CSimpleDeclaration decl = var.getDeclaration();

        if (!(decl instanceof CDeclaration || decl instanceof CParameterDeclaration)) {
          throw new UnrecognizedCCodeException("unknown variable declaration", pEdge, var);
        }

        if (decl instanceof CDeclaration && ((CDeclaration)decl).isGlobal()) {

        } else {
          varName = scope(varName, pFunctionName);
        }
    }
    return varName;
    } else if (pLhs instanceof CFieldReference) {
      CFieldReference fieldRef = (CFieldReference) pLhs;
      String varName = fieldRef.getFieldName();
      CExpression owner = fieldRef.getFieldOwner();
      if (owner != null) {
        varName = getVarName(owner, pEdge, pFunctionName) + (fieldRef.isPointerDereference() ? "->" : ".") + varName;
      }
      return varName;
    } else if (pLhs instanceof CArraySubscriptExpression) {
      CArraySubscriptExpression arraySubscript = (CArraySubscriptExpression) pLhs;
      CExpression subscript = arraySubscript.getSubscriptExpression();
      CExpression owner = arraySubscript.getArrayExpression();
      if (subscript instanceof CIntegerLiteralExpression) {
        CIntegerLiteralExpression literal = (CIntegerLiteralExpression) subscript;
        return String.format("%s[%d]", getVarName(owner, pEdge, pFunctionName), literal.asLong());
      } else {
        return String.format("%s[*]", getVarName(owner, pEdge, pFunctionName));
      }
    } else if (pLhs instanceof CPointerExpression) {
      CPointerExpression pe = (CPointerExpression) pLhs;
      if (pe.getOperand() instanceof CLeftHandSide) {
        return String.format("*(%s)", getVarName(pe.getOperand(), pEdge));
      }
      return pLhs.toString();
    } else if (pLhs instanceof CCastExpression) {
      CCastExpression cast = (CCastExpression) pLhs;
      return getVarName(cast.getOperand(), pEdge);
    } else {
      return pLhs.toString(); // This actually seems wrong but is currently the only way to deal with some cases of pointer arithmetics
    }
  }

  static String scope(String pVar, String pFunction) {
    return pFunction + "::" + pVar;
  }

  @Override
  public Collection<? extends AbstractState> strengthen(
      AbstractState pElement, List<AbstractState> pOtherElements,
      CFAEdge pCfaEdge, Precision pPrecision) {

    return null;
  }

  public ExpressionToFormulaVisitor getExpressionToFormulaVisitor(final CFAEdge pEdge) {
    return getExpressionToFormulaVisitor(new VariableNameExtractor() {

      @Override
      public String extract(CExpression pCExpression) throws UnrecognizedCCodeException {
        return getVarName(pCExpression, pEdge);
      }
    });
  }

  public ExpressionToFormulaVisitor getExpressionToFormulaVisitor(VariableNameExtractor pVariableNameExtractor) {
    return new ExpressionToFormulaVisitor(pVariableNameExtractor);
  }
}