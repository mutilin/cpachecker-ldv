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


/**
 * <code>PartitioningHeuristic</code> that creates blocks for each loop- and function-body.
 */
public class FunctionAndLoopPartitioning extends PartitioningHeuristic {

  private FunctionPartitioning functionPartitioning;
  private LoopPartitioning loopPartitioning;

  public FunctionAndLoopPartitioning(LogManager pLogger, CFA pCfa) {
    super(pLogger, pCfa);
    functionPartitioning = new FunctionPartitioning(pLogger, pCfa);
    loopPartitioning = new LoopPartitioning(pLogger, pCfa);
  }

  @Override
  protected boolean shouldBeCached(CFANode pNode) {
    return functionPartitioning.shouldBeCached(pNode) || loopPartitioning.shouldBeCached(pNode);
  }

  @Override
  protected Set<CFANode> getBlockForNode(CFANode pNode) {
    // TODO what to do if both want to cache it?
    if (functionPartitioning.shouldBeCached(pNode)) {
      return functionPartitioning.getBlockForNode(pNode);
    } else if (loopPartitioning.shouldBeCached(pNode)) {
      return loopPartitioning.getBlockForNode(pNode);
    } else {
      throw new AssertionError("node should not be cached: " + pNode);
    }
  }
}
