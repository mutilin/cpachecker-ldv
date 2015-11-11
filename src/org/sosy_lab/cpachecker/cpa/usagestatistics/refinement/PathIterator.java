/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usagestatistics.refinement;

import static com.google.common.collect.FluentIterable.from;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nullable;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.bam.BAMCEXSubgraphComputer.BackwardARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMMultipleCEXSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

import com.google.common.base.Function;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;


public class PathIterator extends WrappedConfigurableRefinementBlock<UsageInfo, ExtendedARGPath> {

  private final Set<List<Integer>> refinedStates = new HashSet<>();
  private final Map<ARGState, ARGState> subgraphStatesToReachedState;
  private final BAMTransferRelation transfer;
  private final Multimap<AbstractState, AbstractState> fromReducedToExpand;
  private final BAMMultipleCEXSubgraphComputer subgraphComputer;
  //private final LogManager logger;

  //Statistics
  private Timer totalTimer = new Timer();
  private Timer computingPath = new Timer();
  private Timer additionTimer = new Timer();
  private int numberOfPathCalculated = 0;
  private int successfulAdditionChecks = 0;
  //private int numberOfrepeatedPaths = 0;

  private Map<AbstractState, Iterator<ARGState>> toCallerStatesIterator = new HashMap<>();

  private final Function<ARGState, Integer> GET_ORIGIN_STATE_NUMBERS = new Function<ARGState, Integer>() {
    @Override
    @Nullable
    public Integer apply(@Nullable ARGState pInput) {
      assert subgraphStatesToReachedState.containsKey(pInput);
      return subgraphStatesToReachedState.get(pInput).getStateId();
    }
  };

  public PathIterator(Map<ARGState, ARGState> pSubgraphStatesToReachedState, BAMTransferRelation bamTransfer,
      ConfigurableRefinementBlock<ExtendedARGPath> pWrapper, LogManager l) {
    super(pWrapper);
    subgraphStatesToReachedState = pSubgraphStatesToReachedState;
    transfer = bamTransfer;
    fromReducedToExpand = transfer.getMapFromReducedToExpand();
    subgraphComputer = transfer.createBAMMultipleSubgraphComputer(subgraphStatesToReachedState);
    //logger = l;
  }

  @Override
  public RefinementResult call(UsageInfo pInput) throws CPAException, InterruptedException {
    totalTimer.start();
    //The first time, we have no path to iterate
    BackwardARGState newTarget = new BackwardARGState((ARGState)pInput.getKeyState());
    subgraphStatesToReachedState.put(newTarget, (ARGState)pInput.getKeyState());
    ARGPath currentPath = computePath(newTarget);
    RefinementResult result = RefinementResult.createFalse();

    PredicatePrecision completePrecision = PredicatePrecision.empty();
    while (currentPath != null) {
      ARGState startingState;

      numberOfPathCalculated++;
      totalTimer.stop();
      RefinementResult wrapperResult = wrappedRefiner.call(new ExtendedARGPath(currentPath, pInput));
      totalTimer.start();

      if (wrapperResult.isTrue()) {
        wrapperResult.addInfo(PathIterator.class, Pair.of(currentPath, completePrecision));
        totalTimer.stop();
        return wrapperResult;
      } else if (wrapperResult.isFalse()) {
        //Get a starting state
        Object predicateInfo = wrapperResult.getInfo(PredicateRefinerAdapter.class);
        assert predicateInfo instanceof Pair;
        List<ARGState> affectedStates = ((Pair<List<ARGState>, PredicatePrecision>)predicateInfo).getFirst();
        List<Integer>changedStateNumbers = from(affectedStates).transform(GET_ORIGIN_STATE_NUMBERS).toList();
        refinedStates.add(changedStateNumbers);

        PredicatePrecision precision = ((Pair<List<ARGState>, PredicatePrecision>)predicateInfo).getSecond();

        if (precision != null) {
          completePrecision = completePrecision.mergeWith(precision);
        }

        startingState = affectedStates.get(affectedStates.size() - 1);
      } else {
        //start with the beginning
        result = wrapperResult;
        startingState = currentPath.getFirstState().getChildren().iterator().next();
      }
      computingPath.start();
      currentPath = next(startingState);
      computingPath.stop();
    }
    if (result.isFalse()) {
      //No unknown verdict, therefore all paths are false: set a result for usage as false
      result.addInfo(PathIterator.class, completePrecision);
    }
    totalTimer.stop();
    return result;
  }

  //lastAffectedState is Backward!
  public ARGPath next(ARGState lastAffectedState) throws CPATransferException, InterruptedException  {
    assert lastAffectedState != null;

    ARGState nextParent = null, previousCaller, childOfForkState = lastAffectedState;
    do {
      //This is a backward state, which displays the following state after real reduced state, which we want to found
      childOfForkState = findPreviousFork(childOfForkState);

      if (childOfForkState == null) {
        return null;
      }

      //parent = forkState.getParents().iterator().next();
     // assert parent != null;
      ARGState realChildOfForkState = subgraphStatesToReachedState.get(childOfForkState);
      assert realChildOfForkState.getParents().size() == 1;
      ARGState forkState = realChildOfForkState.getParents().iterator().next();
      //Clone is necessary, make tree set for determinism
      Set<ARGState> callerStates = Sets.newTreeSet();
      for (AbstractState state : fromReducedToExpand.get(forkState)) {
        callerStates.add((ARGState)state);
      }
      previousCaller = childOfForkState.getParents().iterator().next();

      assert callerStates != null;

      Iterator<ARGState> iterator;
      //It is important to put a backward state in map, because we can find the same real state during exploration
      //but for it a new backward state will be created
      if (toCallerStatesIterator.containsKey(childOfForkState)) {
        //Means we have already handled this state, just get the next one
        iterator = toCallerStatesIterator.get(childOfForkState);
      } else {
        //We get this fork the second time (the first one was from path computer)
        //Found the caller, we have explored the first time
        ARGState realPreviousCaller = subgraphStatesToReachedState.get(previousCaller);
        assert callerStates.remove(realPreviousCaller);
        iterator = callerStates.iterator();
        toCallerStatesIterator.put(childOfForkState, iterator);
      }

      if (iterator.hasNext()) {
        nextParent = iterator.next();
      } else {
        //We need to find the next fork
        //Do not change the fork state and start exploration from this one
      }
    } while (nextParent == null);

    BackwardARGState newNextParent = new BackwardARGState(nextParent);
    previousCaller.removeFromARG();
    childOfForkState.addParent(newNextParent);
    subgraphStatesToReachedState.put(newNextParent, nextParent);
    return computePath(newNextParent);
  }

  /**
   * Due to special structure of ARGPath,
   * the real fork state (reduced entry) is not included into it.
   * We need to get it.
   *
   * @param parent
   * @return
   */
  private ARGState findPreviousFork(ARGState parent) {
    List<ARGState> potentialForkStates = new LinkedList<>();
    Map<ARGState, ARGState> exitStateToEntryState = new TreeMap<>();
    ARGState currentState = parent;
    ARGState realState = subgraphStatesToReachedState.get(currentState);
    assert parent.getParents().size() == 1;
    ARGState currentParent = parent.getParents().iterator().next();
    ARGState realParent = subgraphStatesToReachedState.get(currentParent);
    while (currentState.getChildren().size() > 0) {

      assert currentState.getChildren().size() == 1;
      currentState = currentState.getChildren().iterator().next();
      realState = subgraphStatesToReachedState.get(currentState);

      //No matter which parent to take - interesting one is single anyway
      realParent = realState.getParents().iterator().next();

      //Check if it is an exit state, we are waiting
      //Attention! Recursion is not supported here!
      if (exitStateToEntryState.containsKey(realParent)) {
        //Due to complicated structure of path we saved an expanded exit state and it isn't contained in the path,
        //so, we look for its parent
        ARGState entryState = exitStateToEntryState.get(realParent);
        //remove all child in cache
        for (ARGState displayedChild : entryState.getChildren()) {
          exitStateToEntryState.remove(displayedChild);
        }
        potentialForkStates.remove(entryState);
      }

      if (fromReducedToExpand.containsKey(realParent) &&
          fromReducedToExpand.get(realParent).size() > 1) {

        assert realParent.getParents().size() == 0;
        assert fromReducedToExpand.get(realParent).size() > 1;

        //Now we should check, that there is no corresponding exit state in the path only in this case this is a real fork
        parent = currentState.getParents().iterator().next();
        ARGState displayedParent = subgraphStatesToReachedState.get(parent);
        //We may have several children, so add all of them
        for (ARGState displayedChild : displayedParent.getChildren()) {
          exitStateToEntryState.put(displayedChild, currentState);
        }
        //Save child and if we meet it, we remove the parent as not a fork

        potentialForkStates.add(currentState);
      }
    }

    if (potentialForkStates.isEmpty()) {
      return null;
    } else {
      return potentialForkStates.get(0);
    }
  }

  ARGPath computePath(BackwardARGState pLastElement) throws InterruptedException, CPATransferException {
    assert (pLastElement != null && !pLastElement.isDestroyed());
      //we delete this state from other unsafe

    ARGState rootOfSubgraph = subgraphComputer.findPath(pLastElement, refinedStates);
    assert (rootOfSubgraph != null);
    if (rootOfSubgraph == BAMMultipleCEXSubgraphComputer.DUMMY_STATE_FOR_REPEATED_STATE) {
      return null;
    }
    ARGPath result = ARGUtils.getRandomPath(rootOfSubgraph);
    additionTimer.start();
    List<Integer> stateNumbers = from(result.asStatesList()).transform(GET_ORIGIN_STATE_NUMBERS).toList();
    for (List<Integer> previousStates : refinedStates) {
      if (stateNumbers.containsAll(previousStates)) {
        successfulAdditionChecks++;
      }
    }
    additionTimer.stop();
    return result;
  }

  @Override
  public void printStatistics(PrintStream pOut) {
    pOut.println("--PathIterator--");
    pOut.println("Timer for block:                     " + totalTimer);
    pOut.println("--Timer for path computing:          " + computingPath);
    pOut.println("--Timer for addition checks:         " + additionTimer);
    pOut.println("Number of path calculated:           " + numberOfPathCalculated);
    pOut.println("Number of successful Addition Checks:" + successfulAdditionChecks);
    wrappedRefiner.printStatistics(pOut);
  }

  @Override
  public Object handleFinishSignal(Class<? extends RefinementInterface> callerClass) {
    if (callerClass.equals(IdentifierIterator.class)) {
      //Refinement iteration finishes
      refinedStates.clear();
    }
    return null;
  }
}
