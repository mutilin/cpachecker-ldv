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

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.EdgeInfo.EdgeType;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;


public class UsageInfo {

  public static class UsageComparator implements Comparator<UsageInfo> {

    @Override
    public int compare(UsageInfo pO1, UsageInfo pO2) {

      if (pO1 == null && pO2 == null) {
        return 0;
      } else if (pO1 == null || pO2 == null) {
        return 20;
      }

      if (pO1.locks == null && pO2.locks == null) {
        return 0;
      } else if (pO1.locks == null || pO2.locks == null) {
        return 20;
      }

      return pO1.locks.diff(pO2.locks);
    }
  }

  public static enum Access {
    WRITE,
    READ;

    public String toASTString() {
      return name().toLowerCase();
    }

    public static Access getValue(String o) throws InvalidConfigurationException {
      if (o.equalsIgnoreCase("READ")) {
        return Access.READ;
      } else if (o.equalsIgnoreCase("WRITE")) {
        return Access.WRITE;
      } else {
        throw new InvalidConfigurationException("Access can't be " + o);
      }
    }
  }

  private final LineInfo line;
  private final EdgeInfo info;
  private final LockStatisticsState locks;
  private final CallstackState callstack;
  private AbstractState keyState;
  private final Access accessType;
  private boolean isRefined;

  public UsageInfo(Access atype, LineInfo l, EdgeInfo t, LockStatisticsState lock, CallstackState call) {
    line = l;
    info = t;
    locks = lock;
    callstack = call;
    accessType = atype;
    keyState = null;
    isRefined = false;
  }

  public LockStatisticsState getLockState() {
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
    if (other == null) {
      return false;
    }
    if (other.locks == null || this.locks == null) {
      return false;
    }
    if (this.accessType == Access.READ && other.accessType == Access.READ) {
      return true;
    }

    return this.locks.intersects(other.locks);
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
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    UsageInfo other = (UsageInfo) obj;
    if (accessType != other.accessType) {
      return false;
    }
    /*if (callstack == null) {
      if (other.callstack != null)
        return false;
    } else if (!callstack.equals(other.callstack))
      return false;*/
    if (line == null) {
      if (other.line != null) {
        return false;
      }
    } else if (!line.equals(other.line)) {
      return false;
    }
    if (locks == null) {
      if (other.locks != null) {
        return false;
      }
    } else if (!locks.equals(other.locks)) {
      return false;
    }
    if (info == null) {
      if (other.info != null) {
        return false;
      }
    } else if (!info.equals(other.info)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString(){
    StringBuilder sb = new StringBuilder();

    sb.append("Line ");
    sb.append(line.toString());
    sb.append(" (" + info.toString() + ", " + accessType.toASTString() + ") from ");
    CallstackState e = callstack;
    sb.append(e.getCurrentFunction());
    sb.append(", " + locks);

    return sb.toString();
  }

  public String createUsageView(SingleIdentifier id) {
    String name = id.toString();
    if (info.getEdgeType() == EdgeType.ASSIGNMENT) {
      if (accessType == Access.READ) {
        name = "... = " + name + ";";
      } else if (accessType == Access.WRITE) {
        name += " = ...;";
      }
    } else if (info.getEdgeType() == EdgeType.ASSUMPTION) {
      name = "if ("  + name + ") {}";
    } else if (info.getEdgeType() == EdgeType.FUNCTION_CALL) {
      name = "f("  + name + ");";
    } else if (info.getEdgeType() == EdgeType.DECLARATION) {
      name = id.getType().toASTString(name);
    }
    return name;
  }

  public void setKeyState(AbstractState state) {
    keyState = state;
  }

  public AbstractState getKeyState() {
    return keyState;
  }

  public void setRefineFlag() {
    isRefined = true;
  }

  public boolean isRefined() {
    return isRefined;
  }
}
