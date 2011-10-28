/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa;

import static org.sosy_lab.cpachecker.util.CFAUtils.*;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionExitNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFALabelNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class CFACheck {

  /**
   * Traverse the CFA and run a series of checks at each node
   * @param cfa Node to start traversal from
   * @param nodes Optional set of all nodes in the CFA (may be null)
   */
  public static boolean check(CFAFunctionDefinitionNode cfa, Collection<CFANode> nodes) {

    Set<CFANode> visitedNodes = new HashSet<CFANode>();
    Deque<CFANode> waitingNodeList = new ArrayDeque<CFANode>();

    waitingNodeList.add(cfa);
    while (!waitingNodeList.isEmpty()) {
      CFANode node = waitingNodeList.poll();

      if (visitedNodes.add(node)) {
        for (CFAEdge edge : leavingEdges(node)) {
          waitingNodeList.add(edge.getSuccessor());
        }

        // The actual checks
        isConsistent(node);
        checkEdgeCount(node);
      }
    }

    if (nodes != null) {
      if (!visitedNodes.equals(nodes)) {
        assert false : "\nNodes in CFA but not reachable through traversal: " + Iterables.transform(Sets.difference(new HashSet<CFANode>(nodes), visitedNodes), DEBUG_FORMAT)
                     + "\nNodes reached that are not in CFA: " + Iterables.transform(Sets.difference(visitedNodes, new HashSet<CFANode>(nodes)), DEBUG_FORMAT);
      }
    }
    return true;
  }

  private static final Function<CFANode, String> DEBUG_FORMAT = new Function<CFANode, String>() {
    @Override
    public String apply(CFANode arg0) {
      return arg0.getFunctionName() + ":" + arg0.toString() + " (line " + arg0.getLineNumber() + ")";
    }
  };

  /**
   * Verify that the number of edges and their types match.
   * @param pNode Node to be checked
   */
  private static void checkEdgeCount(CFANode pNode) {

    // check entering edges
    int entering = pNode.getNumEnteringEdges();
    if (entering == 0) {
      assert (pNode instanceof CFAFunctionDefinitionNode) : "Dead code: node " + DEBUG_FORMAT.apply(pNode) + " has no incoming edges";

    } else if (entering > 2) {
      assert (pNode instanceof CFAFunctionDefinitionNode)
          || (pNode instanceof CFAFunctionExitNode)
          || (pNode instanceof CFALabelNode)
          || (pNode.isLoopStart())
          : "Too many incoming edges at node " + DEBUG_FORMAT.apply(pNode);
    }

    // check leaving edges
    if (!(pNode instanceof CFAFunctionExitNode)) {
      switch (pNode.getNumLeavingEdges()) {
      case 0:
        // not possible to check this, this case occurs when CFA pruning is enabled
//        assert false : "Dead end at node " + pNode;
        break;

      case 1: break;

      case 2:
        CFAEdge edge1 = pNode.getLeavingEdge(0);
        CFAEdge edge2 = pNode.getLeavingEdge(1);
        assert (edge1 instanceof AssumeEdge) && (edge2 instanceof AssumeEdge) : "Branching without conditions at node " + DEBUG_FORMAT.apply(pNode);

        AssumeEdge ae1 = (AssumeEdge)edge1;
        AssumeEdge ae2 = (AssumeEdge)edge2;
        assert ae1.getTruthAssumption() != ae2.getTruthAssumption() : "Inconsistent branching at node " + DEBUG_FORMAT.apply(pNode);
        break;

      default:
        assert false : "Too much branching at node " + DEBUG_FORMAT.apply(pNode);
      }
    }
  }

  /**
   * Check all entering and leaving edges for corresponding leaving/entering edges
   * at predecessor/successor nodes, and that there are no duplicates
   * @param pNode Node to be checked
   */
  private static void isConsistent(CFANode pNode) {
    Set<CFAEdge> seenEdges = new HashSet<CFAEdge>();
    Set<CFANode> seenNodes = new HashSet<CFANode>();

    for (CFAEdge edge : leavingEdges(pNode)) {
      if (!seenEdges.add(edge)) {
        assert false : "Duplicate leaving edge " + edge + " on node " + DEBUG_FORMAT.apply(pNode);
      }

      CFANode successor = edge.getSuccessor();
      if (!seenNodes.add(successor)) {
        assert false : "Duplicate successor " + successor + " for node " + DEBUG_FORMAT.apply(pNode);
      }

      boolean hasEdge = Iterables.contains(enteringEdges(successor), edge);
      assert hasEdge : "Node " + DEBUG_FORMAT.apply(pNode) + " has leaving edge " + edge
          + ", but pNode " + DEBUG_FORMAT.apply(pNode) + " does not have this edge as entering edge!";
    }

    seenEdges.clear();
    seenNodes.clear();

    for (CFAEdge edge : enteringEdges(pNode)) {
      if (!seenEdges.add(edge)) {
        assert false : "Duplicate entering edge " + edge + " on node " + DEBUG_FORMAT.apply(pNode);
      }

      CFANode predecessor = edge.getPredecessor();
      if (!seenNodes.add(predecessor)) {
        assert false : "Duplicate predecessor " + predecessor + " for node " + DEBUG_FORMAT.apply(pNode);
      }

      boolean hasEdge = Iterables.contains(leavingEdges(predecessor), edge);
      assert hasEdge : "Node " + DEBUG_FORMAT.apply(pNode) + " has entering edge " + edge
          + ", but pNode " + DEBUG_FORMAT.apply(pNode) + " does not have this edge as leaving edge!";
    }
  }
}
