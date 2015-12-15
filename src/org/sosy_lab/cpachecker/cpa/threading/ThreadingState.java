/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.threading;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.cpa.location.LocationState;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;

/** This immutable state represents a location state combined with a callstack state. */
public class ThreadingState implements AbstractState, AbstractStateWithLocations, Graphable, Partitionable {

  final static int MIN_THREAD_NUM = 0;

  // String :: identifier for the thread TODO change to object or memory-location
  // CallstackState +  LocationState :: thread-position
  private final PersistentMap<String, AbstractState> callstacks;
  private final PersistentMap<String, AbstractState> locations;

  // Each thread is assigned to an Integer
  // TODO do we really need this? -> needed for identification of cloned functions.
  private final PersistentMap<String, Integer> threadNums;

  // String :: lock-id  -->  String :: thread-id
  private final PersistentMap<String, String> locks;

  public ThreadingState() {
    this.callstacks = PathCopyingPersistentTreeMap.of();
    this.locations = PathCopyingPersistentTreeMap.of();
    this.locks = PathCopyingPersistentTreeMap.of();
    this.threadNums = PathCopyingPersistentTreeMap.of();
  }

  private ThreadingState(
      PersistentMap<String, AbstractState> pStacks,
      PersistentMap<String, AbstractState> pLocations,
      PersistentMap<String, String> pLocks,
      PersistentMap<String, Integer> pThreadNums) {
    this.callstacks = pStacks;
    this.locations = pLocations;
    this.locks = pLocks;
    this.threadNums = pThreadNums;
 }

  public ThreadingState addThreadAndCopy(String id, int num, AbstractState stack, AbstractState loc) {
    Preconditions.checkNotNull(id);
    Preconditions.checkArgument(!locations.containsKey(id), "thread already exists");
    Preconditions.checkArgument(!threadNums.containsValue(num), "thread-number already exists");
    return new ThreadingState(
        callstacks.putAndCopy(id, stack), locations.putAndCopy(id, loc),
        locks, threadNums.putAndCopy(id, num));
  }

  public ThreadingState updateThreadAndCopy(String id, AbstractState stack, AbstractState loc) {
    Preconditions.checkNotNull(id);
    Preconditions.checkArgument(locations.containsKey(id), "updating non-existing thread");
    return new ThreadingState(
        callstacks.putAndCopy(id, stack), locations.putAndCopy(id, loc),
        locks, threadNums);
  }

  public ThreadingState removeThreadAndCopy(String id) {
    Preconditions.checkNotNull(id);
    Preconditions.checkState(locations.containsKey(id), "leaving non-existing thread: " + id);
    return new ThreadingState(
        callstacks.removeAndCopy(id), locations.removeAndCopy(id),
        locks, threadNums.removeAndCopy(id));
  }

  public Set<String> getThreadIds() {
    return callstacks.keySet();
  }

  public AbstractState getThreadCallstack(String id) {
    return Preconditions.checkNotNull(callstacks.get(id));
  }

  public LocationState getThreadLocation(String id) {
    return (LocationState) Preconditions.checkNotNull(locations.get(id));
  }

  Set<Integer> getThreadNums() {
    Set<Integer> result = new HashSet<>(threadNums.values());
    Preconditions.checkState(result.size() == threadNums.size());
    return result;
  }

  int getSmallestMissingThreadNum() {
    int num = MIN_THREAD_NUM;
    // TODO loop is not efficient for big number of threads
    while(threadNums.containsValue(num)) {
      num++;
    }
    return num;
  }

  public ThreadingState addLockAndCopy(String threadId, String lockId) {
    Preconditions.checkNotNull(lockId);
    Preconditions.checkNotNull(threadId);
    Preconditions.checkArgument(locations.containsKey(threadId), "blocking non-existant thread: " + threadId + " with lock: " + lockId);
    return new ThreadingState(callstacks, locations, locks.putAndCopy(lockId, threadId), threadNums);
  }

  public ThreadingState removeLockAndCopy(String threadId, String lockId) {
    Preconditions.checkNotNull(threadId);
    Preconditions.checkNotNull(lockId);
    Preconditions.checkArgument(locations.containsKey(threadId), "unblocking non-existant thread: " + threadId + " with lock: " + lockId);
    return new ThreadingState(callstacks, locations, locks.removeAndCopy(lockId), threadNums);
  }

  /** returns whether any of the threads has the lock */
  public boolean hasLock(String lockId) {
    return locks.containsKey(lockId); // TODO threadId needed?
  }

  /** returns whether the given thread has the lock */
  public boolean hasLock(String threadId, String lockId) {
    return locks.containsKey(lockId) && threadId.equals(locks.get(lockId));
  }

  /** returns whether there is any lock registered for the thread. */
  public boolean hasLockForThread(String threadId) {
    return locks.containsValue(threadId);
  }

  @Override
  public String toString() {
    return "( locations={\n"
        + Joiner.on(",\n ").withKeyValueSeparator("=").join(locations)
        + "}\n with stacks={"
        + Joiner.on(",\n ").withKeyValueSeparator("=").join(callstacks)
        + "}\n and locks={"
        + Joiner.on(",\n ").withKeyValueSeparator("=").join(locks)
        + "}\n and ids={"
        + Joiner.on(",\n ").withKeyValueSeparator("=").join(threadNums)
        + "})";
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof ThreadingState)) {
      return false;
    }
    ThreadingState ts = (ThreadingState)other;
    return callstacks.equals(ts.callstacks)
        && locations.equals(ts.locations)
        && locks.equals(ts.locks)
        && threadNums.equals(ts.threadNums);
  }

  @Override
  public int hashCode() {
    return Objects.hash(callstacks, locations, locks, threadNums);
  }

  private FluentIterable<AbstractStateWithLocations> getLocations() {
    return FluentIterable.from(locations.values()).transform(
        new Function<AbstractState, AbstractStateWithLocations>() {
          @Override
          public AbstractStateWithLocations apply(AbstractState s) {
            return (AbstractStateWithLocations) s;
          }
        });
  }

  private final static Function<AbstractStateWithLocations, Iterable<CFANode>> LOCATION_NODES =
      new Function<AbstractStateWithLocations, Iterable<CFANode>>() {
        @Override
        public Iterable<CFANode> apply(AbstractStateWithLocations loc) {
          return loc.getLocationNodes();
        }
      };

  private final static Function<AbstractStateWithLocations, Iterable<CFAEdge>> OUTGOING_EDGES =
      new Function<AbstractStateWithLocations, Iterable<CFAEdge>>() {
        @Override
        public Iterable<CFAEdge> apply(AbstractStateWithLocations loc) {
          return loc.getOutgoingEdges();
        }
      };

  @Override
  public Iterable<CFANode> getLocationNodes() {
    return getLocations().transformAndConcat(LOCATION_NODES);
  }

  @Override
  public Iterable<CFAEdge> getOutgoingEdges() {
    return getLocations().transformAndConcat(OUTGOING_EDGES);
  }

  @Override
  public String toDOTLabel() {
    StringBuilder sb = new StringBuilder();

    sb.append("[");
    Joiner.on(",\n ").withKeyValueSeparator("=").appendTo(sb, locations);
    sb.append("]");

    return sb.toString();
  }

  @Override
  public boolean shouldBeHighlighted() {
    return false;
  }

  @Override
  public Object getPartitionKey() {
    return locations;
  }
}
