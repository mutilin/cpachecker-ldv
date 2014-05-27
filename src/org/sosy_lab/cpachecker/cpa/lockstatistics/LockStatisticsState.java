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
package org.sosy_lab.cpachecker.cpa.lockstatistics;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.bam.BAMRestoreStack;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackReducer;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier.LockType;
import org.sosy_lab.cpachecker.cpa.usagestatistics.LineInfo;

public class LockStatisticsState implements AbstractState, Serializable {
  private static final long serialVersionUID = -3152134511524554357L;

  private final Set<LockStatisticsLock> locks;
  private LockStatisticsState toRestore;
  //if we need restore state, we save it here
  //Used for function annotations like annotate.function_name.restore
  public LockStatisticsState() {
    this(new TreeSet<LockStatisticsLock>(), null);
  }

  private LockStatisticsState(Set<LockStatisticsLock> gLocks, LockStatisticsState state) {
    this.locks  = gLocks;
    toRestore = state;
  }

  public int getSize() {
    return locks.size();
  }

  public Set<LockStatisticsLock> getLocks() {
    return locks;
  }

  public void setRestoreState(LockStatisticsState state) {
    toRestore = state;
  }

  public void copyRestoreState(LockStatisticsState pRootState) {
    setRestoreState(pRootState.toRestore);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    for (LockStatisticsLock lock : locks) {
      sb.append(lock.toString() + ", ");
    }
    if (locks.size() > 0) {
      sb.delete(sb.length() - 2, sb.length());
    } else {
      sb.append("Without locks");
    }
    return sb.toString();
  }

  public void add(String lockName, int line, CallstackState state, CallstackState reduced, String variable, LogManager logger) {
    boolean b;
    String locksBefore = locks.toString();

    LockStatisticsLock oldLock = findLock(lockName, variable);
    if(oldLock != null) {
      LockStatisticsLock newLock = oldLock.addAccessPointer(new AccessPoint(new LineInfo(line), state, reduced));
      b = locks.remove(oldLock);
      assert b;
      b = locks.add(newLock);
      assert b;
    } else {
      LockStatisticsLock tmpLock = new LockStatisticsLock(lockName, line, LockType.GLOBAL_LOCK, state, reduced, variable);
      b = locks.add(tmpLock);
    }
    if(b && logger != null) {
      logger.log(Level.FINEST, "Locks before: " + locksBefore);
      logger.log(Level.FINEST, "Locks after: " + locks);
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

  public LockStatisticsLock findLock(LockStatisticsLock target) {
    //this search checks faster (without cleaning variable)
    for (LockStatisticsLock lock : locks) {
      if (lock.hasEqualNameAndVariable(target)) {
        return lock;
      }
    }
    return null;
  }

  public void add(LockStatisticsLock l, LogManager logger) {
	  String locksBefore = locks.toString();
	  boolean b = locks.add(l);
	  if(b && logger != null) {
		  logger.log(Level.FINEST, "Locks before: " + locksBefore);
		  logger.log(Level.FINEST, "Locks after: " + locks);
	  }
  }

  private void free(LockStatisticsLock oldLock, LogManager logger) {
    if (oldLock == null) {
      return;
    }
    String locksBefore = locks.toString();
    boolean b = locks.remove(oldLock);
    assert b;
    LockStatisticsLock newLock = oldLock.removeLastAccessPointer();
    if (newLock != null) {
      locks.add(newLock);
      if (logger != null) {
        logger.log(Level.FINEST, "Locks before: " + locksBefore);
        logger.log(Level.FINEST, "Locks after: " + locks);
      }
    }
  }

  public void free(String lockName, String variable, LogManager logger) {
	  LockStatisticsLock oldLock = findLock(lockName, variable);
	  if (oldLock == null) {
	    /*if (logger != null) {
	      logger.log(Level.WARNING, "Free nonexisting lock: " + lockName);
	    }*/
	    return;
    }
    free(oldLock, logger);
  }

  public void reset(String lockName, String var, LogManager logger) {
    LockStatisticsLock lock = findLock(lockName, var);
    if (lock != null) {
      reset(lock, logger);
    }
  }

  private void reset(LockStatisticsLock lock, LogManager logger) {
    String locksBefore = locks.toString();
    boolean b = locks.remove(lock);
    if (b && logger != null) {
      logger.log(Level.FINEST, "Locks before: " + locksBefore);
      logger.log(Level.FINEST, "Locks after: " + locks);
    }
  }

  public LockStatisticsState reset(Map<String, String> pResetLocks, LogManager pLogger) {
    LockStatisticsState newState = this.clone();
    for (String lockName : pResetLocks.keySet()) {
      newState.reset(lockName, pResetLocks.get(lockName), pLogger);
    }
    return newState;
  }

  public void set(String lockName, int num, int line, CallstackState state, CallstackState reduced, String variable) {
    //num can be equal 0, this means, that in origin file it is 0 and we should delete locks
    LockStatisticsLock oldLock = findLock(lockName, variable);
    LockStatisticsLock newLock;

    if (oldLock != null) {
      newLock = oldLock;
      if (num > oldLock.getAccessCounter()) {
        newLock = oldLock.addRecursiveAccessPointer(num - oldLock.getAccessCounter(),
            new AccessPoint(new LineInfo(line), state, reduced));
      } else if (num < oldLock.getAccessCounter()) {
        for (int i = 0; i < oldLock.getAccessCounter() - num; i++) {
          newLock = newLock.removeLastAccessPointer();
        }
      }
      locks.remove(oldLock);
      if (newLock != null) {
        locks.add(newLock);
      }
    } else if (num > 0) {
      newLock = new LockStatisticsLock(lockName, line, LockType.GLOBAL_LOCK, state, reduced, variable);
      newLock = newLock.addRecursiveAccessPointer(num - 1, new AccessPoint(new LineInfo(line), state, reduced));
      // num - 1, because one of them is line above (new lock)
      if (newLock != null) {
        locks.add(newLock);
      }
    }
  }

  public LockStatisticsState restore(Map<String, String> lockNames, LogManager logger) {
    LockStatisticsState restoredState = this.toRestore;
    if (restoredState == null) {
      return this.clone();
    }
    LockStatisticsState newState;
    if (lockNames.size() == 0) {
      //we didn't specify, which locks we would like to restore, so, restore all;
      newState = restoredState.clone();
    } else if (this.locks.equals(restoredState.locks)) {
      //only for optimization. We don't compare restoreState, because in this it can be only more complex.
      newState = this.clone();
    } else {

      newState = this.clone();
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
    }
    newState.copyRestoreState(restoredState);
    return newState;
  }

  public int getCounter(String lockName, String varName) {
    LockStatisticsLock lock = findLock(lockName, varName);
    return (lock == null ? 0 : lock.getAccessCounter());
  }

  //this function is used only in debugging. Do not delete!
  /*public String getAllLines(String lockName) {
    StringBuilder sb = new StringBuilder();
    for (LockStatisticsLock lock : locks) {
      if (lock.hasEqualNameAndVariable(lockName, null)) {
        for (AccessPoint point : lock.getAccessPoints()) {
          sb.append(point.getLineInfo().getLine() + ", ");
        }
      }
    }

    if (sb.length() > 2) {
      sb.delete(sb.length() - 2, sb.length());
    }

    return sb.toString();
  }*/

  @Override
  public LockStatisticsState clone() {
    return new LockStatisticsState(new TreeSet<>(this.locks), this.toRestore);
  }

  public void markOldLocks() {
    LockStatisticsLock tmpLock;
    Set<Pair<LockStatisticsLock, LockStatisticsLock>> toChange = new HashSet<>();
    for (LockStatisticsLock lock : locks) {
      tmpLock = lock.markOldPoints();
      if (lock != tmpLock) {
        toChange.add(Pair.of(lock, tmpLock));
      }
    }
    for (Pair<LockStatisticsLock, LockStatisticsLock> pair : toChange) {
      locks.remove(pair.getFirst());
      locks.add(pair.getSecond());
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((locks == null) ? 0 : locks.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    LockStatisticsState other = (LockStatisticsState) obj;
    if (locks == null) {
      if (other.locks != null) {
        return false;
      }
    } else if (!locks.equals(other.locks)) {
      return false;
    }
    if (toRestore == null) {
      if (other.toRestore != null) {
        return false;
      }
    } else if (!toRestore.equals(other.toRestore)) {
      return false;
    }
    return true;
  }

  public void expandCallstack(LockStatisticsState rootState, BAMRestoreStack restorator) {
    Set<Pair<LockStatisticsLock, LockStatisticsLock>> toChange = new HashSet<>();
    LockStatisticsLock tmpLock;
    for (LockStatisticsLock lock : this.locks) {
      tmpLock = rootState.findLock(lock);
      //null is also correct (it shows, that we've found new lock)
      tmpLock = lock.expandCallstack(tmpLock, restorator);
      if (lock != tmpLock) {
        toChange.add(Pair.of(lock, tmpLock));
      }
    }
    for (Pair<LockStatisticsLock, LockStatisticsLock> pair : toChange) {
      locks.remove(pair.getFirst());
      locks.add(pair.getSecond());
    }
  }

  public void reduceCallstack(CallstackReducer pReducer, CFANode pNode) {
    Set<Pair<LockStatisticsLock, LockStatisticsLock>> toChange = new HashSet<>();
    for (LockStatisticsLock lock : this.locks) {
      LockStatisticsLock newLock = lock.reduceCallStack(pReducer, pNode);
      if (lock != newLock) {
        toChange.add(Pair.of(lock, newLock));
      }
    }
    for (Pair<LockStatisticsLock, LockStatisticsLock> pair : toChange) {
      locks.remove(pair.getFirst());
      locks.add(pair.getSecond());
    }
  }

  /**
   * This method find the difference between two states in some metric.
   * It is useful for comparators. lock1.diff(lock2) <=> lock1 - lock2.
   * @param other The other LockStatisticsState
   * @return Difference between two states
   */
  public int diff(LockStatisticsState other) {
    int result = 0;

    result = other.getSize() - this.getSize(); //decreasing queue

    if (result != 0) {
      return result;
    }

    for (LockStatisticsLock lock : this.locks) {
      result -= lock.norm();
    }
    for (LockStatisticsLock lock : other.locks) {
      result += lock.norm();
    }
    return result;
  }

  public boolean intersects(LockStatisticsState pLocks) {
    /*if (pLocks.locks.size() == 0 && this.locks.size() == 0) {
      return true;    //this is our assumption. This isn't unsafe.
    }*/
    for (LockStatisticsLock lock : pLocks.locks) {
      for (LockStatisticsLock myLock : this.locks) {
        if (lock.hasEqualNameAndVariable(myLock)) {
          return true;
        }
      }
    }
    return false;
  }

}
