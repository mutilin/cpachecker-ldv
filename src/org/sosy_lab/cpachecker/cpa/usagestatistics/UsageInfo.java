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

import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.thread.ThreadState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsagePoint;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
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
  private final ThreadState threadInfo;
  public boolean failureFlag;
  private boolean reachable;

  public UsageInfo(@Nonnull Access atype, @Nonnull LineInfo l, @Nonnull LockStatisticsState lock, AbstractIdentifier ident, ThreadState tState) {
    line = l;
    locks = lock;
    accessType = atype;
    keyState = null;
    failureFlag = false;
    reachable = true;
    if (ident instanceof SingleIdentifier)
    {
      id = (SingleIdentifier)ident;
    } else {
      id = null;
    }
    threadInfo = tState == null ? null : tState.prepareToStore();
  }

  public UsageInfo(@Nonnull Access atype,  int l, @Nonnull UsageStatisticsState state, AbstractIdentifier ident) {
    this(atype, new LineInfo(l, AbstractStates.extractLocation(state)), AbstractStates.extractStateByType(state, LockStatisticsState.class), ident, AbstractStates.extractStateByType(state, ThreadState.class));
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
    assert(id != null);
    return id;
  }

  public @Nonnull void setId(SingleIdentifier pId) {
    //Now it is set while creation
    assert id == null || id.getName().equals(pId.getName()) : "Old id " + id + ", new one - " + pId;
    //id = pId;
  }

  public UsagePoint getUsagePoint() {
    if (this.locks != null && (this.locks.getSize() > 0 || this.accessType == Access.READ)) {
      return new UsagePoint(locks.getLockIdentifiers(), accessType, threadInfo);
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
    result = prime * result + ((threadInfo == null) ? 0 : threadInfo.hashCode());
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
    if (threadInfo == null) {
      if (other.threadInfo != null) {
        return false;
      }
    } else if (!threadInfo.equals(other.threadInfo)) {
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

    if (id != null) {
      sb.append("Id ");
      sb.append(id.toString());
      sb.append(", ");
    }
    sb.append("line ");
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

  public ThreadState getThreadInfo() {
    return threadInfo;
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
    if (threadInfo != null) {
      result = this.threadInfo.compareTo(pO.threadInfo);
      if (result != 0) {
        return result;
      }
    }
    /* We can't use key states for ordering, because the treeSets can't understand,
     * that old refined usage with zero key state is the same as new one
     */
    if (this.id != null && pO.id != null) {
      //Identifiers may not be equal here:
      // if (a.b > c.b)
      // FieldIdentifiers are the same (when we add to container),
      // but full identifiers (here) are not equal
      // TODO should we distinguish them?

    }
    return 0;
  }

  private void setPath(List<CFAEdge> p) {
    List<CFAEdge> edges = p;
    edges = from(edges).filter(new Predicate<CFAEdge>() {
      @Override
      public boolean apply(@Nullable CFAEdge pInput) {
        if (pInput instanceof CDeclarationEdge) {
          if (((CDeclarationEdge)pInput).getDeclaration() instanceof CFunctionDeclaration ||
              ((CDeclarationEdge)pInput).getDeclaration() instanceof CTypeDeclaration) {
            return false;
          }
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
    UsageInfo result = new UsageInfo(accessType, line, locks, id, threadInfo);
    result.id = this.id;
    result.keyState = this.keyState;
    result.path = this.path;
    result.failureFlag = this.failureFlag;
    return result;
  }

  public UsageInfo expand(LockStatisticsState expandedState) {
    UsageInfo result = new UsageInfo(this.accessType, this.line, expandedState, id, threadInfo);
    result.id = this.id;
    result.keyState = this.keyState;
    result.path = this.path;
    result.failureFlag = this.failureFlag;
    return result;
  }
}
