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
package org.sosy_lab.cpachecker.cpa.invariants;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.FormulaReportingState;
import org.sosy_lab.cpachecker.cpa.invariants.formula.CanExtractVariableRelationVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.CollectVarsVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.CompoundIntervalFormulaManager;
import org.sosy_lab.cpachecker.cpa.invariants.formula.Constant;
import org.sosy_lab.cpachecker.cpa.invariants.formula.ContainsVarVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.FormulaAbstractionVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.FormulaCompoundStateEvaluationVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.FormulaDepthCountVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.FormulaEvaluationVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.InvariantsFormula;
import org.sosy_lab.cpachecker.cpa.invariants.formula.PartialEvaluator;
import org.sosy_lab.cpachecker.cpa.invariants.formula.PushAssumptionToEnvironmentVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.ReplaceVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.SplitConjunctionsVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.SplitDisjunctionsVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.StateEqualsVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.ToBooleanFormulaVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.ToFormulaVisitor;
import org.sosy_lab.cpachecker.cpa.invariants.formula.Union;
import org.sosy_lab.cpachecker.cpa.invariants.formula.Variable;
import org.sosy_lab.cpachecker.cpa.invariants.formula.variablerelations.VariableEQ;
import org.sosy_lab.cpachecker.cpa.invariants.formula.variablerelations.VariableLT;
import org.sosy_lab.cpachecker.cpa.invariants.formula.variablerelations.VariableRelation;
import org.sosy_lab.cpachecker.cpa.invariants.formula.variablerelations.VariableRelationSet;
import org.sosy_lab.cpachecker.cpa.invariants.variableselection.VariableSelection;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * Instances of this class represent states in the light-weight invariants analysis.
 */
public class InvariantsState implements AbstractState, FormulaReportingState {

  private static final CollectVarsVisitor<CompoundInterval> COLLECT_VARS_VISITOR = new CollectVarsVisitor<>();

  private static final FormulaDepthCountVisitor<CompoundInterval> FORMULA_DEPTH_COUNT_VISITOR = new FormulaDepthCountVisitor<>();

  /**
   * A visitor used to split boolean conjunction formulas up into the conjuncted clauses
   */
  private static final SplitConjunctionsVisitor<CompoundInterval> SPLIT_CONJUNCTIONS_VISITOR =
      new SplitConjunctionsVisitor<>();

    /**
     * A visitor used to split boolean conjunction formulas up into the conjuncted clauses
     */
    private static final SplitDisjunctionsVisitor<CompoundInterval> SPLIT_DISJUNCTIONS_VISITOR =
        new SplitDisjunctionsVisitor<>();

  /**
   * A visitor used to evaluate formulas as exactly as possible.
   */
  public static final FormulaEvaluationVisitor<CompoundInterval> EVALUATION_VISITOR =
      new FormulaCompoundStateEvaluationVisitor();

  /**
   * A visitor that, like the formula evaluation visitor, is used to evaluate formulas, but far less exact to allow for convergence.
   */
  public static final FormulaEvaluationVisitor<CompoundInterval> ABSTRACTION_VISITOR = new FormulaAbstractionVisitor();

  /**
   * The constant formula representing TOP
   */
  private static final InvariantsFormula<CompoundInterval> TOP = CompoundIntervalFormulaManager.INSTANCE
      .asConstant(CompoundInterval.top());

  /**
   * The constant formula representing BOTTOM
   */
  private static final InvariantsFormula<CompoundInterval> BOTTOM = CompoundIntervalFormulaManager.INSTANCE
      .asConstant(CompoundInterval.bottom());

  /**
   * The environment currently known to the state.
   */
  private final NonRecursiveEnvironment environment;

  /**
   * The currently made assumptions.
   */
  private final VariableRelationSet<CompoundInterval> assumptions;

  /**
   * The edge based abstraction strategy used.
   */
  private final EdgeBasedAbstractionStrategy edgeBasedAbstractionStrategy;

  /**
   * The variables selected for this analysis.
   */
  private final VariableSelection<CompoundInterval> variableSelection;

  private final Map<String, CType> variableTypes;

  /**
   * A flag indicating whether or not to use bit vectors for representing states.
   */
  private final boolean useBitvectors;

  private final PartialEvaluator partialEvaluator;

  private final InvariantsPrecision precision;

  private final Set<InvariantsFormula<CompoundInterval>> collectedInterestingAssumptions;

  private Iterable<InvariantsFormula<CompoundInterval>> environmentAndAssumptions = null;

  private volatile int hash = 0;

  /**
   * Creates a new pristine invariants state with just a value for the flag indicating whether
   * or not to use bit vectors for representing states and a variable selection.
   *
   * @param pUseBitvectors the flag indicating whether or not to use bit vectors for representing states.
   * @param pVariableSelection the selected variables.
   * @param pPrecision the precision of the state.
   */
  public InvariantsState(boolean pUseBitvectors, VariableSelection<CompoundInterval> pVariableSelection,
      InvariantsPrecision pPrecision) {
    this(pUseBitvectors, pVariableSelection, pPrecision.getEdgeBasedAbstractionStrategyFactory().getAbstractionStrategy(), pPrecision);
  }

  public InvariantsState(boolean pUseBitvectors, VariableSelection<CompoundInterval> pVariableSelection,
      InvariantsPrecision pPrecision, InvariantsState pInvariant) {
    this.edgeBasedAbstractionStrategy = pPrecision.getEdgeBasedAbstractionStrategyFactory().getAbstractionStrategy();
    this.environment = pInvariant.environment;
    this.assumptions = pInvariant.assumptions;
    this.partialEvaluator = pInvariant.partialEvaluator;
    this.useBitvectors = pUseBitvectors;
    this.variableSelection = pVariableSelection;
    this.collectedInterestingAssumptions = pInvariant.collectedInterestingAssumptions;
    this.precision = pPrecision;
    this.variableTypes = pInvariant.variableTypes;
  }

  /**
   * Creates a new invariants state with just a value for the flag indicating
   * whether or not to use bit vectors for representing states, a selection of
   * variables, the set of visited edges and a precision.
   *
   * @param pUseBitvectors the flag indicating whether or not to use bit vectors for representing states.
   * @param pVariableSelection the selected variables.
   * @param pEdgeBasedAbstractionStrategy the edge based abstraction strategy used.
   * @param pPrecision the precision of the state.
   */
  private InvariantsState(boolean pUseBitvectors, VariableSelection<CompoundInterval> pVariableSelection,
      EdgeBasedAbstractionStrategy pEdgeBasedAbstractionStrategy,
      InvariantsPrecision pPrecision) {
    this.edgeBasedAbstractionStrategy = pEdgeBasedAbstractionStrategy;
    this.environment = new NonRecursiveEnvironment();
    this.assumptions = new VariableRelationSet<>();
    this.partialEvaluator = new PartialEvaluator(this.environment);
    this.useBitvectors = pUseBitvectors;
    this.variableSelection = pVariableSelection;
    this.collectedInterestingAssumptions = new HashSet<>();
    this.precision = pPrecision;
    this.variableTypes = new HashMap<>();
  }

  /**
   * Creates a new state from the given state properties.
   *
   * @param pEdgeBasedAbstractionStrategy the edge based abstraction strategy.
   * @param pAssumptions the current assumptions.
   * @param pVariableRelations the currently known relations between variables.
   * @param pEnvironment the current environment.
   * @param pUseBitvectors a flag indicating whether or not to use bit vectors to represent states.
   * @param pInterestingAssumptions
   * @return a new state from the given state properties.
   */
  private static InvariantsState from(EdgeBasedAbstractionStrategy pEdgeBasedAbstractionStrategy,
      Set<? extends InvariantsFormula<CompoundInterval>> pAssumptions,
      Map<String, InvariantsFormula<CompoundInterval>> pEnvironment,
      boolean pUseBitvectors, VariableSelection<CompoundInterval> pVariableSelection,
      Set<InvariantsFormula<CompoundInterval>> pCollectedInterestingAssumptions,
      InvariantsPrecision pPrecision,
      Map<String, CType> pVariableTypes) {
    InvariantsState result = new InvariantsState(pUseBitvectors, pVariableSelection, pEdgeBasedAbstractionStrategy, pPrecision);
    if (!result.assumeInternal(pAssumptions, result.getFormulaResolver())) { return null; }
    if (!result.assumeInternal(pCollectedInterestingAssumptions, result.getFormulaResolver())) { return null; }
    result.environment.putAll(pEnvironment);
    result.variableTypes.putAll(pVariableTypes);
    return result;
  }

  public InvariantsState setType(String pVarName, CType pType) {
    if (pType.equals(variableTypes.get(pVarName))) {
      return this;
    }
    InvariantsState result = from(edgeBasedAbstractionStrategy, assumptions, environment, useBitvectors, variableSelection, collectedInterestingAssumptions, precision, variableTypes);
    result.variableTypes.put(pVarName, pType);
    return result;
  }

  public InvariantsState setTypes(Map<String, CType> pVarTypes) {
    boolean allContained = true;
    for (Map.Entry<String, CType> entry : pVarTypes.entrySet()) {
      if (!entry.getValue().equals(variableTypes.get(entry.getKey()))) {
        allContained = false;
        break;
      }
    }
    if (allContained) {
      return this;
    }
    InvariantsState result = from(edgeBasedAbstractionStrategy, assumptions, environment, useBitvectors, variableSelection, collectedInterestingAssumptions, precision, variableTypes);
    result.variableTypes.putAll(pVarTypes);
    return result;
  }

  public InvariantsState assignArray(String pArray, InvariantsFormula<CompoundInterval> pSubscript, InvariantsFormula<CompoundInterval> pValue, CFAEdge pEdge) {
    FormulaEvaluationVisitor<CompoundInterval> fev = getFormulaResolver(pEdge);
    // Edge is already counted by formula resolver access
    boolean ignoreEdge = mayEvaluate(pEdge);
    CompoundInterval value = pSubscript.accept(fev, this.environment);
    if (value.isSingleton()) { // Exact subscript value is known
      return assign(pArray + "[" + value.getValue() + "]", pValue, pEdge, ignoreEdge);
    } else { // Multiple subscript values are possible: All possible subscript targets are now unknown
      InvariantsState result = this;
      for (String varName : this.environment.keySet()) {
        String prefix = pArray + "[";
        if (varName.startsWith(prefix)) {
          String subscriptValueStr = varName.replace(prefix, "").replaceAll("].*", "");
          if (subscriptValueStr.equals("*") || value.contains(new BigInteger(subscriptValueStr))) {
            result = result.assign(varName, TOP, pEdge, ignoreEdge);
          }
        }
      }
      return result;
    }
  }

  public InvariantsState assign(String pVarName, InvariantsFormula<CompoundInterval> pValue, CFAEdge pEdge) {
    InvariantsState result = this;
    for (Map.Entry<String, InvariantsFormula<CompoundInterval>> entry : this.environment.entrySet()) {
      String varName = entry.getKey();
      if (varName.startsWith(pVarName + "->") || varName.startsWith(pVarName + ".")) {
        result = result.assign(varName, TOP, pEdge);
      }
    }
    if (pValue instanceof Variable<?>) {
      String valueVarName = ((Variable<?>) pValue).getName();
      if (valueVarName.startsWith(pVarName + "->") || valueVarName.startsWith(pVarName + ".")) {
        return assign(pVarName, TOP, pEdge);
      }
      String pointerDerefPrefix = valueVarName + "->";
      String nonPointerDerefPrefix = valueVarName + ".";
      for (Map.Entry<String, InvariantsFormula<CompoundInterval>> entry : this.environment.entrySet()) {
        if (entry.getKey().startsWith(pointerDerefPrefix)) {
          String suffix = entry.getKey().substring(pointerDerefPrefix.length());
          result = result.assign(pVarName + "->" + suffix, CompoundIntervalFormulaManager.INSTANCE.asVariable(entry.getKey()), pEdge);
        } else if (entry.getKey().startsWith(nonPointerDerefPrefix)) {
          String suffix = entry.getKey().substring(nonPointerDerefPrefix.length());
          result = result.assign(pVarName + "." + suffix, CompoundIntervalFormulaManager.INSTANCE.asVariable(entry.getKey()), pEdge);
        }
      }
      return result.assign(pVarName, pValue, pEdge, false);
    }
    return result.assign(pVarName, pValue, pEdge, false);
  }

  /**
   * Creates a new state representing the given assignment applied to the current state.
   *
   * @param pVarName the name of the variable being assigned.
   * @param pValue the new value of the variable.
   * @param pEdge the edge containing the assignment.
   * @param pIgnoreEdge flag indicating whether or not to evaluate without checking the remaining evaluations for the edge
   * @return a new state representing the given assignment applied to the current state.
   */
  private InvariantsState assign(String pVarName, InvariantsFormula<CompoundInterval> pValue, CFAEdge pEdge, boolean pIgnoreEdge) {
    Preconditions.checkNotNull(pValue);

    // Check if the assigned variable is selected (newVariableSelection != null)
    VariableSelection<CompoundInterval> newVariableSelection = this.variableSelection.acceptAssignment(pVarName, pValue);
    if (newVariableSelection == null) {
      // Ensure that no information about the irrelevant assigned variable is retained
      Map<String, InvariantsFormula<CompoundInterval>> newEnvironment = this.environment;
      if (this.environment.containsKey(pVarName)) {
        newEnvironment = new HashMap<>(this.environment);
        newEnvironment.remove(pVarName);
      }
      boolean assumptionsChanged = false;
      Set<InvariantsFormula<CompoundInterval>> newAssumptions = new HashSet<>();
      for (InvariantsFormula<CompoundInterval> assumption : this.assumptions) {
        if (!assumption.accept(new ContainsVarVisitor<CompoundInterval>(), pVarName)) {
          newAssumptions.add(assumption);
        } else {
          assumptionsChanged = true;
        }
      }
      for (InvariantsFormula<CompoundInterval> assumption : this.collectedInterestingAssumptions) {
        if (!assumption.accept(new ContainsVarVisitor<CompoundInterval>(), pVarName)) {
          newAssumptions.add(assumption);
        } else {
          assumptionsChanged = true;
        }
      }
      if (this.environment == newEnvironment && !assumptionsChanged) {
        return this;
      }
      return from(edgeBasedAbstractionStrategy, newAssumptions, newEnvironment,
          useBitvectors, variableSelection,
          Collections.<InvariantsFormula<CompoundInterval>>emptySet(),
          precision,
          variableTypes);
    }

    CompoundIntervalFormulaManager ifm = CompoundIntervalFormulaManager.INSTANCE;
    Variable<CompoundInterval> variable = ifm.asVariable(pVarName);
    ContainsVarVisitor<CompoundInterval> containsVarVisitor = new ContainsVarVisitor<>();

    // Optimization: If the value being assigned is equivalent to the value already stored, do nothing
    if (getEnvironmentValue(pVarName).equals(pValue)
        && (pValue instanceof Variable<?> || pValue instanceof Constant<?> && ((Constant<CompoundInterval>) pValue).getValue().isSingleton())
        || variable.accept(new StateEqualsVisitor(getFormulaResolver(), this.environment), pValue)) {
      return this;
    }

    // Avoid self-assignments if an equivalent alternative is available
    if (pValue.accept(containsVarVisitor, pVarName)) {
      InvariantsFormula<CompoundInterval> varValue = environment.get(pVarName);
      boolean isVarValueConstant = varValue instanceof Constant && ((Constant<CompoundInterval>) varValue).getValue().isSingleton();
      InvariantsFormula<CompoundInterval> alternative = varValue;
      if (!(alternative instanceof Variable)) {
        alternative = null;
        for (Map.Entry<String, InvariantsFormula<CompoundInterval>> entry : environment.entrySet()) {
          InvariantsFormula<CompoundInterval> value = entry.getValue();
          if (!entry.getKey().equals(pVarName)
              && (value.equals(variable) || isVarValueConstant && value.equals(varValue))) {
            alternative = CompoundIntervalFormulaManager.INSTANCE.asVariable(entry.getKey());
            break;
          }
        }
      }
      if (alternative != null) {
        pValue = pValue.accept(new ReplaceVisitor<>(variable, alternative));
      }
      CompoundInterval value = pValue.accept(EVALUATION_VISITOR, environment);
      if (value.isSingleton()) {
        for (Map.Entry<String, InvariantsFormula<CompoundInterval>> entry : environment.entrySet()) {
          InvariantsFormula<CompoundInterval> v = entry.getValue();
          if (v instanceof Constant && value.equals(((Constant<CompoundInterval>) v).getValue())) {
            pValue = CompoundIntervalFormulaManager.INSTANCE.asVariable(entry.getKey());
            break;
          }
        }
      }
    }


    InvariantsFormula<CompoundInterval> previousValue = getEnvironmentValue(pVarName);
    FormulaEvaluationVisitor<CompoundInterval> evaluationVisitor = getFormulaResolver(pEdge);
    if (!mayEvaluate(pEdge) && previousValue instanceof Union<?>) {
      Union<CompoundInterval> union = (Union<CompoundInterval>) previousValue;
      if (union.getOperand1() instanceof Union<?>
          || union.getOperand2() instanceof Union<?>) {
        previousValue = CompoundIntervalFormulaManager.INSTANCE.asConstant(previousValue.accept(evaluationVisitor, environment));
      }
    }

    /*
     * A variable is newly assigned, so the appearances of this variable
     * in any previously collected assumptions (including its new value)
     * have to be resolved with the variable's previous value.
     */
    ReplaceVisitor<CompoundInterval> replaceVisitor = new ReplaceVisitor<>(variable, previousValue);

    // Try without widening first
    InvariantsState unwidened =
        assignInternal(pVarName, pValue, pEdge, newVariableSelection, EVALUATION_VISITOR, replaceVisitor);
    InvariantsState result = unwidened;

    // Try to add the assumptions for the unwidened result; if it turns out that they are false, the state is bottom
    if (!updateAssumptions(result, replaceVisitor, pValue, pVarName, pEdge)) { return null; }

    // If widening is required, do so
    if (!evaluationVisitor.equals(EVALUATION_VISITOR)) {
      result = assignInternal(pVarName, pValue, pEdge, newVariableSelection, evaluationVisitor, replaceVisitor);

      // Try to add the assumptions for the widened result; if it turns out that they are false, the state is bottom
      if (!updateAssumptions(result, replaceVisitor, pValue, pVarName, pEdge)) { return null; }

      // If this state covers the unwidened result, use this state as widening
      if (unwidened.isLessThanOrEqualTo(this) && !result.isLessThanOrEqualTo(this)) {
        return this;
      }
    }

    if (equals(result)) {
      return this;
    }
    return result;
  }

  /**
   * @param pVarName
   * @param pValue
   * @param pEdge
   * @param newVariableSelection
   * @param evaluationVisitor
   * @param replaceVisitor
   * @return
   */
  private InvariantsState assignInternal(String pVarName, InvariantsFormula<CompoundInterval> pValue, CFAEdge pEdge,
      VariableSelection<CompoundInterval> newVariableSelection,
      FormulaEvaluationVisitor<CompoundInterval> evaluationVisitor, ReplaceVisitor<CompoundInterval> replaceVisitor) {
    final InvariantsState result = new InvariantsState(useBitvectors, newVariableSelection, edgeBasedAbstractionStrategy.addVisitedEdge(pEdge), precision);
    result.variableTypes.putAll(variableTypes);

    for (Map.Entry<String, InvariantsFormula<CompoundInterval>> environmentEntry : this.environment.entrySet()) {
      if (!environmentEntry.getKey().equals(pVarName)) {
        InvariantsFormula<CompoundInterval> newEnvValue =
            environmentEntry.getValue().accept(replaceVisitor);
        result.environment.put(environmentEntry.getKey(), trim(newEnvValue, evaluationVisitor));
      }
    }
    result.environment.put(pVarName, trim(pValue.accept(replaceVisitor), evaluationVisitor));
    return result;
  }

  /**
   * Gets a state that has no information about the program and the same
   * information about the analysis as this state.
   *
   * @return a state that has no information about the program and the same
   * information about the analysis as this state.
   */
  public InvariantsState clear() {
    if (environment.isEmpty() && assumptions.isEmpty() && collectedInterestingAssumptions.isEmpty()) {
      return this;
    }
    return new InvariantsState(useBitvectors, variableSelection, edgeBasedAbstractionStrategy, precision);
  }

  private InvariantsFormula<CompoundInterval> trim(InvariantsFormula<CompoundInterval> pFormula, FormulaEvaluationVisitor<CompoundInterval> pEvaluationVisitor) {
    if (pFormula.accept(FORMULA_DEPTH_COUNT_VISITOR) > this.precision.getMaximumFormulaDepth()) {
      InvariantsFormula<CompoundInterval> result = pFormula.accept(this.partialEvaluator, pEvaluationVisitor);
      if (result.accept(FORMULA_DEPTH_COUNT_VISITOR) > this.precision.getMaximumFormulaDepth()) {
        result = CompoundIntervalFormulaManager.INSTANCE.asConstant(
            pFormula.accept(pEvaluationVisitor, environment));
      }
      return result;
    }
    return pFormula;
  }

  /**
   * Gets the environment as a set equations of the variables with their values.
   *
   * @return the environment as a set equations of the variables with their values.
   */
  private Set<InvariantsFormula<CompoundInterval>> getEnvironmentAsAssumptions() {
    Set<InvariantsFormula<CompoundInterval>> environmentalAssumptions = new HashSet<>();
    CompoundIntervalFormulaManager ifm = CompoundIntervalFormulaManager.INSTANCE;

    List<InvariantsFormula<CompoundInterval>> atomic = new ArrayList<>(1);
    Deque<InvariantsFormula<CompoundInterval>> toCheck = new ArrayDeque<>(1);
    for (Map.Entry<String, InvariantsFormula<CompoundInterval>> entry : this.environment.entrySet()) {
      InvariantsFormula<CompoundInterval> variable = ifm.asVariable(entry.getKey());

      atomic.clear();
      toCheck.clear();

      toCheck.add(entry.getValue());
      while (!toCheck.isEmpty()) {
        InvariantsFormula<CompoundInterval> current = toCheck.poll();
        if (current instanceof Union<?>) {
          Union<CompoundInterval> union = (Union<CompoundInterval>) current;
          toCheck.add(union.getOperand1());
          toCheck.add(union.getOperand2());
        } else {
          atomic.add(current);
        }
      }
      assert !atomic.isEmpty();
      Iterator<InvariantsFormula<CompoundInterval>> iterator = atomic.iterator();
      InvariantsFormula<CompoundInterval> equation = ifm.equal(variable, iterator.next());
      while (iterator.hasNext()) {
        equation = ifm.logicalOr(equation, ifm.equal(variable, iterator.next()));
      }

      environmentalAssumptions.add(equation);
    }
    return environmentalAssumptions;
  }

  /**
   * Gets the assumptions and the environment stored in this state as an
   * iterable.
   *
   * @return the assumptions and the environment stored in this state as an
   * iterable.
   */
  public Iterable<InvariantsFormula<CompoundInterval>> getAssumptionsAndEnvironment() {
    if (this.environmentAndAssumptions == null) {
      this.environmentAndAssumptions = Iterables.concat(this.assumptions, this.collectedInterestingAssumptions, new Iterable<InvariantsFormula<CompoundInterval>>() {

        private Iterable<InvariantsFormula<CompoundInterval>> lazyInner = null;

        @Override
        public Iterator<InvariantsFormula<CompoundInterval>> iterator() {
          if (lazyInner == null) {
            lazyInner = getEnvironmentAsAssumptions();
          }
          return lazyInner.iterator();
        }

      });
    }
    return this.environmentAndAssumptions;
  }

  /**
   * When an assignment to the state is made, all assumptions of this state must be added to the given
   * state after applying the given replace visitor, used to adjust them to be correct in the new
   * environment, to those assumptions.
   *
   * @param pTargetState the state to add the assumptions to.
   * @param pReplaceVisitor the replace visitor used to transform the assumptions correct in this state to assumptions correct in the new state.
   * @param pNewValue the new value of the assigned variable.
   * @param pVarName the name of the assigned variable.
   *
   * @return <code>true</code> if the transfer of assumptions results in a valid state, <code>false</code> if it is bottom.
   */
  private boolean updateAssumptions(InvariantsState pTargetState, ReplaceVisitor<CompoundInterval> pReplaceVisitor,
      InvariantsFormula<CompoundInterval> pNewValue, String pVarName, CFAEdge pEdge) {
    FormulaEvaluationVisitor<CompoundInterval> resolver = getFormulaResolver(pEdge);
    for (InvariantsFormula<CompoundInterval> oldAssumption : this.assumptions) {
      // Try to add the assumption; if it turns out that it is false, the assumption is bottom
      if (!pTargetState.assumeInternal(oldAssumption.accept(pReplaceVisitor),
          resolver)) {
        return false;
      }
    }
    for (InvariantsFormula<CompoundInterval> oldAssumption : this.collectedInterestingAssumptions) {
      // Try to add the assumption; if it turns out that it is false, the assumption is bottom
      if (!pTargetState.assumeInternal(oldAssumption.accept(pReplaceVisitor),
          resolver)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets the value of the variable with the given name from the environment.
   * @param pVarName the name of the variable.
   * @return the value of the variable with the given name from the environment.
   */
  private InvariantsFormula<CompoundInterval> getEnvironmentValue(String pVarName) {
    InvariantsFormula<CompoundInterval> environmentValue = this.environment.get(pVarName);
    if (environmentValue == null) { return TOP; }
    return environmentValue;
  }

  /**
   * Gets a formula resolver for the given edge. If the edge has not yet been visited,
   * an exact evaluation formula resolver will be returned; afterwards, a less exact abstraction
   * evaluation visitor will be returned.
   *
   * @param pEdge the edge the resolver will be used for.
   * @return a formula resolver for the given edge.
   */
  public FormulaEvaluationVisitor<CompoundInterval> getFormulaResolver(CFAEdge pEdge) {
    if (mayEvaluate(pEdge)) {
      return getFormulaResolver();
    }
    return ABSTRACTION_VISITOR;
  }

  /**
   * Gets an exact formula evaluation visitor.
   *
   * @return an exact formula evaluation visitor.
   */
  private FormulaEvaluationVisitor<CompoundInterval> getFormulaResolver() {
    return EVALUATION_VISITOR;
  }

  /**
   * Makes the given assumptions for this state and checks if this state is still valid.
   *
   * @param pAssumptions the assumptions to be made.
   * @param pEvaluationVisitor the evaluation visitor to use for evaluating the assumptions' correctness.
   * @return <code>true</code> if the state is still valid after the assumptions are made, <code>false</code> otherwise.
   */
  private boolean assumeInternal(Collection<? extends InvariantsFormula<CompoundInterval>> pAssumptions,
      FormulaEvaluationVisitor<CompoundInterval> pEvaluationVisitor) {
    for (InvariantsFormula<CompoundInterval> assumption : pAssumptions) {
      if (!assumeInternal(assumption, pEvaluationVisitor)) { return false; }
    }
    return true;
  }

  /**
   * Makes the given assumption for this state and checks if this state is still valid.
   *
   * @param pAssumption the assumption to be made.
   * @param pEvaluationVisitor the evaluation visitor to use for evaluating the assumptions' correctness.
   * @return <code>true</code> if the state is still valid after the assumptions are made, <code>false</code> otherwise.
   */
  private boolean assumeInternal(InvariantsFormula<CompoundInterval> pAssumption,
      FormulaEvaluationVisitor<CompoundInterval> pEvaluationVisitor) {
    InvariantsFormula<CompoundInterval> assumption = pAssumption.accept(this.partialEvaluator, pEvaluationVisitor);
    // If there are multiple assumptions combined with &&, split them up
    List<InvariantsFormula<CompoundInterval>> assumptionParts = assumption.accept(SPLIT_CONJUNCTIONS_VISITOR);
    if (assumptionParts.size() > 1) { return assumeInternal(assumptionParts, pEvaluationVisitor); }
    // If the assumption is top, it adds no value
    if (assumption.equals(TOP)) { return true; }

    if (assumption instanceof Constant<?>) {
      return !((Constant<CompoundInterval>) assumption).getValue().isDefinitelyFalse();
    }

    // If the assumption is an obvious contradiction, it cannot be validly assumed
    if (assumption.equals(BOTTOM)) { return false; }

    CompoundInterval assumptionEvaluation = assumption.accept(pEvaluationVisitor, getEnvironment());
    // If the invariant evaluates to false or is bottom, it represents an invalid state
    if (assumptionEvaluation.isDefinitelyFalse() || assumptionEvaluation.isBottom()) { return false; }
    // If the invariant evaluates to true, it adds no value for now
    if (assumptionEvaluation.isDefinitelyTrue()) { return true; }

    // If exact evaluation is enabled or the expression relates a maximum of one variable
    // to constants, then environment information may be gained
    if (!(pEvaluationVisitor instanceof FormulaAbstractionVisitor)
        || assumption.accept(COLLECT_VARS_VISITOR).size() <= 1) {
      PushAssumptionToEnvironmentVisitor patev =
          new PushAssumptionToEnvironmentVisitor(pEvaluationVisitor, this.environment);
      if (!assumption.accept(patev, CompoundInterval.logicalTrue())) {
        assert !assumptionEvaluation.isDefinitelyTrue();
        return false;
      }
      // Check all assumptions once more after the environment changed
      if (isDefinitelyFalse(assumption, pEvaluationVisitor)) { return false; }
      for (InvariantsFormula<CompoundInterval> oldAssumption : this.assumptions) {
        if (isDefinitelyFalse(oldAssumption, pEvaluationVisitor)) { return false; }
      }

      // Check again if there is any more value to gain from the assumption after extracting environment information
      assumption = assumption.accept(this.partialEvaluator, EVALUATION_VISITOR);
      if (assumption.accept(EVALUATION_VISITOR, this.environment).isDefinitelyTrue()) {
        // No more value to gain
        return true;
      }
    }

    if (this.precision.isUsingBinaryVariableInterrelations()) {
      extractVariableRelations(assumption, EVALUATION_VISITOR, this.assumptions);
    }

    return true;
  }

  private void extractVariableRelations(InvariantsFormula<CompoundInterval> pFormula, FormulaEvaluationVisitor<CompoundInterval> pEvaluationVisitor,
      VariableRelationSet<CompoundInterval> pVariableRelationSet) {
    List<InvariantsFormula<CompoundInterval>> conjunctiveParts = pFormula.accept(SPLIT_CONJUNCTIONS_VISITOR);
    if (conjunctiveParts.size() > 1) {
      for (InvariantsFormula<CompoundInterval> conjunctivePart : conjunctiveParts) {
        extractVariableRelations(conjunctivePart, pEvaluationVisitor, pVariableRelationSet);
      }
      return;
    }
    List<InvariantsFormula<CompoundInterval>> disjunctiveParts = pFormula.accept(SPLIT_DISJUNCTIONS_VISITOR);
    if (disjunctiveParts.size() > 1) {
      VariableRelationSet<CompoundInterval> union = new VariableRelationSet<>();
      VariableRelationSet<CompoundInterval> partRelations = new VariableRelationSet<>();
      for (InvariantsFormula<CompoundInterval> disjunctivePart : disjunctiveParts) {
        partRelations.clear();
        extractVariableRelations(disjunctivePart, pEvaluationVisitor, partRelations);
        union.uniteWith(partRelations);
      }
      pVariableRelationSet.refineBy(union);
      return;
    }
    Set<String> variables = pFormula.accept(COLLECT_VARS_VISITOR);
    if (variables.size() == 2 && pFormula.accept(new CanExtractVariableRelationVisitor(Collections.unmodifiableMap(environment)), pEvaluationVisitor)) {
      Iterator<String> variableIterator = variables.iterator();
      String var1Name = variableIterator.next();
      String var2Name = variableIterator.next();
      Map<String, InvariantsFormula<CompoundInterval>> tmpEnvironment = new HashMap<>();
      tmpEnvironment.put(var1Name, CompoundIntervalFormulaManager.INSTANCE.asConstant(CompoundInterval.singleton(0)));
      if (pFormula.accept(new PushAssumptionToEnvironmentVisitor(pEvaluationVisitor, tmpEnvironment), CompoundInterval.logicalTrue())) {
        InvariantsFormula<CompoundInterval> var2RelativeValue = tmpEnvironment.get(var2Name);
        if (var2RelativeValue != null) {
          CompoundInterval relationSignum = var2RelativeValue.accept(pEvaluationVisitor, tmpEnvironment).signum();
          if (!relationSignum.isBottom()) {
            Variable<CompoundInterval> var1 = CompoundIntervalFormulaManager.INSTANCE.asVariable(var1Name);
            Variable<CompoundInterval> var2 = CompoundIntervalFormulaManager.INSTANCE.asVariable(var2Name);
            VariableRelation<CompoundInterval> relation = null;
            if (relationSignum.containsZero()) {
              relation = new VariableEQ<>(var1, var2);
            }
            if (relationSignum.containsNegative()) {
              VariableLT<CompoundInterval> gt = new VariableLT<>(var2, var1);
              relation = relation == null ? gt : relation.union(gt);
            }
            if (relationSignum.containsPositive()) {
              VariableLT<CompoundInterval> lt = new VariableLT<>(var1, var2);
              relation = relation == null ? lt : relation.union(lt);
            }
            if (relation != null && !pVariableRelationSet.contains(relation)) {
              pVariableRelationSet.add(relation);
            }
          }
        }
      }
    }
  }

  /**
   * Checks if the given assumption is definitely false for this state.
   * @param pAssumption the assumption to evaluate.
   * @param pEvaluationVisitor the evaluation visitor used to evaluate the assumption within this state's environment.
   * @return <code>true</code> if the given assumption does definitely not hold for this state's environment, <code>false</code> if it might.
   */
  private boolean isDefinitelyFalse(InvariantsFormula<CompoundInterval> pAssumption,
      FormulaEvaluationVisitor<CompoundInterval> pEvaluationVisitor) {
    return pAssumption.accept(pEvaluationVisitor, getEnvironment()).isDefinitelyFalse();
  }

  public InvariantsState assume(InvariantsFormula<CompoundInterval> pAssumption, CFAEdge pEdge) {
    // Check if at least one of the involved variables is selected (newVariableSelection != null)
    VariableSelection<CompoundInterval> newVariableSelection = this.variableSelection.acceptAssumption(pAssumption);
    if (newVariableSelection == null) {
      return this;
    }
    FormulaEvaluationVisitor<CompoundInterval> evaluator = getFormulaResolver(pEdge);
    InvariantsFormula<CompoundInterval> assumption = pAssumption.accept(this.partialEvaluator, evaluator);
    if (assumption instanceof Constant<?>) {
      CompoundInterval value = ((Constant<CompoundInterval>) assumption).getValue();
      // An assumption evaluating to false represents an unreachable state; it can never be fulfilled
      if (value.isDefinitelyFalse()) { return null; }
      // An assumption representing nothing more than "true" or "maybe true" adds no information
      return this;
    }
    if (getAssumptions().contains(assumption)) {
      return this;
    }

    InvariantsState result = from(edgeBasedAbstractionStrategy.addVisitedEdge(pEdge), assumptions, environment, useBitvectors,
        newVariableSelection, collectedInterestingAssumptions, precision, variableTypes);
    if (result != null) {
      if (!result.assumeInternal(assumption, evaluator)) { return null; }
      if (equalsState(result)) {
        return this;
      }
    }
    return result;
  }

  @Override
  public BooleanFormula getFormulaApproximation(FormulaManagerView pManager) {
    FormulaEvaluationVisitor<CompoundInterval> evaluationVisitor = getFormulaResolver();
    BooleanFormulaManager bfmgr = pManager.getBooleanFormulaManager();
    BooleanFormula result = bfmgr.makeBoolean(true);
    ToFormulaVisitor<CompoundInterval, BooleanFormula> toBooleanFormulaVisitor =
        ToBooleanFormulaVisitor.getVisitor(pManager, evaluationVisitor, useBitvectors, precision.getMachineModel(), variableTypes);

    final Predicate<String> acceptVariable = new Predicate<String>() {

      @Override
      public boolean apply(@Nullable String pInput) {
        return pInput != null && !pInput.contains("*");
      }

    };

    final Predicate<InvariantsFormula<CompoundInterval>> acceptFormula = new Predicate<InvariantsFormula<CompoundInterval>>() {

      @Override
      public boolean apply(@Nullable InvariantsFormula<CompoundInterval> pInput) {
        return pInput != null
            && !pInput.equals(TOP)
            && FluentIterable.from(pInput.accept(COLLECT_VARS_VISITOR)).allMatch(acceptVariable);
      }

    };

    Set<InvariantsFormula<CompoundInterval>> assumptions = new HashSet<>();
    assumptions.addAll(collectedInterestingAssumptions);
    assumptions.addAll(this.assumptions);

    for (Map.Entry<String, InvariantsFormula<CompoundInterval>> entry : this.environment.entrySet()) {
      InvariantsFormula<CompoundInterval> valueFormula = entry.getValue();
      String varName = entry.getKey();
      if (!acceptVariable.apply(varName) || !acceptFormula.apply(valueFormula)) {
        continue;
      }
      assumptions.add(CompoundIntervalFormulaManager.INSTANCE.equal(CompoundIntervalFormulaManager.INSTANCE.asVariable(varName), valueFormula));
    }

    for (InvariantsFormula<CompoundInterval> assumption : getAssumptionsAndEnvironment()) {
      if (acceptFormula.apply(assumption)) {
        BooleanFormula assumptionFormula = assumption.accept(toBooleanFormulaVisitor, getEnvironment());
        if (assumptionFormula != null) {
          result = bfmgr.and(result, assumptionFormula);
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object pObj) {
    if (pObj == this) { return true; }
    if (!(pObj instanceof InvariantsState)) { return false; }
    return equalsState((InvariantsState) pObj);
  }

  private boolean equalsState(InvariantsState pOther) {
    return pOther != null && environment.equals(pOther.environment)
        && assumptions.equals(pOther.assumptions)
        && collectedInterestingAssumptions.equals(pOther.collectedInterestingAssumptions);
  }

  @Override
  public int hashCode() {
    int result = hash;
    if (result == 0) {
      result = 17;
      result = 31 * result + environment.hashCode();
      result = 31 * result + assumptions.hashCode();
      result = 31 * result + collectedInterestingAssumptions.hashCode();
      hash = result;
    }
    return result;
  }

  @Override
  public String toString() {
    return String.format("Environment: %s; Assumptions: %s",
        Joiner.on(", ").withKeyValueSeparator("=").join(environment),
        Joiner.on(", ").join(FluentIterable.from(Iterables.concat(assumptions, collectedInterestingAssumptions)).toSet()));
  }

  /**
   * Checks whether or not the given edge may be evaluated exactly any further.
   *
   * @param pEdge the edge to evaluate.
   *
   * @return <code>true</code> if the given edge has any exact evaluations left, <code>false</code>
   * otherwise.
   */
  private boolean mayEvaluate(CFAEdge pEdge) {
    return !edgeBasedAbstractionStrategy.useAbstraction(pEdge);
  }

  /**
   * Gets the assumptions made in this state.
   *
   * @return the assumptions made in this state.
   */
  private Set<? extends InvariantsFormula<CompoundInterval>> getAssumptions() {
    return Collections.unmodifiableSet(this.assumptions);
  }

  /**
   * Gets the environment of this state.
   *
   * @return the environment of this state.
   */
  public Map<? extends String, ? extends InvariantsFormula<CompoundInterval>> getEnvironment() {
    return Collections.unmodifiableMap(environment);
  }

  /**
   * Gets the flag indicating whether or not to use bit vectors to represent the states.
   *
   * @return the flag indicating whether or not to use bit vectors to represent the states.
   */
  private boolean getUseBitvectors() {
    return useBitvectors;
  }

  public boolean isLessThanOrEqualTo(InvariantsState pElement2) {
    if (pElement2 == this) { return true; }
    if (pElement2 == null) {
      return false;
    }
    // Perform the implication check (if this state definitely implies the other one, it is less than or equal to it)
    for (InvariantsFormula<CompoundInterval> rightAssumption : pElement2.getAssumptionsAndEnvironment()) {
      if (!definitelyImplies(rightAssumption)) {
        return false;
      }
    }
    return true;
  }

  public boolean definitelyImplies(InvariantsFormula<CompoundInterval> pFormula) {
    return (this.assumptions.isEmpty() && this.collectedInterestingAssumptions.isEmpty())
        ? CompoundIntervalFormulaManager.definitelyImplies(this.environment, pFormula)
        : CompoundIntervalFormulaManager.definitelyImplies(getAssumptionsAndEnvironment(), pFormula, this.environment);
  }

  private boolean environmentsEqualWithRespectToInterestingVariables(InvariantsState pElement2) {
    Set<String> checkedVariables = new HashSet<>();
    Queue<String> waitlist = new ArrayDeque<>(precision.getInterestingVariables());
    while (!waitlist.isEmpty()) {
      String variableName = waitlist.poll();
      if (checkedVariables.add(variableName)) {
        InvariantsFormula<CompoundInterval> left = environment.get(variableName);
        InvariantsFormula<CompoundInterval> right = pElement2.environment.get(variableName);
        if (left != right && (left == null || !left.equals(right))) {
          return false;
        }
        if (left != null) {
          waitlist.addAll(left.accept(COLLECT_VARS_VISITOR));
        }
      }
    }
    return true;
  }

  public InvariantsState join(InvariantsState pElement2) {
    return join(pElement2, false);
  }

  public InvariantsState join(InvariantsState pElement2, boolean pForceJoin) {
    Preconditions.checkArgument(pElement2.useBitvectors == useBitvectors);
    Preconditions.checkArgument(pElement2.precision == precision);

    InvariantsState element1 = this;
    InvariantsState element2 = pElement2;

    InvariantsState result;

    if (isLessThanOrEqualTo(element2)
        || !pForceJoin && !collectedInterestingAssumptions.equals(element2.collectedInterestingAssumptions)
        || !pForceJoin && !environmentsEqualWithRespectToInterestingVariables(pElement2)) {
      result = element2;
    } else if (element2.isLessThanOrEqualTo(element1)) {
      result = element1;
    } else {
      final EdgeBasedAbstractionStrategy edgeBasedAbstractionStrategy = element1.edgeBasedAbstractionStrategy.join(element2.edgeBasedAbstractionStrategy);

      Map<String, InvariantsFormula<CompoundInterval>> resultEnvironment = new NonRecursiveEnvironment();

      // Get some basic information by joining the environments
      for (Map.Entry<String, InvariantsFormula<CompoundInterval>> entry : element1.environment.entrySet()) {
        String varName = entry.getKey();
        InvariantsFormula<CompoundInterval> rightFormula = element2.environment.get(varName);
        if (rightFormula != null) {
          InvariantsFormula<CompoundInterval> newValueFormula =
              CompoundIntervalFormulaManager.INSTANCE.union(
                  entry.getValue().accept(element1.partialEvaluator, EVALUATION_VISITOR),
                  rightFormula.accept(element2.partialEvaluator, EVALUATION_VISITOR)).accept(new PartialEvaluator(), EVALUATION_VISITOR);
          resultEnvironment.put(varName,
              newValueFormula);
        }
      }

      Set<InvariantsFormula<CompoundInterval>> resultAssumptions;
      if (precision.isUsingBinaryVariableInterrelations()) {
        resultAssumptions = new HashSet<>();
        final VariableRelationSet<CompoundInterval> resultRelations;
        // Make assumptions
        VariableRelationSet<CompoundInterval> leftRelations = new VariableRelationSet<>(element1.assumptions);
        VariableRelationSet<CompoundInterval> rightRelations = new VariableRelationSet<>(element2.assumptions);
        resultRelations = new VariableRelationSet<>(leftRelations);
        resultRelations.uniteWith(element2.assumptions);
        leftRelations.removeAll(resultRelations);
        rightRelations.removeAll(resultRelations);
        resultAssumptions.addAll(resultRelations);

        Iterator<? extends InvariantsFormula<CompoundInterval>> leftAssumptionIterator = leftRelations.iterator();
        Iterator<? extends InvariantsFormula<CompoundInterval>> rightAssumptionIterator = rightRelations.iterator();
        // Apply "or" to the two remaining sets of assumptions
        if (leftAssumptionIterator.hasNext() && rightAssumptionIterator.hasNext()) {

          InvariantsFormula<CompoundInterval> leftTotalAssumption = leftAssumptionIterator.next();
          while (leftAssumptionIterator.hasNext()) {
            leftTotalAssumption = CompoundIntervalFormulaManager.INSTANCE.logicalAnd(leftTotalAssumption, leftAssumptionIterator.next());
          }
          InvariantsFormula<CompoundInterval> rightTotalAssumption = rightAssumptionIterator.next();
          while (rightAssumptionIterator.hasNext()) {
            rightTotalAssumption = CompoundIntervalFormulaManager.INSTANCE.logicalAnd(rightTotalAssumption, rightAssumptionIterator.next());
          }

          Set<InvariantsFormula<CompoundInterval>> newDisjunctiveClauses = new HashSet<>();

          newDisjunctiveClauses.addAll(leftTotalAssumption.accept(SPLIT_DISJUNCTIONS_VISITOR));
          newDisjunctiveClauses.addAll(rightTotalAssumption.accept(SPLIT_DISJUNCTIONS_VISITOR));
          InvariantsFormula<CompoundInterval> newAssumption =
              CompoundIntervalFormulaManager.INSTANCE.logicalOr(leftTotalAssumption, rightTotalAssumption);
          resultAssumptions.add(newAssumption);
        }
      } else {
        resultAssumptions = Collections.emptySet();
      }

      VariableSelection<CompoundInterval> resultVariableSelection = element1.variableSelection.join(element2.variableSelection);

      Map<String, CType> variableTypes = new HashMap<>(element1.variableTypes);
      variableTypes.putAll(element2.variableTypes);

      result = InvariantsState.from(edgeBasedAbstractionStrategy, resultAssumptions,
          resultEnvironment, element1.getUseBitvectors(), resultVariableSelection,
          collectedInterestingAssumptions, precision, variableTypes);

      if (result != null) {
        if (result.equalsState(element1)) {
          result = element1;
        }
      }
    }
    return result;
  }

  public InvariantsPrecision getPrecision() {
    return this.precision;
  }

  static interface EdgeBasedAbstractionStrategy {

    public boolean useAbstraction(CFAEdge pEdge);

    public EdgeBasedAbstractionStrategy addVisitedEdge(CFAEdge pEdge);

    public EdgeBasedAbstractionStrategy join(EdgeBasedAbstractionStrategy pStrategy);

  }

  static interface AbstractEdgeBasedAbstractionStrategyFactory {

    public EdgeBasedAbstractionStrategy getAbstractionStrategy();

  }

  private static enum BasicAbstractionStrategies implements EdgeBasedAbstractionStrategy {

    ALWAYS {

      @Override
      public boolean useAbstraction(CFAEdge pEdge) {
        return true;
      }

      @Override
      public EdgeBasedAbstractionStrategy addVisitedEdge(CFAEdge pEdge) {
        return this;
      }

      @Override
      public EdgeBasedAbstractionStrategy join(EdgeBasedAbstractionStrategy pStrategy) {
        return this;
      }

    },

    NEVER {

      @Override
      public boolean useAbstraction(CFAEdge pEdge) {
        return false;
      }

      @Override
      public EdgeBasedAbstractionStrategy addVisitedEdge(CFAEdge pEdge) {
        return this;
      }

      @Override
      public EdgeBasedAbstractionStrategy join(EdgeBasedAbstractionStrategy pStrategy) {
        if (pStrategy == this) {
          return this;
        }
        return pStrategy.join(this);
      }

    };

  }

  static enum EdgeBasedAbstractionStrategyFactories implements AbstractEdgeBasedAbstractionStrategyFactory {

    ALWAYS {

      @Override
      public EdgeBasedAbstractionStrategy getAbstractionStrategy() {
        return BasicAbstractionStrategies.ALWAYS;
      }

    },

    VISITED_EDGES {

      @Override
      public EdgeBasedAbstractionStrategy getAbstractionStrategy() {
        class VisitedEdgesBasedAbstractionStrategy implements EdgeBasedAbstractionStrategy {

          private final ImmutableSet<CFAEdge> visitedEdges;

          private VisitedEdgesBasedAbstractionStrategy() {
            this(ImmutableSet.<CFAEdge>of());
          }

          private VisitedEdgesBasedAbstractionStrategy(ImmutableSet<CFAEdge> pVisitedEdges) {
            this.visitedEdges = pVisitedEdges;
          }

          @Override
          public boolean useAbstraction(CFAEdge pEdge) {
            return visitedEdges.contains(pEdge);
          }

          @Override
          public EdgeBasedAbstractionStrategy addVisitedEdge(CFAEdge pEdge) {
            if (visitedEdges.contains(pEdge)) {
              return this;
            }
            return new VisitedEdgesBasedAbstractionStrategy(ImmutableSet.<CFAEdge>builder().addAll(visitedEdges).add(pEdge).build());
          }

          @Override
          public EdgeBasedAbstractionStrategy join(EdgeBasedAbstractionStrategy pStrategy) {
            if (pStrategy == BasicAbstractionStrategies.NEVER || pStrategy == this) {
              return this;
            }
            if (pStrategy instanceof VisitedEdgesBasedAbstractionStrategy) {
              VisitedEdgesBasedAbstractionStrategy other = (VisitedEdgesBasedAbstractionStrategy) pStrategy;
              if (this.visitedEdges == other.visitedEdges || other.visitedEdges.containsAll(this.visitedEdges)) {
                return other;
              }
              if (this.visitedEdges.containsAll(other.visitedEdges)) {
                return this;
              }
              final ImmutableSet<CFAEdge> edges =
                  ImmutableSet.<CFAEdge>builder().addAll(visitedEdges).addAll(other.visitedEdges).build();
              return new VisitedEdgesBasedAbstractionStrategy(edges);
            }
            return BasicAbstractionStrategies.ALWAYS;
          }

          @Override
          public boolean equals(Object pO) {
            if (this == pO) {
              return true;
            }
            if (pO instanceof VisitedEdgesBasedAbstractionStrategy) {
              return visitedEdges.equals(((VisitedEdgesBasedAbstractionStrategy) pO).visitedEdges);
            }
            return false;
          }

          @Override
          public int hashCode() {
            return visitedEdges.hashCode();
          }

        }
        return new VisitedEdgesBasedAbstractionStrategy();
      }

    },

    NEVER {

      @Override
      public EdgeBasedAbstractionStrategy getAbstractionStrategy() {
        return BasicAbstractionStrategies.NEVER;
      }

    };

  }

}
