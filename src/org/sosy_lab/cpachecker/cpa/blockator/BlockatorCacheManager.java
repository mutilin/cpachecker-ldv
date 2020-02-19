/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.blockator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.util.Pair;

public class BlockatorCacheManager {
  private static final class CacheKey {
    final Block block;
    final Object reducerHashCode;

    public CacheKey(Block pBlock, Object pReducerHashCode) {
      block = pBlock;
      reducerHashCode = pReducerHashCode;
    }

    @Override
    public boolean equals(Object pO) {
      if (this == pO) {
        return true;
      }

      if (pO == null || getClass() != pO.getClass()) {
        return false;
      }

      CacheKey cacheKey = (CacheKey) pO;
      return block.equals(cacheKey.block) && reducerHashCode.equals(cacheKey.reducerHashCode);
    }

    @Override
    public int hashCode() {
      return Objects.hash(block, reducerHashCode);
    }
  }

  public static final class CacheEntry {
    private AtomicBoolean isComputed = new AtomicBoolean(false);
    private List<Pair<AbstractState, Precision>> cacheUsages = new ArrayList<>();
    private List<Pair<AbstractState, Precision>> exitStates = new ArrayList<>();
    private Map<AbstractState, List<AbstractState>> exitUsages = new HashMap<>();

    public boolean shouldCompute() {
      return !isComputed.getAndSet(true);
    }

    public List<Pair<AbstractState, Precision>> getCacheUsages() {
      return Collections.unmodifiableList(cacheUsages);
    }

    public List<Pair<AbstractState, Precision>> getExitStates() {
      return Collections.unmodifiableList(exitStates);
    }

    public void addCacheUsage(AbstractState state, Precision precision) {
      cacheUsages.add(Pair.of(state, precision));
    }

    public void addExitState(AbstractState state, Precision precision) {
      exitStates.add(Pair.of(state, precision));
    }

    public void addExitUsages(AbstractState exit, Collection<? extends AbstractState> usage) {
      exitUsages.computeIfAbsent(exit, (k) -> new ArrayList<>()).addAll(usage);
    }

    public boolean hasExitUsages(AbstractState exit) {
      return exitUsages.containsKey(exit);
    }

    public List<AbstractState> getExitUsages(AbstractState exit) {
      List<AbstractState> ret = exitUsages.get(exit);
      return ret != null ? Collections.unmodifiableList(ret) : null;
    }

    public void removeExitUsage(AbstractState exit) {
      exitUsages.remove(exit);
    }
  }

  private Reducer reducer;
  private ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

  public BlockatorCacheManager(Reducer pReducer) {
    reducer = pReducer;
  }

  private CacheKey key(Block block, AbstractState state, Precision precision) {
    return new CacheKey(block, reducer.getHashCodeForState(state, precision));
  }

  public CacheEntry getOrCreate(Block block, AbstractState state, Precision precision) {
    return cache.computeIfAbsent(key(block, state, precision), (k) -> new CacheEntry());
  }

  public CacheEntry get(Block block, AbstractState state, Precision precision) {
    return cache.get(key(block, state, precision));
  }

  public void remove(Block block, AbstractState state, Precision precision) {
    cache.remove(key(block, state, precision));
  }
}
