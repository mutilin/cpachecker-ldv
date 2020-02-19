/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.blockator;

import com.google.common.collect.ImmutableCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation.ReachedSetAware;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.blockator.BlockatorCacheManager.CacheEntry;
import org.sosy_lab.cpachecker.cpa.blockator.BlockatorState.BlockEntry;
import org.sosy_lab.cpachecker.cpa.blockator.BlockatorState.BlockStackEntry;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;

public class BlockatorTransferRelation implements TransferRelation, ReachedSetAware {
  private BlockatorCPA parent;
  private TransferRelation wrappedTransfer;
  private ReachedSet reachedSet;
  private boolean enableCache = true;

  public BlockatorTransferRelation(
      BlockatorCPA pParent,
      TransferRelation pWrappedTransfer) {
    parent = pParent;
    wrappedTransfer = pWrappedTransfer;
  }

  @Override
  public void updateReachedSet(ReachedSet pReachedSet) {
    reachedSet = pReachedSet;
  }

  private BlockEntry getBlockEntry(AbstractState state, Block expectedBlock) {
    BlockatorStateRegistry registry = parent.getStateRegistry();
    BlockStackEntry stackEntry = registry.get(state).getLastBlock();
    if (stackEntry == null)
      throw new IllegalArgumentException("State does not have any blocks");

    if (!stackEntry.block.equals(expectedBlock))
      throw new IllegalArgumentException("State is inside of different block");

    BlockEntry blockEntry = registry.get(stackEntry.entryState).getBlockEntry();
    return Objects.requireNonNull(blockEntry, "State has wrong block entry pointer");
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState state, Precision precision) throws CPATransferException, InterruptedException {
    if (reachedSet == null) {
      throw new IllegalStateException("updateReachedSet() should be called prior to invocation");
    }

    BlockatorState bState = parent.getStateRegistry().get(state);
    BlockPartitioning partitioning = parent.getPartitioning();
    Reducer reducer = parent.getReducer();
    CFANode node = AbstractStates.extractLocation(state);

    if (partitioning.isCallNode(node)) {
      Objects.requireNonNull(node, "CallNode should have Node available");

      Block block = partitioning.getBlockForCallNode(node);
      AbstractState reducedState = reducer.getVariableReducedState(state, block, node);
      Precision reducedPrecision = reducer.getVariableReducedPrecision(precision, block);

      CacheEntry cached = null;
      if (enableCache) {
        cached = parent.getCacheManager().getOrCreate(block, reducedState, reducedPrecision);
      }

      if (cached == null || cached.shouldCompute()) {
        parent.getStatistics().cacheMisses.inc();
        bState = bState.enterBlock(state, precision, block, reducedState, reducedPrecision);
        parent.getStateRegistry().put(reducedState, bState);

        Collection<? extends AbstractState> subStates = getWrappedSuccessors(bState.transition(),
            reducedState, reducedPrecision);

        setParent(subStates, state);
        return subStates;
      } else {
        parent.getStatistics().cacheHits.inc();
        cached.addCacheUsage(state, precision);

        List<AbstractState> result = new ArrayList<>();
        for (Pair<AbstractState, Precision> exit: cached.getExitStates()) {
          AbstractState expandedState = reducer.getVariableExpandedState(state, block,
              exit.getFirstNotNull());

          Precision expandedPrecision = reducer.getVariableExpandedPrecision(precision, block,
              exit.getSecondNotNull());

          BlockEntry blockEntry = getBlockEntry(exit.getFirstNotNull(), block);
          BlockatorState wrappedState = bState.transition(expandedPrecision)
              .cacheUsage(blockEntry.entryState, exit.getFirstNotNull());

          Collection<? extends  AbstractState> wrappedResults = getWrappedSuccessors(wrappedState,
              expandedState, expandedPrecision);

          result.addAll(wrappedResults);
        }

        setParent(result, state);

        return result;
      }
    }

    if (partitioning.isReturnNode(node)) {
      Objects.requireNonNull(node, "ReturnNode should have Node available");

      ImmutableCollection<Block> blocks = partitioning.getBlocksForReturnNode(node);
      if (blocks.size() != 1) {
        throw new RuntimeException("Returning from multiple blocks is not supported");
      }

      Block block = blocks.iterator().next();
      BlockStackEntry blockStackEntry = bState.getLastBlock();
      if (blockStackEntry == null || !blockStackEntry.block.equals(block)) {
        // FIXME This should not happen
        return Collections.emptyList();
//        throw new RuntimeException("Trying to exit from wrong block! innerBlock=" + innerBlock +
//            ", exiting from block " + block);
      }

      bState = bState.exitBlock();
      BlockatorState.BlockEntry blockEntry = parent.getStateRegistry()
          .get(blockStackEntry.entryState).getBlockEntry();

      Objects.requireNonNull(blockEntry, "blockEntry should be defined for entry states");

      if (enableCache) {
        CacheEntry cached = parent.getCacheManager().getOrCreate(block, blockEntry.reducedState,
            blockEntry.reducedPrecision);

        cached.addExitState(state, precision);

        for (Pair<AbstractState, Precision> usage: cached.getCacheUsages()) {
          AbstractState expandedState = reducer.getVariableExpandedState(usage.getFirstNotNull(),
              block, state);

          Precision expandedPrecision =
              reducer.getVariableExpandedPrecision(usage.getSecondNotNull(),
                  block, precision);

          BlockatorState usageState = parent.getStateRegistry().get(usage.getFirstNotNull())
              .transition(expandedPrecision)
              .cacheUsage(blockEntry.entryState, state);

          Collection<? extends AbstractState> wrappedResults = getWrappedSuccessors(usageState,
              expandedState, expandedPrecision);

          setParent(wrappedResults, usage.getFirstNotNull());
          for (AbstractState st: wrappedResults) {
            reachedSet.add(st, expandedPrecision);
          }
        }
      }

      AbstractState expandedState = reducer.getVariableExpandedState(blockEntry.entryState,
          blockEntry.block, state);

      Precision expandedPrecision = reducer.getVariableExpandedPrecision(blockEntry.entryPrecision,
          blockEntry.block, precision);

      Collection<? extends AbstractState> subStates = getWrappedSuccessors(
          bState.transition(expandedPrecision), expandedState, expandedPrecision);

      setParent(subStates, state);
      return subStates;
    }

    return getWrappedSuccessors(bState.transition(), state, precision);
  }

  private Collection<? extends AbstractState> getWrappedSuccessors(
      BlockatorState extState, AbstractState state, Precision precision)
      throws CPATransferException, InterruptedException {
    Collection<? extends AbstractState> ret =
        wrappedTransfer.getAbstractSuccessors(state, reachedSet, precision);

    for (AbstractState st: ret) {
      parent.getStateRegistry().put(st, extState);
    }

    return ret;
  }

  private void setParent(Collection<? extends AbstractState> states, AbstractState parentState) {
    for (AbstractState s: states) {
      if (!(s instanceof ARGState)) {
        throw new IllegalArgumentException("ARGState expected here");
      }

      ARGState as = (ARGState) s;
      while (!as.getParents().isEmpty()) {
        as.removeParent(as.getParents().iterator().next());
      }

      as.addParent((ARGState) parentState);
    }
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state, Precision precision, CFAEdge cfaEdge)
      throws CPATransferException, InterruptedException {
    throw new UnsupportedOperationException("Unsupported, should not be called");
  }
}
