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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.cpa.arg.ARGBasedRefiner;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.AbstractARGBasedRefiner;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.blockator.BlockatorState.BlockCacheUsage;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.CPAs;

public class BlockatorBasedRefiner extends AbstractARGBasedRefiner {
  private BlockatorCPA cpa;

  /**
   * Create a {@link Refiner} instance that supports BAM from a {@link ARGBasedRefiner} instance.
   */
  public static Refiner forARGBasedRefiner(
      final ARGBasedRefiner pRefiner, final ConfigurableProgramAnalysis pCpa)
      throws InvalidConfigurationException {
    checkArgument(
        !(pRefiner instanceof Refiner),
        "ARGBasedRefiners may not implement Refiner, choose between these two!");

    if (!(pCpa instanceof BlockatorCPA)) {
      throw new InvalidConfigurationException("BlockatorCPA CPA needed for BlockatorCPA-based refinement");
    }

    BlockatorCPA bCPA = (BlockatorCPA) pCpa;
    return new BlockatorBasedRefiner(pRefiner, bCPA, bCPA.getLogger());
  }

  protected BlockatorBasedRefiner(
      ARGBasedRefiner pRefiner,
      BlockatorCPA pCpa, LogManager pLogger) throws InvalidConfigurationException {
    super(pRefiner, CPAs.retrieveCPAOrFail(pCpa, ARGCPA.class, BlockatorBasedRefiner.class), pLogger);
    this.cpa = pCpa;
  }

  private void recurseBlock(ARGState last, ARGState first, List<ARGState> path) {
    ARGState current = last;

    while (!current.getParents().isEmpty() && !current.equals(first)) {
      BlockCacheUsage cacheUsage = cpa.getStateRegistry().get(current).getBlockCacheUsage();

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
  protected @Nullable ARGPath computePath(ARGState pLastElement, ARGReachedSet pReached)
      throws InterruptedException, CPATransferException
  {
    System.out.println("BlockatorBasedRefiner.computePath");
    List<ARGState> path = new ArrayList<>();

    recurseBlock(pLastElement, null, path);

//    for (int i = 0; i < path.size(); i++) {
//      path.set(i, new BackwardARGState(path.get(i)));
//    }
//
//    for (int i = 0; i < path.size() - 1; i++) {
//      path.get(i).addParent(path.get(i + 1));
//    }

    return new ARGPath(Lists.reverse(path));
  }

  @Override
  protected CounterexampleInfo performRefinementForPath(ARGReachedSet pReached, ARGPath pPath)
      throws CPAException, InterruptedException
  {
    assert pPath == null || pPath.size() > 0;

    if (pPath == null) {
      return CounterexampleInfo.spurious();
    } else {
      return super.performRefinementForPath(new BlockatorReachedSet(pReached, cpa, pPath), pPath);
    }
  }

  static class BackwardARGState extends ARGState {
    private static final long serialVersionUID = -3279533907385516993L;

    public BackwardARGState(ARGState originalState) {
      super(originalState, null);
    }

    public ARGState getARGState() {
      return (ARGState) getWrappedState();
    }

    public BackwardARGState copy() {
      return new BackwardARGState(getARGState());
    }

    @Override
    public String toString() {
      return "BackwardARGState {{" + super.toString() + "}}";
    }
  }
}
