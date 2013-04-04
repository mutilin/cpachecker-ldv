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
package org.sosy_lab.cpachecker.cpa.cover;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;


public class CoverTransferRelation implements TransferRelation {

  private final TransferRelation wrappedTransfer;
  private Set<String> UsedFunctions;
  private Set<Integer> DeclarationLines;

  CoverTransferRelation(TransferRelation pWrappedTransfer, Set<String> used, Set<Integer> lines) throws InvalidConfigurationException {
    wrappedTransfer = pWrappedTransfer;
    UsedFunctions = used;
    DeclarationLines = lines;
    }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(AbstractState pState, Precision pPrecision,
      CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {

    Collection<AbstractState> results;
    if (pCfaEdge == null) {
      CFANode node = extractLocation(pState);
      results = new ArrayList<AbstractState>(node.getNumLeavingEdges());

      for (int edgeIdx = 0; edgeIdx < node.getNumLeavingEdges(); edgeIdx++) {
        CFAEdge edge = node.getLeavingEdge(edgeIdx);
        getAbstractSuccessorForEdge(pState, pPrecision, edge, results);
      }

    } else {
      results = new ArrayList<AbstractState>(1);
      getAbstractSuccessorForEdge(pState, pPrecision, pCfaEdge, results);

    }
    return results;
  }

  private void getAbstractSuccessorForEdge(AbstractState oldState,
      Precision pPrecision, CFAEdge pCfaEdge, Collection<AbstractState> results)
      throws InterruptedException, CPATransferException {

    Collection<? extends AbstractState> newWrappedStates = wrappedTransfer.getAbstractSuccessors(oldState, pPrecision, pCfaEdge);
    handleEdge(pCfaEdge);
    for (AbstractState newWrappedState : newWrappedStates) {
      if (newWrappedState != null) {
        results.add(newWrappedState);
      }
    }
  }

  private void handleEdge(CFAEdge pCfaEdge) throws UnrecognizedCFAEdgeException {
    switch(pCfaEdge.getEdgeType()) {

    case DeclarationEdge: {
      DeclarationLines.add(pCfaEdge.getLineNumber());
      break;
    }

    // if edge is a statement edge, e.g. a = b + c
    case StatementEdge: {
      CStatementEdge statementEdge = (CStatementEdge) pCfaEdge;
      CStatement pStatement = statementEdge.getStatement();

      if (pStatement instanceof CAssignment) {
        // assignment like "a = b" or "a = foo()"
        CRightHandSide right = ((CAssignment)pStatement).getRightHandSide();

        if (right instanceof CFunctionCallExpression) {
          UsedFunctions.add(((CFunctionCallExpression)right).getFunctionNameExpression().toASTString());
        }

      } else if (pStatement instanceof CFunctionCallStatement) {
        UsedFunctions.add(((CFunctionCallStatement)pStatement).getFunctionCallExpression().getFunctionNameExpression().toASTString());
      }
      break;
    }

    case FunctionCallEdge: {
      UsedFunctions.add(((CFunctionCallEdge)pCfaEdge).getSuccessor().getFunctionName());
      break;
    }

    case AssumeEdge:
    case FunctionReturnEdge:
    case ReturnStatementEdge:
    case BlankEdge:
    case CallToReturnEdge: {
      break;
    }

    default:
      throw new UnrecognizedCFAEdgeException(pCfaEdge);
  }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState pState, List<AbstractState> pOtherStates,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException, InterruptedException {
    return null;
  }

}
