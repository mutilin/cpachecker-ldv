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

import javax.annotation.Nonnull;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.EdgeInfo.EdgeType;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;


public class UsageInfo implements Comparable<UsageInfo> {

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
  public boolean failureFlag;
  
  private static final boolean mergeUsagesWithEqualCallstacks = false;

  public UsageInfo(@Nonnull Access atype, @Nonnull LineInfo l,
  								 @Nonnull EdgeInfo t, @Nonnull LockStatisticsState lock,
  								 @Nonnull CallstackState call) {
    line = l;
    info = t;
    locks = lock;
    callstack = call;
    accessType = atype;
    keyState = null;
    isRefined = false;
    failureFlag = false;
  }

  public @Nonnull LockStatisticsState getLockState() {
    return locks;
  }

  public @Nonnull Access getAccess() {
    return accessType;
  }

  public @Nonnull CallstackState getCallStack() {
    return callstack;
  }

  public @Nonnull LineInfo getLine() {
    return line;
  }

  public @Nonnull EdgeInfo getEdgeInfo() {
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
    if (this == other) {
      return false;
    }

    boolean result = this.locks.intersects(other.locks);
    if (result) {
      return result;
    }
    if (locks.getSize() == 0 && other.locks.getSize() == 0 ){
      if (this.callstack.getCurrentFunction().equals(other.callstack.getCurrentFunction())) {
        return true; //this is equal states, like for a++;
      }
    }
    return false;
  }
  
  public UsagePoint getUsagePoint() {
    if (this.locks.getSize() > 0 || !mergeUsagesWithEqualCallstacks && this.accessType == Access.READ) {
      return new UsagePoint(locks.getLockIdentifiers(), accessType);
    } else {
      return new UsagePoint(accessType, this);
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accessType == null) ? 0 : accessType.hashCode());
    result = prime * result + ((callstack == null) ? 0 : callstack.hashCodeWithoutNode());
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
    //Callstack is important for refinement
    if (callstack == null) {
      if (other.callstack != null) {
        return false;
      }
    } else if (!callstack.equalsWithoutNode(other.callstack)) {
      return false;
    }
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
  
  public void resetKeyState() {
    keyState = null;
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

  @Override
  public int compareTo(UsageInfo pO) {

    if (this == pO) {
      return 0;
    }
    
    int result = this.locks.compareTo(pO.locks);
    if (result != 0) {
      //Usages without locks are more convenient to analyze
      return -result;
    }
    result = this.getCallStack().getDepth() - pO.getCallStack().getDepth();
    if (result != 0) {
      //Experiments show that callstacks should be ordered as it is done now
      return result;
    }

    result = this.line.getLine() - pO.line.getLine();
    if (result != 0) {
      return result;
    }
    //Some nodes can be from one line, but different nodes
    result = this.line.getNode().getNodeNumber() - pO.line.getNode().getNodeNumber();
    if (result != 0) {
      return result;
    }
    result = this.accessType.compareTo(pO.accessType);
    if (result != 0) {
      return result;
    }
    /* We can't use key states for ordering, because the treeSets can't understand,
     * that old refined usage with zero key state is the same as new one
     */
    return 0;
  }
}
