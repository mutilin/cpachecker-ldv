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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Comparator;
import java.util.Set;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.AccessPoint;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsLock;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;


public class UsageInfo {

  public static class UsageComparator implements Comparator<UsageInfo> {

    @Override
    public int compare(UsageInfo pO1, UsageInfo pO2) {
      int result = 0;

      if (pO1.locks.getSize() == 0)
        result -= 50;
      if (pO2.locks.getSize() == 0)
        result += 50;

      for (LockStatisticsLock lock : pO1.locks.getLocks()) {
        if (lock.getName().equals("")) {
          result -= 10;
        }
        for (AccessPoint point : lock.getAccessPoints()) {
          result += point.getCallstack().getDepth();
        }
      }
      for (LockStatisticsLock lock : pO2.locks.getLocks()) {
        if (lock.getName().equals("")) {
          result += 10;
        }
        for (AccessPoint point : lock.getAccessPoints()) {
          result -= point.getCallstack().getDepth();
        }
      }
      return result;
    }
  }

  public static enum Access {
    WRITE,
    READ;

    public String toASTString() {
      return name().toLowerCase() + " access";
    }

    public static Access getValue(String o) throws InvalidConfigurationException {
      if (o.equalsIgnoreCase("READ"))
        return Access.READ;
      else if (o.equalsIgnoreCase("WRITE"))
        return Access.WRITE;
      else
        throw new InvalidConfigurationException("Access can't be " + o);
    }
  }

  private LineInfo line;
  private EdgeInfo info;
  private LockStatisticsState locks;
  private CallstackState callstack;
  private Access accessType;

  public UsageInfo(Access atype, LineInfo l, EdgeInfo t, LockStatisticsState lock, CallstackState call) {
    line = l;
    info = t;
    locks = lock;
    callstack = call;
    accessType = atype;
  }

  public LockStatisticsState getLockState() {
    //allLocks.addAll(locks.getLocalLocks());
    return locks;
  }

  public Access getAccess() {
    return accessType;
  }

  public CallstackState getCallStack() {
    return callstack;
  }

  public LineInfo getLine() {
    return line;
  }

  public EdgeInfo getEdgeInfo() {
    return info;
  }

  public boolean intersect(UsageInfo other) {
    if (other == null)
      return false;
    if (other.locks == null)
      return false;
    Set<LockStatisticsLock> otherLocks = other.locks.getLocks();
    if (otherLocks.size() == 0 && this.locks.getLocks().size() == 0)
      return true;

    if (otherLocks.size() == 0 && this.locks.getLocks().size() > 0)
      return false;

    for (LockStatisticsLock lock : otherLocks) {
      for (LockStatisticsLock myLock : this.locks.getLocks()) {
        if (myLock.getName().equals(lock.getName()) && myLock.getVariable().equals(lock.getVariable()))
          return true;
      }
    }

    /*for (LockStatisticsLock lock : other.locks.getLocalLocks()) {
      if (this.locks.getLocalLocks().contains(lock))
        return true;
    }*/

    return false;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accessType == null) ? 0 : accessType.hashCode());
    //result = prime * result + ((callstack == null) ? 0 : callstack.hashCode());
    result = prime * result + ((line == null) ? 0 : line.hashCode());
    result = prime * result + ((locks == null) ? 0 : locks.hashCode());
    result = prime * result + ((info == null) ? 0 : info.hashCode());
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
    UsageInfo other = (UsageInfo) obj;
    if (accessType != other.accessType)
      return false;
    /*if (callstack == null) {
      if (other.callstack != null)
        return false;
    } else if (!callstack.equals(other.callstack))
      return false;*/
    if (line == null) {
      if (other.line != null)
        return false;
    } else if (!line.equals(other.line))
      return false;
    if (locks == null) {
      if (other.locks != null)
        return false;
    } else if (!locks.equals(other.locks))
      return false;
    if (info == null) {
      if (other.info != null)
        return false;
    } else if (!info.equals(other.info))
      return false;
    return true;
  }

  @Override
  public String toString(){
    StringBuilder sb = new StringBuilder();

    sb.append("------Line ");
    sb.append(line.toString());
    sb.append(" (" + info.toString() + ", " + accessType.toASTString() + ")\n");
    CallstackState e = callstack;
    sb.append("      ");
    while (e != null) {
      LineInfo lineN = null;
      for (int i = 0; i < e.getCallNode().getNumLeavingEdges(); i++) {
        CFAEdge edge = e.getCallNode().getLeavingEdge(i);
        if (edge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
          lineN = new LineInfo(edge.getLineNumber());
          break;
        }
      }
      if (lineN == null || lineN.line != line.line)
        sb.append(e.getCurrentFunction() + "(" + (lineN != null ? lineN.toString() : "-") + ")" + " <- ");
      e = e.getPreviousState();
    }
    if (callstack != null)
      sb.delete(sb.length() - 3, sb.length());
    sb.append("\n      {\n");
    for (LockStatisticsLock lock : locks.getLocks()) {
      sb.append("    " + lock.toString() + "\n\n");
    }
    if (locks.getSize() > 0) {
      sb.delete(sb.length() - 2, sb.length());
      sb.append("      }\n\n");
    } else {
      sb.delete(sb.length() - 1, sb.length());
      sb.append("}\n\n");
    }


    return sb.toString();
  }


}
