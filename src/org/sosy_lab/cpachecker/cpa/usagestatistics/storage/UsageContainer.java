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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsState;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

public class UsageContainer {
  private final Map<SingleIdentifier, UnrefinedUsagePointSet> unrefinedStat;
  private final Map<UnrefinedUsagePointSet, SingleIdentifier> toId;
  private final Map<SingleIdentifier, RefinedUsagePointSet> refinedStat;

  private final Set<SingleIdentifier> falseUnsafes;

  private final LogManager logger;

  public Timer resetTimer = new Timer();

  int unsafeUsages = -1;
  int totalIds = 0;

  public UsageContainer(Configuration config, LogManager l) throws InvalidConfigurationException {
    unrefinedStat = new TreeMap<>();
    toId = new HashMap<>();
    refinedStat = new TreeMap<>();
    falseUnsafes = new TreeSet<>();
    logger = l;
  }

  public void add(final SingleIdentifier id, final UsageInfo usage) {
    UnrefinedUsagePointSet uset;

    if (falseUnsafes.contains(id)) {
      return;
    }
    if (refinedStat.containsKey(id)) {
      return;
    }
    if (!unrefinedStat.containsKey(id)) {
      uset = new UnrefinedUsagePointSet();
      unrefinedStat.put(id, uset);
      toId.put(uset, id);
    } else {
      uset = unrefinedStat.get(id);
    }
    uset.add(usage);
  }

  private void getUnsafesIfNecessary() {
    if (unsafeUsages == -1) {
      unsafeUsages = 0;
      Set<SingleIdentifier> toDelete = new HashSet<>();
      for (SingleIdentifier id : unrefinedStat.keySet()) {
        UnrefinedUsagePointSet tmpList = unrefinedStat.get(id);
        if (tmpList.isUnsafe()) {
          unsafeUsages += tmpList.size();
        } else {
          toDelete.add(id);
          falseUnsafes.add(id);
        }
      }
      for (SingleIdentifier id : toDelete) {
        unrefinedStat.remove(id);
      }
      for (SingleIdentifier id : refinedStat.keySet()) {
        RefinedUsagePointSet tmpList = refinedStat.get(id);
        unsafeUsages += tmpList.size();
      }
    }
  }

  public Iterator<SingleIdentifier> getUnsafeIterator() {
    getUnsafesIfNecessary();
    Set<SingleIdentifier> result = new TreeSet<>(unrefinedStat.keySet());
    result.addAll(refinedStat.keySet());
    return result.iterator();
  }

  public Iterator<SingleIdentifier> getUnrefinedUnsafeIterator() {
    getUnsafesIfNecessary();
    Set<SingleIdentifier> result = new TreeSet<>(unrefinedStat.keySet());
    return result.iterator();
  }

  public Iterator<SingleIdentifier> getGeneralIterator() {
    return getUnsafeIterator();
  }

  public int getUnsafeSize() {
    getUnsafesIfNecessary();
    return unrefinedStat.size() + refinedStat.size();
  }

  public int getTrueUnsafeSize() {
    return refinedStat.size();
  }

  public int getFalseUnsafeSize() {
    return falseUnsafes.size();
  }

  public void resetUnrefinedUnsafes() {
    resetTimer.start();
    unsafeUsages = -1;
    for (UnrefinedUsagePointSet uset : unrefinedStat.values()) {
      uset.reset();
    }
    logger.log(Level.FINE, "Unsafes are reseted");
    resetTimer.stop();
  }

  public void removeState(final UsageStatisticsState pUstate) {
    for (UnrefinedUsagePointSet uset : unrefinedStat.values()) {
      uset.remove(pUstate);
    }
    logger.log(Level.ALL, "All unsafes related to key state " + pUstate + " were removed from reached set");
  }

  public AbstractUsagePointSet getUsages(SingleIdentifier id) {
    if (unrefinedStat.containsKey(id)) {
      return unrefinedStat.get(id);
    } else {
      return refinedStat.get(id);
    }
  }

  public LinkedList<UnrefinedUsagePointSet> getUnrefinedUnsafes() {
    getUnsafesIfNecessary();
    LinkedList<UnrefinedUsagePointSet> result = new LinkedList<>();
    //Not just values()! We must create a collection deterministically
    for (SingleIdentifier id : unrefinedStat.keySet()) {
      result.add(unrefinedStat.get(id));
    }
    return result;
  }

  public void setAsRefined(UnrefinedUsagePointSet set) {
    assert toId.containsKey(set);
    SingleIdentifier id = toId.get(set);
    refinedStat.put(id, set.asTrueUnsafe());
    unrefinedStat.remove(id);
    toId.remove(set);
  }

  public void printUsagesStatistics(final PrintStream out) {
    int allUsages = 0, maxUsage = 0;
    final int generalUnrefinedSize = unrefinedStat.keySet().size();
    for (UnrefinedUsagePointSet uset : unrefinedStat.values()) {
      allUsages += uset.size();
      if (maxUsage < uset.size()) {
        maxUsage = uset.size();
      }
    }
    out.println("Total amount of unrefined variables:              " + generalUnrefinedSize);
    out.println("Total amount of unrefined usages:                 " + allUsages + "(avg. " +
        (generalUnrefinedSize == 0 ? "0" : (allUsages/generalUnrefinedSize)) + ", max " + maxUsage + ")");
    final int generalRefinedSize = refinedStat.keySet().size();
    allUsages = 0;
    for (RefinedUsagePointSet uset : refinedStat.values()) {
      allUsages += uset.size();
    }
    out.println("Total amount of refined variables:                " + generalRefinedSize);
    out.println("Total amount of refined usages:                   " + allUsages + "(avg. " +
        (generalRefinedSize == 0 ? "0" : (allUsages/generalRefinedSize)) + ")");
  }
}
