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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState.LockStatisticsStateBuilder;
import org.sosy_lab.cpachecker.cpa.lockstatistics.effects.LockEffect;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

import com.google.common.collect.LinkedListMultimap;

public class TemporaryUsageStorage extends TreeMap<SingleIdentifier, SortedSet<UsageInfo>> {
  private static final long serialVersionUID = -8932709343923545136L;

  private Set<SingleIdentifier> deeplyCloned = new TreeSet<>();

  private LinkedListMultimap<SingleIdentifier, UsageInfo> withoutARGState;

  private final TemporaryUsageStorage previousStorage;

  public TemporaryUsageStorage(TemporaryUsageStorage previous) {
    super(previous);
    //Copy states without ARG to set it later
    withoutARGState = LinkedListMultimap.create(previous.withoutARGState);
    previousStorage = previous;
  }

  public TemporaryUsageStorage() {
    withoutARGState = LinkedListMultimap.create();
    previousStorage = null;
  }

  public boolean add(SingleIdentifier id, UsageInfo info) {
    SortedSet<UsageInfo> storage = getStorageForId(id);
    if (info.getKeyState() == null) {
      withoutARGState.put(id, info);
    }
    return storage.add(info);
  }

  @Override
  public SortedSet<UsageInfo> put(SingleIdentifier id, SortedSet<UsageInfo> list) {
    deeplyCloned.add(id);
    return super.put(id, list);
  }

  public boolean addAll(SingleIdentifier id, SortedSet<UsageInfo> list) {
    SortedSet<UsageInfo> storage = getStorageForId(id);
    return storage.addAll(list);
  }

  private SortedSet<UsageInfo> getStorageForId(SingleIdentifier id) {
    if (deeplyCloned.contains(id)) {
      //List is already cloned
      assert this.containsKey(id);
      return this.get(id);
    } else {
      deeplyCloned.add(id);
      SortedSet<UsageInfo> storage;
      if (this.containsKey(id)) {
        //clone
        storage = new TreeSet<>(this.get(id));
      } else {
        storage = new TreeSet<>();
      }
      this.put(id, storage);
      return storage;
    }
  }

  public void setKeyState(ARGState state) {
    for (UsageInfo uinfo : withoutARGState.values()) {
      uinfo.setKeyState(state);
    }
    withoutARGState.clear();
  }

  @Override
  public void clear() {
    clearSets();
    TemporaryUsageStorage previous = previousStorage;
    //We cannot use recursion, due to large callstack and stack overflow exception
    while (previous != null) {
      previous.clearSets();
      previous = previous.previousStorage;
    }
  }

  private void clearSets() {
    super.clear();
    deeplyCloned.clear();
    withoutARGState.clear();
  }

  public void join(TemporaryUsageStorage pRecentUsages, List<LockEffect> effects) {
    // Used if the state covers the other, thus we need to copy new, only new, usages

    for (SingleIdentifier id : pRecentUsages.keySet()) {
      SortedSet<UsageInfo> otherStorage = pRecentUsages.get(id);
      if (effects.isEmpty()) {
        if (this.containsKey(id)) {
          SortedSet<UsageInfo> currentStorage = this.get(id);
          currentStorage.addAll(otherStorage);
        } else {
          this.put(id, new TreeSet<>(otherStorage));
        }
      } else {
        LockStatisticsState previousState = null, currentState;
        LockStatisticsState previousExpandedState = null, expandedState;
        for (UsageInfo uinfo : otherStorage) {
          currentState = uinfo.getLockState();
          if (previousState != null && previousState.equals(currentState)) {
            expandedState = previousExpandedState;
          } else {
            LockStatisticsStateBuilder builder = currentState.builder();
            for (LockEffect effect : effects) {
              effect.effect(builder);
            }
            expandedState = builder.build();
            previousState = currentState;
            previousExpandedState = expandedState;
          }
          add(id, uinfo.expand(expandedState));
        }
      }
    }
  }

  /*public TemporaryUsageStorage expand(List<LockEffect> effects) {
    TemporaryUsageStorage result = new TemporaryUsageStorage();
    for (SingleIdentifier id : keySet()) {
      SortedSet<UsageInfo> storage = get(id);
      for (UsageInfo uinfo : storage) {
        LockStatisticsStateBuilder builder = uinfo.getLockState().builder();
        for (LockEffect effect : effects) {
          effect.effect(builder);
        }
        LockStatisticsState expandedState2 = builder.build();

        result.add(id, uinfo.expand(expandedState2));
      }
    }
    return result;
  }*/
}
