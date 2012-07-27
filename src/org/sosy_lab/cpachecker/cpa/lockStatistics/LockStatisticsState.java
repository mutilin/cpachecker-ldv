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
package org.sosy_lab.cpachecker.cpa.lockStatistics;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.FormulaReportingState;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock.LockType;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;

import com.google.common.base.Preconditions;

public class LockStatisticsState implements AbstractQueryableState, FormulaReportingState, Serializable {
  private static final long serialVersionUID = -3152134511524554357L;

  private final Set<LockStatisticsLock> Locks;

  public LockStatisticsState() {
    Locks  = new HashSet<LockStatisticsLock>();
  }

  private LockStatisticsState(Set<LockStatisticsLock> pLocks) {
    this.Locks  = pLocks;
  }

  public boolean contains(String variableName) {
    for (LockStatisticsLock mutex : Locks) {
      if (mutex.getName().equals(variableName))
        return true;
    }
    return false;
  }

  public int getSize() {
    return Locks.size();
  }

  public Set<LockStatisticsLock> getLocks() {
    return Locks;
  }

  void add (String lockName, int line, LockType type) {
    LockStatisticsLock tmpMutex = new LockStatisticsLock(lockName, line, type);
    Locks.add(tmpMutex);
  }

  void delete(String lockName) {
    for (LockStatisticsLock mutex : Locks) {
      if (mutex.getName().equals(lockName)){
        Locks.remove(mutex);
        break;
      }
    }
  }

  String print() {
    return Locks.toString();
  }

  /**
   * This element joins this element with another element.
   *
   * @param other the other element to join with this element
   * @return a new element representing the join of this element and the other element
   */
  LockStatisticsState join(LockStatisticsState other) {
    int size = Math.min(Locks.size(), other.Locks.size());

    Set<LockStatisticsLock> newLocks = new HashSet<LockStatisticsLock>(size);

    for (LockStatisticsLock otherLock : other.Locks) {

      if (Locks.contains(otherLock)) {
        newLocks.add(otherLock);
      }
    }

    return new LockStatisticsState(newLocks);
  }

  /**
   * This method decides if this element is less or equal than the other element, based on the order imposed by the lattice.
   *
   * @param other the other element
   * @return true, if this element is less or equal than the other element, based on the order imposed by the lattice
   */
  boolean isLessOrEqual(LockStatisticsState other) {

    // also, this element is not less or equal than the other element, if it contains less elements
    if (Locks.size() < other.Locks.size()) {
      return false;
    }

    // also, this element is not less or equal than the other element,
    // if any one constant's value of the other element differs from the constant's value in this element
    for (LockStatisticsLock Lock : Locks) {
      if (!other.Locks.contains(Lock)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public LockStatisticsState clone() {
    return new LockStatisticsState(new HashSet<LockStatisticsLock>(Locks));
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

    LockStatisticsState otherElement = (LockStatisticsState) other;

    return otherElement.Locks.equals(Locks);
  }

  @Override
  public int hashCode() {
    return Locks.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (LockStatisticsLock lock : Locks) {
      sb.append(lock);
      sb.append(", ");
    }
    //if we have added something, we need to remove last ", "
    if (sb.length() > 2)
      sb.delete(sb.length() - 2, sb.length());
    return sb.append("] size->  ").append(Locks.size()).toString();
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    pProperty = pProperty.trim();

    if (pProperty.startsWith("contains(")) {
      String varName = pProperty.substring("contains(".length(), pProperty.length() - 1);
      return this.Locks.contains(varName);
    } else {
      return checkProperty(pProperty);
    }
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    if (Locks.contains(pProperty))
      return true;
    else
      return false;
  }

  @Override
  public void modifyProperty(String pModification) throws InvalidQueryException {
    Preconditions.checkNotNull(pModification);

    // either "deletelock(lockname)" or "setlock(lockname)"
    String[] statements = pModification.split(";");
    for (int i = 0; i < statements.length; i++) {
      String statement = statements[i].trim().toLowerCase();
      if (statement.startsWith("deletelock(")) {
        if (!statement.endsWith(")")) {
          throw new InvalidQueryException(statement + " should end with \")\"");
        }

        String varName = statement.substring("deletelock(".length(), statement.length() - 1);

        Object x = this.Locks.remove(varName);

        if (x == null) {
          // varname was not present in one of the maps
          // i would like to log an error here, but no logger is available
        }
      }

      else if (statement.startsWith("setlock(")) {
        if (!statement.endsWith(")")) {
          throw new InvalidQueryException(statement + " should end with \")\"");
        }

        String assignment = statement.substring("setlock(".length(), statement.length() - 1);
        String varName = assignment.trim();
        //TODO what line? mutex?
        this.add(varName, 0, LockType.MUTEX);
      }
    }
  }

  @Override
  public String getCPAName() {
    return "LockStatisticsAnalysis";
  }

  @Override
  public Formula getFormulaApproximation(FormulaManager manager) {
    Formula formula = manager.makeTrue();
    //TODO understand this. Make it normally
    for (LockStatisticsLock lock : Locks) {
      Formula var = manager.makeVariable(lock.getName());
      //1 is equal that lock is locked, but it is "ugly hack"
      Formula val = manager.makeNumber("1");
      formula = manager.makeAnd(formula, manager.makeEqual(var, val));
    }

    return formula;
  }
}
