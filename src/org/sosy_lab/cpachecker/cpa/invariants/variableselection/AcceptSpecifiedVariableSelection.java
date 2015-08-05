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
package org.sosy_lab.cpachecker.cpa.invariants.variableselection;

import java.util.Set;

import javax.annotation.Nullable;

import org.sosy_lab.cpachecker.cpa.invariants.formula.BooleanFormula;
import org.sosy_lab.cpachecker.cpa.invariants.formula.CollectVarsVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.NumeralFormula;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;


public class AcceptSpecifiedVariableSelection<ConstantType> implements VariableSelection<ConstantType> {

  private final CollectVarsVisitor<ConstantType> collectVarsVisitor = new CollectVarsVisitor<>();

  private final VariableSelectionJoiner variableSelectionJoiner = new VariableSelectionJoiner();

  private final ImmutableSet<String> specifiedVariables;

  public AcceptSpecifiedVariableSelection(Iterable<? extends String> pIterable) {
    this.specifiedVariables = ImmutableSet.copyOf((pIterable));
  }

  @Override
  public boolean contains(final String pVariableName) {

    return FluentIterable.from(specifiedVariables).anyMatch(new Predicate<String>() {

      @Override
      public boolean apply(@Nullable String pArg0) {
        if (pVariableName.equals(pArg0)) {
          return true;
        }
        if (pArg0.endsWith("[*]")) {
          int arraySubscriptIndex = pVariableName.indexOf('[');
          if (arraySubscriptIndex >= 0) {
            String containedArray = pArg0.substring(0, pArg0.indexOf('['));
            String array = pVariableName.substring(arraySubscriptIndex);
            return containedArray.equals(array);
          }
        }
        return false;
      }

    });
  }

  @Override
  public VariableSelection<ConstantType> acceptAssumption(BooleanFormula<ConstantType> pAssumption) {
    Set<String> involvedVariables = pAssumption.accept(this.collectVarsVisitor);
    for (String involvedVariable : involvedVariables) {
      if (contains(involvedVariable)) {
        /*
         * Extend the set of specified variables transitively.
         * This might be too precise in general, because after they are reassigned,
         * they might no longer be relevant. Perhaps a second, "temporary", set
         * might help... however, this would require the algorithm using this
         * to not only not record assignments to variables not selected, but also
         * actively delete information about previously selected variables.
         */
        return join(involvedVariables);
      }
    }
    return null;
  }

  @Override
  public VariableSelection<ConstantType> acceptAssignment(String pVariableName, NumeralFormula<ConstantType> pAssumption) {
    if (contains(pVariableName)) {
      /*
       * Extend the set of specified variables transitively.
       * See acceptAssumptions for thoughts about this.
       */
      return join(pAssumption.accept(this.collectVarsVisitor));
    }
    return null;
  }

  @Override
  public String toString() {
    return this.specifiedVariables.toString();
  }

  @Override
  public VariableSelection<ConstantType> join(VariableSelection<ConstantType> pOther) {
    return pOther.acceptVisitor(variableSelectionJoiner);
  }

  @Override
  public <T> T acceptVisitor(VariableSelectionVisitor<ConstantType, T> pVisitor) {
    return pVisitor.visit(this);
  }

  private class VariableSelectionJoiner implements VariableSelectionVisitor<ConstantType, VariableSelection<ConstantType>> {

    @Override
    public VariableSelection<ConstantType> visit(AcceptAllVariableSelection<ConstantType> pAcceptAllVariableSelection) {
      return pAcceptAllVariableSelection;
    }

    @Override
    public VariableSelection<ConstantType> visit(
        AcceptSpecifiedVariableSelection<ConstantType> pAcceptSpecifiedVariableSelection) {
      if (AcceptSpecifiedVariableSelection.this == pAcceptSpecifiedVariableSelection
          || AcceptSpecifiedVariableSelection.this.specifiedVariables.containsAll(pAcceptSpecifiedVariableSelection.specifiedVariables)) {
        return AcceptSpecifiedVariableSelection.this;
      }
      return pAcceptSpecifiedVariableSelection.join(specifiedVariables);
    }

  }

  private VariableSelection<ConstantType> join(Set<String> pSpecifiedVariables) {
    if (this.specifiedVariables == pSpecifiedVariables || this.specifiedVariables.containsAll(pSpecifiedVariables)) {
      return this;
    }
    AcceptSpecifiedVariableSelection<ConstantType> result = new AcceptSpecifiedVariableSelection<>(
        Iterables.concat(AcceptSpecifiedVariableSelection.this.specifiedVariables, pSpecifiedVariables));
    return result;
  }

}
