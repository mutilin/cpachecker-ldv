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

import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionExitNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFALabelNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;

/**
 * Helper class that contains some complex operations that may be useful during
 * the creation of a CFA.
 */
public class CFACreationUtils {

  /**
   * This method adds this edge to the leaving and entering edges
   * of its predecessor and successor respectively, but it does so only
   * if the edge does not contain dead code
   */
  public static void addEdgeToCFA(CFAEdge edge, LogManager logger) {
    CFANode predecessor = edge.getPredecessor();
    CFANode successor = edge.getSuccessor();

    // check control flow branching at predecessor
    if (edge instanceof AssumeEdge) {
      assert predecessor.getNumLeavingEdges() <= 1;
      if (predecessor.getNumLeavingEdges() > 0) {
        assert predecessor.getLeavingEdge(0) instanceof AssumeEdge;
      }

    } else {
      assert predecessor.getNumLeavingEdges() == 0;
    }

    // check control flow merging at successor
    if (   !(successor instanceof CFAFunctionExitNode)
        && !(successor instanceof CFALabelNode)
        && !(successor.isLoopStart())) {
      // these two node types may have unlimited incoming edges,
      // a loopStart can be reachable through 'continue' several times,
      // all other may have at most two of them
      assert successor.getNumEnteringEdges() <= 1;
    }

    // check if predecessor is reachable
    // or if the predecessor is a loopStart of a forLoop
    // and the edge is the "counter++"-edge
    if (isReachableNode(predecessor) ||
        (edge instanceof StatementEdge && edge.getSuccessor().isLoopStart())) {

      // all checks passed, add it to the CFA
      edge.getPredecessor().addLeavingEdge(edge);
      edge.getSuccessor().addEnteringEdge(edge);

    } else {
      // unreachable edge, don't add it to the CFA

      if (!edge.getRawStatement().isEmpty()) {
        // warn user
        logger.log(Level.INFO, "Dead code detected at line", edge.getLineNumber() + ":", edge.getRawStatement());
      }
    }
  }

  /**
   * Returns true if a node is reachable, that is if it contains an incoming edge.
   * Label nodes and function start nodes are always considered to be reachable.
   * If a LabelNode has an empty labelText, it is not reachable through gotos.
   */
  public static boolean isReachableNode(CFANode node) {
    return (node.getNumEnteringEdges() > 0)
        || (node instanceof CFAFunctionDefinitionNode)
        || (node.isLoopStart())
        || ((node instanceof CFALabelNode)
            && !((CFALabelNode)node).getLabel().isEmpty());
  }

  /**
   * Remove nodes from the CFA beginning at a certain node n until there is a node
   * that is reachable via some other path (not going through n).
   * Useful for eliminating dead node, if node n is not reachable.
   */
  public static void removeChainOfNodesFromCFA(CFANode n) {
    if (n.getNumEnteringEdges() > 0) {
      return;
    }

    for (int i = n.getNumLeavingEdges()-1; i >= 0; i--) {
      CFAEdge e = n.getLeavingEdge(i);
      CFANode succ = e.getSuccessor();

      n.removeLeavingEdge(e);
      succ.removeEnteringEdge(e);
      removeChainOfNodesFromCFA(succ);
    }
  }
}
