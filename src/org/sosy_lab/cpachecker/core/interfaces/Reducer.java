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
package org.sosy_lab.cpachecker.core.interfaces;

import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;


public interface Reducer {

  AbstractState getVariableReducedState(AbstractState expandedState, Block context, CFANode callNode);

  AbstractState getVariableExpandedState(AbstractState rootState, Block reducedContext, AbstractState reducedState);

  Precision getVariableReducedPrecision(Precision precision, Block context);

  Precision getVariableExpandedPrecision(Precision rootPrecision, Block rootContext, Precision reducedPrecision);

  /** Returns a hashable object for the stateKey and the precisionKey.
   * This object is used to identify elements in the
   * {@link org.sosy_lab.cpachecker.cpa.bam.BAMCache.AbstractStateHash AbstractStateHash}. */
  Object getHashCodeForState(AbstractState stateKey, Precision precisionKey);

  /** Returns a (non-negative) value for the difference between two precisions.
   * This function is called for aggressive caching
   * (see {@link org.sosy_lab.cpachecker.cpa.bam.BAMCache#get(AbstractState, Precision, Block) BAMCache.get}).
   * A greater value indicates a bigger difference in the precision.
   * If the implementation of this function is not important, return zero. */
  int measurePrecisionDifference(Precision pPrecision, Precision pOtherPrecision);

  AbstractState getVariableReducedStateForProofChecking(AbstractState expandedState, Block context, CFANode callNode);

  AbstractState getVariableExpandedStateForProofChecking(AbstractState rootState, Block reducedContext, AbstractState reducedState);

  /**
   * Use the expandedState as basis for a new state,
   * that can be used as rebuildState for the next funcion-return-edge.
   *
   * @param rootState state before the function-call. this is the predecessor of the block-start-state, that will be reduced.
   * @param entryState state after the function-call. this is the block-start-state, that will be reduced.
   * @param expandedState expanded state at function-return, before the function-return-dge.
   * @param exitLocation location of expandedState and also reducedExitState,
   *                     must be the location of rebuildState,
   *                     TODO should be instance of FunctionExitNode?
   *
   *                                             +---------- BLOCK ----------+
   *                                             |                           |
   * rootState ---------------> entryState - - - - - -> reducedEntryState    |
   *     |     functionCallEdge               reduce          |              |
   *     |                                       |            V              |
   *     |function-                              |         function-         |
   *     |summary-                               |         execution         |
   *     |edge                                   |            |              |
   *     |                                    expand          V              |
   *     |                     expandedState <- - - - - reducedExitState     |
   *     |                         | | |         |                           |
   *     V     functionReturnEdge  V V V         +---------------------------+
   * returnState <------------  rebuildState
   */
  AbstractState rebuildStateAfterFunctionCall(AbstractState rootState, AbstractState entryState,
      AbstractState expandedState, FunctionExitNode exitLocation);
  }
