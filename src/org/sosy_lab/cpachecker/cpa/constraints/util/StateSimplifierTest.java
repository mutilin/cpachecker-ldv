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
package org.sosy_lab.cpachecker.cpa.constraints.util;

import org.junit.Assert;
import org.junit.Test;
import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.common.log.TestLogManager;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cpa.constraints.ConstraintsState;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.Constraint;
import org.sosy_lab.cpachecker.cpa.constraints.util.StateSimplifier;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.symbolic.type.SymbolicExpression;
import org.sosy_lab.cpachecker.cpa.value.symbolic.type.SymbolicValueFactory;
import org.sosy_lab.cpachecker.cpa.value.type.NumericValue;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * Unit tests for {@link org.sosy_lab.cpachecker.cpa.constraints.util.StateSimplifier}.
 *
 * There exist four symbolic identifiers that are divided into two groups by their dependencies:
 * Constraints exist so that the first identifier is dependent on the second one and so that the
 * third is dependent on the fourth. No dependencies exist between these two groups.
 */
public class StateSimplifierTest {

  private final LogManagerWithoutDuplicates logger =
      new LogManagerWithoutDuplicates(TestLogManager.getInstance());

  private final MachineModel machineModel = MachineModel.LINUX32;
  private final StateSimplifier simplifier = new StateSimplifier(machineModel, logger);

  private final SymbolicValueFactory factory = SymbolicValueFactory.getInstance();

  private final Type defaultNumericType = CNumericTypes.INT;

  private final SymbolicExpression number =
      factory.asConstant(new NumericValue(5), defaultNumericType);

  private final SymbolicExpression group1Id1 =
      factory.asConstant(factory.newIdentifier(), defaultNumericType);
  private final SymbolicExpression group1Id2 =
      factory.asConstant(factory.newIdentifier(), defaultNumericType);
  private final SymbolicExpression group2Id1 =
      factory.asConstant(factory.newIdentifier(), defaultNumericType);
  private final SymbolicExpression group2Id2 =
      factory.asConstant(factory.newIdentifier(), defaultNumericType);

  private final Constraint group1Constraint1 = (Constraint)
      factory.greaterThanOrEqual(group1Id1, group1Id2, defaultNumericType, defaultNumericType);
  private final Constraint group1Constraint2 = (Constraint)
      factory.lessThanOrEqual(group1Id2, group1Id1, defaultNumericType, defaultNumericType);

  private final Constraint group2Constraint1 = (Constraint)
      factory.lessThan(group2Id1, number, defaultNumericType, defaultNumericType);
  private final Constraint group2Constraint2 = (Constraint)
      factory.lessThan(group2Id2, group2Id1, defaultNumericType, defaultNumericType);


  private final MemoryLocation group1MemLoc1 = MemoryLocation.valueOf("a", 0);
  private final MemoryLocation group1MemLoc2 = MemoryLocation.valueOf("b", 0);
  private final MemoryLocation group2MemLoc1 = MemoryLocation.valueOf("c", 0);
  private final MemoryLocation group2MemLoc2 = MemoryLocation.valueOf("d", 0);


  @Test
  public void testRemoveOutdatedConstraints_allConstraintsOutdated() {
    final ValueAnalysisState initialValueState = new ValueAnalysisState();

    ConstraintsState constraintsState = getSampleConstraints();

    ConstraintsState newState = simplifier.removeOutdatedConstraints(constraintsState, initialValueState);

    Assert.assertTrue(newState.isEmpty());
  }

  @Test
  public void testRemoveOutdatedConstraints_noConstraintsOutdated() {
    final ValueAnalysisState valueState = getCompleteValueState();

    valueState.forget(group1MemLoc1);
    valueState.forget(group2MemLoc1);

    ConstraintsState constraintsState = getSampleConstraints();

    ConstraintsState newState = simplifier.removeOutdatedConstraints(constraintsState, valueState);

    Assert.assertTrue(allConstraintsExist(newState));
  }

  private boolean allConstraintsExist(ConstraintsState pNewState) {
    boolean allExist = true;

    allExist &= pNewState.contains(group1Constraint1);
    allExist &= pNewState.contains(group1Constraint2);
    allExist &= pNewState.contains(group2Constraint1);
    allExist &= pNewState.contains(group2Constraint2);

    return allExist;
  }

  @Test
  public void testRemoveOutdatedConstraints_group1ConstraintsOutdated() {
    final ValueAnalysisState valueState = getCompleteValueState();

    valueState.forget(group1MemLoc1);
    valueState.forget(group1MemLoc2);
    valueState.forget(group2MemLoc2);
    ConstraintsState constraintsState = getSampleConstraints();

    ConstraintsState newState = simplifier.removeOutdatedConstraints(constraintsState, valueState);

    Assert.assertTrue(group2ConstraintsExist(newState));
  }

  private boolean group2ConstraintsExist(ConstraintsState pNewState) {
    boolean correct = pNewState.size() == 2;

    correct &= pNewState.contains(group2Constraint1);
    correct &= pNewState.contains(group2Constraint2);

    return correct;
  }


  private ConstraintsState getSampleConstraints() {
    ConstraintsState state = new ConstraintsState();

    state.add(group1Constraint1);
    state.add(group1Constraint2);
    state.add(group2Constraint1);
    state.add(group2Constraint2);

    return state;
  }

  private ValueAnalysisState getCompleteValueState() {
    ValueAnalysisState state = new ValueAnalysisState();

    state.assignConstant(group1MemLoc1, group1Id1, defaultNumericType);
    state.assignConstant(group1MemLoc2, group1Id2, defaultNumericType);
    state.assignConstant(group2MemLoc1, group2Id1, defaultNumericType);
    state.assignConstant(group2MemLoc2, group2Id2, defaultNumericType);

    return state;
  }

}
