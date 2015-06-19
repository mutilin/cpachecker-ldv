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

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsPrecision;
import org.sosy_lab.cpachecker.util.AbstractStates;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;


public class MultipleARGSubtreeRemover extends ARGSubtreeRemover {

  private Set<ARGState> setsForRemoveFromCache = new HashSet<>();
  private Map<ARGState, Set<ARGState>> cachedSubtreesToRemove = new HashMap<>();
  private Multimap<String, AbstractState> functionToRootState;
  private BAMTransferRelation transfer;

  public MultipleARGSubtreeRemover(BlockPartitioning partitioning, Reducer reducer,
      BAMCache bamCache, ReachedSetFactory reachedSetFactory,
      Map<AbstractState, ReachedSet> abstractStateToReachedSet,
      Timer removeCachedSubtreeTimer, LogManager logger, Multimap<String, AbstractState> map,
      BAMTransferRelation pTransfer) {
    super(partitioning, reducer, bamCache, reachedSetFactory, abstractStateToReachedSet, removeCachedSubtreeTimer, logger);
    functionToRootState = map;
    transfer = pTransfer;
  }

  @Override
  protected void removeCachedSubtree(ARGState rootState, ARGState removeElement,
      List<Precision> pNewPrecisions,
      List<Predicate<? super Precision>> pPrecisionTypes) {
    removeCachedSubtreeTimer.start();

    Set<ARGState> set;
    Set<ARGState> toDelete = new HashSet<>();
    if (cachedSubtreesToRemove.containsKey(rootState)) {
      set = cachedSubtreesToRemove.get(rootState);
      for (ARGState state : set) {
        if (state.getSubgraph().contains(removeElement)) {
          return;
        } else if (removeElement.getSubgraph().contains(state)){
          toDelete.add(state);
        }
      }
      for (ARGState state : toDelete) {
        set.remove(state);
      }
    } else {
      set = new HashSet<>();
      cachedSubtreesToRemove.put(rootState, set);
    }
    set.add(removeElement);
    removeCachedSubtreeTimer.stop();
  }

  @Override
  protected void handleEndOfThePath(ARGPath pPath, ARGState affectedState,
      Map<ARGState, ARGState> pSubgraphStatesToReachedState) {
    List<ARGState> tail = trimPath(pPath, affectedState);

    List<ARGState> callNodes = getCallNodes(tail);
    setsForRemoveFromCache.addAll(callNodes);
  }

  /** remove all states before pState from path */
  private static List<ARGState> trimPath(final ARGPath pPath, final ARGState pState) {
    boolean meet = false;
    final List<ARGState> result = new ArrayList<>();
    for (ARGState state : pPath.asStatesList()) {
      if (state.equals(pState)) { meet = true; }
      if (meet) {
        result.add(state);
      }
    }
    if (meet) {
      return result;
    } else {
      throw new IllegalArgumentException("State " + pState + " could not be found in path " + pPath + ".");
    }
  }

  public void addStateForRemoving(ARGState state) {
    //This not the state to remove. Now we should find all such states using the map
    LinkedList<String> toProcess = new LinkedList<>();

    String functionName = AbstractStates.extractLocation(state).getFunctionName();
    Collection<AbstractState> callers;
    toProcess.add(functionName);
    while (!toProcess.isEmpty()) {
      functionName = toProcess.pollFirst();
      callers = functionToRootState.get(functionName);
      if (callers != null) {
        for (AbstractState caller : callers) {
          setsForRemoveFromCache.add((ARGState)caller);
          //logger.log(Level.INFO, "Add " + caller + " to removing");
          //tmpState is an entrance into current function
          CallstackState previousState = AbstractStates.extractStateByType(caller, CallstackState.class).getPreviousState();
          if (previousState == null) {
            //main function
            continue;
          }
          toProcess.add(previousState.getCurrentFunction());
        }
        functionToRootState.removeAll(functionName);
      }
    }
  }

  private List<ARGState> getCallNodes(List<ARGState> path) {
    Deque<ARGState> openCallElements = new ArrayDeque<>();

    for (final ARGState pathState : path) {
      CFANode node = extractLocation(pathState);

      if (partitioning.isCallNode(node)) {
        // we have a callnode, but current block is wrong, add new currentBlock and state as relevant.
        // the block can be equal, if this is a loop-block.
        openCallElements.addLast(pathState);
      }
    }

    return new ArrayList<>(openCallElements);
  }

  public void cleanCaches(Precision precision) {
    removeCachedSubtreeTimer.start();
    bamCache.printSizes();
    transfer.printCacheStatistics();

    for (ARGState rootState : setsForRemoveFromCache) {

      CFANode rootNode = extractLocation(rootState);
      Block rootSubtree = partitioning.getBlockForCallNode(rootNode);

      ReachedSet reachedSet = abstractStateToReachedSet.get(rootState);

      AbstractState reducedRootState = reachedSet.getFirstState();
      Collection<ARGState> children = ((ARGState)reducedRootState).getChildren();
      if (children.size() == 0) {
        //This cache was cleaned from another rootState
        continue;
      }
      assert ((ARGState)reducedRootState).getChildren().size() == 1;

      Precision reducedRootPrecision = reachedSet.getPrecision(reducedRootState);
      bamCache.removeFromAllCaches(reducedRootState, reducedRootPrecision, rootSubtree);
      transfer.removeStateFromCaches(rootState);
      List<Precision> precisions = Lists.newLinkedList();
      precisions.add(precision);
      List<Predicate<? super Precision>> pPrecisions = Lists.newLinkedList();
      pPrecisions.add(Predicates.instanceOf(UsageStatisticsPrecision.class));

      ARGState child = children.iterator().next();
      removeSubtree(reachedSet, child, precisions, pPrecisions);
    }
    //We can't remove it in the previous loop, because we may use information after that
    transfer.clearCaches();
    System.out.println("------------------------");
    bamCache.printSizes();
    transfer.printCacheStatistics();
    setsForRemoveFromCache.clear();
    //functionToRootState.clear();
    removeCachedSubtreeTimer.stop();
  }
}
