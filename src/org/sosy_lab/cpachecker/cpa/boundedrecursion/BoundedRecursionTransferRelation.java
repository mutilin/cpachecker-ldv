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

import java.util.Collection;
import java.util.Collections;
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
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.exceptions.StopAnalysisException;


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

    Collection<? extends AbstractState> results = Collections.emptySet();

    if (pCfaEdge == null) {
      try {
        results = wrappedTransfer.getAbstractSuccessors(pElement, pPrecision, pCfaEdge);

      } catch (StopAnalysisException e) {
        CFANode node = e.getNode();
        CFAEdge edge = e.getEdge();

        logger.log(Level.FINER, "Stop analisys: " + e.getMessage());

        if (edge == null) {
          for (int edgeIdx = 0; edgeIdx < node.getNumLeavingEdges(); edgeIdx++) {

            edge = node.getLeavingEdge(edgeIdx);

            if (edge instanceof CFunctionCallEdge)
              break;
          }
        }

        assert edge != null;

        CFunctionSummaryEdge sEdge = ((CFunctionCallEdge)edge).getSummaryEdge();
        CFAEdge newEdge = new BlankEdge(edge.getRawStatement(), edge.getLineNumber(), edge.getPredecessor(), sEdge.getSuccessor(), "recursion edge");
        results = wrappedTransfer.getAbstractSuccessors(pElement, pPrecision, newEdge);
      }

    } else {
      throw new HandleCodeException("Not first CPA");
    }
    return results;
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
