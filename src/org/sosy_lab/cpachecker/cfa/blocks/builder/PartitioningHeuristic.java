/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.util.CFATraversal;


/**
 * Defines an interface for heuristics for the partition of a program's CFA into blocks.
 */
public abstract class PartitioningHeuristic {
  /**
   * Creates a <code>BlockPartitioning</code> using the represented heuristic.
   * @param mainFunction CFANode at which the main-function is defined
   * @return BlockPartitioning
   * @see org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning
   */
  public final BlockPartitioning buildPartitioning(CFANode mainFunction) {
    CFATraversal.NodeCollectingCFAVisitor visitor = new CFATraversal.NodeCollectingCFAVisitor();
    CFATraversal.dfs().ignoreFunctionCalls().traverse(mainFunction, visitor);
    Set<CFANode> mainFunctionBody = visitor.getVisitedNodes();
    BlockPartitioningBuilder builder = new BlockPartitioningBuilder(mainFunctionBody);

    //traverse CFG
    Set<CFANode> seen = new HashSet<CFANode>();
    Deque<CFANode> stack = new ArrayDeque<CFANode>();

    seen.add(mainFunction);
    stack.push(mainFunction);

    while(!stack.isEmpty()) {
      CFANode node = stack.pop();

      if(shouldBeCached(node)) {
        Set<CFANode> subtree = getBlockForNode(node);
        if(subtree != null) {
          builder.addBlock(subtree);
        }
      }

      for(int i = 0; i < node.getNumLeavingEdges(); i++) {
        CFANode nextNode = node.getLeavingEdge(i).getSuccessor();
        if(!seen.contains(nextNode)) {
          stack.push(nextNode);
          seen.add(nextNode);
        }
      }
    }

    return builder.build();
  }

  /**
   * @param pNode
   * @return <code>true</code>, if for the given node a new <code>Block</code> should be created; <code>false</code> otherwise
   */
  protected abstract boolean shouldBeCached(CFANode pNode);

  /**
   * @param pNode CFANode that should be cached.
   * @return set of nodes that represent a <code>Block</code>.
   */
  protected abstract Set<CFANode> getBlockForNode(CFANode pNode);
}
