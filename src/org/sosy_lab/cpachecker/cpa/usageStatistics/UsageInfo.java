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
package org.sosy_lab.cpachecker.cpa.usageStatistics;

import java.util.Set;

import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsState;


public class UsageInfo {
  public static enum Access {
    WRITE,
    READ;

    public String toASTString() {
      return name().toLowerCase() + " access";
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

  public Set<LockStatisticsLock> getLocks() {
    return locks.getLocks();
  }

  public boolean intersect(UsageInfo other) {
    if (other.locks.getLocks().size() == 0 && this.locks.getLocks().size() == 0)
      return true;

    for (LockStatisticsLock lock : other.locks.getLocks()) {
      if (this.locks.getLocks().contains(lock))
        return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accessType == null) ? 0 : accessType.hashCode());
    result = prime * result + ((callstack == null) ? 0 : callstack.hashCode());
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
    if (callstack == null) {
      if (other.callstack != null)
        return false;
    } else if (!callstack.equals(other.callstack))
      return false;
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

    sb.append("      In line ");
    sb.append(line.toString());
    sb.append(" (" + info.toString() + ")");
    sb.append(" was locked with " + locks.toString()+ ", ");
    sb.append(accessType.toASTString());
    //sb.append(" in " + info.toString());

    sb.append("\n        Call stack: ");

    CallstackState e = callstack;
    while (e != null) {
      sb.append(e.getCurrentFunction() + " <- ");
      e = e.getPreviousState();
    }
    if (callstack != null)
      sb.delete(sb.length() - 3, sb.length());
    sb.append("\n");
    return sb.toString();
  }


}
