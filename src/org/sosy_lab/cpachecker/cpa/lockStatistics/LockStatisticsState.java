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
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock.LockType;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;

import com.google.common.base.Preconditions;

public class LockStatisticsState implements AbstractQueryableState, Serializable {
  private static final long serialVersionUID = -3152134511524554357L;

  private final Set<LockStatisticsLock> GlobalLocks;
  private final Set<LockStatisticsLock> LocalLocks;

  public LockStatisticsState() {
    GlobalLocks  = new HashSet<LockStatisticsLock>();
    LocalLocks = new HashSet<LockStatisticsLock>();
  }

  private LockStatisticsState(Set<LockStatisticsLock> gLocks, Set<LockStatisticsLock> lLocks) {
    this.GlobalLocks  = gLocks;
    this.LocalLocks  = lLocks;
  }

  public boolean contains(String variableName) {
    for (LockStatisticsLock mutex : GlobalLocks) {
      if (mutex.getName().equals(variableName))
        return true;
    }
    for (LockStatisticsLock mutex : LocalLocks) {
      if (mutex.getName().equals(variableName))
        return true;
    }
    return false;
  }

  public int getGlobalSize() {
    return GlobalLocks.size();
  }

  public int getLocalSize() {
    return LocalLocks.size();
  }

  public Set<LockStatisticsLock> getGlobalLocks() {
    return GlobalLocks;
  }

  public Set<LockStatisticsLock> getLocalLocks() {
    return LocalLocks;
  }

  void addLocal(String lockName, int line, String pCurrentFunction, CallstackState state) {
    LockStatisticsLock tmpMutex = new LockStatisticsLock(lockName, line, LockType.LOCAL_LOCK, pCurrentFunction, state);
    LocalLocks.add(tmpMutex);
  }

  void addGlobal(String lockName, int line, CallstackState state) {
    LockStatisticsLock tmpMutex = new LockStatisticsLock(lockName, line, LockType.GLOBAL_LOCK, state);
    GlobalLocks.add(tmpMutex);
  }

  LockStatisticsState removeLocal(String functionName) {
    LockStatisticsState newLock = this.clone();

    for (LockStatisticsLock mutex : newLock.LocalLocks) {
      if (mutex.getFunctionName().equals(functionName)){
        newLock.LocalLocks.remove(mutex);
      }
    }

    return newLock;
  }

  void delete(String lockName) {
    for (LockStatisticsLock mutex : GlobalLocks) {
      if (mutex.getName().equals(lockName)){
        GlobalLocks.remove(mutex);
        return;
      }
    }
    for (LockStatisticsLock mutex : LocalLocks) {
      if (mutex.getName().equals(lockName)){
        LocalLocks.remove(mutex);
        return;
      }
    }
  }

  String print() {
    return "Global locks: " + GlobalLocks.toString() + "\nLocal locks: " + LocalLocks.toString();
  }

  /**
   * This element joins this element with another element.
   *
   * @param other the other element to join with this element
   * @return a new element representing the join of this element and the other element
   */
  LockStatisticsState join(LockStatisticsState other) {
    Set<LockStatisticsLock> newGlobalLocks = new HashSet<LockStatisticsLock>();
    Set<LockStatisticsLock> newLocalLocks = new HashSet<LockStatisticsLock>();

    for (LockStatisticsLock otherLock : other.GlobalLocks) {

      if (GlobalLocks.contains(otherLock)) {
        newGlobalLocks.add(otherLock);
      }
    }

    for (LockStatisticsLock otherLock : other.LocalLocks) {

      if (LocalLocks.contains(otherLock)) {
        newLocalLocks.add(otherLock);
      }
    }

    return new LockStatisticsState(newGlobalLocks, newLocalLocks);
  }

  LockStatisticsState combine(LockStatisticsState other) {
    Set<LockStatisticsLock> newGlobalLocks = new HashSet<LockStatisticsLock>();
    Set<LockStatisticsLock> newLocalLocks = new HashSet<LockStatisticsLock>();

    for (LockStatisticsLock lock : other.GlobalLocks) {
      newGlobalLocks.add(lock);
    }
    for (LockStatisticsLock lock : other.LocalLocks) {
      newLocalLocks.add(lock);
    }
    return new LockStatisticsState(newGlobalLocks, newLocalLocks);
  }

  /**
   * This method decides if this element is less or equal than the other element, based on the order imposed by the lattice.
   *
   * @param other the other element
   * @return true, if this element is less or equal than the other element, based on the order imposed by the lattice
   */
  boolean isLessOrEqual(LockStatisticsState other) {

    // also, this element is not less or equal than the other element, if it contains less elements
    if (GlobalLocks.size() < other.GlobalLocks.size()) {
      return false;
    }

    if (LocalLocks.size() < other.LocalLocks.size()) {
      return false;
    }

    // also, this element is not less or equal than the other element,
    // if any one constant's value of the other element differs from the constant's value in this element
    for (LockStatisticsLock Lock : GlobalLocks) {
      if (!other.GlobalLocks.contains(Lock)) {
        return false;
      }
    }

    for (LockStatisticsLock Lock : LocalLocks) {
      if (!other.LocalLocks.contains(Lock)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public LockStatisticsState clone() {
    return new LockStatisticsState(new HashSet<LockStatisticsLock>(GlobalLocks), new HashSet<LockStatisticsLock>(LocalLocks));
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((GlobalLocks == null) ? 0 : GlobalLocks.hashCode());
    result = prime * result + ((LocalLocks == null) ? 0 : LocalLocks.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    LockStatisticsState other = (LockStatisticsState) obj;
    if (GlobalLocks == null) {
      if (other.GlobalLocks != null)
        return false;
    } else if (!GlobalLocks.equals(other.GlobalLocks))
      return false;
    if (LocalLocks == null) {
      if (other.LocalLocks != null)
        return false;
    } else if (!LocalLocks.equals(other.LocalLocks))
      return false;
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    for (LockStatisticsLock lock : GlobalLocks) {
      sb.append(lock);
      sb.append(", ");
    }
    for (LockStatisticsLock lock : LocalLocks) {
      sb.append(lock);
      sb.append(", ");
    }
    //if we have added something, we need to remove last ", "
    if (sb.length() > 2)
      sb.delete(sb.length() - 2, sb.length());
    sb.append("}");
    return sb.toString();
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    pProperty = pProperty.trim();

    if (pProperty.startsWith("contains(")) {
      String varName = pProperty.substring("contains(".length(), pProperty.length() - 1);
      return this.contains(varName);
    } else {
      return checkProperty(pProperty);
    }
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    if (this.contains(pProperty))
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

        Object x = this.GlobalLocks.remove(varName);

        if (x == null) x = this.LocalLocks.remove(varName);

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
        //this.addGlobal(varName, 0);
      }
    }
  }

  @Override
  public String getCPAName() {
    return "LockStatisticsAnalysis";
  }

}
