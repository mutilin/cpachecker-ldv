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
package org.sosy_lab.cpachecker.cpa.interval;

import static com.google.common.base.Preconditions.checkState;

import java.io.Serializable;
import java.util.Map;

import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.TargetableWithPredicatedAnalysis;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;

public class IntervalAnalysisState implements AbstractState, TargetableWithPredicatedAnalysis, Serializable,
    LatticeAbstractState<IntervalAnalysisState>{

  private static final long serialVersionUID = -2030700797958100666L;
  private static IntervalTargetChecker targetChecker;
  private static boolean ignoreRefInMerge;

  static void init(Configuration config, boolean pIgnoreRefCount) throws InvalidConfigurationException{
    targetChecker = new IntervalTargetChecker(config);
    ignoreRefInMerge = pIgnoreRefCount;
  }

  /**
   * the intervals of the element
   */
  private PersistentMap<String, Interval> intervals;

  /**
   * the reference counts of the element
   */
  private PersistentMap<String, Integer> referenceCounts;

  /**
   *  This method acts as the default constructor, which initializes the intervals and reference counts to empty maps and the previous element to null.
   */
  public IntervalAnalysisState() {
    intervals = PathCopyingPersistentTreeMap.of();
    referenceCounts = PathCopyingPersistentTreeMap.of();
  }

  /**
   * This method acts as constructor, which initializes the intervals, the reference counts and the previous element to the respective objects.
   *
   * @param intervals the intervals
   * @param referencesMap the reference counts
   * @param previousState from the previous context
   */
  public IntervalAnalysisState(PersistentMap<String, Interval> intervals, PersistentMap<String, Integer> referencesMap) {
    this.intervals        = intervals;

    this.referenceCounts  = referencesMap;
  }

  /**
   * This method returns the intervals of a given variable.
   *
   * @param variableName the name of the variable
   * @return the intervals of the variable
   */
  // see ExplicitState::getValueFor
  public Interval getInterval(String variableName) {
    return intervals.containsKey(variableName)?intervals.get(variableName):Interval.createUnboundInterval();
  }

  /**
   * This method returns the reference count for a given variable.
   *
   * @param variableName of the variable to query the reference count on
   * @return the reference count of the variable, or 0 if the the variable is not yet referenced
   */
  private Integer getReferenceCount(String variableName) {
    return (referenceCounts.containsKey(variableName)) ? referenceCounts.get(variableName) : 0;
  }

  /**
   * This method determines whether or not the reference count for a given variable exceeds a given threshold.
   *
   * @param variableName the name of the variable
   * @param threshold the threshold
   * @return true, if the reference count of the variable exceeds the given threshold, else false
   */
  @Deprecated
  public boolean exceedsThreshold(String variableName, Integer threshold) {
    Integer referenceCount = (referenceCounts.containsKey(variableName)) ? referenceCounts.get(variableName) : 0;

    return referenceCount > threshold;
  }

  /**
   * This method determines if this element contains an interval for a variable.
   *
   * @param variableName the name of the variable
   * @return true, if this element contains an interval for the given variable
   */
  public boolean contains(String variableName) {
    return intervals.containsKey(variableName);
  }

  /**
   * This method assigns an interval to a variable and puts it in the map.
   *
   * @param variableName name of the variable
   * @param interval the interval to be assigned
   * @param pThreshold threshold from property explicitAnalysis.threshold
   * @return this
   */
  // see ExplicitState::assignConstant
  public IntervalAnalysisState addInterval(String variableName, Interval interval, int pThreshold) {
    if (interval.isUnbound()) {
      removeInterval(variableName);
      return this;
    }
    // only add the interval if it is not already present
    if (!intervals.containsKey(variableName) || !intervals.get(variableName).equals(interval)) {
      int referenceCount = getReferenceCount(variableName);

      if (pThreshold == -1 || referenceCount < pThreshold) {
        referenceCounts = referenceCounts.putAndCopy(variableName, referenceCount + 1);

        intervals = intervals.putAndCopy(variableName, interval);
      } else {
        removeInterval(variableName);
      }
    }

    return this;
  }

  /**
   * This method removes the interval for a given variable.
   *
   * @param variableName the name of the variable whose interval should be removed
   * @return this
   */
  // see ExplicitState::forget
  public IntervalAnalysisState removeInterval(String variableName) {
    if (intervals.containsKey(variableName)) {
      intervals = intervals.removeAndCopy(variableName);
    }

    return this;
  }

  public void dropFrame(String pCalledFunctionName) {
    for (String variableName : intervals.keySet()) {
      if (variableName.startsWith(pCalledFunctionName+"::")) {
        removeInterval(variableName);
      }
    }
  }

  /**
   * This element joins this element with a reached state.
   *
   * @param reachedState the reached state to join this element with
   * @return a new state representing the join of this element and the reached state
   */
  @Override
  public IntervalAnalysisState join(IntervalAnalysisState reachedState) {
    boolean changed = false;
    PersistentMap<String, Interval> newIntervals = PathCopyingPersistentTreeMap.of();
    PersistentMap<String, Integer> newReferences = referenceCounts;

    int newRefCount;
    Interval mergedInterval;

    for (String variableName : reachedState.intervals.keySet()) {
      if (intervals.containsKey(variableName)) {
        // update the interval
        mergedInterval = getInterval(variableName).union(reachedState.getInterval(variableName));
        if (mergedInterval != reachedState.getInterval(variableName)) {
          changed = true;
        }

        if (!mergedInterval.isUnbound()) {
          newIntervals = newIntervals.putAndCopy(variableName, mergedInterval);
        }

        // update the references
        newRefCount = Math.max(getReferenceCount(variableName), reachedState.getReferenceCount(variableName));
        if (!ignoreRefInMerge && newRefCount > reachedState.getReferenceCount(variableName)) {
          changed = true;
        }
        newReferences = newReferences.putAndCopy(variableName, newRefCount);
      } else {
        newReferences = newReferences.putAndCopy(variableName, reachedState.getReferenceCount(variableName));
        changed = true;
      }
    }

    if (changed) {
      return new IntervalAnalysisState(newIntervals, newReferences);
    } else {
      return reachedState;
    }
  }

  /**
   * This method decides if this element is less or equal than the reached state, based on the order imposed by the lattice.
   *
   * @param reachedState the reached state
   * @return true, if this element is less or equal than the reached state, based on the order imposed by the lattice
   */
  @Override
  public boolean isLessOrEqual(IntervalAnalysisState reachedState) {
    if (intervals.equals(reachedState.intervals)) { return true; }
    // this element is not less or equal than the reached state, if it contains less intervals
    if (intervals.size() < reachedState.intervals.size()) {
      return false;
    }

    // also, this element is not less or equal than the reached state, if any one interval of the reached state is not contained in this element,
    // or if the interval of the reached state is not wider than the respective interval of this element
    for (String variableName : reachedState.intervals.keySet()) {
      if (!intervals.containsKey(variableName) || !reachedState.getInterval(variableName).contains(getInterval(variableName))) {
        return false;
      }
    }

    // else, this element < reached state on the lattice
    return true;
  }

  public static IntervalAnalysisState copyOf(IntervalAnalysisState old) {
    IntervalAnalysisState newElement = new IntervalAnalysisState(old.intervals,old.referenceCounts);
    return newElement;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || !getClass().equals(other.getClass())) {
      return false;
    }

    IntervalAnalysisState otherElement = (IntervalAnalysisState)other;

    if (intervals.size() != otherElement.intervals.size()) {
      return false;
    }

    for (String variableName : intervals.keySet()) {
      if (!otherElement.intervals.containsKey(variableName) || !otherElement.intervals.get(variableName).equals(intervals.get(variableName))) {
        return false;
      }
    }

    return true;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    return intervals.hashCode();
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");

    for (Map.Entry<String, Interval> entry: intervals.entrySet()) {
      String key = entry.getKey();
      sb.append(" <");
      sb.append(key);
      sb.append(" = ");
      sb.append(entry.getValue());
      sb.append(" :: ");
      sb.append(getReferenceCount(key));
      sb.append(">\n");
    }

    return sb.append("] size->  ").append(intervals.size()).toString();
  }

  @Override
  public boolean isTarget() {
   return targetChecker == null? false: targetChecker.isTarget(this);
  }

  @Override
  public String getViolatedPropertyDescription() throws IllegalStateException {
    checkState(isTarget());
    return "";
  }

  @Override
  public BooleanFormula getErrorCondition(FormulaManagerView pFmgr) {
    return targetChecker== null? pFmgr.getBooleanFormulaManager().makeBoolean(false):targetChecker.getErrorCondition(this, pFmgr);
  }
}
