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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractWrapperState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

import com.google.common.base.Preconditions;


public class UsageInfo implements Comparable<UsageInfo> {

  public static enum Access {
    WRITE,
    READ;
  }

  private final LineInfo line;
  private final Access accessType;
  private AbstractState keyState;
  private List<CFAEdge> path;
  private SingleIdentifier id = null;
  //Can not be immutable due to reduce/expand - lock states are modified (may be smth else)
  private final Map<Class<? extends CompatibleState>, CompatibleState> compatibleStates = new LinkedHashMap<>();
  public boolean failureFlag;
  private boolean reachable;

  private UsageInfo(@Nonnull Access atype, @Nonnull LineInfo l, AbstractIdentifier ident) {
    line = l;
    accessType = atype;
    keyState = null;
    failureFlag = false;
    reachable = true;
    Preconditions.checkArgument(ident instanceof SingleIdentifier,
        "Attempt to create a usage for %s, the construction is not supported", ident);
    id = (SingleIdentifier)ident;
  }

  public UsageInfo(@Nonnull Access atype,  int l, @Nonnull UsageStatisticsState state, AbstractIdentifier ident) {
    this(atype, new LineInfo(l, AbstractStates.extractLocation(state)), ident);
    addCompatibleParts(state);
  }

  private void addCompatibleParts(AbstractState state) {
    if (state instanceof CompatibleState) {
      CompatibleState cState = (CompatibleState) state;
      compatibleStates.put(cState.getClass(), cState.prepareToStore());
    }
    if (state instanceof AbstractWrapperState) {
      for (AbstractState child : ((AbstractWrapperState)state).getWrappedStates()) {
        addCompatibleParts(child);
      }
    }
  }

  public CompatibleState getState(Class<? extends CompatibleState> pClass) {
    return compatibleStates.get(pClass);
  }

  public @Nonnull Access getAccess() {
    return accessType;
  }

  public @Nonnull LineInfo getLine() {
    return line;
  }

  public @Nonnull SingleIdentifier getId() {
    assert(id != null);
    return id;
  }

  public @Nonnull void setId(SingleIdentifier pId) {
    //Now it is set while creation
    assert id == null || id.getName().equals(pId.getName()) : "Old id " + id + ", new one - " + pId;
    //id = pId;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accessType == null) ? 0 : accessType.hashCode());
    result = prime * result + ((line == null) ? 0 : line.hashCode());
    result = prime * result + ((compatibleStates == null) ? 0 : compatibleStates.hashCode());
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
    if (line == null) {
      if (other.line != null) {
        return false;
      }
    } else if (!line.equals(other.line)) {
      return false;
    }
    if (compatibleStates == null) {
      if (other.compatibleStates != null) {
        return false;
      }
    } else if (!compatibleStates.equals(other.compatibleStates)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString(){
    StringBuilder sb = new StringBuilder();

    if (id != null) {
      sb.append("Id ");
      sb.append(id.toString());
      sb.append(", ");
    }
    sb.append("line ");
    sb.append(line.toString());
    sb.append(" (" + accessType + ")");
    sb.append(", " + compatibleStates.get(LockStatisticsState.class));

    return sb.toString();
  }

  public void setKeyState(AbstractState state) {
    keyState = state;
  }

  public void resetKeyState(List<CFAEdge> p) {
    keyState = null;
    setPath(p);
  }

  public AbstractState getKeyState() {
    return keyState;
  }

  public List<CFAEdge> getPath() {
    assert path != null;
    return path;
  }

  @Override
  public int compareTo(UsageInfo pO) {
    int result;

    if (this == pO) {
      return 0;
    }
    Set<Class<? extends CompatibleState>> currentStateTypes = compatibleStates.keySet();
    Set<Class<? extends CompatibleState>> otherStateTypes = pO.compatibleStates.keySet();
    Preconditions.checkArgument(currentStateTypes.equals(otherStateTypes),
        "Different compatible states in usages are not supported");
    for (Class<? extends CompatibleState> pClass : currentStateTypes) {
      //May be sorted not in the convenient order: Locks last
      CompatibleState currentState = this.getState(pClass);
      if (currentState != null) {
        result = currentState.compareTo(pO.getState(pClass));
        if (result != 0) {
          //Usages without locks are more convenient to analyze
          return -result;
        }
      }
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
    if (this.id != null && pO.id != null) {
      //Not while adding in container
      assert this.id.equals(pO.id);
    }
    return 0;
  }

  private void setPath(List<CFAEdge> p) {
    path = p;
  }

  public boolean isReachable() {
    return reachable;
  }

  public void setAsUnreachable() {
    reachable = false;
  }

  @Override
  public UsageInfo clone() {
    UsageInfo result = new UsageInfo(accessType, line, id);
    result.keyState = this.keyState;
    result.path = this.path;
    result.failureFlag = this.failureFlag;
    return result;
  }

  public UsageInfo expand(LockStatisticsState expandedState) {
    UsageInfo result = clone();
    compatibleStates.put(LockStatisticsState.class, expandedState);
    return result;
  }
}
