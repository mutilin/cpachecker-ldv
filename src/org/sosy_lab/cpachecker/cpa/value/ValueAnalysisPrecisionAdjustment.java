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
package org.sosy_lab.cpachecker.cpa.value;

import static com.google.common.base.Preconditions.checkState;

import java.io.PrintStream;
import java.util.Collection;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.defaults.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.conditions.path.AssignmentsInPathCondition.UniqueAssignmentsInPathConditionState;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.LiveVariables;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

@Options(prefix="cpa.value.blk")
public class ValueAnalysisPrecisionAdjustment implements PrecisionAdjustment, StatisticsProvider {

  @Option(secure=true, description="restrict abstractions to loop heads")
  private boolean alwaysAtLoops = false;

  @Option(secure=true, description="restrict abstractions to function calls/returns")
  private boolean alwaysAtFunctions = false;

  @Option(secure=true, description="restrict abstractions to assume edges")
  private boolean alwaysAtAssumes = false;

  @Option(secure=true, description="restrict abstractions to join points")
  private boolean alwaysAtJoins = false;

  @Option(secure=true, description="restrict liveness abstractions to nodes with more than one entering and/or leaving edge")
  private boolean onlyAtNonLinearCFA = false;

  private final ImmutableSet<CFANode> loopHeads;

  // statistics
  final StatCounter abstractions    = new StatCounter("Number of abstraction computations");
  final StatTimer totalLiveness     = new StatTimer("Total time for liveness abstraction");
  final StatTimer totalAbstraction  = new StatTimer("Total time for abstraction computation");
  final StatTimer totalEnforcePath  = new StatTimer("Total time for path thresholds");

  private final Statistics statistics;

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(statistics);
  }

  private final Optional<LiveVariables> liveVariables;

  public ValueAnalysisPrecisionAdjustment(Configuration pConfig, CFA pCfa)
      throws InvalidConfigurationException {

    pConfig.inject(this);

    if (alwaysAtLoops && pCfa.getAllLoopHeads().isPresent()) {
      loopHeads = pCfa.getAllLoopHeads().get();
    } else {
      loopHeads = null;
    }

    statistics = new Statistics() {
      @Override
      public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {

        StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(pOut);
        writer.put(abstractions);
        writer.put(totalLiveness);
        writer.put(totalAbstraction);
        writer.put(totalEnforcePath);
      }

      @Override
      public String getName() {
        return ValueAnalysisPrecisionAdjustment.this.getClass().getSimpleName();
      }
    };

    liveVariables = pCfa.getLiveVariables();
  }

  @Override
  public PrecisionAdjustmentResult prec(AbstractState pState, Precision pPrecision, UnmodifiableReachedSet pStates, AbstractState fullState)
      throws CPAException, InterruptedException {

    return prec((ValueAnalysisState)pState,
        (VariableTrackingPrecision)pPrecision,
        AbstractStates.extractStateByType(fullState, LocationState.class),
        AbstractStates.extractStateByType(fullState, UniqueAssignmentsInPathConditionState.class));
  }

  private PrecisionAdjustmentResult prec(ValueAnalysisState pState,
      VariableTrackingPrecision pPrecision,
      LocationState location,
      UniqueAssignmentsInPathConditionState assignments) {
    ValueAnalysisState resultState = ValueAnalysisState.copyOf(pState);

    if(liveVariables.isPresent()) {
      totalLiveness.start();
      enforceLiveness(pState, location, resultState);
      totalLiveness.stop();
    }

    // compute the abstraction based on the value-analysis precision, unless assignment information is available
    // then, this is dealt with during enforcement of the path thresholds, see below
    if (assignments == null) {
      totalAbstraction.start();
      enforcePrecision(resultState, location, pPrecision);
      totalAbstraction.stop();
    }

    // compute the abstraction for assignment thresholds
    else {
      totalEnforcePath.start();
      enforcePathThreshold(resultState, pPrecision, assignments, location.getLocationNode());
      totalEnforcePath.stop();
    }

    return PrecisionAdjustmentResult.create(resultState, pPrecision, Action.CONTINUE);
  }

  private void enforceLiveness(ValueAnalysisState pState, LocationState location, ValueAnalysisState resultState) {
    CFANode actNode = location.getLocationNode();

    boolean hasMoreThanOneEnteringLeavingEdge = actNode.getNumEnteringEdges() > 1 || actNode.getNumLeavingEdges() > 1;

    if (!onlyAtNonLinearCFA || hasMoreThanOneEnteringLeavingEdge) {
      boolean onlyBlankEdgesEntering = true;
      for (int i = 0; i < actNode.getNumEnteringEdges() && onlyBlankEdgesEntering; i++) {
        onlyBlankEdgesEntering = location.getLocationNode().getEnteringEdge(i) instanceof BlankEdge;
      }

      // when there are only blank edges that lead to this state, then we can
      // skip the abstraction, after a blank edge there cannot be a variable
      // less live
      if (!onlyBlankEdgesEntering) {
        for (MemoryLocation variable : pState.getTrackedMemoryLocations()) {
          if (!liveVariables.get().isVariableLive(variable.getAsSimpleString(), location.getLocationNode())) {
            resultState.forget(variable);
          }
        }
      }
    }
  }

  /**
   * This method performs an abstraction computation on the current value-analysis state.
   *
   * @param location the current location
   * @param state the current state
   * @param precision the current precision
   */
  private void enforcePrecision(ValueAnalysisState state, LocationState location, VariableTrackingPrecision precision) {
    if (abstractAtEachLocation()
        || abstractAtAssumes(location)
        || abstractAtJoins(location)
        || abstractAtFunction(location)
        || abstractAtLoopHead(location)) {

      for (MemoryLocation memoryLocation : state.getTrackedMemoryLocations()) {
        if (!precision.isTracking(memoryLocation, state.getTypeForMemoryLocation(memoryLocation), location.getLocationNode())) {
          state.forget(memoryLocation);
        }
      }

      abstractions.inc();
    }
  }

  /**
   * This method determines whether or not to abstract at each location.
   *
   * @return true, if an abstraction should be computed at each location, else false
   */
  private boolean abstractAtEachLocation() {
    return !alwaysAtAssumes && !alwaysAtJoins && !alwaysAtFunctions && !alwaysAtLoops;
  }

  /**
   * This method determines whether or not the given location is a branching,
   * and whether or not an abstraction shall be computed or not.
   *
   * @param location the current location
   * @return true, if at the current location an abstraction shall be computed, else false
   */
  private boolean abstractAtAssumes(LocationState location) {
    return alwaysAtAssumes && location.getLocationNode().getEnteringEdge(0).getEdgeType() == CFAEdgeType.AssumeEdge;
  }

  /**
   * This method determines whether or not to abstract before a join point.
   *
   * @param location the current location
   * @return true, if at the current location an abstraction shall be computed, else false
   */
  private boolean abstractAtJoins(LocationState location) {
    return alwaysAtJoins && location.getLocationNode().getNumEnteringEdges() > 1;
  }

  /**
   * This method determines whether or not the given location is a function entry or exit,
   * and whether or not an abstraction shall be computed or not.
   *
   * @param location the current location
   * @return true, if at the current location an abstraction shall be computed, else false
   */
  private boolean abstractAtFunction(LocationState location) {
    return alwaysAtFunctions && (location.getLocationNode() instanceof FunctionEntryNode
        || location.getLocationNode().getEnteringSummaryEdge() != null);
  }

  /**
   * This method determines whether or not the given location is a loop head,
   * and whether or not an abstraction shall be computed or not.
   *
   * @param location the current location
   * @return true, if at the current location an abstraction shall be computed, else false
   */
  private boolean abstractAtLoopHead(LocationState location) {
    checkState(!alwaysAtLoops || loopHeads != null);
    return alwaysAtLoops && loopHeads.contains(location.getLocationNode());
  }

  /**
   * This method abstracts variables that exceed the threshold of assignments along the current path.
   *
   * @param state the state to abstract
   * @param precision the current precision
   * @param assignments the assignment information
   */
  private void enforcePathThreshold(ValueAnalysisState state,
      VariableTrackingPrecision precision,
      UniqueAssignmentsInPathConditionState assignments, CFANode location) {

    // forget the value for all variables that exceed their threshold
    for (MemoryLocation memoryLocation: state.getTrackedMemoryLocations()) {

      // if memory location is being tracked, check against hard threshold
      if (precision.isTracking(memoryLocation, state.getTypeForMemoryLocation(memoryLocation), location)) {
        assignments.updateAssignmentInformation(memoryLocation, state.getValueFor(memoryLocation));

        if (assignments.exceedsHardThreshold(memoryLocation)) {
          state.forget(memoryLocation);
        }

      } else {
        // otherwise, check against soft threshold, including the pending assignment
        if (assignments.wouldExceedSoftThreshold(state, memoryLocation)) {
          state.forget(memoryLocation);
        } else {
          assignments.updateAssignmentInformation(memoryLocation, state.getValueFor(memoryLocation));
        }
      }
    }
  }
}
