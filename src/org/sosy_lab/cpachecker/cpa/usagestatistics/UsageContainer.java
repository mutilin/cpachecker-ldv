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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UnsafeDetector.SearchMode;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

@Options(prefix="cpa.usagestatistics")
public class UsageContainer {

  private PairwiseUnsafeDetector unsafeDetector = null;

  private Map<SingleIdentifier, UsageList> Stat;

  public List<SingleIdentifier> unsafes = null;

  @Option(name="debug", description="provides a way to look after refinement a target unsafe. Requires an option targetunsafename")
  private boolean debugMode = false;

  @Option(name="targetUnsafeName", description="Name the unsafe, which is target for debugging mode")
  private String checkId;

  private final LogManager logger;

  public Timer resetTimer = new Timer();

  int totalUsages = 0;
  int totalIds = 0;

  public UsageContainer(Configuration config, LogManager l) throws InvalidConfigurationException {
    config.inject(this);
    unsafeDetector = new PairwiseUnsafeDetector(config);
    Stat = new TreeMap<>();
    logger = l;
  }

  public void add(final SingleIdentifier id, final UsageInfo usage) {
    UsageList uset;

    if (!Stat.containsKey(id)) {
      uset = new UsageList();
      Stat.put(id, uset);
    } else {
      uset = Stat.get(id);
      if (uset.isTrueUnsafe()) {
        //don't spend time
        return;
      }
      if (uset.contains(usage)) {
        UsageInfo oldUsage = uset.get(uset.indexOf(usage));
        if (oldUsage.isRefined()) {
          return;
        } else if (oldUsage.getCallStack().equals(usage.getCallStack())){
          //TODO May be, if this usage isn't refined and still here, it hasn't got pair to be an unsafe. And we may not to update it
          uset.remove(oldUsage);
        }
      }
    }
    uset.add(usage);
  }

  public Map<SingleIdentifier, UsageList> getStatistics() {
    return Stat;
  }

  public List<SingleIdentifier> getUnsafes() {
    if (unsafes == null) {
      totalIds = 0;
      totalUsages = 0;
      unsafes = unsafeDetector.getUnsafes(Stat);
      for (SingleIdentifier id : Stat.keySet()) {
        totalUsages += Stat.get(id).size();
        totalIds++;
      }
    }
    return unsafes;
  }

  public void resetUnsafes() {
    resetTimer.start();
    unsafes = null;
    Set<UsageInfo> toDelete = new HashSet<>();
    Set<SingleIdentifier> idToDelete = new HashSet<>();

    for (SingleIdentifier id : Stat.keySet()) {
      UsageList uset = Stat.get(id);
      //All false unsafes were deleted during refinement
      for (UsageInfo uinfo : uset) {
        /* This is done to free memory
         * otherwise all ARG will be stored on the next stage of analysis
         */
        if (uinfo.isRefined()) {
          uinfo.setKeyState(null);
        } else {
          toDelete.add(uinfo);
        }
      }

      uset.removeAll(toDelete);
      toDelete.clear();
    }
    for (SingleIdentifier id : idToDelete) {
      logger.log(Level.ALL, "Identifier " + id + " was removed from statistics");
      Stat.remove(id);
    }
    logger.log(Level.FINE, "Unsafes are reseted");
    resetTimer.stop();
  }

  public void removeState(final UsageStatisticsState pUstate) {
    List<UsageInfo> uset;
    //Not a set! Some usages and sets can be equals, but referes to different ids
    List<UsageInfo> toDelete = new LinkedList<>();
    for (SingleIdentifier id : Stat.keySet()) {
      uset = Stat.get(id);
      for (UsageInfo uinfo : uset) {
        AbstractState keyState = uinfo.getKeyState();
        assert (keyState != null);
        if (AbstractStates.extractStateByType(keyState, UsageStatisticsState.class).equals(pUstate)) {
          if (!uinfo.isRefined()) {
            toDelete.add(uinfo);
          }
        }
      }
      uset.removeAll(toDelete);
      toDelete.clear();
    }
    logger.log(Level.ALL, "All unsafes related to key state " + pUstate + " were removed from reached set");
  }

  public SingleIdentifier getRefineableId(SingleIdentifier refinementId) {
    if (refinementId == null) {
      return null;
    }
    UsageList uset = Stat.get(refinementId);
    if (debugMode) {
      if (refinementId.getName().equals(checkId)) {
        if (isRefineableUsageList(uset)) {
          return refinementId;
        } else {
          return null;
        }
      }
    }
    if (!isRefineableUsageList(uset) || debugMode) {
      for (SingleIdentifier id : unsafes) {
        if (!debugMode || id.getName().equals(checkId)) {
          uset = Stat.get(id);
          if (isRefineableUsageList(uset)) {
            return id;
          }
        }
      }
      return null;
    } else {
      return refinementId;
    }
  }

  public void remove(SingleIdentifier pRefinementId, UsageInfo pTarget) {
    Stat.get(pRefinementId).remove(pTarget);
    logger.log(Level.ALL, "Remove " + pTarget + " identifier " + pRefinementId + " was removed from statistics");
  }

  private boolean isRefineableUsageList(UsageList ulist) {
    if (ulist.isTrueUnsafe()) {
      return false;
    } else if (unsafeDetector.containsUnsafe(ulist, SearchMode.TRUE)) {
      ulist.markAsTrueUnsafe();
      return false;
    } else if (!unsafeDetector.containsUnsafe(ulist, SearchMode.FALSE)) {
      return false;
    }
    return true;
  }
}
