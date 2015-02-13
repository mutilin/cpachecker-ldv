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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.bam.BAMRestoreStack;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackReducer;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier.LockType;
import org.sosy_lab.cpachecker.cpa.usagestatistics.LineInfo;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class LockStatisticsState implements Comparable<LockStatisticsState>, AbstractState, Serializable {

  public class LockStatisticsStateBuilder {
    private Set<LockStatisticsLock> mutableLocks;
    private LockStatisticsState mutableToRestore;

    public LockStatisticsStateBuilder(LockStatisticsState state) {
      mutableLocks = Sets.newTreeSet(state.locks);
      mutableToRestore = state.toRestore;
    }

    private boolean add(LockStatisticsLock l) {
      return mutableLocks.add(l);
    }

    private boolean remove(LockStatisticsLock l) {
      return mutableLocks.remove(l);
    }

    public void add(String lockName, LineInfo line, CallstackState state, String variable, LogManager logger) {
      LockStatisticsLock newLock;
      LockStatisticsLock oldLock = findLock(lockName, variable, mutableLocks);
      if(oldLock != null) {
        newLock = oldLock.addAccessPointer(new AccessPoint(line, state));
        remove(oldLock);
      } else {
        newLock = new LockStatisticsLock(lockName, line, LockType.GLOBAL_LOCK, state, variable);
      }
      add(newLock);
    }

    public void free(String lockName, String variable, LogManager logger) {
      LockStatisticsLock oldLock = findLock(lockName, variable, mutableLocks);
      if (oldLock != null) {
        String locksBefore = locks.toString();
        remove(oldLock);
        LockStatisticsLock newLock = oldLock.removeLastAccessPointer();
        if (newLock != null) {
          add(newLock);
          if (logger != null) {
            logger.log(Level.FINEST, "Locks before: " + locksBefore);
            logger.log(Level.FINEST, "Locks after: " + locks);
          }
        }
      }
    }

    public void reset(String lockName, String var, LogManager logger) {
      String locksBefore = locks.toString();
      LockStatisticsLock lock = findLock(lockName, var, mutableLocks);
      if (lock != null) {
        remove(lock);
        if (logger != null) {
          logger.log(Level.FINEST, "Locks before: " + locksBefore);
          logger.log(Level.FINEST, "Locks after: " + locks);
        }
      }
    }

    public void set(String lockName, int num, LineInfo line, CallstackState state, String variable) {
      //num can be equal 0, this means, that in origin file it is 0 and we should delete locks
      LockStatisticsLock oldLock = findLock(lockName, variable, mutableLocks);
      LockStatisticsLock newLock;

      if (oldLock != null) {
        newLock = oldLock;
        if (num > oldLock.getAccessCounter()) {
          newLock = oldLock.addRecursiveAccessPointer(num - oldLock.getAccessCounter(),
              new AccessPoint(line, state));
        } else if (num < oldLock.getAccessCounter()) {
          for (int i = 0; i < oldLock.getAccessCounter() - num; i++) {
            newLock = newLock.removeLastAccessPointer();
          }
        }
        remove(oldLock);
        if (newLock != null) {
          add(newLock);
        }
      } else if (num > 0) {
        newLock = new LockStatisticsLock(lockName, line, LockType.GLOBAL_LOCK, state, variable);
        newLock = newLock.addRecursiveAccessPointer(num - 1, new AccessPoint(line, state));
        // num - 1, because one of them is line above (new lock)
        if (newLock != null) {
          add(newLock);
        }
      }
    }

    public void restore(Map<String, String> lockNames, LogManager logger) {
      if (mutableToRestore == null) {
        return;
      }
      if (lockNames.size() == 0) {
        //we didn't specify, which locks we would like to restore, so, restore all;
        mutableLocks = mutableToRestore.locks;
      } else if (locks.equals(mutableToRestore.locks)) {

      } else {
        for (String lockName : lockNames.keySet()) {
          LockStatisticsLock oldLock = findLock(lockName, lockNames.get(lockName), mutableLocks);
          LockStatisticsLock newLock = mutableToRestore.findLock(lockName, lockNames.get(lockName));
          if (oldLock != null) {
            remove(oldLock);
            if (newLock != null) {
              add(newLock);
            }
          } else if (newLock != null){
            add(newLock);
          }
        }
      }
      mutableToRestore = mutableToRestore.toRestore;
    }

    LockStatisticsState build() {
      return new LockStatisticsState(mutableLocks, mutableToRestore);
    }

    public void resetAll() {
      mutableLocks.clear();
    }

    public void reduce() {
      LockStatisticsLock tmpLock;
      Set<LockStatisticsLock> iterativeLocks = Sets.newHashSet(mutableLocks);
      for (LockStatisticsLock lock : iterativeLocks) {
        tmpLock = lock.markOldPoints();
        if (lock != tmpLock) {
          remove(lock);
          add(tmpLock);
        }
      }
      mutableToRestore = null;
    }

    public void reduceLocks(Set<String> exceptLocks) {
      Set<LockStatisticsLock> newLocks = new HashSet<>();
      boolean changed = false;

      for (LockStatisticsLock lock : mutableLocks) {
        LockStatisticsLock reducedLock;
        if (!exceptLocks.contains(lock.getLockIdentifier().getName())) {
          reducedLock = lock.reduce();
        } else {
          reducedLock = lock;
        }
        newLocks.add(reducedLock);
        if (lock != reducedLock) {
          changed = true;
        }
      }
      if (changed) {
        mutableLocks = newLocks;
      }
    }

    public void expand(LockStatisticsState rootState, BAMRestoreStack restorator, CallstackReducer pReducer, CFANode pNode) {
      LockStatisticsLock tmpLock;
      Set<LockStatisticsLock> iterativeLocks = Sets.newHashSet(mutableLocks);
      for (LockStatisticsLock lock : iterativeLocks) {
        tmpLock = rootState.findLock(lock);
        //null is also correct (it shows, that we've found new lock)
        tmpLock = lock.expandCallstack(tmpLock, restorator, pReducer, pNode);
        if (lock != tmpLock) {
          remove(lock);
          add(tmpLock);
        }
      }
      mutableToRestore = rootState.toRestore;
    }

    public void expandLocks(LockStatisticsState pRootState, Set<String> pRestrictedLocks) {
      LockStatisticsLock tmpLock;
      for (LockStatisticsLock lock : pRootState.locks) {
        if (!pRestrictedLocks.contains(lock.getLockIdentifier().getName())) {
          tmpLock = findLock(lock, mutableLocks);
          //null is also correct (it shows, that we've found new lock)
          if (tmpLock == null) {
            tmpLock = lock.removeLastAccessPointer();
            if (tmpLock != null) {
              add(tmpLock);
            }
          } else {
            remove(tmpLock);
            tmpLock = tmpLock.expand(lock);
            add(tmpLock);
          }
        }
      }
    }

    public void setRestoreState(LockStatisticsState pOldState) {
      mutableToRestore = pOldState;
    }
  }

  private static final long serialVersionUID = -3152134511524554357L;

  private final ImmutableSet<LockStatisticsLock> locks;
  private final LockStatisticsState toRestore;
  //if we need restore state, we save it here
  //Used for function annotations like annotate.function_name.restore
  public LockStatisticsState() {
    locks = ImmutableSet.of();
    toRestore = null;
  }

  private LockStatisticsState(Set<LockStatisticsLock> gLocks, LockStatisticsState state) {
    this.locks  = ImmutableSet.copyOf(gLocks);
    toRestore = state;
  }

  public int getSize() {
    return locks.size();
  }

  public Set<LockStatisticsLock> getHashCodeForState() {
    //Special hash for BAM, in other cases use iterator
    return locks;
  }

  public Set<LockIdentifier> getLockIdentifiers() {
    Set<LockIdentifier> result = new TreeSet<>();

    for (LockStatisticsLock lock : locks) {
      result.add(lock.getLockIdentifier());
    }
    return result;
  }

  public Iterator<LockStatisticsLock> getLockIterator() {
    return locks.iterator();
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

  public LockStatisticsLock findLock(String lockName, String variable) {
    return findLock(lockName, variable, locks);
  }

  private LockStatisticsLock findLock(String lockName, String variable, Set<LockStatisticsLock> lockSet) {
    for (LockStatisticsLock lock : lockSet) {
      if (lock.hasEqualNameAndVariable(lockName, variable)) {
        return lock;
      }
    }
    return null;
  }

  private LockStatisticsLock findLock(LockStatisticsLock target) {
    return findLock(target, locks);
  }

  private LockStatisticsLock findLock(LockStatisticsLock target, Set<LockStatisticsLock> lockSet) {
    //this search checks faster (without cleaning variable)
    for (LockStatisticsLock lock : lockSet) {
      if (lock.hasEqualNameAndVariable(target)) {
        return lock;
      }
    }
    return null;
  }


  public int getCounter(String lockName, String varName) {
    LockStatisticsLock lock = findLock(lockName, varName);
    return (lock == null ? 0 : lock.getAccessCounter());
  }

  @Override
  public LockStatisticsState clone() {
    return new LockStatisticsState(new TreeSet<>(this.locks), this.toRestore);
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

  /**
   * This method decides if this element is less or equal than the other element, based on the order imposed by the lattice.
   *
   * @param other the other element
   * @return true, if this element is less or equal than the other element, based on the order imposed by the lattice
   */
  public boolean isLessOrEqual(LockStatisticsState other) {
    //State is less, if it has the same locks as the other and may be some more
    /*if (locks.size() == 0 && other.locks.size() > 0) {
      return false;
    }*/

    for (LockStatisticsLock Lock : other.locks) {
      if (this.findLock(Lock) == null) {
        return false;
      }
    }
    return true;
  }

  /**
   * This method find the difference between two states in some metric.
   * It is useful for comparators. lock1.diff(lock2) <=> lock1 - lock2.
   * @param other The other LockStatisticsState
   * @return Difference between two states
   */
  @Override
  public int compareTo(LockStatisticsState other) {
    int result = 0;

    result = other.getSize() - this.getSize(); //decreasing queue

    if (result != 0) {
      return result;
    }

    Iterator<LockStatisticsLock> iterator1 = locks.iterator();
    Iterator<LockStatisticsLock> iterator2 = other.locks.iterator();
    //Sizes are equal
    while (iterator1.hasNext()) {
      result = iterator1.next().compareTo(iterator2.next());
      if (result != 0) {
        return result;
      }
    }
    return 0;
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

  LockStatisticsStateBuilder builder() {
    return new LockStatisticsStateBuilder(this);
  }
}
