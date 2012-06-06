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
package org.sosy_lab.cpachecker.core.algorithm;

import static com.google.common.collect.ImmutableList.copyOf;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Timer;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.cbmctools.CBMCChecker;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.CounterexampleChecker;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.Path;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException.Reason;

import com.google.common.collect.Iterables;

@Options(prefix="counterexample")
public class CounterexampleCheckAlgorithm implements Algorithm, StatisticsProvider, Statistics {

  private final Algorithm algorithm;
  private final CounterexampleChecker checker;
  private final LogManager logger;

  private final Timer checkTime = new Timer();
  private int numberOfInfeasiblePaths = 0;

  @Option(name="checker", toUppercase=true, values={"CBMC", "CPACHECKER"},
          description="which model checker to use for verifying counterexamples as a second check\n"
                    + "Currently CBMC or CPAchecker with a different config can be used.")
  private String checkerName = "CBMC";

  @Option(description="continue analysis after an counterexample was found that was denied by the second check")
  private boolean continueAfterInfeasibleError = true;

  @Option(description="If continueAfterInfeasibleError is true, remove the infeasible counterexample before continuing."
              + "Setting this to false may prevent a lot of similar infeasible counterexamples to get discovered, but is unsound")
  private boolean removeInfeasibleErrors = false;

  public CounterexampleCheckAlgorithm(Algorithm algorithm, ConfigurableProgramAnalysis pCpa, Configuration config, LogManager logger, ReachedSetFactory reachedSetFactory, CFA cfa) throws InvalidConfigurationException, CPAException {
    this.algorithm = algorithm;
    this.logger = logger;
    config.inject(this);

    if (!(pCpa instanceof ARGCPA)) {
      throw new InvalidConfigurationException("ARG CPA needed for counterexample check");
    }

    if (checkerName.equals("CBMC")) {
      checker = new CBMCChecker(config, logger);
    } else if (checkerName.equals("CPACHECKER")) {
      checker = new CounterexampleCPAChecker(config, logger, reachedSetFactory, cfa);
    } else {
      throw new AssertionError();
    }
  }

  @Override
  public boolean run(ReachedSet reached) throws CPAException, InterruptedException {
    boolean sound = true;

    while (reached.hasWaitingState()) {
      sound &= algorithm.run(reached);

      AbstractState lastElement = reached.getLastState();
      if (!(lastElement instanceof ARGState)) {
        // no analysis possible
        break;
      }

      ARGState errorState = (ARGState)lastElement;
      if (!errorState.isTarget()) {
        // no analysis necessary
        break;
      }

      // check counterexample
      checkTime.start();
      try {
        ARGState rootState = (ARGState)reached.getFirstState();

        Set<ARGState> elementsOnErrorPath = ARGUtils.getAllStatesOnPathsTo(errorState);

        boolean feasibility;
        try {
          feasibility = checker.checkCounterexample(rootState, errorState, elementsOnErrorPath);
        } catch (CPAException e) {
          logger.logUserException(Level.WARNING, e, "Counterexample found, but feasibility could not be verified");
          //return false;
          throw e;
        }

        if (feasibility) {
          logger.log(Level.INFO, "Error path found and confirmed by counterexample check with " + checkerName + ".");
          return sound;

        } else {
          numberOfInfeasiblePaths++;
          logger.log(Level.INFO, "Error path found, but identified as infeasible by counterexample check with " + checkerName + ".");

          if (continueAfterInfeasibleError) {
            // This counterexample is infeasible, so usually we would remove it
            // from the reached set. This is not possible, because the
            // counterexample of course contains the root element and we don't
            // know up to which point we have to remove the path from the reached set.
            // However, we also cannot let it stay in the reached set, because
            // then the states on the path might cover other, actually feasible,
            // paths, so this would prevent other real counterexamples to be found (unsound!).

            // So there are two options: either let them stay in the reached set
            // and mark analysis as unsound, or let them stay in the reached set
            // and prevent them from covering new paths.

            if (removeInfeasibleErrors) {
              sound &= handleInfeasibleCounterexample(reached, elementsOnErrorPath);

            } else if (sound) {
              logger.log(Level.WARNING, "Infeasible counterexample found, but could not remove it from the ARG. Therefore, we cannot prove safety.");
              sound = false;
            }

            sound &= removeErrorState(reached, errorState);

          } else {
            Path path = ARGUtils.getOnePathTo(errorState);
            throw new RefinementFailedException(Reason.InfeasibleCounterexample, path);
          }
        }
      } finally {
        checkTime.stop();
      }
    }
    return sound;
  }

  private boolean handleInfeasibleCounterexample(ReachedSet reached, Set<ARGState> elementsOnErrorPath) {
    boolean sound = true;

    // So we let the states stay in the reached set, and just prevent
    // them from covering other elements by removing all existing
    // coverage relations (and re-adding the covered elements)
    // and preventing new ones via ARGState#setNotCovering().

    Collection<ARGState> coveredByErrorPath = new ArrayList<ARGState>();

    for (ARGState errorPathState : elementsOnErrorPath) {
      // schedule for coverage removal
      coveredByErrorPath.addAll(errorPathState.getCoveredByThis());

      // prevent future coverage
      errorPathState.setNotCovering();
    }

    for (ARGState coveredElement : coveredByErrorPath) {
      if (isTransitiveChildOf(coveredElement, coveredElement.getCoveringState())) {
        // This element is covered by one of it's (transitive) parents
        // so this is a loop.
        // Don't add the element, because otherwise the loop would
        // get unrolled endlessly.
        logger.log(Level.WARNING, "Infeasible counterexample found, but could not remove it from the ARG due to loops in the counterexample path. Therefore, we cannot prove safety.");
        sound = false;
        continue;
      }

      for (ARGState parentOfCovered : coveredElement.getParents()) {
        if (elementsOnErrorPath.contains(parentOfCovered)) {
          // this should never happen, but handle anyway
          // we may not re-add this parent, because otherwise
          // the error-path will be re-discovered again
          // but not adding the parent is unsound
          logger.log(Level.WARNING, "Infeasible counterexample found, but could not remove it from the ARG. Therefore, we cannot prove safety.");
          sound = false;

        } else {
          // let covered element be re-discovered
          reached.reAddToWaitlist(parentOfCovered);
        }
      }
      assert !reached.contains(coveredElement) : "covered element in reached set";
      coveredElement.removeFromART();
    }
    return sound;
  }

  private boolean isTransitiveChildOf(ARGState potentialChild, ARGState potentialParent) {

    Set<ARGState> seen = new HashSet<ARGState>();
    Deque<ARGState> waitlist = new ArrayDeque<ARGState>(); // use BFS

    waitlist.addAll(potentialChild.getParents());
    while (!waitlist.isEmpty()) {
      ARGState current = waitlist.pollFirst();

      for (ARGState currentParent : current.getParents()) {
        if (currentParent.equals(potentialParent)) {
          return true;
        }

        if (!seen.add(currentParent)) {
          waitlist.addLast(currentParent);
        }
      }
    }

    return false;
  }

  private boolean removeErrorState(ReachedSet reached, ARGState errorState) {
    boolean sound = true;

    // remove re-added parent of errorState to prevent computing
    // the same error element over and over
    Set<ARGState> parents = errorState.getParents();
    assert parents.size() == 1 : "error element that was merged";

    ARGState parent = Iterables.getOnlyElement(parents);

    if (parent.getChildren().size() > 1) {
      // The error element has a sibling, so the parent and the sibling
      // should stay in the reached set, but then the error element
      // would get re-discovered.
      // Currently just handle this by removing them anyway,
      // as this probably doesn't occur.
      sound = false;
    }

    // this includes the errorState and its siblings
    List<ARGState> siblings = copyOf(parent.getChildren());
    for (ARGState toRemove : siblings) {

      assert toRemove.getChildren().isEmpty();

      // element toRemove may cover some elements, but hopefully only siblings which we remove anyway
      assert siblings.containsAll(toRemove.getCoveredByThis());

      reached.remove(toRemove);
      toRemove.removeFromART();
    }

    reached.remove(parent);
    parent.removeFromART();

    assert errorState.isDestroyed() : "errorState is not the child of its parent";
    assert !reached.contains(errorState) : "reached.remove() didn't work";
    return sound;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (algorithm instanceof StatisticsProvider) {
      ((StatisticsProvider)algorithm).collectStatistics(pStatsCollection);
    }
    pStatsCollection.add(this);
  }

  @Override
  public void printStatistics(PrintStream out, Result pResult,
      ReachedSet pReached) {

    out.println("Number of counterexample checks:    " + checkTime.getNumberOfIntervals());
    if (checkTime.getNumberOfIntervals() > 0) {
      out.println("Number of infeasible paths:         " + numberOfInfeasiblePaths + " (" + toPercent(numberOfInfeasiblePaths, checkTime.getNumberOfIntervals()) +")" );
      out.println("Time for counterexample checks:     " + checkTime);
      if (checker instanceof Statistics) {
        ((Statistics)checker).printStatistics(out, pResult, pReached);
      }
    }
  }

  private static String toPercent(double val, double full) {
    return String.format("%1.0f", val/full*100) + "%";
  }

  @Override
  public String getName() {
    return "Counterexample-Check Algorithm";
  }
}
