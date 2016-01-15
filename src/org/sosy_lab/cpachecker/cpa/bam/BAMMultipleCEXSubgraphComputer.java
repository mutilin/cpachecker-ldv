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
package org.sosy_lab.cpachecker.cpa.bam;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.util.AbstractStates;


public class BAMMultipleCEXSubgraphComputer extends BAMCEXSubgraphComputer{

  private final Map<AbstractState, AbstractState> reducedToExpanded;
  private Set<LinkedList<Integer>> remainingStates = new HashSet<>();

  BAMMultipleCEXSubgraphComputer(BAMCPA bamCPA, Map<ARGState, ARGState> pPathStateToReachedState,
      Map<AbstractState, AbstractState> pReduced) {
    super(bamCPA, pPathStateToReachedState);
    this.reducedToExpanded = pReduced;
  }


  public ARGState findPath(BackwardARGState newTreeTarget, Set<List<Integer>> pProcessedStates) {

    Map<ARGState, BackwardARGState> elementsMap = new HashMap<>();
    Stack<ARGState> openElements = new Stack<>();
    ARGState root = null;

    //Deep clone to be patient about modification
    remainingStates.clear();
    for (List<Integer> newList : pProcessedStates) {
      remainingStates.add(new LinkedList<>(newList));
    }

    ARGState target = pathStateToReachedState.get(newTreeTarget);
    elementsMap.put(target, newTreeTarget);
    ARGState currentState = target;

    //Find path to nearest abstraction state
    PredicateAbstractState pState = AbstractStates.extractStateByType(currentState, PredicateAbstractState.class);
    if (pState != null) {
      assert (pState.isAbstractionState());
    }

    openElements.push(target);
    while (!openElements.empty()) {
      ARGState currentElement = openElements.pop();
      BackwardARGState newCurrentElement = elementsMap.get(currentElement);

      if (currentElement.getParents().isEmpty()) {
        //Find correct expanded state
        ARGState expandedState = (ARGState) reducedToExpanded.get(currentElement);

        if (expandedState == null) {
          //The first state
          root = newCurrentElement;
          break;
        }
        //Try to find path.
        BackwardARGState newExpandedState = new BackwardARGState(expandedState);
        pathStateToReachedState.put(newExpandedState, expandedState);
        elementsMap.put(expandedState, newExpandedState);
        for (ARGState child : newCurrentElement.getChildren()) {
          child.addParent(newExpandedState);
        }
        newCurrentElement.removeFromARG();
        openElements.push(expandedState);
      } else {
        for (ARGState parent : currentElement.getParents()) {
          //create node for parent in the new subtree
          BackwardARGState newParent = new BackwardARGState(parent);
          elementsMap.put(parent, newParent);
          pathStateToReachedState.put(newParent, parent);
          //and remember to explore the parent later
          openElements.push(parent);
          if (data.expandedStateToReducedState.containsKey(currentElement)) {
            //this is a summarized call and thus an direct edge could not be found
            //we have the transfer function to handle this case, as our reachSet is wrong
            //(we have to use the cached ones)
            ARGState targetARGState = (ARGState) data.expandedStateToReducedState.get(currentElement);
            ARGState innerTree =
                computeCounterexampleSubgraphForBlock(parent, parent.getParents().iterator().next(), targetARGState, newCurrentElement);
            if (innerTree == null) {
              return null;
            }
            ARGState tmpState = newCurrentElement, nextState;
            while (tmpState != innerTree) {
              Collection<ARGState> parents = tmpState.getParents();
              assert parents.size() == 1;
              nextState = parents.iterator().next();
              if (checkRepeatitionOfState(pathStateToReachedState.get(tmpState))) {
                return DUMMY_STATE_FOR_REPEATED_STATE;
              }
              tmpState = nextState;
            }
            for (ARGState child : innerTree.getChildren()) {
              child.addParent(newParent);
            }
            innerTree.removeFromARG();
          } else {
            //normal edge
            //create an edge from parent to current
            newCurrentElement.addParent(newParent);
            if (checkRepeatitionOfState(currentElement)) {
              return DUMMY_STATE_FOR_REPEATED_STATE;
            }
          }
        }
      }
      if (currentElement.isDestroyed()) {
        return null;
      }
    }
    assert root != null;
    return root;
  }

  private boolean checkRepeatitionOfState(ARGState currentElement) {
    int currentId = currentElement.getStateId();
    for (LinkedList<Integer> rest : remainingStates) {
      if (rest.getLast() == currentId) {
        rest.removeLast();
        if (rest.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  /** This states is used for UsageStatisticsRefinement:
   *  If after some refinement iterations the path goes through already processed states,
   *  this marked state is returned.
   */
  public final static BackwardARGState DUMMY_STATE_FOR_REPEATED_STATE = new BackwardARGState(new ARGState(null, null));
  /**
   * This is a ARGState, that counts backwards, used to build the Pseudo-ARG for CEX-retrieval.
   * As the Pseudo-ARG is build backwards starting at its end-state, we count the ID backwards.
   */
}
