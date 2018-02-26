/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.bam.cache;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;

/**
 * Data structures required for BAM.
 *
 * <p>TODO: clear cache to avoid memory-leaks.
 */
public class BAMDataManagerImpl implements BAMDataManager {

  private final LogManager logger;

  /**
   * Main data structure.
   * Contains every {@link ReachedSet} of every recursive
   * {@link org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm}
   * invocation.
   * */
  private final BAMCache bamCache;

  private final ReachedSetFactory reachedSetFactory;

  /** Mapping of non-reduced initial states to {@link ReachedSet}. */
  private final Table<AbstractState, AbstractState, ReachedSet> initialStateToReachedSet =
      HashBasedTable.create();

  /** Mapping of reduced initial states to non-reduced initial states. */
  private final Multimap<AbstractState, AbstractState> reducedToNonReduced = HashMultimap.create();

  private final Map<AbstractState, BlockExitData> expandedStateToBlockExit = new HashMap<>();


  private static class BlockExitData {

    private final AbstractState reducedState;
    private final Block block;
    private final Precision expandedPrecision;

    BlockExitData(AbstractState pReducedState, Block pBlock, Precision pExpandedPrecision) {
      reducedState = pReducedState;
      block = pBlock;
      expandedPrecision = pExpandedPrecision;
    }

    @Override
    public String toString() {
      return String.format("Data <%s, %s, %s>", reducedState, block, expandedPrecision);
    }
  }

  /**
   * The corresponding blocks will not start the recursive analysis
   */
  private final Set<CFANode> uncachedBlockEntries = new HashSet<>();

  public BAMDataManagerImpl(
      BAMCache pArgCache,
      ReachedSetFactory pReachedSetFactory,
      LogManager pLogger) {
    bamCache = pArgCache;
    reachedSetFactory = pReachedSetFactory;
    logger = pLogger;
  }

  /**
   * Associate the value previously associated with {@code oldState} with
   * {@code newState}.
   *
   * @param oldStateMustExist If set, assumes that {@code oldState} is in the
   *                          cache, otherwise, fails silently if it isn't.
   */
  @Override
  public void replaceStateInCaches(
      AbstractState oldState, AbstractState newState, boolean oldStateMustExist) {
    if (oldState.equals(newState)) {
      return; // nothing to do
    }
    if (oldStateMustExist || expandedStateToBlockExit.containsKey(oldState)) {
      final BlockExitData entry = expandedStateToBlockExit.remove(oldState);
      expandedStateToBlockExit.put(newState, entry);
    }
  }

  /**
   * Create a new reached-set with the given state as root and register it in the cache.
   **/
  @Override
  public ReachedSet createAndRegisterNewReachedSet(
      AbstractState initialState, Precision initialPrecision, Block context) {
    final ReachedSet reached = reachedSetFactory.create();
    reached.add(initialState, initialPrecision);
    bamCache.put(initialState, initialPrecision, context, reached);
    return reached;
  }

  @Override
  public ReachedSetFactory getReachedSetFactory() {
    return reachedSetFactory;
  }

  /**
   * Register an expanded state in our data-manager,
   * such that we know later, which state in which block was expanded to the state.
   * */
  @Override
  public void registerExpandedState(AbstractState expandedState, Precision expandedPrecision,
      AbstractState reducedState, Block innerBlock) {
    BlockExitData previousValue =
        expandedStateToBlockExit.put(
            expandedState, new BlockExitData(reducedState, innerBlock, expandedPrecision));
    assert previousValue == null
        : "expanded state was registered before with data " + previousValue;
  }

  /**
   * @param state Has to be a block-end state.
   * It can be expanded or reduced (or even reduced expanded),
   * because this depends on the nesting of blocks,
   * i.e. if there are several overlapping block-end-nodes
   * (e.g. nested loops or program calls 'exit()' inside a function).
   *
   * @return Whether the current state is at a node,
   * where several block-exits are available and one of them was already left.
   **/
  @Override
  public boolean alreadyReturnedFromSameBlock(AbstractState state, Block block) {
    BlockExitData data = expandedStateToBlockExit.get(state);
    while (data != null) {
      if (block == data.block) {
        return true;
      }
      data = expandedStateToBlockExit.get(data.reducedState);
    }
    return false;
  }

  @Override
  public AbstractState getInnermostState(AbstractState state) {
    BlockExitData data = expandedStateToBlockExit.get(state);
    while (data != null) {
      state = data.reducedState;
      data = expandedStateToBlockExit.get(state);
    }
    return state;
  }

  /**
   * Get a list of states {@code [s1,s2,s3...]}, such that {@code expand(s0)=s1}, {@code
   * expand(s1)=s2},... The state {@code s0} is the most inner non-expanded state (and given as
   * parameter) and is not included in the list. This method returns an empty list, if the state is
   * not an exit state.
   */
  @Override
  public List<AbstractState> getExpandedStatesList(AbstractState state) {
    List<AbstractState> lst = new ArrayList<>();
    BlockExitData data;
    while (true) {
      data = expandedStateToBlockExit.get(state);
      if (data == null) {
        break;
      }
      lst.add(state);
      state = data.reducedState;
    }
    return Lists.reverse(lst);
  }

  /**
   * Register a mapping from the non-reduced initial state and the non-expanded block-exit states to
   * a reached-set that was used between them.
   */
  @Override
  public void registerInitialState(
      AbstractState initialState, AbstractState exitState, ReachedSet reachedSet) {
    ReachedSet oldReachedSet = initialStateToReachedSet.get(initialState, exitState);
    if (oldReachedSet != null && oldReachedSet != reachedSet) {
      // TODO This might be a hint for a memory leak, i.e., the old reachedset
      // is no longer accessible through BAMDataManager, but registered in BAM-cache.
      // This happens, when the reducer changes, e.g., BAMPredicateRefiner.refineRelevantPredicates.
      logger.logf(
          Level.ALL,
          "New root state %s with exit state %s overrides old reachedset %s with new reachedset %s.",
          initialState,
          exitState,
          oldReachedSet.getFirstState(),
          reachedSet.getFirstState());
    }
    initialStateToReachedSet.put(initialState, exitState, reachedSet);
    reducedToNonReduced.put(reachedSet.getFirstState(), initialState);
  }

  @Override
  public ReachedSet getReachedSetForInitialState(
      AbstractState initialState, AbstractState exitState) {
    assert initialStateToReachedSet.contains(initialState, exitState)
        : "no block matching states: " + initialState + " -> " + exitState;
    ReachedSet reached = checkNotNull(initialStateToReachedSet.get(initialState, exitState));
    assert reached.contains(exitState)
        : "reachedset should contain exit state for block: " + exitState;
    return reached;
  }

  @Override
  public boolean hasInitialState(AbstractState state) {
    return initialStateToReachedSet.containsRow(state);
  }

  @Override
  public ImmutableSet<AbstractState> getNonReducedInitialStates(AbstractState pReducedState) {
    return ImmutableSet.copyOf(reducedToNonReduced.get(pReducedState));
  }

  @Override
  public AbstractState getReducedStateForExpandedState(AbstractState state) {
    assert hasExpandedState(state) : "no match for state: " + state;
    return expandedStateToBlockExit.get(state).reducedState;
  }

  @Override
  public Block getInnerBlockForExpandedState(AbstractState state) {
    assert hasExpandedState(state) : "no match for state: " + state;
    return expandedStateToBlockExit.get(state).block;
  }

  @Override
  public boolean hasExpandedState(AbstractState state) {
    return expandedStateToBlockExit.containsKey(state);
  }

  private static int getId(AbstractState state) {
    return ((ARGState) state).getStateId();
  }

  @Override
  public BAMCache getCache() {
    return bamCache;
  }

  /** return a matching precision for the given state, or Null if state is not found. */
  @Override
  public @Nullable Precision getExpandedPrecisionForState(AbstractState pState) {
    final BlockExitData data = expandedStateToBlockExit.get(pState);
    return data == null ? null : data.expandedPrecision;
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder("BAM DATA MANAGER\n");

    str.append("initial state to (first state of) reached set:\n");
    for (Cell<AbstractState, AbstractState, ReachedSet> entry :
        initialStateToReachedSet.cellSet()) {
      str.append(
          String.format(
              "    (%s, %s) -> %s%n",
              getId(checkNotNull(entry.getRowKey())),
              getId(checkNotNull(entry.getColumnKey())),
              getId(checkNotNull(entry.getValue()).getFirstState())));
    }

    str.append("expanded state to reduced state:\n");
    for (Entry<AbstractState, BlockExitData> entry : sorted(expandedStateToBlockExit)) {
      str.append(
          String.format(
              "    %s -> %s%n", getId(entry.getKey()), getId(entry.getValue().reducedState)));
    }

    return str.toString();
  }

  /** sort map-entries by their key. */
  private static <T> List<Entry<AbstractState, T>> sorted(Map<AbstractState, T> map) {
    List<Entry<AbstractState, T>> sorted = new ArrayList<>(map.entrySet());
    Collections.sort(sorted, (x, y) -> Integer.compare(getId(x.getKey()), getId(y.getKey())));
    return sorted;
  }

  @Override
  public void clear() {
    initialStateToReachedSet.clear();
    expandedStateToBlockExit.clear();
    bamCache.clear();
    reducedToNonReduced.clear();
  }

  @Override
  public boolean addUncachedBlockEntry(CFANode pNode) {
    return uncachedBlockEntries.add(pNode);
  }

  @Override
  public boolean isUncachedBlockEntry(CFANode pNode) {
    return uncachedBlockEntries.contains(pNode);
  }
}
