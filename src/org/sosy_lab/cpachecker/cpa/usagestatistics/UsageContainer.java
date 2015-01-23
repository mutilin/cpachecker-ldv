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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

public class UsageContainer {
  private final Map<SingleIdentifier, UnrefinedUsagePointSet> Stat;

  public List<SingleIdentifier> unsafes = null;

  private final LogManager logger;

  public Timer resetTimer = new Timer();

  int unsafeUsages = 0;
  int totalIds = 0;

  public UsageContainer(Configuration config, LogManager l) throws InvalidConfigurationException {
    Stat = new TreeMap<>();
    logger = l;
  }

  public void add(final SingleIdentifier id, final UsageInfo usage) {
    UnrefinedUsagePointSet uset;

    if (!Stat.containsKey(id)) {
      uset = new UnrefinedUsagePointSet();
      Stat.put(id, uset);
    } else {
      uset = Stat.get(id);
      if (uset.isFalseUnsafe() || uset.isTrueUnsafe()) {
        //When we clean precision we can add smth false
        return;
      }
    }
    uset.add(usage);
  }

  private void getUnsafesIfNecessary() {
    if (unsafes == null) {
      unsafeUsages = 0;
      unsafes = new LinkedList<>();
      for (SingleIdentifier id : Stat.keySet()) {
        UnrefinedUsagePointSet tmpList = Stat.get(id);
        tmpList.checkAllEmptyPoints();
        if (tmpList.isUnsafe()) {
          unsafes.add(id);
          unsafeUsages += Stat.get(id).size();
        } else {
          tmpList.setFalseUnsafe();
        }
      }
    }
  }

  public Iterator<SingleIdentifier> getUnsafeIterator() {
    getUnsafesIfNecessary();
    return unsafes.iterator();
  }

  public Iterator<SingleIdentifier> getGeneralIterator() {
    return Stat.keySet().iterator();
  }

  public int getUnsafeSize() {
    getUnsafesIfNecessary();
    return unsafes.size();
  }

  public void resetUnsafes() {
    resetTimer.start();
    unsafes = null;

    for (SingleIdentifier id : Stat.keySet()) {
      UnrefinedUsagePointSet uset = Stat.get(id);
      uset.reset();
    }
    logger.log(Level.FINE, "Unsafes are reseted");
    resetTimer.stop();
  }

  public void removeState(final UsageStatisticsState pUstate) {
    UnrefinedUsagePointSet uset;
    //Not a set! Some usages and sets can be equals, but referes to different ids
    for (SingleIdentifier id : Stat.keySet()) {
      uset = Stat.get(id);
      uset.remove(pUstate);
    }
    logger.log(Level.ALL, "All unsafes related to key state " + pUstate + " were removed from reached set");
  }

  public UnrefinedUsagePointSet getUsages(SingleIdentifier id) {
    return Stat.get(id);
  }

  public void printUsagesStatistics(final PrintStream out) {
    int allUsages = 0, maxUsage = 0;
    final int generalSize = Stat.keySet().size();
    for (SingleIdentifier id : Stat.keySet()) {
      allUsages += Stat.get(id).size();
      if (maxUsage < Stat.get(id).size()) {
        maxUsage = Stat.get(id).size();
      }
    }
    out.println("Total amount of variables:                " + generalSize);
    out.println("Total amount of usages:                   " + allUsages + "(avg. " +
        (generalSize == 0 ? "0" : (allUsages/generalSize)) + ", max " + maxUsage + ")");
  }
}
