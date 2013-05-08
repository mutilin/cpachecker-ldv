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
package org.sosy_lab.cpachecker.cpa.explicit;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.FormulaReportingState;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.RationalFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.RationalFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

public class ExplicitState implements AbstractQueryableState, FormulaReportingState, Serializable {
  private static final long serialVersionUID = -3152134511524554357L;

  /**
   * the map that keeps the name of variables and their constant values
   */
  private PersistentMap<String, Long> constantsMap;

  /**
   * the current delta of this state to the previous state
   */
  private Set<String> delta;

  public ExplicitState() {
    constantsMap = PathCopyingPersistentTreeMap.of();
  }

  public ExplicitState(PersistentMap<String, Long> constantsMap) {
    this.constantsMap = constantsMap;
  }

  /**
   * This method assigns a value to the variable and puts it in the map.
   *
   * @param variableName name of the variable.
   * @param value value to be assigned.
   */
  void assignConstant(String variableName, Long value) {
    constantsMap = constantsMap.putAndCopy(checkNotNull(variableName), checkNotNull(value));
  }

  /**
   * This method removes a variable and its value from the underlying map.
   *
   * @param variableName the name of the variable to be removed
   */
  public void forget(String variableName) {
    constantsMap = constantsMap.removeAndCopy(variableName);
  }

  /**
   * This method removes all variables and their respective values from the underlying map.
   *
   * @param variableNames the names of the variables to be removed
   */
  void removeAll(Collection<String> variableNames) {
    for(String variableName : variableNames) {
      constantsMap = constantsMap.removeAndCopy(variableName);
    }
  }

  /**
   * This method drops all entries belonging to the stack frame of a function. This method should be called right before leaving a function.
   *
   * @param functionName the name of the function that is about to be left
   */
  void dropFrame(String functionName) {
    List<String> toDropAll = new ArrayList<>();

    for (String variableName : constantsMap.keySet()) {
      if (variableName.startsWith(functionName + "::")) {
        toDropAll.add(variableName);
      }
    }

    for (String variableNameToDrop : toDropAll) {
      constantsMap = constantsMap.removeAndCopy(variableNameToDrop);
    }
  }

  /**
   * This method returns the value for the given variable.
   *
   * @param variableName the name of the variable for which to get the value
   * @throws NullPointerException - if no value is present in this state for the given variable
   * @return the value associated with the given variable
   */
  public Long getValueFor(String variableName) {
    return checkNotNull(constantsMap.get(variableName));
  }

  /**
   * This method checks whether or not the given variable is contained in this state.
   *
   * @param variableName the name of variable to check for
   * @return true, if the variable is contained, else false
   */
  public boolean contains(String variableName) {
    return constantsMap.containsKey(variableName);
  }

  /**
   * This method determines the total number of variables contained in this state.
   *
   * @return the total number of variables contained in this state
   */
  public int getSize() {
    return constantsMap.size();
  }

  /**
   * This method determines the number of global variables contained in this state.
   *
   * @return the number of global variables contained in this state
   */
  int getNumberOfGlobalVariables() {
    int numberOfGlobalVariables = 0;

    for (String variableName : constantsMap.keySet()) {
      if (variableName.contains("::")) {
        numberOfGlobalVariables++;
      }
    }

    return numberOfGlobalVariables;
  }

  /**
   * This element joins this element with another element.
   *
   * @param reachedState the other element to join with this element
   * @return a new state representing the join of this element and the other element
   */
  ExplicitState join(ExplicitState reachedState) {
    PersistentMap<String, Long> newConstantsMap = PathCopyingPersistentTreeMap.of();

    for (Map.Entry<String, Long> otherEntry : reachedState.constantsMap.entrySet()) {
      String key = otherEntry.getKey();

      if (equal(otherEntry.getValue(), constantsMap.get(key))) {
        newConstantsMap = newConstantsMap.putAndCopy(key, otherEntry.getValue());
      }
    }

    // return the reached state if both maps are equal
    if (newConstantsMap.size() == reachedState.constantsMap.size()) {
      return reachedState;
    } else {
      return new ExplicitState(newConstantsMap);
    }
  }

  /**
   * This method decides if this element is less or equal than the other element, based on the order imposed by the lattice.
   *
   * @param other the other element
   * @return true, if this element is less or equal than the other element, based on the order imposed by the lattice
   */
  boolean isLessOrEqual(ExplicitState other) {

    // also, this element is not less or equal than the other element, if it contains less elements
    if (constantsMap.size() < other.constantsMap.size()) {
      return false;
    }

    // also, this element is not less or equal than the other element,
    // if any one constant's value of the other element differs from the constant's value in this element
    for (Map.Entry<String, Long> otherEntry : other.constantsMap.entrySet()) {
      String key = otherEntry.getKey();

      if (!otherEntry.getValue().equals(constantsMap.get(key))) {
        return false;
      }
    }

    return true;
  }

  @Override
  public ExplicitState clone() {
    return new ExplicitState(PathCopyingPersistentTreeMap.copyOf(constantsMap));
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null) {
      return false;
    }

    if (!getClass().equals(other.getClass())) {
      return false;
    }

    ExplicitState otherElement = (ExplicitState) other;

    return otherElement.constantsMap.equals(constantsMap);
  }

  @Override
  public int hashCode() {
    return constantsMap.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (Map.Entry<String, Long> entry : constantsMap.entrySet()) {
      String key = entry.getKey();
      sb.append(" <");
      sb.append(key);
      sb.append(" = ");
      sb.append(entry.getValue());
      sb.append(">\n");
    }

    return sb.append("] size->  ").append(constantsMap.size()).toString();
  }

  /**
   * This method returns a more compact string representation of the state, compared to toString().
   *
   * @return a more compact string representation of the state
   */
  public String toCompactString() {
    StringBuilder sb = new StringBuilder();

    sb.append("[");
    Joiner.on(", ").withKeyValueSeparator("=").appendTo(sb, constantsMap);
    sb.append("]");

    return sb.toString();
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    pProperty = pProperty.trim();

    if (pProperty.startsWith("contains(")) {
      String varName = pProperty.substring("contains(".length(), pProperty.length() - 1);
      return this.constantsMap.containsKey(varName);
    } else {
      String[] parts = pProperty.split("==");
      if (parts.length != 2) {
        Long value = this.constantsMap.get(pProperty);
        if (value != null) {
          return value;
        } else {
          throw new InvalidQueryException("The Query \"" + pProperty + "\" is invalid. Could not find the variable \""
              + pProperty + "\"");
        }
      } else {
        return checkProperty(pProperty);
      }
    }
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    // e.g. "x==5" where x is a variable. Returns if 5 is the associated constant
    String[] parts = pProperty.split("==");

    if (parts.length != 2) {
      throw new InvalidQueryException("The Query \"" + pProperty
          + "\" is invalid. Could not split the property string correctly.");
    } else {
      Long value = this.constantsMap.get(parts[0]);

      if (value == null) {
        return false;
      } else {
        try {
          return value.longValue() == Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
          // The command might contains something like "main::p==cmd" where the user wants to compare the variable p to the variable cmd (nearest in scope)
          // perhaps we should omit the "main::" and find the variable via static scoping ("main::p" is also not intuitive for a user)
          // TODO: implement Variable finding via static scoping
          throw new InvalidQueryException("The Query \"" + pProperty + "\" is invalid. Could not parse the long \""
              + parts[1] + "\"");
        }
      }
    }
  }

  @Override
  public void modifyProperty(String pModification) throws InvalidQueryException {
    Preconditions.checkNotNull(pModification);

    // either "deletevalues(methodname::varname)" or "setvalue(methodname::varname:=1929)"
    String[] statements = pModification.split(";");
    for (int i = 0; i < statements.length; i++) {
      String statement = statements[i].trim().toLowerCase();
      if (statement.startsWith("deletevalues(")) {
        if (!statement.endsWith(")")) {
          throw new InvalidQueryException(statement + " should end with \")\"");
        }

        String varName = statement.substring("deletevalues(".length(), statement.length() - 1);

        if (contains(varName)) {
          forget(varName);
        } else {
          // varname was not present in one of the maps
          // i would like to log an error here, but no logger is available
        }
      }

      else if (statement.startsWith("setvalue(")) {
        if (!statement.endsWith(")")) {
          throw new InvalidQueryException(statement + " should end with \")\"");
        }

        String assignment = statement.substring("setvalue(".length(), statement.length() - 1);
        String[] assignmentParts = assignment.split(":=");

        if (assignmentParts.length != 2) {
          throw new InvalidQueryException("The Query \"" + pModification
              + "\" is invalid. Could not split the property string correctly.");
        } else {
          String varName = assignmentParts[0].trim();
          try {
            long newValue = Long.parseLong(assignmentParts[1].trim());
            this.assignConstant(varName, newValue);
          } catch (NumberFormatException e) {
            throw new InvalidQueryException("The Query \"" + pModification
                + "\" is invalid. Could not parse the long \"" + assignmentParts[1].trim() + "\"");
          }
        }
      }
    }
  }

  @Override
  public String getCPAName() {
    return "ExplicitAnalysis";
  }

  @Override
  public BooleanFormula getFormulaApproximation(FormulaManagerView manager) {
    BooleanFormulaManager bfmgr = manager.getBooleanFormulaManager();
    RationalFormulaManager nfmgr = manager.getRationalFormulaManager();
    BooleanFormula formula = bfmgr.makeBoolean(true);

    for (Map.Entry<String, Long> entry : constantsMap.entrySet()) {
      RationalFormula var = nfmgr.makeVariable(entry.getKey());
      RationalFormula val = nfmgr.makeNumber(entry.getValue());
      formula = bfmgr.and(formula, nfmgr.equal(var, val));
    }

    return formula;
  }

  /**
   * This method determines the set of variable names that are in the other state but not in this,
   * or that are in both, but differ in their value.
   *
   * @param other the other state for which to get the difference
   * @return the set of variable names that differ
   */
  public Set<String> getDifference(ExplicitState other) {
    Set<String> difference = new HashSet<>();

    for (String variableName : other.constantsMap.keySet()) {
      if (!contains(variableName)) {
        difference.add(variableName);
      } else if (!getValueFor(variableName).equals(other.getValueFor(variableName))) {
        difference.add(variableName);
      }
    }

    return difference;
  }

  /**
   * This method returns the current delta of this state.
   *
   * @return the current delta of this state
   */
  Collection<String> getDelta() {
    return ImmutableSet.copyOf(delta);
  }

  /**
   * This method sets the delta of this state, in relation to the given other state.
   *
   * This is used for a more efficient abstraction computation, where only the delta of a state is considered.
   *
   * @param other the state to which to compute the delta
   */
  void setDelta(ExplicitState other) {
    delta = other.getDifference(this);
  }

  /**
   * This method resets the delta of this state.
   */
  void clearDelta() {
    delta = null;
  }

  /**
   * This method adds the key-value-pairs of this state to the given value mapping and returns the new mapping.
   *
   * @param valueMapping the mapping from variable name to the set of values of this variable
   * @return the new mapping
   */
  public Multimap<String, Long> addToValueMapping(Multimap<String, Long> valueMapping) {
    for (Map.Entry<String, Long> entry : constantsMap.entrySet()) {
      valueMapping.put(entry.getKey(), entry.getValue());
    }

    return valueMapping;
  }

  /**
   * This method returns the set of tracked variables by this state.
   *
   * @return the set of tracked variables by this state
   */
  ImmutableCollection<String> getTrackedVariableNames() {
    return ImmutableSet.copyOf(constantsMap.keySet());
  }

  /**
   * This method returns the internal mapping of this state.
   *
   * @return the internal mapping of this state
   * @TODO: eliminate this - breaks encapsulation
   */
  Map<String, Long> getConstantsMap() {
    return constantsMap;
  }
}
