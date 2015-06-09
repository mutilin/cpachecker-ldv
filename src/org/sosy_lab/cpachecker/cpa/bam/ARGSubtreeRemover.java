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
package org.sosy_lab.cpachecker.cpa.bam;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.Precisions;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class ARGSubtreeRemover {

  protected final BlockPartitioning partitioning;
  protected final Reducer wrappedReducer;
  protected final BAMCache bamCache;
  protected final ReachedSetFactory reachedSetFactory;
  protected final Map<AbstractState, ReachedSet> abstractStateToReachedSet;
  protected final Timer removeCachedSubtreeTimer;
  protected final LogManager logger;

  public ARGSubtreeRemover(BlockPartitioning partitioning, Reducer reducer,
                           BAMCache bamCache, ReachedSetFactory reachedSetFactory,
                           Map<AbstractState, ReachedSet> abstractStateToReachedSet,
                           Timer removeCachedSubtreeTimer, LogManager logger) {
    this.partitioning = partitioning;
    this.wrappedReducer = reducer;
    this.bamCache = bamCache;
    this.reachedSetFactory =reachedSetFactory;
    this.abstractStateToReachedSet = abstractStateToReachedSet;
    this.removeCachedSubtreeTimer = removeCachedSubtreeTimer;
    this.logger = logger;
  }


  void removeSubtree(ARGReachedSet mainReachedSet, ARGPath pPath,
                     ARGState element, List<Precision> pNewPrecisions,
                     List<Predicate<? super Precision>> pNewPrecisionTypes,
                     Map<ARGState, ARGState> pPathElementToReachedState) {

    handleEndOfThePath(pPath, element, pPathElementToReachedState);

    List<ARGState> path = trimPath(pPath, element);
    assert path.get(path.size() - 1).equals(element);
    assert path.size() >= 2; // extreme case of length 2: [root, target]

    List<ARGState> relevantCallNodes = getRelevantDefinitionNodes(path);
    assert path.containsAll(relevantCallNodes) : "only nodes of path are relevant";
    assert relevantCallNodes.get(0) == path.get(0) : "root should be relevant";
    assert relevantCallNodes.size() >= 1 : "at least the main-function should be open at the target-state";

    Set<Pair<ARGState, ARGState>> neededRemoveCachedSubtreeCalls = new LinkedHashSet<>();

    //iterate from root to element and remove all subtrees for subgraph calls
    for (int i = 0; i < relevantCallNodes.size() - 1; i++) { // ignore root and the last element
      final ARGState pathElement = relevantCallNodes.get(i);
      final ARGState nextElement = relevantCallNodes.get(i+1);
      neededRemoveCachedSubtreeCalls.add(Pair.of(
              pPathElementToReachedState.get(pathElement),
              pPathElementToReachedState.get(nextElement)));
    }

    if (bamCache.doesAggressiveCaching()) {
      ensureExactCacheHitsOnPath(mainReachedSet, pPath, element, pNewPrecisions, pPathElementToReachedState,
              neededRemoveCachedSubtreeCalls);
    }

    final ARGState lastRelevantNode = pPathElementToReachedState.get(Iterables.getLast(relevantCallNodes));
    final ARGState target = pPathElementToReachedState.get(element);
    for (final Pair<ARGState, ARGState> removeCachedSubtreeArguments : neededRemoveCachedSubtreeCalls) {
      final List<Precision> newPrecisions;
      if (removeCachedSubtreeArguments.getSecond() == lastRelevantNode) { // last iteration
        newPrecisions = pNewPrecisions;
      } else {
        ReachedSet nextReachedSet = abstractStateToReachedSet.get(removeCachedSubtreeArguments.getSecond());
        // assert nextReachedSet != null : "call-state does not match reachedset";
        if (nextReachedSet != null && target.getParents().contains(nextReachedSet.getFirstState())) {
          newPrecisions = pNewPrecisions;
        } else {
          newPrecisions = null; // ignore newPrecisions for all iterations except the last one
        }
      }
      removeCachedSubtree(removeCachedSubtreeArguments.getFirst(), removeCachedSubtreeArguments.getSecond(), newPrecisions, pNewPrecisionTypes);
    }

    removeCachedSubtree(pPathElementToReachedState.get(Iterables.getLast(relevantCallNodes)),
            pPathElementToReachedState.get(element), pNewPrecisions, pNewPrecisionTypes);

    // the main-reachedset contains only the root, exit-states and targets.
    // we assume, that the current refinement was caused by a target-state.
    final ARGState lastState = (ARGState)mainReachedSet.asReachedSet().getLastState();
    assert lastState.isTarget();
    mainReachedSet.removeSubtree(lastState);
  }

  /**
   * @return <code>true</code>, if the precision of the first element of the given reachedSet changed by this operation; <code>false</code>, otherwise.
   */
  private static boolean removeSubtree(ReachedSet reachedSet, ARGState argElement,
                                       List<Precision> newPrecisions, List<Predicate<? super Precision>> pPrecisionTypes) {
    ARGReachedSet argReachSet = new ARGReachedSet(reachedSet);
    boolean updateCacheNeeded = argElement.getParents().contains(reachedSet.getFirstState());
    removeSubtree(argReachSet, argElement, newPrecisions, pPrecisionTypes);
    return updateCacheNeeded;
  }

  static void removeSubtree(ARGReachedSet reachedSet, ARGState argElement) {
    if (BAMTransferRelation.isHeadOfMainFunction(extractLocation(argElement))) {
      reachedSet.removeSubtree((ARGState)reachedSet.asReachedSet().getLastState());
    } else {
      reachedSet.removeSubtree(argElement);
    }
  }

  private static void removeSubtree(ARGReachedSet reachedSet, ARGState argElement,
                                    List<Precision> newPrecisions, List<Predicate<? super Precision>> pPrecisionTypes) {
    if (newPrecisions == null || newPrecisions.size() == 0) {
      removeSubtree(reachedSet, argElement);
    } else {
      reachedSet.removeSubtree(argElement, newPrecisions, pPrecisionTypes);
    }
  }

  protected void removeCachedSubtree(ARGState rootState, ARGState removeElement,
                                   List<Precision> pNewPrecisions,
                                   List<Predicate<? super Precision>> pPrecisionTypes) {
    removeCachedSubtreeTimer.start();

    try {
      CFANode rootNode = extractLocation(rootState);
      Block rootSubtree = partitioning.getBlockForCallNode(rootNode);

      logger.log(Level.FINER, "Remove cached subtree for", removeElement,
              "(rootNode: ", rootNode, ") issued with precision", pNewPrecisions);

      AbstractState reducedRootState = wrappedReducer.getVariableReducedState(rootState, rootSubtree, null, rootNode);
      ReachedSet reachedSet = abstractStateToReachedSet.get(rootState);

      if (removeElement.isDestroyed()) {
        logger.log(Level.FINER, "state was destroyed before");
        //apparently, removeElement was removed due to prior deletions
        return;
      }

      assert reachedSet.contains(removeElement) : "removing state from wrong reachedSet: " + removeElement;

      Precision removePrecision = reachedSet.getPrecision(removeElement);
      ArrayList<Precision> newReducedRemovePrecision = null; // TODO newReducedRemovePrecision: NullPointerException 20 lines later!

      if (pNewPrecisions != null) {
        newReducedRemovePrecision = new ArrayList<>(1);

        for (int i = 0; i < pNewPrecisions.size(); i++) {
          removePrecision = Precisions.replaceByType(removePrecision, pNewPrecisions.get(i), pPrecisionTypes.get(i));
        }

        newReducedRemovePrecision.add(wrappedReducer.getVariableReducedPrecision(removePrecision, rootSubtree));
        pPrecisionTypes = new ArrayList<>();
        pPrecisionTypes.add(Predicates.instanceOf(newReducedRemovePrecision.get(0).getClass()));
      }

      assert !removeElement.getParents().isEmpty();

      Precision reducedRootPrecision = reachedSet.getPrecision(reachedSet.getFirstState());
      bamCache.removeReturnEntry(reducedRootState, reducedRootPrecision, rootSubtree);
      bamCache.removeBlockEntry(reducedRootState, reducedRootPrecision, rootSubtree);

      logger.log(Level.FINEST, "Removing subtree, adding a new cached entry, and removing the former cached entries");

      if (removeSubtree(reachedSet, removeElement, newReducedRemovePrecision, pPrecisionTypes)) {
        logger.log(Level.FINER, "updating cache");
        bamCache.updatePrecisionForEntry(reducedRootState, reducedRootPrecision, rootSubtree, newReducedRemovePrecision.get(0));
      }

    } finally {
      removeCachedSubtreeTimer.stop();
    }
  }

  /** returns only those states/nodes, where a new block starts that is 'open' at the end of the path. */
  private List<ARGState> getRelevantDefinitionNodes(List<ARGState> path) {
    Deque<ARGState> openCallElements = new ArrayDeque<>();
    Deque<Block> openSubtrees = new ArrayDeque<>();

    final ARGState lastState = path.get(path.size() - 1);
    for (final ARGState pathState : path) {
      assert openSubtrees.size() == openCallElements.size();
      CFANode node = extractLocation(pathState);

      // we use a loop here, because a return-node can be the exit of several blocks at once.
      while (!openSubtrees.isEmpty() && openSubtrees.getLast().isReturnNode(node) && pathState != lastState) {
        // we are leaving a block,
        // remove/pop the block and its start-state from the stacks
        openCallElements.removeLast();
        Block lastBlock = openSubtrees.removeLast();

        if (BAMTransferRelation.isFunctionBlock(lastBlock)) {
          // we assume that leaving a function-block is only done once.
          break;
        }
      }

      if (partitioning.isCallNode(node)
              && !partitioning.getBlockForCallNode(node).equals(openSubtrees.peek())) {
        // we have a callnode, but current block is wrong, add new currentBlock and state as relevant.
        // the block can be equal, if this is a loop-block.
        openCallElements.addLast(pathState);
        openSubtrees.addLast(partitioning.getBlockForCallNode(node));
      }
    }

    return new ArrayList<>(openCallElements);
  }

  private void ensureExactCacheHitsOnPath(ARGReachedSet mainReachedSet, ARGPath pPath, final ARGState pElement,
                                          List<Precision> pNewPrecisions, Map<ARGState, ARGState> pPathElementToReachedState,
                                          Set<Pair<ARGState, ARGState>> neededRemoveCachedSubtreeCalls) {
    Map<ARGState, UnmodifiableReachedSet> pathElementToOuterReachedSet = new HashMap<>();
    Pair<Set<ARGState>, Set<ARGState>> pair =
            getCallAndReturnNodes(pPath, pathElementToOuterReachedSet, mainReachedSet.asReachedSet(),
                    pPathElementToReachedState);
    Set<ARGState> callNodes = pair.getFirst();
    Set<ARGState> returnNodes = pair.getSecond();

    Deque<ARGState> remainingPathElements = new LinkedList<>(pPath.asStatesList());

    boolean starting = false;
    while (!remainingPathElements.isEmpty()) {
      ARGState currentElement = remainingPathElements.pop();

      if (currentElement.equals(pElement)) {
        starting = true;
      }

      if (starting) {
        if (callNodes.contains(currentElement)) {
          ARGState currentReachedState = pPathElementToReachedState.get(currentElement);
          CFANode node = extractLocation(currentReachedState);
          Block currentBlock = partitioning.getBlockForCallNode(node);
          AbstractState reducedState = wrappedReducer.getVariableReducedState(currentReachedState, currentBlock, null, node);

          removeUnpreciseCacheEntriesOnPath(currentElement, reducedState, pNewPrecisions, currentBlock,
                  remainingPathElements, pPathElementToReachedState, callNodes, returnNodes, pathElementToOuterReachedSet,
                  neededRemoveCachedSubtreeCalls);
        }
      }
    }
  }

  private Pair<Set<ARGState>, Set<ARGState>> getCallAndReturnNodes(ARGPath path,
                                                                   Map<ARGState, UnmodifiableReachedSet> pathElementToOuterReachedSet, UnmodifiableReachedSet mainReachedSet,
                                                                   Map<ARGState, ARGState> pPathElementToReachedState) {
    Set<ARGState> callNodes = new HashSet<>();
    Set<ARGState> returnNodes = new HashSet<>();

    Deque<Block> openSubtrees = new ArrayDeque<>();

    Deque<UnmodifiableReachedSet> openReachedSets = new ArrayDeque<>();
    openReachedSets.push(mainReachedSet);

    for (ARGState pathState : path.asStatesList()) {
      CFANode node = extractLocation(pathState);

      // we use a loop here, because a return-node can be the exit of several blocks at once.
      // we have to handle returnNodes before entryNodes, because some nodes can be both,
      // and the transferRelation also handles entryNodes as first case.
      while (!openSubtrees.isEmpty() && openSubtrees.peek().isReturnNode(node)) {
        openSubtrees.pop();
        openReachedSets.pop();
        returnNodes.add(pathState);
      }

      // this line comes after handling returnStates --> returnStates from path are part of the outer-block-reachedSet
      pathElementToOuterReachedSet.put(pathState, openReachedSets.peek());

      if (partitioning.isCallNode(node)
              && !partitioning.getBlockForCallNode(node).equals(openSubtrees.peek())) {
        // the block can be equal, if this is a loop-block.
          openSubtrees.push(partitioning.getBlockForCallNode(node));
          openReachedSets.push(abstractStateToReachedSet.get(pPathElementToReachedState.get(pathState)));
          callNodes.add(pathState);
      }
    }

    return Pair.of(callNodes, returnNodes);
  }

  private boolean removeUnpreciseCacheEntriesOnPath(ARGState rootState, AbstractState reducedRootState,
                                                    List<Precision> pNewPrecisions, Block rootBlock, Deque<ARGState> remainingPathElements,
                                                    Map<ARGState, ARGState> pPathElementToReachedState, Set<ARGState> callNodes, Set<ARGState> returnNodes,
                                                    Map<ARGState, UnmodifiableReachedSet> pathElementToOuterReachedSet,
                                                    Set<Pair<ARGState, ARGState>> neededRemoveCachedSubtreeCalls) {
    UnmodifiableReachedSet outerReachedSet = pathElementToOuterReachedSet.get(rootState);

    Precision rootPrecision = outerReachedSet.getPrecision(pPathElementToReachedState.get(rootState));

    for (Precision pNewPrecision : pNewPrecisions) {
      rootPrecision = Precisions.replaceByType(rootPrecision, pNewPrecision, Predicates.instanceOf(pNewPrecision.getClass()));
    }
    Precision reducedNewPrecision =
            wrappedReducer.getVariableReducedPrecision(
                    rootPrecision, rootBlock);

    UnmodifiableReachedSet innerReachedSet = abstractStateToReachedSet.get(pPathElementToReachedState.get(rootState));
    Precision usedPrecision = innerReachedSet.getPrecision(innerReachedSet.getFirstState());

    //add precise key for new precision if needed
    if (!bamCache.containsPreciseKey(reducedRootState, reducedNewPrecision, rootBlock)) {
      ReachedSet reachedSet = createInitialReachedSet(reducedRootState, reducedNewPrecision);
      bamCache.put(reducedRootState, reducedNewPrecision, rootBlock, reachedSet);
    }

    boolean isNewPrecisionEntry = usedPrecision.equals(reducedNewPrecision);

    //fine, this block will not lead to any problems anymore, but maybe inner blocks will?
    //-> check other (inner) blocks on path
    boolean foundInnerUnpreciseEntries = false;
    while (!remainingPathElements.isEmpty()) {
      ARGState currentElement = remainingPathElements.pop();

      if (callNodes.contains(currentElement)) {
        ARGState currentReachedState = pPathElementToReachedState.get(currentElement);
        CFANode node = extractLocation(currentReachedState);
        Block currentBlock = partitioning.getBlockForCallNode(node);
        AbstractState reducedState = wrappedReducer.getVariableReducedState(currentReachedState, currentBlock, null, node);

        boolean removedUnpreciseInnerBlock =
                removeUnpreciseCacheEntriesOnPath(currentElement, reducedState, pNewPrecisions, currentBlock,
                        remainingPathElements, pPathElementToReachedState, callNodes, returnNodes,
                        pathElementToOuterReachedSet, neededRemoveCachedSubtreeCalls);
        if (removedUnpreciseInnerBlock) {
          //ok we indeed found an inner block that was unprecise
          if (isNewPrecisionEntry && !foundInnerUnpreciseEntries) {
            //if we are in a reached set that already uses the new precision and this is the first such entry we have to remove the subtree starting from currentElement in the rootReachedSet
            neededRemoveCachedSubtreeCalls.add(Pair.of(pPathElementToReachedState.get(rootState), currentReachedState));
            foundInnerUnpreciseEntries = true;
          }
        }
      }

      if (returnNodes.contains(currentElement)) {
        //our block ended. Leave..
        return foundInnerUnpreciseEntries || !isNewPrecisionEntry;
      }
    }

    return foundInnerUnpreciseEntries || !isNewPrecisionEntry;
  }

  private ReachedSet createInitialReachedSet(AbstractState initialState, Precision initialPredicatePrecision) {
    ReachedSet reached = reachedSetFactory.create();
    reached.add(initialState, initialPredicatePrecision);
    return reached;
  }

  /** remove all states after pState from path */
  private static List<ARGState> trimPath(final ARGPath pPath, final ARGState pState) {
    final List<ARGState> result = new ArrayList<>();
    for (ARGState state : pPath.asStatesList()) {
      result.add(state);
      if (state.equals(pState)) { return result; }
    }
    throw new IllegalArgumentException("State " + pState + " could not be found in path " + pPath + ".");
  }


  protected void handleEndOfThePath(ARGPath pPath, ARGState affectedState,
      Map<ARGState, ARGState> pSubgraphStatesToReachedState) {
    //Dummy method, it is implemented in the MultiARGSubtreeRemover
  }
}
