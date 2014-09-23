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
package org.sosy_lab.cpachecker.cpa.apron.refiner;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.defaults.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.core.defaults.VariableTrackingPrecisionOptions;
import org.sosy_lab.cpachecker.cpa.apron.ApronCPA;
import org.sosy_lab.cpachecker.cpa.apron.ApronState;
import org.sosy_lab.cpachecker.cpa.apron.ApronTransferRelation;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.MutableARGPath;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState.MemoryLocation;
import org.sosy_lab.cpachecker.cpa.value.refiner.utils.AssumptionUseDefinitionCollector;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.VariableClassification;

import apron.ApronException;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

public class ApronAnalysisFeasabilityChecker {

  private final ApronTransferRelation transfer;

  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final MutableARGPath checkedPath;
  private final MutableARGPath foundPath;

  public ApronAnalysisFeasabilityChecker(CFA cfa, LogManager log, ShutdownNotifier pShutdownNotifier, MutableARGPath path, ApronCPA cpa) throws InvalidConfigurationException, CPAException, InterruptedException, ApronException {
    logger = log;
    shutdownNotifier = pShutdownNotifier;

    // use the normal configuration for creating the transferrelation
    transfer  = new ApronTransferRelation(logger, cfa);
    checkedPath = path;

    // use a new configuration which only has the default values for the precision
    // we do not want any special options to be set there
    foundPath = getInfeasiblePrefix(new VariableTrackingPrecision(VariableTrackingPrecisionOptions.getDefaultOptions(), Optional.<VariableClassification>absent()),
                                    new ApronState(logger, cpa.getManager()));
  }

  /**
   * This method checks if the given path is feasible, when not tracking the given set of variables.
   *
   * @param path the path to check
   * @return true, if the path is feasible, else false
   * @throws CPAException
   * @throws InterruptedException
   */
  public boolean isFeasible() {
      return checkedPath.size() == foundPath.size();
  }

  public Multimap<CFANode, MemoryLocation> getPrecisionIncrement(VariableTrackingPrecision precision) {
    if (isFeasible()) {
      return ArrayListMultimap.<CFANode, ValueAnalysisState.MemoryLocation>create();
    } else {
      Set<MemoryLocation> varNames = new HashSet<>();
      LinkedList<CFAEdge> edgesList = new LinkedList<>(foundPath.asEdgesList());

      // search for new trackable variables until we find some
      do {
        varNames.addAll(FluentIterable.from(new AssumptionUseDefinitionCollector().obtainUseDefInformation(edgesList)).transform(new Function<String, MemoryLocation>() {
          @Override
          public MemoryLocation apply(String pInput) {
            return MemoryLocation.valueOf(pInput);
          }}).toSet());
        edgesList.removeLast();
        while (!edgesList.isEmpty() && !(edgesList.getLast() instanceof AssumeEdge)) {
          edgesList.removeLast();
        }
      } while (varNames.isEmpty() && !edgesList.isEmpty());

      Multimap<CFANode, MemoryLocation> increment = ArrayListMultimap.<CFANode, ValueAnalysisState.MemoryLocation>create();

      for (MemoryLocation loc : varNames) {
        increment.put(new CFANode("BOGUS-NODE"), loc);
      }

      return increment;
    }
  }

  /**
   * This method obtains the prefix of the path, that is infeasible by itself. If the path is feasible, the whole path
   * is returned
   *
   * @param path the path to check
   * @param pPrecision the precision to use
   * @param pInitial the initial state
   * @return the prefix of the path that is feasible by itself
   * @throws CPAException
   * @throws InterruptedException
   */
  private MutableARGPath getInfeasiblePrefix(final VariableTrackingPrecision pPrecision, final ApronState pInitial)
      throws CPAException, InterruptedException {
    try {
      Collection<ApronState> next = Lists.newArrayList(pInitial);

      MutableARGPath prefix = new MutableARGPath();

      Collection<ApronState> successors = new HashSet<>();

      for (Pair<ARGState, CFAEdge> pathElement : checkedPath) {
        successors.clear();
        for (ApronState st : next) {
          successors.addAll(transfer.getAbstractSuccessorsForEdge(
              st,
              pPrecision,
              pathElement.getSecond()));

          // computing the feasibility check takes sometimes much time with ocatongs
          // so if the shutdownNotifer says that we should shutdown, we cannot
          // make any assumptions about the path reachibility and say that it's
          // reachable (over-approximation)
          if (shutdownNotifier.shouldShutdown()) {
            logger.log(Level.INFO, "Cancelling feasibility check with octagon Analysis, timelimit reached");
            return checkedPath;
          }
        }

        prefix.addLast(pathElement);

        // no successors => path is infeasible
        if (successors.isEmpty()) {
          break;
        }

        // get matching successor state and apply precision
        next.clear();
        next.addAll(successors);
      }
      return prefix;
    } catch (CPATransferException e) {
      throw new CPAException("Computation of successor failed for checking path: " + e.getMessage(), e);
    }
  }

}
