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

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.usageStatistics.VariableInfo.EdgeType;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

@Options(prefix="cpa.usagestatistics")
class UsageStatisticsTransferRelation implements TransferRelation {

  private final TransferRelation wrappedTransfer;
  private final UsageStatisticsCPAStatistics statistics;

  UsageStatisticsTransferRelation(TransferRelation pWrappedTransfer,
      Configuration config, UsageStatisticsCPAStatistics s) throws InvalidConfigurationException {
    config.inject(this);
    wrappedTransfer = pWrappedTransfer;
    statistics = s;

    }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState pElement, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException, InterruptedException {

    final UsageStatisticsState oldState = (UsageStatisticsState)pElement;
    Collection<UsageStatisticsState> results;

    if (pCfaEdge == null) {
      CFANode node = extractLocation(oldState);
      results = new ArrayList<UsageStatisticsState>(node.getNumLeavingEdges());

      for (int edgeIdx = 0; edgeIdx < node.getNumLeavingEdges(); edgeIdx++) {
        CFAEdge edge = node.getLeavingEdge(edgeIdx);
          getAbstractSuccessorForEdge(oldState, pPrecision, edge, results);
      }

    } else {
      results = new ArrayList<UsageStatisticsState>(1);
      getAbstractSuccessorForEdge(oldState, pPrecision, pCfaEdge, results);

    }
    return results;
  }

  private void getAbstractSuccessorForEdge(UsageStatisticsState oldState,
      Precision pPrecision, CFAEdge pCfaEdge, Collection<UsageStatisticsState> results)
      throws CPATransferException, InterruptedException {

    Collection<? extends AbstractState> newWrappedStates = wrappedTransfer.getAbstractSuccessors(oldState.getWrappedState(), pPrecision, pCfaEdge);

    for (AbstractState newWrappedState : newWrappedStates) {
      UsageStatisticsState newState = oldState.createDuplicateWithNewWrappedState(newWrappedState);

      newState = handleEdge(newState, pCfaEdge);

      if (newState != null) {
        results.add(newState);
      }
    }
  }

  private UsageStatisticsState handleEdge(UsageStatisticsState newState, CFAEdge pCfaEdge) throws CPATransferException {

    switch(pCfaEdge.getEdgeType()) {

      // declaration of a function pointer.
      case DeclarationEdge: {
        CDeclarationEdge declEdge = (CDeclarationEdge) pCfaEdge;
        handleDeclaration(newState, declEdge);
        break;
      }

      // if edge is a statement edge, e.g. a = b + c
      case StatementEdge: {
        CStatementEdge statementEdge = (CStatementEdge) pCfaEdge;
        handleStatement(newState, statementEdge.getStatement(), pCfaEdge);
        break;
      }

      // maybe two function pointers are compared.
      case AssumeEdge: {
        CAssumeEdge assumeEdge = (CAssumeEdge) pCfaEdge;
        handleAssumption(newState, assumeEdge.getExpression(), pCfaEdge);
        break;
      }

      // nothing to do.
      case FunctionCallEdge:
      case ReturnStatementEdge:
      case FunctionReturnEdge:
      case BlankEdge:
      case CallToReturnEdge: {
        break;
      }

      default:
        throw new UnrecognizedCFAEdgeException(pCfaEdge);
    }

    return newState;
  }

  private void handleDeclaration(UsageStatisticsState pNewState, CDeclarationEdge declEdge) throws UnrecognizedCCodeException {

    if (!(declEdge.getDeclaration() instanceof CVariableDeclaration)) {
      // not a variable declaration
      return;
    }
    CVariableDeclaration decl = (CVariableDeclaration)declEdge.getDeclaration();

    String name = decl.getName();

    if (decl.getInitializer() == null) {
      //no assumption
      return;
    }

    if (name == null) {
      // not a variable declaration
      return;
    }
    statistics.add(pNewState, declEdge.getDeclaration());
  }

  private void handleStatement(UsageStatisticsState pNewState, CStatement pStatement,
        CFAEdge pCfaEdge) throws UnrecognizedCCodeException {

    if (pStatement instanceof CAssignment) {
      // assignment like "a = b" or "a = foo()"

      CAssignment assignment = (CAssignment)pStatement;
      statistics.add(pNewState, assignment.getLeftHandSide(), false, true, EdgeType.ASSIGNMENT);
      statistics.add(pNewState, assignment.getRightHandSide(), false, false, EdgeType.ASSIGNMENT);

    } else if (pStatement instanceof CFunctionCallStatement) {
      // external function call without return value

    } else if (pStatement instanceof CExpressionStatement) {
      // side-effect free statement

    } else {
      throw new UnrecognizedCCodeException(pCfaEdge, pStatement);
    }
  }

  private void handleAssumption(UsageStatisticsState element,
                                  CExpression pExpression, CFAEdge cfaEdge) {

    if (pExpression instanceof CBinaryExpression) {
      CExpression op1 = ((CBinaryExpression)pExpression).getOperand1();
      CExpression op2 = ((CBinaryExpression)pExpression).getOperand2();

      statistics.add(element, op1, false, false, EdgeType.ASSUMPTION);
      statistics.add(element, op2, false, false, EdgeType.ASSUMPTION);
    }
    else {
      statistics.add(element, pExpression, false, false, EdgeType.ASSUMPTION);
    }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(
      AbstractState pElement, List<AbstractState> pOtherElements,
      CFAEdge pCfaEdge, Precision pPrecision) {
    // in this method we could access the abstract domains of other CPAs
    // if required.
    return null;
  }
}
