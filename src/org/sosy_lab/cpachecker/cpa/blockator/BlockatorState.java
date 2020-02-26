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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;

public class BlockatorState {
  public enum StateKind {
    NORMAL,
    BLOCK_ENTRY
  }

  public static class BlockEntry {
    public final AbstractState entryState;
    public final Precision entryPrecision;
    public final Block block;
    public final AbstractState reducedState;
    public final Precision reducedPrecision;

    public BlockEntry(
        AbstractState pEntryState,
        Precision pEntryPrecision,
        Block pBlock,
        AbstractState pReducedState,
        Precision pReducedPrecision) {
      entryState = pEntryState;
      entryPrecision = pEntryPrecision;
      block = pBlock;
      reducedState = pReducedState;
      reducedPrecision = pReducedPrecision;
    }
  }

  public static class BlockStackEntry {
    public final Block block;
    public final AbstractState entryState;

    public BlockStackEntry(Block pBlock, AbstractState pEntryState) {
      block = pBlock;
      entryState = pEntryState;
    }

    @Override
    public String toString() {
      return "BlockInner{" +
          "block=" + block +
          ", entryState=#" + ((ARGState) entryState).getStateId() +
          '}';
    }
  }

  public static class BlockCacheUsage {
    public final AbstractState firstBlockState;
    public final AbstractState lastBlockState;

    public BlockCacheUsage(AbstractState pFirstBlockState, AbstractState pLastBlockState) {
      firstBlockState = pFirstBlockState;
      lastBlockState = pLastBlockState;
    }
  }

  private BlockEntry blockEntry = null;
  private BlockCacheUsage blockCacheUsage = null;
  private List<BlockStackEntry> blockStack = Collections.emptyList();
  private Precision modifiedPrecision = null;
  private StateKind stateKind = StateKind.NORMAL;

  public BlockatorState() {

  }

  private BlockatorState(BlockatorState st) {
    blockEntry = st.blockEntry;
    blockCacheUsage = st.blockCacheUsage;
    blockStack = st.blockStack;
    modifiedPrecision = st.modifiedPrecision;
    stateKind = st.stateKind;
  }

  @Nullable
  public BlockEntry getBlockEntry() {
    return blockEntry;
  }

  @Nullable
  public BlockCacheUsage getBlockCacheUsage() {
    return blockCacheUsage;
  }

  @Nonnull
  public List<BlockStackEntry> getBlockStack() {
    return blockStack;
  }

  @Nullable
  public Precision getModifiedPrecision() {
    return modifiedPrecision;
  }

  @Nullable
  public StateKind getStateKind() {
    return stateKind;
  }

  @Nullable
  public BlockStackEntry getLastBlock() {
    if (blockStack.isEmpty()) return null;
    return blockStack.get(blockStack.size() - 1);
  }

  public BlockatorState transition() {
    return transition(null);
  }

  public BlockatorState transition(Precision pExpandedPrecision) {
    BlockatorState st = new BlockatorState(this);
    st.blockEntry = null;
    st.blockCacheUsage = null;
    st.modifiedPrecision = pExpandedPrecision;
    st.stateKind = blockEntry != null ? StateKind.BLOCK_ENTRY : StateKind.NORMAL;
    return st;
  }

  public BlockatorState cacheUsage(AbstractState firstState, AbstractState lastState) {
    BlockatorState st = new BlockatorState(this);
    st.blockCacheUsage = new BlockCacheUsage(firstState, lastState);
    return st;
  }

  public BlockatorState enterBlock(AbstractState pEntryState, Precision pEntryPrecision,
                                   Block pBlock, AbstractState pReducedState, Precision pReducedPrecision) {
    BlockEntry newEntry = new BlockEntry(pEntryState, pEntryPrecision, pBlock, pReducedState,
        pReducedPrecision);
    List<BlockStackEntry> newBlockStack = new ArrayList<>(blockStack);
    newBlockStack.add(new BlockStackEntry(pBlock, pReducedState));

    BlockatorState state = new BlockatorState(this);
    state.blockEntry = newEntry;
    state.blockStack = Collections.unmodifiableList(newBlockStack);
    state.modifiedPrecision = pReducedPrecision;
    return state;
  }

  public BlockatorState exitBlock() {
    List<BlockStackEntry> newBlockStack = new ArrayList<>(blockStack);
    newBlockStack.remove(newBlockStack.size() - 1);

    BlockatorState state = new BlockatorState();
    state.blockStack = Collections.unmodifiableList(newBlockStack);
    return state;
  }
}
