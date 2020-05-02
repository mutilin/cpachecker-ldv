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

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.blockator.BlockatorState.BlockCacheUsage;
import org.sosy_lab.cpachecker.cpa.usage.refinement.PathIterator;
import org.sosy_lab.cpachecker.cpa.usage.refinement.PathRestorator;

public class BlockatorPathRestorator implements PathRestorator {
  private final BlockatorCPA parent;

  public BlockatorPathRestorator(BlockatorCPA pParent) {
    parent = pParent;
  }

  @Override
  public ARGPath computePath(ARGState pLastElement) {
    return computePath(pLastElement, Collections.emptySet());
  }

  private void recurseBlock(ARGState last, ARGState first, List<ARGState> path) {
    ARGState current = last;

    while (!current.getParents().isEmpty() && !current.equals(first)) {
      BlockCacheUsage cacheUsage = parent.getStateRegistry().get(current).getBlockCacheUsage();

      path.add(current);

      if (cacheUsage != null) {
        recurseBlock((ARGState) cacheUsage.lastBlockState, (ARGState) cacheUsage.firstBlockState,
            path);
      }

      if (current.getParents().size() != 1) {
        throw new RuntimeException("State has multiple parents: #" + current.getStateId());
      }

      current = current.getParents().iterator().next();
    }

    if (first != null && !current.equals(first)) {
      throw new RuntimeException("Did'nt found entry state in ARG");
    }

    if (!current.equals(first)) {
      path.add(current);
    }
  }


  @Override
  public ARGPath computePath(ARGState pLastElement, Set<List<Integer>> pRefinedStates) {
    List<ARGState> path = new ArrayList<>();

    recurseBlock(pLastElement, null, path);

    return new ARGPath(Lists.reverse(path));
  }

  @Override
  public PathIterator iterator(ARGState pTarget) {
    return new PathIterator() {
      boolean returnedPath = false;

      @Override
      public ARGPath nextPath(Set<List<Integer>> pRefinedStatesIds) {
        if (returnedPath) {
          return null;
        }

        returnedPath = true;
        return computePath(pTarget, pRefinedStatesIds);
      }
    };
  }
}
