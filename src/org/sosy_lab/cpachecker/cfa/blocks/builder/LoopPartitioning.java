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
package org.sosy_lab.cpachecker.cfa.blocks.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.objectmodel.BlankEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionCallEdge;
import org.sosy_lab.cpachecker.util.CFA;
import org.sosy_lab.cpachecker.util.CFA.Loop;

import com.google.common.collect.Iterables;


/**
 * <code>PartitioningHeuristic</code> that creates blocks for each loop- and function-body.
 */
public class LoopPartitioning extends PartitioningHeuristic {
  protected final LogManager logger;
  private Map<CFANode, Set<CFANode>> loopHeaderToLoopBody;

  public LoopPartitioning(LogManager pLogger) {
    this.logger = pLogger;
    this.loopHeaderToLoopBody = null;
  }

  private void initLoopMap() {
    loopHeaderToLoopBody = new HashMap<CFANode, Set<CFANode>>();
    for(String functionName : CFACreator.loops.keySet()) {
      for(Loop loop : CFACreator.loops.get(functionName)) {
        if(loop.getLoopHeads().size() == 1) {
          //currently only loops with single loop heads supported
          loopHeaderToLoopBody.put(Iterables.getOnlyElement(loop.getLoopHeads()), loop.getLoopNodes());
        }
      }
    }
  }

  @Override
  protected boolean shouldBeCached(CFANode pNode) {
    if(pNode instanceof CFAFunctionDefinitionNode && pNode.getNumEnteringEdges() == 0) {
      //main function
      return true;
    }
    if(pNode.isLoopStart()) {
      if(hasBlankEdgeFromLoop(pNode) || selfLoop(pNode)) {
        return false;
      }
      return true;
    }
    return false;
  }

  private static boolean hasBlankEdgeFromLoop(CFANode pNode) {
    for(int i = 0; i < pNode.getNumEnteringEdges(); i++) {
      CFAEdge edge = pNode.getEnteringEdge(i);
      if(edge instanceof BlankEdge && edge.getPredecessor().isLoopStart()) {
        return true;
      }
    }
    return false;
  }

  private static boolean selfLoop(CFANode pNode) {
    return pNode.getNumLeavingEdges() == 1 && pNode.getLeavingEdge(0).getSuccessor().equals(pNode);
  }

  @Override
  protected Set<CFANode> getCachedSubtree(CFANode pNode) {
    if(pNode instanceof CFAFunctionDefinitionNode) {
      CFAFunctionDefinitionNode functionNode = (CFAFunctionDefinitionNode) pNode;
      return CFA.exploreSubgraph(functionNode, functionNode.getExitNode());
    }
    if(pNode.isLoopStart()) {
      Set<CFANode> loopBody = new HashSet<CFANode>();
      if(loopHeaderToLoopBody == null) {
        initLoopMap();
      }
      loopBody.addAll(loopHeaderToLoopBody.get(pNode));
      insertLoopStartState(loopBody, pNode);
      insertLoopReturnStates(loopBody);
      return loopBody;
    }
    return null;
  }

  private void insertLoopStartState(Set<CFANode> pLoopBody, CFANode pLoopHeader) {
    for(int i = 0; i < pLoopHeader.getNumEnteringEdges(); i++) {
      CFAEdge edge = pLoopHeader.getEnteringEdge(i);
      if(edge instanceof BlankEdge && !pLoopBody.contains(edge.getPredecessor())) {
        pLoopBody.add(edge.getPredecessor());
      }
    }
  }

  private void insertLoopReturnStates(Set<CFANode> pLoopBody) {
    List<CFANode> addNodes = new ArrayList<CFANode>();
    for(CFANode node : pLoopBody) {
      for(int i = 0; i < node.getNumLeavingEdges(); i++) {
        CFAEdge edge = node.getLeavingEdge(i);
        if(!pLoopBody.contains(edge.getSuccessor()) && !(node.getLeavingEdge(i) instanceof FunctionCallEdge))  {
          addNodes.add(edge.getSuccessor());
        }
      }
    }
    pLoopBody.addAll(addNodes);
  }
}
