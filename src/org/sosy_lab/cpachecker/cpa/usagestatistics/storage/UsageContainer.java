/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.RefinementResult;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class UsageContainer {
  private final SortedMap<SingleIdentifier, UnrefinedUsagePointSet> unrefinedIds;
  private final Map<UnrefinedUsagePointSet, SingleIdentifier> toId;
  private final SortedMap<SingleIdentifier, RefinedUsagePointSet> refinedIds;

  private final UnsafeDetector detector;

  private final Set<SingleIdentifier> falseUnsafes;

  private final Set<SingleIdentifier> processedUnsafes = new HashSet<>();
  //Only for statistics
  private Set<SingleIdentifier> initialSet = null;
  private int initialUsages;

  private final LogManager logger;

  public Timer resetTimer = new Timer();

  int unsafeUsages = -1;
  int totalIds = 0;

  public UsageContainer(Configuration config, LogManager l) throws InvalidConfigurationException {
    this(new TreeMap<SingleIdentifier, UnrefinedUsagePointSet>(),
        new HashMap<UnrefinedUsagePointSet, SingleIdentifier>(),
        new TreeMap<SingleIdentifier, RefinedUsagePointSet>(),
        new TreeSet<SingleIdentifier>(), l, new UnsafeDetector(config));
  }

  private UsageContainer(SortedMap<SingleIdentifier, UnrefinedUsagePointSet> pUnrefinedStat,
      Map<UnrefinedUsagePointSet, SingleIdentifier> pToId,
      SortedMap<SingleIdentifier, RefinedUsagePointSet> pRefinedStat,
      Set<SingleIdentifier> pFalseUnsafes, LogManager pLogger,
      UnsafeDetector pDetector) {
    unrefinedIds = pUnrefinedStat;
    toId = pToId;
    refinedIds = pRefinedStat;
    falseUnsafes = pFalseUnsafes;
    logger = pLogger;
    detector = pDetector;
  }

  public void add(final SingleIdentifier id, final UsageInfo usage) {
    UnrefinedUsagePointSet uset;

    if (falseUnsafes.contains(id)) {
      return;
    }
    if (refinedIds.containsKey(id)) {
      return;
    }
    if (!unrefinedIds.containsKey(id)) {
      uset = new UnrefinedUsagePointSet();
      unrefinedIds.put(id, uset);
      toId.put(uset, id);
    } else {
      uset = unrefinedIds.get(id);
    }
    usage.setId(id);
    uset.add(usage);
  }

  private void getUnsafesIfNecessary() {
    if (unsafeUsages == -1) {
      processedUnsafes.clear();
      unsafeUsages = 0;
      Set<SingleIdentifier> toDelete = new HashSet<>();
      for (SingleIdentifier id : unrefinedIds.keySet()) {

        UnrefinedUsagePointSet tmpList = unrefinedIds.get(id);
        if (detector.isUnsafe(tmpList)) {
          unsafeUsages += tmpList.size();
        } else {
          toDelete.add(id);
          falseUnsafes.add(id);
        }
      }
      for (SingleIdentifier id : toDelete) {
        removeIdFromCaches(id);
      }
      for (SingleIdentifier id : refinedIds.keySet()) {
        RefinedUsagePointSet tmpList = refinedIds.get(id);
        unsafeUsages += tmpList.size();
      }
      if (initialSet == null) {
        assert refinedIds.isEmpty();
        initialSet = Sets.newHashSet(unrefinedIds.keySet());
        initialUsages = unsafeUsages;
      }
    }
  }

  private void removeIdFromCaches(SingleIdentifier id) {
    //It is important to remove it from both of the caches
    UnrefinedUsagePointSet uset = unrefinedIds.get(id);
    unrefinedIds.remove(id);
    toId.remove(uset);
    processedUnsafes.add(id);
  }

  public Set<SingleIdentifier> getUnsafes() {
    getUnsafesIfNecessary();
    Set<SingleIdentifier> result = new TreeSet<>(unrefinedIds.keySet());
    result.addAll(refinedIds.keySet());
    return result;
  }

  public Set<SingleIdentifier> getInitialUnsafes() {
    return initialSet;
  }

  public Iterator<SingleIdentifier> getUnsafeIterator() {
    return getUnsafes().iterator();
  }

  public Iterator<SingleIdentifier> getUnrefinedUnsafeIterator() {
    getUnsafesIfNecessary();
    Set<SingleIdentifier> result = new TreeSet<>(unrefinedIds.keySet());
    return result.iterator();
  }

  public int getUnsafeSize() {
    getUnsafesIfNecessary();
    return unrefinedIds.size() + refinedIds.size();
  }

  public int getTrueUnsafeSize() {
    return refinedIds.size();
  }

  public int getFalseUnsafeSize() {
    return falseUnsafes.size();
  }

  public Set<SingleIdentifier> getFalseUnsafes() {
    return falseUnsafes;
  }

  public UnsafeDetector getUnsafeDetector() {
    return detector;
  }

  public void resetUnrefinedUnsafes() {
    resetTimer.start();
    unsafeUsages = -1;
    for (UnrefinedUsagePointSet uset : unrefinedIds.values()) {
      uset.reset();
    }
    logger.log(Level.FINE, "Unsafes are reseted");
    resetTimer.stop();
  }

  public void removeState(final UsageStatisticsState pUstate) {
    for (UnrefinedUsagePointSet uset : unrefinedIds.values()) {
      uset.remove(pUstate);
    }
    logger.log(Level.ALL, "All unsafes related to key state " + pUstate + " were removed from reached set");
  }

  public AbstractUsagePointSet getUsages(SingleIdentifier id) {
    if (unrefinedIds.containsKey(id)) {
      return unrefinedIds.get(id);
    } else {
      return refinedIds.get(id);
    }
  }

  public LinkedList<UnrefinedUsagePointSet> getUnrefinedUnsafes() {
    getUnsafesIfNecessary();
    LinkedList<UnrefinedUsagePointSet> result = new LinkedList<>();
    //Not just values()! We must create a collection deterministically
    for (SingleIdentifier id : unrefinedIds.keySet()) {
      result.add(unrefinedIds.get(id));
    }
    return result;
  }

  public void setAsRefined(UnrefinedUsagePointSet set, RefinementResult result) {
    Preconditions.checkArgument(result.isTrue(), "Result is not true, can not set the set as refined");

    setAsRefined(set, result.getTrueRace().getFirst(), result.getTrueRace().getSecond());
  }

  public void setAsFalseUnsafe(SingleIdentifier id) {
    falseUnsafes.add(id);
    removeIdFromCaches(id);
  }

  public void setAsRefined(UnrefinedUsagePointSet set, UsageInfo firstUsage, UsageInfo secondUsage) {
    assert toId.containsKey(set);
    SingleIdentifier id = toId.get(set);
    refinedIds.put(id, RefinedUsagePointSet.create(firstUsage, secondUsage));
    removeIdFromCaches(id);
  }

  public void printUsagesStatistics(final PrintStream out) {
    int allUsages = 0, maxUsage = 0;
    final int generalUnrefinedSize = unrefinedIds.keySet().size();
    for (UnrefinedUsagePointSet uset : unrefinedIds.values()) {
      allUsages += uset.size();
      if (maxUsage < uset.size()) {
        maxUsage = uset.size();
      }
    }
    out.println("Total amount of unrefined variables:              " + generalUnrefinedSize);
    out.println("Total amount of unrefined usages:                 " + allUsages + "(avg. " +
        (generalUnrefinedSize == 0 ? "0" : (allUsages/generalUnrefinedSize)) + ", max " + maxUsage + ")");
    final int generalRefinedSize = refinedIds.keySet().size();
    allUsages = 0;
    for (RefinedUsagePointSet uset : refinedIds.values()) {
      allUsages += uset.size();
    }
    out.println("Total amount of refined variables:                " + generalRefinedSize);
    out.println("Total amount of refined usages:                   " + allUsages + "(avg. " +
        (generalRefinedSize == 0 ? "0" : (allUsages/generalRefinedSize)) + ")");
    out.println("Initial amount of unsafes (before refinement):    " + initialSet.size());
    out.println("Initial amount of usages (before refinement):     " + initialUsages);
  }

  @Override
  public UsageContainer clone() {
    UsageContainer result = new UsageContainer(Maps.newTreeMap(unrefinedIds), Maps.newHashMap(toId),
        Maps.newTreeMap(refinedIds), Sets.newHashSet(falseUnsafes), logger, detector);
    return result;
  }

  public SingleIdentifier getIdentifier(UnrefinedUsagePointSet set) {
    assert toId.containsKey(set);

    return toId.get(set);
  }

  public Set<SingleIdentifier> getProcessedUnsafes() {
    return processedUnsafes;
  }
}
