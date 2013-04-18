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
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock.LockType;
import org.sosy_lab.cpachecker.cpa.usageStatistics.LineInfo;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;

import com.google.common.base.Preconditions;

public class LockStatisticsState implements AbstractQueryableState, Serializable {
  private static final long serialVersionUID = -3152134511524554357L;

  private final Set<LockStatisticsLock> locks;
  //if we need restore state, we save it here
  //Used for function annotations like annotate.function_name.restore
  private LockStatisticsState toRestore;

  public LockStatisticsState() {
    locks  = new HashSet<>();
    toRestore = null;
  }

  private LockStatisticsState(Set<LockStatisticsLock> gLocks, LockStatisticsState state) {
    this.locks  = gLocks;
    toRestore = state;
  }

  public boolean contains(String lockName, String variableName) {
    for (LockStatisticsLock lock : locks) {
      if (lock.hasEqualNameAndVariable(lockName, variableName))
        return true;
    }
    return false;
  }

  public boolean contains(String lockName) {
    for (LockStatisticsLock lock : locks) {
      if (lock.hasEqualNameAndVariable(lockName, null))
        return true;
    }
    return false;
  }

  public boolean contains(LockStatisticsLock lock) {
    return locks.contains(lock);
  }

  public int getSize() {
    return locks.size();
  }

  public Set<LockStatisticsLock> getLocks() {
    return locks;
  }

  public LockStatisticsState getRestoreState() {
    return toRestore;
  }

  public void setRestoreState(LockStatisticsState state) {
    toRestore = state;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    LockStatisticsLock tmpLock;
    Map<String, LockStatisticsLock> locksToString = new HashMap<>();

    for (LockStatisticsLock lock : locks) {
      if (!locksToString.containsKey(lock.getName() + lock.getVariable())) {
        locksToString.put(lock.getName() + lock.getVariable(), lock);
      } else {
        tmpLock = locksToString.get(lock.getName() + lock.getVariable());
        if (tmpLock.getRecursiveCounter() < lock.getRecursiveCounter())
          locksToString.put(lock.getName() + lock.getVariable(), lock);
      }
    }

    for (String lockName : locksToString.keySet()) {
      sb.append(locksToString.get(lockName).toString() + ", ");
    }
    if (locks.size() > 0) {
      sb.delete(sb.length() - 2, sb.length());
    } else {
      sb.append("Without locks");
    }
    return sb.toString();
  }

  void add(String lockName, int line, CallstackState state, String variable, LogManager logger) {
    boolean b;
    String locksBefore = locks.toString();

    LockStatisticsLock oldLock = findLock(lockName, variable);
    if(oldLock != null) {
      LockStatisticsLock newLock = oldLock.addAccessPointer(new AccessPoint(new LineInfo(line), state));
      b = locks.remove(oldLock);
      assert b;
      b = locks.add(newLock);
      assert b;
    } else {
      LockStatisticsLock tmpMutex = new LockStatisticsLock(lockName, line, LockType.GLOBAL_LOCK, state, variable);
      b = locks.add(tmpMutex);
    }
    if(b) {
      logger.log(Level.FINER, "Locks before: " + locksBefore);
      logger.log(Level.FINER, "Locks after: " + locks);
    }
  }

  public LockStatisticsLock findLock(String lockName, String variable) {
    for (LockStatisticsLock lock : locks) {
      if (lock.hasEqualNameAndVariable(lockName, variable)) {
        return lock;
      }
    }
    return null;
  }

  void add(LockStatisticsLock l, LogManager logger) {
	  String locksBefore = locks.toString();
	  boolean b = locks.add(l);
	  if(b && logger != null) {
		  logger.log(Level.FINER, "Locks before: " + locksBefore);
		  logger.log(Level.FINER, "Locks after: " + locks);
	  }
  }

  private void delete(LockStatisticsLock oldLock, LogManager logger) {
    String locksBefore = locks.toString();
    boolean b = locks.remove(oldLock);
    assert b;
    LockStatisticsLock newLock = oldLock.removeLastAccessPointer();
    if (newLock != null) {
      locks.add(newLock);
      if (logger != null) {
        logger.log(Level.FINER, "Locks before: " + locksBefore);
        logger.log(Level.FINER, "Locks after: " + locks);
      }
    }
  }

  void delete(String lockName, String variable, LogManager logger) {
	  LockStatisticsLock oldLock = findLock(lockName, variable);
	  if (oldLock == null)
	    //TODO what should we do, if we've lost a lock?
	    return;
    delete(oldLock, logger);
  }

  void reset(String lockName, String var, LogManager logger) {
    LockStatisticsLock lock = findLock(lockName, var);
    reset(lock, logger);
  }

  private void reset(LockStatisticsLock lock, LogManager logger) {
    String locksBefore = locks.toString();
    boolean b = locks.remove(lock);
    if (b && logger != null) {
      logger.log(Level.FINER, "Locks before: " + locksBefore);
      logger.log(Level.FINER, "Locks after: " + locks);
    }
  }

  LockStatisticsState reset(Map<String, String> pResetLocks, LogManager pLogger) {
    LockStatisticsState newState = this.clone();
    for (String lockName : pResetLocks.keySet()) {
      newState.reset(lockName, pResetLocks.get(lockName), pLogger);
    }
    return newState;
  }

  void set(String lockName, int num, int line, CallstackState state, String variable) {
    //num can be equal -1, this means, that in origin file it is 0 and we should delete locks
    LockStatisticsLock oldLock = findLock(lockName, variable);
    LockStatisticsLock newLock;

    if (oldLock != null) {
      newLock = oldLock;
      if (num > oldLock.getRecursiveCounter()) {
        newLock = oldLock.addRecursiveAccessPointer(num - oldLock.getRecursiveCounter(),
            new AccessPoint(new LineInfo(line), state));
      } else if (num < oldLock.getRecursiveCounter()) {
        for (int i = 0; i < oldLock.getRecursiveCounter() - num; i++) {
          newLock = newLock.removeLastAccessPointer();
        }
      }
      locks.remove(oldLock);
      if (newLock != null)
        locks.add(newLock);
    } else if (num > -1) {
      newLock = new LockStatisticsLock(lockName, line, LockType.GLOBAL_LOCK, state, variable);
      newLock = newLock.addRecursiveAccessPointer(num, new AccessPoint(new LineInfo(line), state));
      if (newLock != null)
        locks.add(newLock);
    }
  }

  LockStatisticsState restore(LockStatisticsState restoredState, Map<String, String> lockNames, LogManager logger) {
    if (lockNames.size() == 0) {
      //we didn't specify, which locks we would like to restore, so, restore all;
      return restoredState;
    }
    if (this.locks.equals(restoredState.locks))
      //only for optimization. We don't compare restoreState, because in this it can be only more complex.
      return this;

    LockStatisticsState newState = this.clone();
    for (String lockName : lockNames.keySet()) {
      LockStatisticsLock oldLock = this.findLock(lockName, lockNames.get(lockName));
      LockStatisticsLock newLock = restoredState.findLock(lockName, lockNames.get(lockName));
      if (oldLock != null) {
        newState.reset(oldLock, logger);
        if (newLock != null) {
          newState.add(newLock, logger);
        }
      } else if (newLock != null){
        newState.add(newLock, logger);
      }
    }
    return newState;
  }

  LockStatisticsState free(Map<String, String> freeLocks, LogManager logger) {
    LockStatisticsState newState = this.clone();
    for (String lockName : freeLocks.keySet()) {
      if (this.contains(lockName)) {
        newState.delete(lockName, freeLocks.get(lockName), logger);
      }
    }
    return newState;
  }

  public int getCounter(String lockName, String varName) {
    int counter = 0;
    for (LockStatisticsLock lock : locks) {
      if (lock.hasEqualNameAndVariable(lockName, varName))
        return lock.getRecursiveCounter();
    }
    return counter;
  }

  //this function is used only in debugging. Do not delete!
  public String getAllLines(String lockName) {
    StringBuilder sb = new StringBuilder();
    for (LockStatisticsLock lock : locks) {
      if (lock.hasEqualNameAndVariable(lockName, null)) {
        for (AccessPoint point : lock.getAccessPoints())
        sb.append(point.line.getLine() + ", ");
      }
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
    Set<LockStatisticsLock> newGlobalLocks = new HashSet<>();

    for (LockStatisticsLock otherLock : other.locks) {

      if (locks.contains(otherLock)) {
        newGlobalLocks.add(otherLock);
      }
    }

    return new LockStatisticsState(newGlobalLocks, this.toRestore);
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

    if (toRestore != null && !toRestore.equals(other.toRestore))
      return false;
    else if (toRestore == null && other.toRestore != null)
      return false;

    if (locks.size() == 0 && other.locks.size() > 0)
      return false;

    // also, this element is not less or equal than the other element,
    // if any one constant's value of the other element differs from the constant's value in this element
    for (LockStatisticsLock Lock : locks) {
      if (!Lock.existsIn(other.locks)) return false;
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
    return new LockStatisticsState(new HashSet<>(locks), this.toRestore);
  }

  public void initReplaceLabels() {
    for (LockStatisticsLock lock : locks) {
      lock.initReplaceLabel();
    }
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
    if (toRestore == null) {
      if (other.toRestore != null)
        return false;
    } else if (!toRestore.equals(other.toRestore))
      return false;
    return true;
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    /*pProperty = pProperty.trim();

    if (pProperty.startsWith("contains(")) {
      String varName = pProperty.substring("contains(".length(), pProperty.length() - 1);
      return this.contains(varName);
    } else {
      return checkProperty(pProperty);
    }*/
    //Qu'est que c'est?
    return true;
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    /*if (this.contains(pProperty))
      return true;
    else
      return false;*/
    return true;
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

        //String assignment = statement.substring("setlock(".length(), statement.length() - 1);
        //String varName = assignment.trim();
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
