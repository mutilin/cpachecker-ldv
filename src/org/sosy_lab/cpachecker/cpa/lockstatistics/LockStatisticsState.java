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
import java.util.SortedMap;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier.LockType;
import org.sosy_lab.cpachecker.cpa.usagestatistics.LineInfo;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class LockStatisticsState implements Comparable<LockStatisticsState>, AbstractState, Serializable {

  public class LockStatisticsStateBuilder {
    private SortedMap<LockIdentifier, Integer> mutableLocks;
    private LockStatisticsState mutableToRestore;

    public LockStatisticsStateBuilder(LockStatisticsState state) {
      mutableLocks = Maps.newTreeMap(state.locks);
      mutableToRestore = state.toRestore;
    }

    private void put(LockIdentifier lockId) {
      Integer a;
      if (mutableLocks.containsKey(lockId)) {
        a = mutableLocks.get(lockId);
        a++;
      } else {
        a = 1;
      }
      assert (a != null);
      mutableLocks.put(lockId, a);
    }

    private void removeLastAccess(LockIdentifier lockId) {
      if (mutableLocks.containsKey(lockId)) {
        Integer a = mutableLocks.get(lockId);
        if (a != null) {
          a--;
          if (a > 0) {
            mutableLocks.put(lockId, a);
          } else {
            mutableLocks.remove(lockId);
          }
        }
      }
    }

    public void add(String lockName, LineInfo line, String variable, LogManager logger) {
      LockIdentifier lockId = LockIdentifier.of(lockName, variable, LockType.GLOBAL_LOCK);
      put(lockId);
    }

    public void free(String lockName, String variable, LogManager logger) {
      LockIdentifier lockId = LockIdentifier.of(lockName, variable, LockType.GLOBAL_LOCK);
      removeLastAccess(lockId);
    }

    public void reset(String lockName, String var, LogManager logger) {
      LockIdentifier lockId = LockIdentifier.of(lockName, var, LockType.GLOBAL_LOCK);
      mutableLocks.remove(lockId);
    }

    public void set(String lockName, int num, LineInfo line, String variable) {
      //num can be equal 0, this means, that in origin file it is 0 and we should delete locks
      LockIdentifier lockId = LockIdentifier.of(lockName, variable, LockType.GLOBAL_LOCK);

      Integer size = mutableLocks.get(lockId);

      if (size == null) {
        size = 0;
      }
      if (num > size) {
        for (int i = 0; i < num - size; i++) {
          put(lockId);
        }
      } else if (num < size) {
        for (int i = 0; i < size - num; i++) {
          removeLastAccess(lockId);
        }
      }
    }

    public void restore(Map<String, String> lockNames, LogManager logger) {
      if (mutableToRestore == null) {
        return;
      }
      if (lockNames.size() == 0) {
        //we didn't specify, which locks we would like to restore, so, restore all;
        mutableLocks = Maps.newTreeMap(mutableToRestore.locks);
      } else if (locks.equals(mutableToRestore.locks)) {

      } else {
        for (String lockName : lockNames.keySet()) {
          LockIdentifier lockId = LockIdentifier.of(lockName, lockNames.get(lockName), LockType.GLOBAL_LOCK);
          Integer size = mutableToRestore.locks.get(lockId);
          mutableLocks.remove(lockId);
          if (size != null) {
            mutableLocks.put(lockId, size);
          }
        }
      }
      mutableToRestore = mutableToRestore.toRestore;
    }

    LockStatisticsState build() {
      if (locks.equals(mutableLocks) && mutableToRestore == toRestore) {
        return getParentLink();
      } else {
        return new LockStatisticsState(mutableLocks, mutableToRestore);
      }
    }

    public void resetAll() {
      mutableLocks.clear();
    }

    public void reduce() {
      mutableToRestore = null;
    }

    public void reduceLocks(Set<String> exceptLocks) {
      for (LockIdentifier lock : new HashSet<>(mutableLocks.keySet())) {
        mutableLocks.remove(lock);
        put(lock);
      }
    }

    public void expand(LockStatisticsState rootState) {
      mutableToRestore = rootState.toRestore;
    }

    public void expandLocks(LockStatisticsState pRootState, Set<String> pRestrictedLocks) {
      for (LockIdentifier lock : pRootState.locks.keySet()) {
        if (!pRestrictedLocks.contains(lock.getName())) {
          Integer size = mutableLocks.get(lock);
          Integer rootSize = pRootState.locks.get(lock);
          //null is also correct (it shows, that we've found new lock)

          Integer newSize;
          if (size == null) {
            newSize = rootSize - 1;
          } else {
            newSize = size + rootSize - 1;
          }
          if (newSize > 0) {
            mutableLocks.put(lock, newSize);
          } else {
            mutableLocks.remove(lock);
          }
        }
      }
    }

    public void setRestoreState(LockStatisticsState pOldState) {
      mutableToRestore = pOldState;
    }
  }

  private static final long serialVersionUID = -3152134511524554357L;

  private final SortedMap<LockIdentifier, Integer> locks;
  private final LockStatisticsState toRestore;
  //if we need restore state, we save it here
  //Used for function annotations like annotate.function_name.restore
  public LockStatisticsState() {
    locks = Maps.newTreeMap();
    toRestore = null;
  }

  private LockStatisticsState(SortedMap<LockIdentifier, Integer> gLocks, LockStatisticsState state) {
    this.locks  = Maps.newTreeMap(gLocks);
    toRestore = state;
  }

  public int getSize() {
    return locks.size();
  }

  public Map<LockIdentifier, Integer> getHashCodeForState() {
    //Special hash for BAM, in other cases use iterator
    return locks;
  }

  public Set<LockIdentifier> getLockIdentifiers() {
    return Sets.newTreeSet(locks.keySet());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    for (LockIdentifier lock : Sets.newTreeSet(locks.keySet())) {
      sb.append(lock.toString() + "[" + locks.get(lock) + "]" + ", ");
    }
    if (locks.size() > 0) {
      sb.delete(sb.length() - 2, sb.length());
    } else {
      sb.append("Without locks");
    }
    return sb.toString();
  }

  public int getCounter(String lockName, String varName) {
    LockIdentifier lock = LockIdentifier.of(lockName, varName, LockType.GLOBAL_LOCK);
    Integer size = locks.get(lock);
    return (size == null ? 0 : size);
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

    for (LockIdentifier lock : other.locks.keySet()) {
      if (!(this.locks.containsKey(lock))) {
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

    Iterator<LockIdentifier> iterator1 = locks.keySet().iterator();
    Iterator<LockIdentifier> iterator2 = other.locks.keySet().iterator();
    //Sizes are equal
    while (iterator1.hasNext()) {
      LockIdentifier lockId1 = iterator1.next();
      LockIdentifier lockId2 = iterator2.next();
      result = lockId1.compareTo(lockId2);
      if (result != 0) {
        return result;
      }
      Integer Result = locks.get(lockId1) - other.locks.get(lockId1);
      if (Result != 0) {
        return Result;
      }
    }
    return 0;
  }

  public boolean intersects(LockStatisticsState pLocks) {
    if (Sets.intersection(locks.keySet(), pLocks.locks.keySet()).isEmpty()) {
      return false;
    } else {
      return true;
    }
  }

  LockStatisticsStateBuilder builder() {
    return new LockStatisticsStateBuilder(this);
  }

  private LockStatisticsState getParentLink() {
    return this;
  }
}
