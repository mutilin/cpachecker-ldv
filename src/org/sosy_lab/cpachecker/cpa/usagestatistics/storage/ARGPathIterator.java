/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.bam.BAMCEXSubgraphComputer.BackwardARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMMultipleCEXSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

import com.google.common.collect.Multimap;


public class ARGPathIterator {

  private final Set<List<Integer>> refinedStates;
  private final UsageInfo targetUsage;
  private final Map<ARGState, ARGState> subgraphStatesToReachedState;
  private final BAMTransferRelation transfer;
  private final Multimap<AbstractState, AbstractState> fromReducedToExpand;
  private final BAMMultipleCEXSubgraphComputer subgraphComputer;

  private Map<AbstractState, Iterator<AbstractState>> toCallerStatesIterator = new HashMap<>();

  public ARGPathIterator(UsageInfo pTarget, Set<List<Integer>> pRefinedStates,
      Map<ARGState, ARGState> pSubgraphStatesToReachedState, BAMTransferRelation bamTransfer) {
    refinedStates = pRefinedStates;
    targetUsage = pTarget;
    subgraphStatesToReachedState = pSubgraphStatesToReachedState;
    transfer = bamTransfer;
    fromReducedToExpand = transfer.getMapFromReducedToExpand();
    subgraphComputer = transfer.createBAMMultipleSubgraphComputer(subgraphStatesToReachedState);

  }

  //lastAffectedState is Backward!
  public ARGPath next(ARGState lastAffectedState) throws CPATransferException, InterruptedException  {
    if (lastAffectedState == null) {
      //The first time, we have no path to iterate
      BackwardARGState newTarget = new BackwardARGState((ARGState)targetUsage.getKeyState());
      subgraphStatesToReachedState.put(newTarget, (ARGState)targetUsage.getKeyState());
      return computePath(newTarget);
    }

    ARGState nextParent = null, parent, forkState;
    do {
      //This is backward state, it has one parent, need to be displayed to reached set state
      forkState = findPreviousFork(lastAffectedState);

      if (forkState == null) {
        return null;
      }

      parent = forkState.getParents().iterator().next();
      assert parent != null;
      ARGState realForkState = subgraphStatesToReachedState.get(forkState);
      Collection<AbstractState> callerStates = fromReducedToExpand.get(realForkState);

      assert callerStates != null;

      Iterator<AbstractState> iterator;
      if (toCallerStatesIterator.containsKey(realForkState)) {
        //Means we have already handled this state, just get the next one
        iterator = toCallerStatesIterator.get(realForkState);
      } else {
        //We get this fork the second time (the first one was from path computer)
        assert forkState.getParents().size() == 1;
        ARGState realParent = subgraphStatesToReachedState.get(parent);
        assert callerStates.remove(realParent);
        iterator = callerStates.iterator();
        toCallerStatesIterator.put(realForkState, iterator);
      }

      if (iterator.hasNext()) {
        nextParent = (ARGState) iterator.next();
      } else {
        //We need to find the next fork
      }
    } while (nextParent == null);

    BackwardARGState newNextParent = new BackwardARGState(nextParent);
    parent.removeFromARG();
    forkState.addParent(newNextParent);
    subgraphStatesToReachedState.put(newNextParent, nextParent);
    return computePath(newNextParent);
  }

  private ARGState findPreviousFork(ARGState parent) {
    ARGState currentState = parent;
    while (subgraphStatesToReachedState.get(currentState).getParents().size() == 1) {
      if (currentState.getChildren().size() == 0) {
        //The last state in the path
        return null;
      }
      assert currentState.getChildren().size() == 1;
      currentState = currentState.getChildren().iterator().next();
    }
    assert subgraphStatesToReachedState.get(currentState).getParents().size() > 1;
    return currentState;
  }

  ARGPath computePath(BackwardARGState pLastElement) throws InterruptedException, CPATransferException {
    assert (pLastElement != null && !pLastElement.isDestroyed());
      //we delete this state from other unsafe

    ARGState rootOfSubgraph = subgraphComputer.findPath(pLastElement, refinedStates);
    assert (rootOfSubgraph != null);
    if (rootOfSubgraph == BAMMultipleCEXSubgraphComputer.DUMMY_STATE_FOR_REPEATED_STATE) {
      return null;
    }
    return ARGUtils.getRandomPath(rootOfSubgraph);
  }
}
