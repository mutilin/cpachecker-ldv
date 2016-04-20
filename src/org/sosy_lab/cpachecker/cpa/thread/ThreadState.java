/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.thread;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.core.interfaces.AbstractWrapperState;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.location.LocationState;

import com.google.common.base.Preconditions;


public class ThreadState implements AbstractState, AbstractStateWithLocations, Partitionable,
    AbstractWrapperState, Comparable<ThreadState> {

  private final LocationState location;
  private final CallstackState callstack;
  private final Set<ThreadLabel> threadSet;
  private final Set<ThreadLabel> removedSet;

  public ThreadState(LocationState l, CallstackState c, Set<ThreadLabel> Tset, Set<ThreadLabel> Rset) {
    location = l;
    callstack = c;
    threadSet = Tset;
    removedSet = Rset;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((callstack == null) ? 0 : callstack.hashCode());
    result = prime * result + ((location == null) ? 0 : location.hashCode());
    result = prime * result + ((removedSet == null) ? 0 : removedSet.hashCode());
    result = prime * result + ((threadSet == null) ? 0 : threadSet.hashCode());
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
    ThreadState other = (ThreadState) obj;
    if (callstack == null) {
      if (other.callstack != null) {
        return false;
      }
    } else if (!callstack.equals(other.callstack)) {
      return false;
    }
    if (location == null) {
      if (other.location != null) {
        return false;
      }
    } else if (!location.equals(other.location)) {
      return false;
    }
    if (removedSet == null) {
      if (other.removedSet != null) {
        return false;
      }
    } else if (!removedSet.equals(other.removedSet)) {
      return false;
    }
    if (threadSet == null) {
      if (other.threadSet != null) {
        return false;
      }
    } else if (!threadSet.equals(other.threadSet)) {
      return false;
    }
    return true;
  }

  @Override
  public Object getPartitionKey() {
    List<Object> keys = new ArrayList<>(2);
    keys.add(location.getPartitionKey());
    keys.add(callstack.getPartitionKey());
    return keys;
  }

  @Override
  public Iterable<CFANode> getLocationNodes() {
    return location.getLocationNodes();
  }

  @Override
  public Iterable<CFAEdge> getOutgoingEdges() {
    return location.getOutgoingEdges();
  }

  @Override
  public Iterable<AbstractState> getWrappedStates() {
    List<AbstractState> states = new ArrayList<>(2);
    states.add(location);
    states.add(callstack);
    return states;
  }

  public Set<ThreadLabel> getThreadSet() {
    return threadSet;
  }

  public Set<ThreadLabel> getRemovedSet() {
    return removedSet;
  }

  public LocationState getLocationState() {
    return location;
  }

  public CallstackState getCallstackState() {
    return callstack;
  }

  @Override
  public int compareTo(ThreadState other) {
    int result = 0;

    result = other.threadSet.size() - this.threadSet.size(); //decreasing queue

    if (result != 0) {
      return result;
    }

    Iterator<ThreadLabel> iterator1 = threadSet.iterator();
    Iterator<ThreadLabel> iterator2 = other.threadSet.iterator();
    //Sizes are equal
    while (iterator1.hasNext()) {
      ThreadLabel label1 = iterator1.next();
      ThreadLabel label2 = iterator2.next();
      result = label1.compareTo(label2);
      if (result != 0) {
        return result;
      }
    }
    //Use compare only for StoredThreadState
    Preconditions.checkArgument(location == null && callstack == null);
    return 0;
  }


  public boolean isCompatibleWith(ThreadState other) {
    for (ThreadLabel label : threadSet) {
      for (ThreadLabel oLabel : other.threadSet) {
        if (label.isCompatibleWith(oLabel)) {
          return true;
        }
      }
    }
    return false;
  }

  public ThreadState prepareToStore() {
    return new StoredThreadState(this);
  }

  @Override
  public String toString() {
    return threadSet.toString();
  }

  public class StoredThreadState extends ThreadState {
    StoredThreadState(ThreadState origin) {
      super(null, null, origin.threadSet, null);
    }
  }
}
