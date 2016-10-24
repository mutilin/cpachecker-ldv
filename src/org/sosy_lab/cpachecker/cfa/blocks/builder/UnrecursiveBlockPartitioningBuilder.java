/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.blocks.builder;

import java.util.ArrayList;
import java.util.Collection;

import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.lock.LockStatisticsTransferRelation;

import com.google.common.collect.ImmutableSet;



public class UnrecursiveBlockPartitioningBuilder extends BlockPartitioningBuilder {

  public UnrecursiveBlockPartitioningBuilder(LockStatisticsTransferRelation l) {
    super(l);
  }

  @Override
  public BlockPartitioning build(CFANode mainFunction) {
    //now we can create the Blocks   for the BlockPartitioning
    Collection<Block> blocks = new ArrayList<>(returnNodesMap.keySet().size());
    for (CFANode key : returnNodesMap.keySet()) {
      blocks.add(new Block(ImmutableSet.copyOf(referencedVariablesMap.get(key)), callNodesMap.get(key),
          returnNodesMap.get(key), ImmutableSet.copyOf(blockNodesMap.get(key)), ImmutableSet.copyOf(capturedLocksMap.get(key))));
    }
    return new BlockPartitioning(blocks, mainFunction);
  }
}
