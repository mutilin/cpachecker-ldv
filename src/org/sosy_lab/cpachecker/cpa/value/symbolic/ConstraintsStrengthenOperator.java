/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.value.symbolic;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cpa.constraints.ConstraintsCPA;
import org.sosy_lab.cpachecker.cpa.constraints.ConstraintsState;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.IdentifierAssignment;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.symbolic.type.SymbolicIdentifier;
import org.sosy_lab.cpachecker.cpa.value.type.Value;

import com.google.common.base.Optional;

/**
 * Strengthener for ValueAnalysis with {@link ConstraintsCPA}.
 */
public class ConstraintsStrengthenOperator {

  private final MachineModel machineModel;
  private final LogManagerWithoutDuplicates logger;
  
  private ConstraintsStrengthenOperator(
      MachineModel pMachineModel, LogManagerWithoutDuplicates pLogger) {
    logger = pLogger;
    machineModel = pMachineModel;
  }

  public static ConstraintsStrengthenOperator getInstance(
      MachineModel pMachineModel, LogManagerWithoutDuplicates pLogger) {

    return new ConstraintsStrengthenOperator(pMachineModel, pLogger);
  }

  /**
   * Strengthen the given {@link ValueAnalysisState} with the given {@link ConstraintsState}.
   *
   * <p>The returned <code>Collection</code> contains all reachable states after strengthening.
   * A returned empty <code>Collection</code> represents 'bottom', a returned <code>null</code>
   * represents that no changes were made to the given <code>ValueAnalysisState</code>.</p>
   *
   *
   * @param pStateToStrengthen the state to strengthen
   * @param pStrengtheningState the state to strengthen the first state with
   * @return <code>null</code> if no changes were made to the given <code>ValueAnalysisState</code>,
   *    an empty <code>Collection</code>, if the resulting state is not reachable and
   *    a <code>Collection</code> containing all reachable states, otherwise
   */
  public Collection<ValueAnalysisState> strengthen(
      ValueAnalysisState pStateToStrengthen, ConstraintsState pStrengtheningState) {

    Optional<ValueAnalysisState> newElement =
        evaluateAssignment(pStrengtheningState.getDefiniteAssignment(), pStateToStrengthen);

    if (newElement.isPresent()) {
      return Collections.singleton(newElement.get());
    } else {
      return null;
    }
  }

  private Optional<ValueAnalysisState> evaluateAssignment(
      IdentifierAssignment pAssignment, ValueAnalysisState pValueState) {

    ValueAnalysisState newElement = ValueAnalysisState.copyOf(pValueState);
    boolean somethingChanged = false;

    for (Map.Entry<? extends SymbolicIdentifier, Value> onlyValidAssignment : pAssignment.entrySet()) {
      final SymbolicIdentifier identifierToReplace = onlyValidAssignment.getKey();
      final Value newIdentifierValue = onlyValidAssignment.getValue();

      if (!newElement.hasKnownValue(identifierToReplace)) {
        somethingChanged = true;
      }

      newElement.assignConstant(identifierToReplace, newIdentifierValue);
    }

    if (somethingChanged) {
      return Optional.of(newElement);
    } else {
      return Optional.absent();
    }
  }
}
