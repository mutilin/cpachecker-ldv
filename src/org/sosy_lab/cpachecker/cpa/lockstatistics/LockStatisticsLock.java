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

import java.util.Comparator;
import java.util.List;
import java.util.Stack;

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.abm.ABMRestoreStack;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackReducer;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.LineInfo;


public class LockStatisticsLock {

  public static class LockComparator implements Comparator<LockStatisticsLock> {

    @Override
    public int compare(LockStatisticsLock pO1, LockStatisticsLock pO2) {
      int result = 0;
      if (pO1.variable.equals("")) {
        result -= 50;
      }
      if (pO2.variable.equals("")) {
        result += 50;
      }
      String name1 = pO1.toString();
      String name2 = pO2.toString();
      return (result + name1.compareTo(name2));
    }

  }

  public static enum LockType {
    MUTEX,
    GLOBAL_LOCK,
    LOCAL_LOCK,
    SPINLOCK;

    public String toASTString() {
      return name().toLowerCase();
    }
  }

  private String name;
  private Stack<AccessPoint> accessPoints;
  private LockType type;
  private int recursiveCounter;
  private String variable;

  LockStatisticsLock(String n, int l, LockType t, CallstackState s, CallstackState reduced, String v) {
    name = n;
    accessPoints = new Stack<>();
    accessPoints.add(new AccessPoint( new LineInfo(l), s, reduced));
    type = t;
    recursiveCounter = 0;
    variable = v;
  }

  private LockStatisticsLock(String n, LockType t, Stack<AccessPoint> points, String v, int r) {
    name = n;
    accessPoints = new Stack<>();
    for (AccessPoint point : points) {
      accessPoints.add(point);
    }
    type = t;
    recursiveCounter = r;
    variable = v;
  }

  public String getName() {
    return name;
  }

  public String getVariable() {
    return variable;
  }

  public Stack<AccessPoint> getAccessPoints() {
    return accessPoints;
  }

  public int getRecursiveCounter() {
    return recursiveCounter;
  }

  @Override
  public LockStatisticsLock clone() {
    return new LockStatisticsLock(this.name, this.type, this.accessPoints, this.variable, this.recursiveCounter);
  }

  public LockStatisticsLock addAccessPointer(AccessPoint accessPoint) {
    LockStatisticsLock cloned = this.clone();
    cloned.recursiveCounter++;
    cloned.accessPoints.push(accessPoint);
    return cloned;
  }

  public LockStatisticsLock removeLastAccessPointer() {
    if(recursiveCounter > 0) {
      LockStatisticsLock cloned = this.clone();
      cloned.recursiveCounter--;
      cloned.accessPoints.pop();
      return cloned;
    } else {
      return null;
    }
  }

  public boolean hasEqualNameAndVariable(LockStatisticsLock lock) {
    return hasEqualNameAndVariable(lock.name, lock.variable);
  }

  public boolean hasEqualNameAndVariable(String lockName, String variableName) {
    if (variableName != null) {
      //variable is important
      String myVariableName = this.variable.replaceAll("\\(", "");
      myVariableName = myVariableName.replaceAll("\\)", "");
      String otherVariable = variableName.replaceAll("\\(", "");
      otherVariable = otherVariable.replaceAll("\\)", "");
      //this is only for cil: it likes change i -> i___0
      myVariableName = myVariableName.replaceAll("___\\d*", "");
      otherVariable = otherVariable.replaceAll("___\\d*", "");
      return (this.name.equals(lockName) && myVariableName.equals(otherVariable));
    } else {
      return this.name.equals(lockName);
    }
  }

  public void initReplaceLabel() {
    for (AccessPoint accessPoint : accessPoints) {
      accessPoint.setLabel();
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((variable == null) ? 0 : variable.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + recursiveCounter;
    result = prime * result + ((type == null) ? 0 : type.hashCode());
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
    if (variable == null) {
      if (other.variable != null) {
        return false;
      }
    } else if (!variable.equals(other.variable)) {
      return false;
    }
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    if (recursiveCounter != other.recursiveCounter) {
      return false;
    }
    if (type != other.type) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {

    return/*type.toASTString() + " " +*/ name + ( variable != "" ? ("(" + variable + ")") : "" )  + "[" + recursiveCounter + "]";
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
    LockStatisticsLock tmpLock = this.clone();
    for (int i = 0; i < pNum; i++) {
      tmpLock = tmpLock.addAccessPointer(pAccessPoint);
    }
    return tmpLock;
  }

  public LockStatisticsLock expandCallstack(LockStatisticsLock rootLock, ABMRestoreStack restorator) {
    boolean changed = false;

    LockStatisticsLock expandedLock = this.clone();
    AccessPoint tmpPoint, newPoint;
    for (int i = 0; i < this.accessPoints.size(); i++) {
      tmpPoint = accessPoints.get(i);
      if (!tmpPoint.isNew() || rootLock == null) {
        newPoint = tmpPoint.expandCallstack(restorator);
        if (newPoint != tmpPoint) {
          changed = true;
          expandedLock.accessPoints.setElementAt(newPoint, i);
        }
      } else if (rootLock.accessPoints.size() > i) {
        changed = true;
        expandedLock.accessPoints.setElementAt(rootLock.accessPoints.get(i), i);
      }
    }
    if (changed) {
      return expandedLock;
    } else {
      return this;
    }
  }

  public LockStatisticsLock reduceCallStack(CallstackReducer pReducer, CFANode pNode) {
    LockStatisticsLock newLock = this.clone();
    AccessPoint tmpPoint, newPoint;
    for (int i = 0; i < this.accessPoints.size(); i++) {
      tmpPoint = accessPoints.get(i);
      if (!tmpPoint.isNew()) {
        newPoint = tmpPoint.reduceCallstack(pReducer, pNode);
        if (newPoint != tmpPoint) {
          newLock.accessPoints.setElementAt(newPoint, i);
        }
      }
    }
    if (this.equals(newLock)) {
      return this;
    } else {
      return newLock;
    }
  }
}
