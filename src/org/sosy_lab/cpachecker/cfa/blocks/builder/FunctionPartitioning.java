/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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

import java.util.Set;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.util.CFATraversal;

import com.google.common.base.Preconditions;


/**
 * <code>PartitioningHeuristic</code> that creates a block for each function-body.
 */
public class FunctionPartitioning extends PartitioningHeuristic {

  private static final CFATraversal TRAVERSE_CFA_INSIDE_FUNCTION = CFATraversal.dfs().ignoreFunctionCalls();

  /** Do not change signature! Constructor will be created with Reflections. */
  public FunctionPartitioning(LogManager pLogger, CFA pCfa) {
    super(pLogger, pCfa);
  }

  @Override
  protected boolean shouldBeCached(CFANode pNode) {
    if (pNode.getFunctionName().startsWith("__VERIFIER_")) {
      // exception for __VERIFIER helper functions
      // TODO do we need this? why?
      return false;
    }
    return pNode instanceof FunctionEntryNode;
  }

  @Override
  protected Set<CFANode> getBlockForNode(CFANode pNode) {
    Preconditions.checkArgument(shouldBeCached(pNode));
    Set<CFANode> blockNodes = TRAVERSE_CFA_INSIDE_FUNCTION.collectNodesReachableFrom(pNode);
    return blockNodes;
  }
}
