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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock.LockType;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;

import com.google.common.base.Preconditions;

public class LockStatisticsState implements AbstractQueryableState, Serializable {
  private static final long serialVersionUID = -3152134511524554357L;

  private final Set<LockStatisticsLock> locks;

  public LockStatisticsState() {
    locks  = new HashSet<LockStatisticsLock>();
  }

  private LockStatisticsState(Set<LockStatisticsLock> gLocks) {
    this.locks  = gLocks;
  }

  public boolean contains(String variableName) {
    for (LockStatisticsLock mutex : locks) {
      if (mutex.getName().equals(variableName))
        return true;
    }
    return false;
  }

  public int getSize() {
    return locks.size();
  }

  public Set<LockStatisticsLock> getLocks() {
    return locks;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    LockStatisticsLock tmpLock;
    Map<String, LockStatisticsLock> locksToString = new HashMap<String, LockStatisticsLock>();

    for (LockStatisticsLock lock : locks) {
      if (!locksToString.containsKey(lock.getName())) {
        locksToString.put(lock.getName(), lock);
      } else {
        tmpLock = locksToString.get(lock.getName());
        if (tmpLock.getRecursiveCounter() < lock.getRecursiveCounter())
          locksToString.put(lock.getName(), lock);
      }
    }

    for (String lockName : locksToString.keySet()) {
      sb.append(locksToString.get(lockName).toString() + ", ");
    }
    if (locks.size() > 0)
      sb.delete(sb.length() - 2, sb.length());
    return sb.toString();
  }

  void add(String lockName, int line, CallstackState state, String variable) {
    LockStatisticsLock tmpMutex;
    int counter = 0;

    for (LockStatisticsLock tmpLock : locks) {
      if (tmpLock.getName().equals(lockName) && tmpLock.getVariable().equals(variable)) {
        counter++;
      }
    }
    tmpMutex = new LockStatisticsLock(lockName, line, LockType.GLOBAL_LOCK, state, counter, variable);
    locks.add(tmpMutex);
  }

  void add(LockStatisticsLock l) {
    locks.add(l);
  }

  void delete(String lockName, String variable, boolean all) {
    LockStatisticsLock lockToDelete = null;
    int counter = 0;
    for (LockStatisticsLock mutex : locks) {
      if (mutex.getName().equals(lockName) && counter <= mutex.getRecursiveCounter()
          && (mutex.getVariable().equals(variable) || all)) {
        lockToDelete = mutex;
        counter = mutex.getRecursiveCounter();
      }
    }
    if (lockToDelete != null)
      locks.remove(lockToDelete);
  }

  void reset(String lockName) {
    Set<LockStatisticsLock> toDelete = new HashSet<LockStatisticsLock>();
    for (LockStatisticsLock mutex : locks) {
      if (mutex.getName().equals(lockName)) {
        toDelete.add(mutex);
      }
    }
    for (LockStatisticsLock lock : toDelete) {
      locks.remove(lock);
    }
  }

  void set(String lockName, int num, int line, CallstackState state, String variable) {
    int counter = 0;

    for (LockStatisticsLock lock : locks) {
      if (lock.getName().equals(lockName)) {
        counter++;
      }
    }

    for (int i = counter; i < num; i++) {
      locks.add(new LockStatisticsLock(lockName, line, LockType.GLOBAL_LOCK, state, i, variable));
    }
  }

  public int getCounter(String lockName) {
    int counter = 0;
    for (LockStatisticsLock lock : locks) {
      if (lock.getName().equals(lockName)) counter++;
    }
    return counter;
  }

  //this function is used only in debugging. Do not delete!
  public String getAllLines(String lockName) {
    StringBuilder sb = new StringBuilder();
    for (LockStatisticsLock lock : locks) {
      if (lock.getName().equals(lockName)) sb.append(lock.getLine().getLine() + ", ");
    }

    if (sb.length() > 2)
      sb.delete(sb.length() - 2, sb.length());

    return sb.toString();
  }

  /**
   * This element joins this element with another element.
   *
   * @param other the other element to join with this element
   * @return a new element representing the join of this element and the other element
   */
  LockStatisticsState join(LockStatisticsState other) {
    Set<LockStatisticsLock> newGlobalLocks = new HashSet<LockStatisticsLock>();

    for (LockStatisticsLock otherLock : other.locks) {

      if (locks.contains(otherLock)) {
        newGlobalLocks.add(otherLock);
      }
    }

    return new LockStatisticsState(newGlobalLocks/*, newLocalLocks*/);
  }

  /*LockStatisticsState combine(LockStatisticsState other) {
    Set<LockStatisticsLock> newGlobalLocks = new HashSet<LockStatisticsLock>();
    //Set<LockStatisticsLock> newLocalLocks = new HashSet<LockStatisticsLock>();

    for (LockStatisticsLock lock : other.locks) {
      newGlobalLocks.add(lock);
    }
    /*for (LockStatisticsLock lock : other.LocalLocks) {
      newLocalLocks.add(lock);
    }
    return new LockStatisticsState(newGlobalLocks/*, newLocalLocks);
  }*/

  /**
   * This method decides if this element is less or equal than the other element, based on the order imposed by the lattice.
   *
   * @param other the other element
   * @return true, if this element is less or equal than the other element, based on the order imposed by the lattice
   */
  boolean isLessOrEqual(LockStatisticsState other) {

    // also, this element is not less or equal than the other element, if it contains less elements
    /*if (locks.size() < other.locks.size()) {
      return false;
    }*/

    /*if (LocalLocks.size() < other.LocalLocks.size()) {
      return false;
    }*/
    if (locks.size() == 0 && other.locks.size() > 0)
      return false;

    // also, this element is not less or equal than the other element,
    // if any one constant's value of the other element differs from the constant's value in this element
ok: for (LockStatisticsLock Lock : locks) {
      for (LockStatisticsLock lock : other.locks) {
        if (Lock.getName().equals(lock.getName())) continue ok;
      }
      return false;
    }

    /*for (LockStatisticsLock Lock : LocalLocks) {
      if (!other.LocalLocks.contains(Lock)) {
        return false;
      }
    }*/

    return true;
  }

  @Override
  public LockStatisticsState clone() {
    return new LockStatisticsState(new HashSet<LockStatisticsLock>(locks)/*, new HashSet<LockStatisticsLock>(LocalLocks)*/);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((locks == null) ? 0 : locks.hashCode());
    //result = prime * result + ((LocalLocks == null) ? 0 : LocalLocks.hashCode());
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
    if (locks == null) {
      if (other.locks != null)
        return false;
    } else if (!locks.equals(other.locks))
      return false;
    /*if (LocalLocks == null) {
      if (other.LocalLocks != null)
        return false;
    } else if (!LocalLocks.equals(other.LocalLocks))
      return false;*/
    return true;
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

        Object x = this.locks.remove(varName);

        //if (x == null) x = this.LocalLocks.remove(varName);

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
