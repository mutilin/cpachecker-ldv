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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;

public class BlockatorState {
  public static class BlockEntry {
    public final AbstractState entryState;
    public final Precision entryPrecision;
    public final Block block;
    public final Precision reducedPrecision;

    public BlockEntry(
        AbstractState pEntryState,
        Precision pEntryPrecision,
        Block pBlock,
        Precision pReducedPrecision) {
      entryState = pEntryState;
      entryPrecision = pEntryPrecision;
      block = pBlock;
      reducedPrecision = pReducedPrecision;
    }
  }

  public static class BlockInner {
    public final Block block;
    public final AbstractState entryState;

    public BlockInner(Block pBlock, AbstractState pEntryState) {
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

  private BlockEntry blockEntry = null;
  private List<BlockInner> blockStack = Collections.emptyList();
  private Precision expandedPrecision = null;

  public BlockatorState() {

  }

  public BlockatorState(BlockatorState st) {
    blockEntry = st.blockEntry;
    blockStack = st.blockStack;
    expandedPrecision = st.expandedPrecision;
  }

  @Nullable
  public BlockEntry getBlockEntry() {
    return blockEntry;
  }

  @Nonnull
  public List<BlockInner> getBlockStack() {
    return blockStack;
  }

  @Nullable
  public Precision getExpandedPrecision() {
    return expandedPrecision;
  }

  @Nullable
  public BlockInner getLastBlock() {
    if (blockStack.isEmpty()) return null;
    return blockStack.get(blockStack.size() - 1);
  }

  public BlockatorState transition() {
    return transition(null);
  }

  public BlockatorState transition(Precision pExpandedPrecision) {
    BlockatorState st = new BlockatorState(this);
    st.blockEntry = null;
    st.expandedPrecision = pExpandedPrecision;
    return st;
  }

  public BlockatorState enterBlock(AbstractState pEntryState, Precision pEntryPrecision,
                                   Block pBlock, Precision pReducedPrecision) {
    BlockEntry newEntry = new BlockEntry(pEntryState, pEntryPrecision, pBlock, pReducedPrecision);
    List<BlockInner> newBlockStack = new ArrayList<>(blockStack);
    newBlockStack.add(new BlockInner(pBlock, pEntryState));

    BlockatorState state = new BlockatorState(this);
    state.blockEntry = newEntry;
    state.blockStack = Collections.unmodifiableList(newBlockStack);
    return state;
  }

  public BlockatorState exitBlock() {
    List<BlockInner> newBlockStack = new ArrayList<>(blockStack);
    newBlockStack.remove(newBlockStack.size() - 1);

    BlockatorState state = new BlockatorState();
    state.blockStack = Collections.unmodifiableList(newBlockStack);
    return state;
  }
}
