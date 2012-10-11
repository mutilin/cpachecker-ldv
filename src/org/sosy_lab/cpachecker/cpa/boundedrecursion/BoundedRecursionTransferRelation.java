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
package org.sosy_lab.cpachecker.cpa.boundedrecursion;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.StopRecursionException;

class BoundedRecursionTransferRelation implements TransferRelation {

  private final TransferRelation wrappedTransfer;
  private final LogManager logger;

  BoundedRecursionTransferRelation(TransferRelation pWrappedTransfer,
      Configuration config, LogManager pLogManager) throws InvalidConfigurationException {
    wrappedTransfer = pWrappedTransfer;
    logger = pLogManager;
    }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState pElement, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException, InterruptedException {

    final BoundedRecursionState oldState = (BoundedRecursionState)pElement;
    Collection<BoundedRecursionState> results;

    if (pCfaEdge == null) {
      CFANode node = extractLocation(oldState);
      results = new ArrayList<BoundedRecursionState>(node.getNumLeavingEdges());

      for (int edgeIdx = 0; edgeIdx < node.getNumLeavingEdges(); edgeIdx++) {
        CFAEdge edge = node.getLeavingEdge(edgeIdx);
        try {
          getAbstractSuccessorForEdge(oldState, pPrecision, edge, results);
        }
        catch (StopRecursionException e) {
          assert (edge instanceof CFunctionCallEdge);

          logger.log(Level.INFO, "Recursion found: " + edge.getCode() + ", (" + edge.getLineNumber() + ")");

          CFunctionSummaryEdge sEdge = ((CFunctionCallEdge)edge).getSummaryEdge();
          CFAEdge newEdge;
          newEdge = new BlankEdge(edge.getRawStatement(),
              edge.getLineNumber(), edge.getPredecessor(), sEdge.getSuccessor(),
              "recursion edge");
          getAbstractSuccessorForEdge(oldState, pPrecision, newEdge, results);
        }
      }

    } else {
      results = new ArrayList<BoundedRecursionState>(1);
      try{
        getAbstractSuccessorForEdge(oldState, pPrecision, pCfaEdge, results);
      }
      catch (StopRecursionException e) {
        assert (pCfaEdge instanceof CFunctionCallEdge);

        logger.log(Level.INFO, "Recursion found: " + pCfaEdge.getCode() + ", (" + pCfaEdge.getLineNumber() + ")");

        CFunctionSummaryEdge sEdge = ((CFunctionCallEdge)pCfaEdge).getSummaryEdge();
        CFAEdge newEdge;
        newEdge = new BlankEdge(pCfaEdge.getRawStatement(),
            pCfaEdge.getLineNumber(), pCfaEdge.getPredecessor(), sEdge.getSuccessor(),
            "recursion edge");
        getAbstractSuccessorForEdge(oldState, pPrecision, newEdge, results);
      }
    }
    return results;
  }

  private void getAbstractSuccessorForEdge(BoundedRecursionState oldState,
      Precision pPrecision, CFAEdge pCfaEdge, Collection<BoundedRecursionState> results)
      throws CPATransferException, InterruptedException {

    Collection<? extends AbstractState> newWrappedStates = wrappedTransfer.getAbstractSuccessors(oldState.getWrappedState(), pPrecision, pCfaEdge);

    for (AbstractState newWrappedState : newWrappedStates) {
      BoundedRecursionState newState = oldState.createDuplicateWithNewWrappedState(newWrappedState);

      if (newState != null) {
        results.add(newState);
      }
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
