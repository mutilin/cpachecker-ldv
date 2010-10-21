/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;

/**
 * @author Michael Tautschnig <tautschnig@forsyte.de>
 *
 */
public class CFACheck {
  /**
   * BFS-traverse the CFA and run a series of checks at each node
   * @param cfa Node to start traversal from
   */
  public static boolean check (CFAFunctionDefinitionNode cfa)
  {
    // Code copied from CFASimplifier.java
    Set<CFANode> visitedNodes = new HashSet<CFANode> ();
    LinkedList<CFANode> waitingNodeList = new LinkedList<CFANode> ();

    waitingNodeList.add (cfa);
    while (!waitingNodeList.isEmpty ())
    {
      CFANode node = waitingNodeList.poll ();
      if (!visitedNodes.add (node)) continue;

      int leavingEdgeCount = node.getNumLeavingEdges ();
      for (int edgeIdx = 0; edgeIdx < leavingEdgeCount; edgeIdx++)
      {
        waitingNodeList.add (node.getLeavingEdge (edgeIdx).getSuccessor ());
      }

      // The actual checks
      assert isConsistent(node) : "Incosistent node " + node;
      assert jumpConsistency(node) : "Incosistent jump at node " + node;
    }
    return true;
  }

  /**
   * Check all entering and leaving edges for corresponding leaving/entering edges
   * at predecessor/successor nodes, and that there are no duplicates
   * @param pNode Node to be checked
   * @return False, if an inconsistency is detected
   */
  private static boolean isConsistent(CFANode pNode) {
    Set<CFAEdge> seen = new HashSet<CFAEdge>();
    // as long as the direction of traversal of the CFA is forward, we could actually
    // omit the forward consistency check, but better check twice than not at all
    int leavingEdgeCount = pNode.getNumLeavingEdges ();
    for (int edgeIdx = 0; edgeIdx < leavingEdgeCount; ++edgeIdx)
    {
      CFAEdge edge = pNode.getLeavingEdge(edgeIdx);
      if (!seen.add(edge)) {
        assert false : "Duplicate leaving edge " + edge + " on node " + pNode;
      }
      CFANode successor = edge.getSuccessor();
      int succEnteringEdgeCount = successor.getNumEnteringEdges();
      boolean hasEdge = false;
      for (int succEdgeIdx = 0; succEdgeIdx < succEnteringEdgeCount; ++succEdgeIdx) {
        if (successor.getEnteringEdge(succEdgeIdx) == edge) {
          hasEdge = true;
          break;
        }
      }
      if (!hasEdge) {
        assert false : "Node " + pNode + " has leaving edge " + edge
            + ", but pNode " + successor + " does not have this edge as entering edge!";
      }
    }

    seen.clear();
    int enteringEdgeCount = pNode.getNumEnteringEdges ();
    for (int edgeIdx = 0; edgeIdx < enteringEdgeCount; ++edgeIdx)
    {
      CFAEdge edge = pNode.getEnteringEdge(edgeIdx);
      if (!seen.add(edge)) {
        assert false : "Duplicate entering edge " + edge + " on node " + pNode;
      }
      CFANode predecessor = edge.getPredecessor();
      int predLeavingEdgeCount = predecessor.getNumLeavingEdges();
      boolean hasEdge = false;
      for (int predEdgeIdx = 0; predEdgeIdx < predLeavingEdgeCount; ++predEdgeIdx) {
        if (predecessor.getLeavingEdge(predEdgeIdx) == edge) {
          hasEdge = true;
          break;
        }
      }
      if (!hasEdge) {
        assert false : "Node " + pNode + " has entering edge " + edge
            + ", but pNode " + predecessor + " does not have this edge as leaving edge!";
      }
    }

    return true;
  }

  /**
   * Check for jump edges and make sure there is only one of them
   * @param pNode Node to be checked
   * @return False, if an inconsistency is detected
   */
  private static boolean jumpConsistency(CFANode pNode) {
    if (pNode.hasJumpEdgeLeaving()) {
      return (pNode.getNumLeavingEdges() == 1);
    }

    return true;
  }
}
