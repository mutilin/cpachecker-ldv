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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.blockator.BlockatorBasedRefiner.BackwardARGState;
import org.sosy_lab.cpachecker.cpa.blockator.BlockatorCacheManager.CacheEntry;
import org.sosy_lab.cpachecker.cpa.blockator.BlockatorState.BlockEntry;
import org.sosy_lab.cpachecker.cpa.blockator.BlockatorState.BlockStackEntry;
import org.sosy_lab.cpachecker.cpa.blockator.BlockatorState.StateKind;
import org.sosy_lab.cpachecker.util.Pair;

public class BlockatorReachedSet extends ARGReachedSet.ForwardingARGReachedSet {
  private ARGPath path;
  private BlockatorCPA cpa;

  public BlockatorReachedSet(ARGReachedSet pReached, BlockatorCPA pCpa, ARGPath pPath) {
    super(pReached);

    this.cpa = pCpa;
    this.path = pPath;
  }

  @Override
  public UnmodifiableReachedSet asReachedSet() {
    return new BlockatorReachedSetView(path.getFirstState(), path.getLastState(),
        s -> super.asReachedSet().getPrecision(super.asReachedSet().getLastState()));
  }

  @Override
  public void removeSubtree(ARGState e) throws InterruptedException {
    removeSubtree(e, ImmutableList.of(), ImmutableList.of());
  }

  @Override
  public void removeSubtree(ARGState e, Precision p, Predicate<? super Precision> pPrecisionType)
      throws InterruptedException {
    removeSubtree(e, ImmutableList.of(p), ImmutableList.of(pPrecisionType));
  }

  @Override
  public void removeSubtree(
      ARGState pState, List<Precision> pPrecisions, List<Predicate<? super Precision>> pPrecTypes)
      throws InterruptedException {
    if (pState instanceof BackwardARGState) {
      pState = ((BackwardARGState) pState).getARGState();
    }

    Set<ARGState> toRemove = new HashSet<>();
    Deque<ARGState> worklist = new ArrayDeque<>();
    System.out.println("Iter start");

    worklist.add(pState);
    while (!worklist.isEmpty()) {
      ARGState current = worklist.removeFirst();
      if (current instanceof BackwardARGState) {
        current = ((BackwardARGState) current).getARGState();
      }

      if (current.isDestroyed()) continue; // FIXME This check should not be needed!
      BlockatorState bState = cpa.getStateRegistry().get(current);
      if (bState.getLastBlock() != null) {
        BlockStackEntry stackEntry = bState.getLastBlock();
        BlockEntry blockEntry = cpa.getStateRegistry().get(stackEntry.entryState).getBlockEntry();
        if (blockEntry == null || !blockEntry.block.equals(stackEntry.block)) {
          throw new RuntimeException("Mismatched stack entry pointer");
        }

        CacheEntry cacheEntry = cpa.getCacheManager().get(blockEntry.block,
            blockEntry.reducedState, blockEntry.reducedPrecision);

        if (cacheEntry != null && cacheEntry.hasExitUsages(current)) {
          for (AbstractState st: cacheEntry.getExitUsages(current)) {
            worklist.add((ARGState) st);
          }

          cacheEntry.removeExitUsage(current);
        }

        if (cacheEntry != null) {
          cacheEntry.removeExitState(current);
        }
      }

      if (bState.getStateKind() == StateKind.BLOCK_ENTRY) {
        BlockStackEntry stackEntry = bState.getLastBlock();
        if (stackEntry == null) {
          throw new RuntimeException("Empty block stack on block entry state");
        }

        BlockEntry blockEntry = cpa.getStateRegistry().get(stackEntry.entryState).getBlockEntry();
        if (blockEntry == null || !blockEntry.block.equals(stackEntry.block)) {
          throw new RuntimeException("Mismatched stack entry pointer");
        }

        CacheEntry cacheEntry = cpa.getCacheManager().getOrCreate(blockEntry.block,
            blockEntry.reducedState, blockEntry.reducedPrecision);

        for (Pair<AbstractState, Precision> usage: cacheEntry.getCacheUsages()) {
          ARGState usageState = (ARGState) usage.getFirstNotNull();
          // Cache usage may have been removed by other refinement
          if (!usageState.isDestroyed()) {
            worklist.addAll(usageState.getSuccessors());
            worklist.addAll(usageState.getCoveredByThis());
          }
        }

        cpa.getCacheManager().remove(blockEntry.block, blockEntry.reducedState,
            blockEntry.reducedPrecision);
      }

      toRemove.add(current);
      worklist.addAll(current.getSuccessors());
      worklist.addAll(current.getCoveredByThis());
    }

    Set<ARGState> toReadd = new HashSet<>();
    for (ARGState state: toRemove) {
      for (ARGState parent: state.getParents()) {
        if (parent.getAppliedFrom() != null) {
          parent = parent.getAppliedFrom().getFirst();
        }

        if (parent == null || toRemove.contains(parent)) {
          continue;
        }

        toReadd.add(parent);
      }

      state.removeFromARG();
      delegate.remove(state);
      cpa.getStateRegistry().remove(state);
    }

    for (ARGState state: toReadd) {
      delegate.readdToWaitlist(state, pPrecisions, pPrecTypes);
    }
  }
}
