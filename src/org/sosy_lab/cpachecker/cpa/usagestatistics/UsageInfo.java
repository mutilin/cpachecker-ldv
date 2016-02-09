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

import static com.google.common.collect.FluentIterable.from;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsagePoint;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

import com.google.common.base.Predicate;


public class UsageInfo implements Comparable<UsageInfo> {

  public static enum Access {
    WRITE,
    READ;
  }

  private final LineInfo line;
  private final LockStatisticsState locks;
  private final Access accessType;
  private AbstractState keyState;
  private List<CFAEdge> path;
  private SingleIdentifier id = null;
  public boolean failureFlag;
  private boolean reachable;

  public UsageInfo(@Nonnull Access atype, @Nonnull LineInfo l, @Nonnull LockStatisticsState lock) {
    line = l;
    locks = lock;
    accessType = atype;
    keyState = null;
    failureFlag = false;
    reachable = true;
  }

  public UsageInfo(@Nonnull Access atype,  int l, @Nonnull UsageStatisticsState state) {
    this(atype, new LineInfo(l, AbstractStates.extractLocation(state)), AbstractStates.extractStateByType(state, LockStatisticsState.class));
  }

  public @Nonnull LockStatisticsState getLockState() {
    return locks;
  }

  public @Nonnull Access getAccess() {
    return accessType;
  }

  public @Nonnull LineInfo getLine() {
    return line;
  }

  public @Nonnull SingleIdentifier getId() {
    return id;
  }

  public @Nonnull void setId(SingleIdentifier pId) {
    //Only once
    assert id == null;
    id = pId;
  }

  public UsagePoint getUsagePoint() {
    if (this.locks != null && (this.locks.getSize() > 0 || this.accessType == Access.READ)) {
      return new UsagePoint(locks.getLockIdentifiers(), accessType);
    } else {
      return new UsagePoint(this);
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accessType == null) ? 0 : accessType.hashCode());
    result = prime * result + ((line == null) ? 0 : line.hashCode());
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
    if (locks == null) {
      if (other.locks != null) {
        return false;
      }
    } else if (!locks.equals(other.locks)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString(){
    StringBuilder sb = new StringBuilder();

    sb.append("Id ");
    sb.append(id.toString());
    sb.append(", line ");
    sb.append(line.toString());
    sb.append(" (" + accessType + ")");
    sb.append(", " + locks);

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
    if (this.locks != null) {
      result = this.locks.compareTo(pO.locks);
      if (result != 0) {
        //Usages without locks are more convenient to analyze
        return -result;
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
    assert this.id.equals(pO.id);
    return 0;
  }

  private void setPath(List<CFAEdge> p) {
    List<CFAEdge> edges = p;
    edges = from(edges).filter(new Predicate<CFAEdge>() {
      @Override
      public boolean apply(@Nullable CFAEdge pInput) {
        if (pInput instanceof CDeclarationEdge) {
          if (((CDeclarationEdge)pInput).getDeclaration().isGlobal() ||
              pInput.getSuccessor().getFunctionName().equals("ldv_main")) {
            return false;
          }
        } else if (pInput.getSuccessor().getFunctionName().equals("ldv_main")
            && pInput instanceof CAssumeEdge) {
          //Remove infinite switch, it's too long
          return false;
        }
        return true;
      }
    }).toList();
    path = edges;
  }

  public boolean isReachable() {
    return reachable;
  }

  public void setAsUnreachable() {
    reachable = false;
  }

  @Override
  public UsageInfo clone() {
    UsageInfo result = new UsageInfo(accessType, line, locks);
    result.id = this.id;
    result.keyState = this.keyState;
    result.path = this.path;
    result.failureFlag = this.failureFlag;
    return result;
  }
}
