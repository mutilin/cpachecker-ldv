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

import java.util.LinkedList;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.bam.BAMRestoreStack;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackReducer;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier.LockType;
import org.sosy_lab.cpachecker.cpa.usagestatistics.LineInfo;

import com.google.common.collect.ImmutableList;


public class LockStatisticsLock implements Comparable<LockStatisticsLock> {

  private final LockIdentifier lockId;
  private final ImmutableList<AccessPoint> accessPoints;

  LockStatisticsLock(String n, int l, LockType t, CallstackState s, CallstackState reduced, String v) {
    lockId = LockIdentifier.of(n, getCleanName(v), t);
    LinkedList<AccessPoint> tmpAccessPoints = new LinkedList<>();
    tmpAccessPoints.add(new AccessPoint( new LineInfo(l), s, reduced));
    accessPoints = ImmutableList.copyOf(tmpAccessPoints);
  }

  private LockStatisticsLock(LockIdentifier id, LinkedList<AccessPoint> points) {
    lockId = id;
    accessPoints = ImmutableList.copyOf(points);
  }

  public ImmutableList<AccessPoint> getAccessPoints() {
    return accessPoints;
  }

  public int getAccessCounter() {
    return accessPoints.size();
  }

  /**
   * This function clean variable name from brackets and cil's '___0'
   * @param originName original name of variable
   * @return new name
   */
  private String getCleanName(String originName) {
    if (originName != null) {
      String newName = originName.replaceAll("\\(", "");
      newName = newName.replaceAll("\\)", "");
      newName = newName.replaceAll("___\\d*", "");
      return newName;
    } else {
      return null;
    }
  }

  @Override
  public LockStatisticsLock clone() {
    LinkedList<AccessPoint> tmpAccessPoints = new LinkedList<>(accessPoints);
    return clone(tmpAccessPoints);
  }

  private LockStatisticsLock clone(LinkedList<AccessPoint> tmpAccessPoints) {
    return new LockStatisticsLock(this.lockId, tmpAccessPoints);
  }

  public LockStatisticsLock addAccessPointer(AccessPoint accessPoint) {
    LinkedList<AccessPoint> tmpAccessPoints = new LinkedList<>(this.accessPoints);
    tmpAccessPoints.add(accessPoint);
    return clone(tmpAccessPoints);
  }

  public LockStatisticsLock removeLastAccessPointer() {
    if(this.accessPoints.size() > 1) { //we have access points after removing
      LinkedList<AccessPoint> tmpAccessPoints = new LinkedList<>(this.accessPoints);
      tmpAccessPoints.removeLast();
      return clone(tmpAccessPoints);
    } else {
      return null;
    }
  }

  public boolean hasEqualNameAndVariable(LockStatisticsLock lock) {
    //Here we know exactly, that variable is clean, so check equals at once (without getCleanName)
    return this.lockId == lock.lockId;
  }

  public boolean hasEqualNameAndVariable(String lockName, String variableName) {
    //most of cases, where is used this method, have such variables, as 'var___0', so we need clean it
    //variable is important
    return (lockId == LockIdentifier.of(lockName, getCleanName(variableName), LockType.GLOBAL_LOCK));
  }

  public LockStatisticsLock markOldPoints() {
    LinkedList<AccessPoint> tmpAccessPoints = new LinkedList<>();
    boolean changed = false;
    AccessPoint tmpPoint;
    for (AccessPoint point : this.accessPoints) {
      tmpPoint = point.clone();
      if (point.isNew()) {
        changed = true;
      }
      tmpPoint.markAsOld();
      tmpAccessPoints.add(tmpPoint);
    }
    if (changed) {
      return clone(tmpAccessPoints);
    } else {
      return this;
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((lockId == null) ? 0 : lockId.hashCode());
    result = prime * result + accessPoints.size();
    //result = prime * result + ((accessPoints == null) ? 0 : accessPoints.hashCode());
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
    LockStatisticsLock other = (LockStatisticsLock) obj;
    if (lockId == null) {
      if (other.lockId != null) {
        return false;
      }
    } else if (!lockId.equals(other.lockId)) {
      return false;
    }
    if (accessPoints.size() != other.accessPoints.size()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return lockId.toString()  + "[" + accessPoints.size() + "]";
  }

  public boolean existsIn(List<LockStatisticsLock> locks) {
    for (LockStatisticsLock usedLock : locks) {
      if (usedLock.hasEqualNameAndVariable(this)) {
      	return true;
      }
    }
	  return false;
  }

  public LockStatisticsLock addRecursiveAccessPointer(int pNum, AccessPoint pAccessPoint) {
    LockStatisticsLock tmpLock = this;
    for (int i = 0; i < pNum; i++) {
      //addAccessCounter() also clones
      tmpLock = tmpLock.addAccessPointer(pAccessPoint);
    }
    return tmpLock;
  }

  public LockStatisticsLock expandCallstack(LockStatisticsLock rootLock, BAMRestoreStack restorator) {
    boolean changed = false;

    AccessPoint tmpPoint, newPoint;
    LinkedList<AccessPoint> newAccessPoints = new LinkedList<>(this.accessPoints);

    for (int i = 0; i < this.accessPoints.size(); i++) {
      tmpPoint = accessPoints.get(i);
      if (tmpPoint.isNew() || rootLock == null) {
        newPoint = tmpPoint.expandCallstack(restorator);
        if (newPoint != tmpPoint) {
          changed = true;
          newAccessPoints.set(i, newPoint);
        }
      } else if (rootLock.accessPoints.size() > i) {
        //restore marks, which were new before function call
        changed = true;
        newAccessPoints.set(i, rootLock.accessPoints.get(i).clone());
      } else {
        //Also strange situation...
        System.out.println("size < i");
      }
    }
    if (changed) {
      return clone(newAccessPoints);
    } else {
      return this;
    }
  }

  public LockStatisticsLock reduceCallStack(CallstackReducer pReducer, CFANode pNode) {
    boolean isChanged = false;
    AccessPoint tmpPoint, newPoint;
    LinkedList<AccessPoint> newAccessPoints = new LinkedList<>(this.accessPoints);

    for (int i = 0; i < this.accessPoints.size(); i++) {
      tmpPoint = accessPoints.get(i);
      if (tmpPoint.isNew()) {
        newPoint = tmpPoint.reduceCallstack(pReducer, pNode);
        if (newPoint != tmpPoint) {
          isChanged = true;
          newAccessPoints.set(i, newPoint);
        }
      }
    }
    if (isChanged) {
      return clone(newAccessPoints);
    } else {
      return this;
    }
  }

  /**
   * This function measures the norm of lock. It is useful to compare states, consists from variety of locks.
   * Now it considers only depth of callstack.
   * @return result in some metric
   */
  public int norm() {
    int result = 0;
    for (AccessPoint point : accessPoints) {
      CallstackState state = (point == null ? null : point.getCallstack());
      result += (state == null ? 0 : state.getDepth());
    }
    return result;
  }

  @Override
  public int compareTo(LockStatisticsLock pO) {
    int result = this.lockId.compareTo(pO.lockId);
    if (result != 0) {
      return result;
    }
    return this.accessPoints.size() - pO.accessPoints.size();
  }
}
