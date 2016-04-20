/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.InfeasibleCounterexampleException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class ExceptionHandlingAlgorithm implements Algorithm {

  @Options
  private static class ExceptionHandlingOptions {
    @Option(secure=true, name="counterexample.removeInfeasibleErrors", description="If continueAfterInfeasibleError is true, remove the infeasible counterexample before continuing."
        + "Setting this to false may prevent a lot of similar infeasible counterexamples to get discovered, but is unsound")
    private boolean removeInfeasibleErrors = false;

    @Option(
      secure = true,
      name = "counterexample.continueAfterInfeasibleError",
      description =
          "continue analysis after an counterexample was found that was denied by the second check"
    )
    private boolean continueAfterInfeasibleError = true;

    @Option(
      secure = true,
      name = "cegar.continueAfterFailedRefinement",
      description = "continue analysis after a failed refinement (e.g. due to interpolation) other paths"
          + " may still contain errors that could be found"
    )
    private boolean continueAfterFailedRefinement = false;

    private ExceptionHandlingOptions(Configuration pConfig) throws InvalidConfigurationException {
      pConfig.inject(this);
    }
  }

  private final Algorithm algorithm;
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final ARGCPA cpa;
  private final ExceptionHandlingOptions options;

  private ExceptionHandlingAlgorithm(Algorithm pAlgorithm, ConfigurableProgramAnalysis pCpa,
      ExceptionHandlingOptions pOptions, LogManager pLogger, ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException {
    options = pOptions;
    algorithm = pAlgorithm;
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;

    if (!(pCpa instanceof ARGCPA)) {
      throw new InvalidConfigurationException("ARG CPA needed for counterexample check");
    }
    cpa = (ARGCPA)pCpa;
  }

  public static Algorithm create(
      Configuration pConfig,
      Algorithm pAlgorithm,
      ConfigurableProgramAnalysis pCpa,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      boolean pCheckCounterexamples,
      boolean pUseCEGAR)
      throws InvalidConfigurationException {

    ExceptionHandlingOptions options = new ExceptionHandlingOptions(pConfig);

    if ((options.continueAfterInfeasibleError && pCheckCounterexamples)
        || (options.continueAfterFailedRefinement && pUseCEGAR)) {
      return new ExceptionHandlingAlgorithm(pAlgorithm, pCpa, options, pLogger, pShutdownNotifier);
    }

    return pAlgorithm;
  }

  @Override
  public AlgorithmStatus run(ReachedSet reached)
      throws CPAException, InterruptedException, CPAEnabledAnalysisPropertyViolationException {
    AlgorithmStatus status = AlgorithmStatus.SOUND_AND_PRECISE;

    while (reached.hasWaitingState()) {
      // exit loop when we don't have time left
      if (shutdownNotifier.shouldShutdown()) {
        break;
      }

      try {
        status = status.update(algorithm.run(reached));

        // no exception occurred so we are finished
        break;


        // if we find an infeasible counterexample we handle it depending on the
        // configuration options. If specified we just ignore the path later on
        // mark the analysis as unsound and continue searching for errors on other
        // paths through the program
      } catch (InfeasibleCounterexampleException e) {
        // we don't want to continue, so no handling is necessary
        if (!options.continueAfterInfeasibleError) {
          throw e;
        }
        status = handleExceptionWithErrorPath(reached, status, e.getErrorPath().getLastState());

        // Handle failed refinements if specified by the configuration
        // we can do exactly the same as for infeasible counterexamples
      } catch (RefinementFailedException e) {
        // we don't want to continue, so no handling is necessary
        if (!options.continueAfterFailedRefinement) {
          throw e;
        }

        ARGState lastState;
        if (e.getErrorPath() != null) {
          lastState = e.getErrorPath().getLastState();
        } else {
          lastState = (ARGState) reached.getLastState();
        }

        status = handleExceptionWithErrorPath(reached, status, lastState);
      }
    }
    return status;
  }

  private AlgorithmStatus handleExceptionWithErrorPath(ReachedSet reached, AlgorithmStatus status,
      ARGState lastState) {

    // we need to have an (unreachable) error state on the last position
    assert (lastState != null && lastState.isTarget());

    // This counterexample is infeasible, so usually we would remove it
    // from the reached set. This is not possible, because the
    // counterexample of course contains the root state and we don't
    // know up to which point we have to remove the path from the reached set.
    // However, we also cannot let it stay in the reached set, because
    // then the states on the path might cover other, actually feasible,
    // paths, so this would prevent other real counterexamples to be found (unsound!).

    // So there are two options: either let them stay in the reached set
    // and mark analysis as unsound, or let them stay in the reached set
    // and prevent them from covering new paths.

    boolean sound = status.isSound();
    if (options.removeInfeasibleErrors) {
      // bit-wise and to have handleInfeasibleCounterexample() definitely executed
      sound &= handleInfeasibleCounterexample(reached, ARGUtils.getAllStatesOnPathsTo(lastState));
    } else if (sound) {
      logger.log(Level.WARNING, "Infeasible counterexample found, but could not remove it from the ARG. Therefore, we cannot prove safety.");
      sound = false;
    } else {
      logger.log(Level.INFO, "Another infeasible counterexample found which could not be removed from the ARG.");
    }

    // bit-wise and to have removeErrorState() definitely executed
    sound &= removeErrorState(reached, lastState);
    assert ARGUtils.checkARG(reached);

    status = status.withSound(sound);
    return status;
  }

  private boolean handleInfeasibleCounterexample(ReachedSet reached, Set<ARGState> statesOnErrorPath) {
    boolean sound = true;

    // So we let the states stay in the reached set, and just prevent
    // them from covering other states by removing all existing
    // coverage relations (and re-adding the covered states)
    // and preventing new ones via ARGState#setNotCovering().

    Collection<ARGState> coveredByErrorPath = new ArrayList<>();

    for (ARGState errorPathState : statesOnErrorPath) {
      // schedule for coverage removal
      coveredByErrorPath.addAll(errorPathState.getCoveredByThis());

      // prevent future coverage
      errorPathState.setNotCovering();
    }

    for (ARGState coveredState : coveredByErrorPath) {
      if (isTransitiveChildOf(coveredState, coveredState.getCoveringState())) {
        // This state is covered by one of it's (transitive) parents
        // so this is a loop.
        // Don't add the state, because otherwise the loop would
        // get unrolled endlessly.
        logger.log(Level.WARNING, "Infeasible counterexample found, but could not remove it from the ARG due to loops in the counterexample path. Therefore, we cannot prove safety.");
        sound = false;
        continue;
      }

      for (ARGState parentOfCovered : coveredState.getParents()) {
        if (statesOnErrorPath.contains(parentOfCovered)) {
          // this should never happen, but handle anyway
          // we may not re-add this parent, because otherwise
          // the error-path will be re-discovered again
          // but not adding the parent is unsound
          logger.log(Level.WARNING, "Infeasible counterexample found, but could not remove it from the ARG. Therefore, we cannot prove safety.");
          sound = false;

        } else {
          // let covered state be re-discovered
          reached.reAddToWaitlist(parentOfCovered);
        }
      }
      assert !reached.contains(coveredState) : "covered state in reached set";
      coveredState.removeFromARG();
    }
    return sound;
  }

  private boolean isTransitiveChildOf(ARGState potentialChild, ARGState potentialParent) {

    Set<ARGState> seen = new HashSet<>();
    Deque<ARGState> waitlist = new ArrayDeque<>(); // use BFS

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

    assert errorState.getChildren().isEmpty();
    assert errorState.getCoveredByThis().isEmpty();

    // remove re-added parent of errorState to prevent computing
    // the same error state over and over
    Collection<ARGState> parents = errorState.getParents();
    assert parents.size() == 1 : "error state that was merged";

    ARGState parent = Iterables.getOnlyElement(parents);

    if (parent.getChildren().size() > 1 || parent.getCoveredByThis().isEmpty()) {
      // The error state has a sibling, so the parent and the sibling
      // should stay in the reached set, but then the error state
      // would get re-discovered.
      // Similarly for covered states.
      // Currently just handle this by removing them anyway,
      // as this probably doesn't occur.
      sound = false;
    }

    // this includes the errorState and its siblings
    List<ARGState> siblings = copyOf(parent.getChildren());
    for (ARGState toRemove : siblings) {

      assert toRemove.getChildren().isEmpty();

      // state toRemove may cover some states, but hopefully only siblings which we remove anyway
      assert siblings.containsAll(toRemove.getCoveredByThis());

      reached.remove(toRemove);
      toRemove.removeFromARG();
    }

    List<ARGState> coveredByParent = copyOf(parent.getCoveredByThis());
    for (ARGState covered : coveredByParent) {
      assert covered.getChildren().isEmpty();
      assert covered.getCoveredByThis().isEmpty();

      // covered is not in reached
      covered.removeFromARG();
    }

    cpa.clearCounterexamples(ImmutableSet.of(errorState));
    reached.remove(parent);
    parent.removeFromARG();

    assert errorState.isDestroyed() : "errorState is not the child of its parent";
    assert !reached.contains(errorState) : "reached.remove() didn't work";
    return sound;
  }
}
