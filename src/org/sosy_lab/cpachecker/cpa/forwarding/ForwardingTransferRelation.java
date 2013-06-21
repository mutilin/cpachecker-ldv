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
package org.sosy_lab.cpachecker.cpa.forwarding;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.IADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.IAExpression;
import org.sosy_lab.cpachecker.cfa.ast.IAStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.java.JDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JMethodOrConstructorInvocation;
import org.sosy_lab.cpachecker.cfa.ast.java.JParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JStatement;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JMethodCallEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JMethodReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JMethodSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

/** This Transfer Relation forwards the method 'getAbstractSuccessors()'
 * to all edge-specific sub-methods ('AssumeEdge', 'DeclarationEdge', ...).
 * It handles all casting of the edges and their information.
 * There is always an abstract method, that calls either the matching
 * C- or Java-Methods, depending on the type of the edge.
 * A developer should override the methods to get a valid analysis. */
public abstract class ForwardingTransferRelation<S extends AbstractState, P extends Precision>
    implements TransferRelation {

  private static final String NOT_IMPLEMENTED = "this method is not implemented";

  /** the given edge, not casted, for local access */
  protected CFAEdge edge;

  /** the given state, casted to correct type, for local access */
  protected S state;

  /** the given precision, casted to correct type, for local access */
  protected P precision;

  /** the function BEFORE the current edge */
  protected String functionName;

  protected CFAEdge getEdge() {
    return edge;
  }

  protected S getState() {
    return state;
  }

  protected P getPrecision() {
    return precision;
  }

  protected String getFunctionName() {
    return functionName;
  }


  @Override
  public Collection<S> getAbstractSuccessors(
      AbstractState abstractState, Precision abstractPrecision, CFAEdge cfaEdge)
      throws CPATransferException {

    setInfo(abstractState, abstractPrecision, cfaEdge);

    final Collection<S> preCheck = preCheck();
    if (preCheck != null) { return preCheck; }

    final S successor;

    switch (cfaEdge.getEdgeType()) {

    case AssumeEdge:
      final AssumeEdge assumption = (AssumeEdge) cfaEdge;
      successor = handleAssumption(assumption, assumption.getExpression(), assumption.getTruthAssumption());
      break;

    case FunctionCallEdge:
      final FunctionCallEdge fnkCall = (FunctionCallEdge) cfaEdge;
      final FunctionEntryNode succ = fnkCall.getSuccessor();
      final String calledFunctionName = succ.getFunctionName();
      successor = handleFunctionCallEdge(fnkCall, fnkCall.getArguments(),
          succ.getFunctionParameters(), calledFunctionName);
      break;

    case FunctionReturnEdge:
      final String callerFunctionName = cfaEdge.getSuccessor().getFunctionName();
      final FunctionReturnEdge fnkReturnEdge = (FunctionReturnEdge) cfaEdge;
      final FunctionSummaryEdge summaryEdge = fnkReturnEdge.getSummaryEdge();
      successor = handleFunctionReturnEdge(fnkReturnEdge,
          summaryEdge, summaryEdge.getExpression(), callerFunctionName);

      break;

    case MultiEdge:
      successor = handleMultiEdge((MultiEdge) cfaEdge);
      break;

    default:
      successor = handleSimpleEdge(cfaEdge);
    }

    postProcessing(successor);

    resetInfo();

    if (successor == null) {
      return Collections.emptySet();
    } else {
      return Collections.singleton(successor);
    }
  }


  @SuppressWarnings("unchecked")
  protected void setInfo(AbstractState abstractState, Precision abstractPrecision, CFAEdge cfaEdge) {
    edge = cfaEdge;
    state = (S) abstractState;
    precision = (P) abstractPrecision;
    functionName = cfaEdge.getPredecessor().getFunctionName();
  }

  protected void resetInfo() {
    edge = null;
    state = null;
    precision = null;
    functionName = null;
  }

  /** This function handles simple edges like Declarations, Statements,
   * ReturnStatements and BlankEdges.
   * They have in common, that they all can be part of an MultiEdge. */
  protected S handleSimpleEdge(final CFAEdge cfaEdge) throws CPATransferException {

    switch (cfaEdge.getEdgeType()) {
    case DeclarationEdge:
      final ADeclarationEdge declarationEdge = (ADeclarationEdge) cfaEdge;
      return handleDeclarationEdge(declarationEdge, declarationEdge.getDeclaration());

    case StatementEdge:
      final AStatementEdge statementEdge = (AStatementEdge) cfaEdge;
      return handleStatementEdge(statementEdge, statementEdge.getStatement());

    case ReturnStatementEdge:
      // this statement is a function return, e.g. return (a);
      // note that this is different from return edge,
      // this is a statement edge, which leads the function to the
      // last node of its CFA, where return edge is from that last node
      // to the return site of the caller function
      AReturnStatementEdge returnEdge = (AReturnStatementEdge) cfaEdge;
      return handleReturnStatementEdge(returnEdge, returnEdge.getExpression());

    case BlankEdge:
      return handleBlankEdge((BlankEdge) cfaEdge);

    case CallToReturnEdge:
      return handleFunctionSummaryEdge((FunctionSummaryEdge) cfaEdge);

    default:
      throw new UnrecognizedCFAEdgeException(cfaEdge);
    }
  }

  /** This method just forwards the handling to every inner edge. */
  protected S handleMultiEdge(MultiEdge cfaEdge) throws CPATransferException {
    for (CFAEdge innerEdge : cfaEdge) {
      state = handleSimpleEdge(innerEdge);
    }
    return state;
  }


  /** This is a fast check, if the edge should be analyzed.
   * It returns NULL for further processing,
   * otherwise the return-value for skipping. */
  protected Collection<S> preCheck() {
    return null;
  }

  /** This method can modify the successor. */
  protected void postProcessing(@Nullable S successor) {
    // do nothing
  }


  /** This function handles assumptions like "if(a==b)" and "if(a!=0)".
   * If the assumption is not fulfilled, NULL should be returned. */
  protected S handleAssumption(AssumeEdge cfaEdge, IAExpression expression, boolean truthAssumption)
      throws CPATransferException {
    if (cfaEdge instanceof CAssumeEdge) {
      return handleAssumption((CAssumeEdge) cfaEdge, (CExpression) expression, truthAssumption);

    } else if (cfaEdge instanceof JAssumeEdge) {
      return handleAssumption((JAssumeEdge) cfaEdge, (JExpression) expression, truthAssumption);

    } else {
      throw new AssertionError("unknown edge");
    }
  }

  protected S handleAssumption(CAssumeEdge cfaEdge, CExpression expression, boolean truthAssumption)
      throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }

  protected S handleAssumption(JAssumeEdge cfaEdge, JExpression expression, boolean truthAssumption)
      throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }


  /** This function handles functioncalls like "f(x)", that calls "f(int a)". */
  @SuppressWarnings("unchecked")
  protected S handleFunctionCallEdge(FunctionCallEdge cfaEdge,
      List<? extends IAExpression> arguments, List<? extends AParameterDeclaration> parameters,
      String calledFunctionName) throws CPATransferException {
    if (cfaEdge instanceof CFunctionCallEdge) {
      return handleFunctionCallEdge((CFunctionCallEdge) cfaEdge,
          (List<CExpression>) arguments, (List<CParameterDeclaration>) parameters,
          calledFunctionName);

    } else if (cfaEdge instanceof JMethodCallEdge) {
      return handleFunctionCallEdge((JMethodCallEdge) cfaEdge,
          (List<JExpression>) arguments, (List<JParameterDeclaration>) parameters,
          calledFunctionName);

    } else {
      throw new AssertionError("unknown edge");
    }
  }

  protected S handleFunctionCallEdge(CFunctionCallEdge cfaEdge,
      List<CExpression> arguments, List<CParameterDeclaration> parameters,
      String calledFunctionName) throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }

  protected S handleFunctionCallEdge(JMethodCallEdge cfaEdge,
      List<JExpression> arguments, List<JParameterDeclaration> parameters,
      String calledFunctionName) throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }


  /** This function handles functionReturns like "y=f(x)". */
  protected S handleFunctionReturnEdge(FunctionReturnEdge cfaEdge,
      FunctionSummaryEdge fnkCall, AFunctionCall summaryExpr, String callerFunctionName)
      throws CPATransferException {
    if (cfaEdge instanceof CFunctionReturnEdge) {
      return handleFunctionReturnEdge((CFunctionReturnEdge) cfaEdge,
          (CFunctionSummaryEdge) fnkCall, (CFunctionCall) summaryExpr, callerFunctionName);

    } else if (cfaEdge instanceof JMethodReturnEdge) {
      return handleFunctionReturnEdge((JMethodReturnEdge) cfaEdge,
          (JMethodSummaryEdge) fnkCall, (JMethodOrConstructorInvocation) summaryExpr, callerFunctionName);

    } else {
      throw new AssertionError("unknown edge");
    }
  }

  protected S handleFunctionReturnEdge(CFunctionReturnEdge cfaEdge,
      CFunctionSummaryEdge fnkCall, CFunctionCall summaryExpr, String callerFunctionName)
      throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }

  protected S handleFunctionReturnEdge(JMethodReturnEdge cfaEdge,
      JMethodSummaryEdge fnkCall, JMethodOrConstructorInvocation summaryExpr, String callerFunctionName)
      throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }


  /** This function handles declarations like "int a = 0;" and "int b = !a;". */
  protected S handleDeclarationEdge(ADeclarationEdge cfaEdge, IADeclaration decl)
      throws CPATransferException {
    if (cfaEdge instanceof CDeclarationEdge) {
      return handleDeclarationEdge((CDeclarationEdge) cfaEdge, (CDeclaration) decl);

    } else if (cfaEdge instanceof JDeclarationEdge) {
      return handleDeclarationEdge((JDeclarationEdge) cfaEdge, (JDeclaration) decl);

    } else {
      throw new AssertionError("unknown edge");
    }
  }

  protected S handleDeclarationEdge(CDeclarationEdge cfaEdge, CDeclaration decl)
      throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }

  protected S handleDeclarationEdge(JDeclarationEdge cfaEdge, JDeclaration decl)
      throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }

  /** This function handles statements like "a = 0;" and "b = !a;"
   * and calls of external functions. */
  protected S handleStatementEdge(AStatementEdge cfaEdge, IAStatement statement)
      throws CPATransferException {
    if (cfaEdge instanceof CStatementEdge) {
      return handleStatementEdge((CStatementEdge) cfaEdge, (CStatement) statement);

    } else if (cfaEdge instanceof JStatementEdge) {
      return handleStatementEdge((JStatementEdge) cfaEdge, (JStatement) statement);

    } else {
      throw new AssertionError("unknown edge");
    }
  }

  protected S handleStatementEdge(CStatementEdge cfaEdge, CStatement statement)
      throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }

  protected S handleStatementEdge(JStatementEdge cfaEdge, JStatement statement)
      throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }


  /** This function handles functionStatements like "return (x)". */
  protected S handleReturnStatementEdge(AReturnStatementEdge cfaEdge, @Nullable IAExpression expression)
      throws CPATransferException {
    if (cfaEdge instanceof CReturnStatementEdge) {
      return handleReturnStatementEdge((CReturnStatementEdge) cfaEdge, (CExpression) expression);

    } else if (cfaEdge instanceof JReturnStatementEdge) {
      return handleReturnStatementEdge((JReturnStatementEdge) cfaEdge, (JExpression) expression);

    } else {
      throw new AssertionError("unknown edge");
    }
  }

  protected S handleReturnStatementEdge(CReturnStatementEdge cfaEdge, @Nullable CExpression expression)
      throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }

  protected S handleReturnStatementEdge(JReturnStatementEdge cfaEdge, @Nullable JExpression expression)
      throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }


  /** This function handles blank edges, that are used for plain connectors
   *  in the CFA. This default implementation returns the input-state. */
  protected S handleBlankEdge(BlankEdge cfaEdge) throws CPATransferException {
    return state;
  }

  protected S handleFunctionSummaryEdge(FunctionSummaryEdge cfaEdge) throws CPATransferException {
    throw new AssertionError(NOT_IMPLEMENTED);
  }

  protected static boolean isGlobal(final CExpression exp) {
    if (exp instanceof CIdExpression) {
      CSimpleDeclaration decl = ((CIdExpression) exp).getDeclaration();
      if (decl instanceof CDeclaration) { return ((CDeclaration) decl).isGlobal(); }
    }
    return false;
  }

  /**  */
  protected static String buildVarName(@Nullable final String function, final String var) {
    return (function == null) ? var : function + "::" + var;
  }
}
