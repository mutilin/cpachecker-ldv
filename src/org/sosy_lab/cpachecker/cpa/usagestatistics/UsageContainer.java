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

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UnsafeDetector.SearchMode;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

//@Options(prefix="cpa.usagestatistics")
public class UsageContainer {

  private PairwiseUnsafeDetector unsafeDetector = null;

  private Map<SingleIdentifier, UsageSet> Stat;
  private boolean containsUnrefinedUnsafes;

  public List<SingleIdentifier> unsafes = null;

  int totalUsages = 0;
  int totalIds = 0;

  public UsageContainer(Configuration config) throws InvalidConfigurationException {
    //config.inject(this);
    unsafeDetector = new PairwiseUnsafeDetector(config);
    Stat = new TreeMap<>();
    containsUnrefinedUnsafes = false;
  }

  public void add(SingleIdentifier id, UsageInfo usage) {


    UsageSet uset;


    /*if (id instanceof StructureIdentifier) {
      id = ((StructureIdentifier)id).toStructureFieldIdentifier();
    }*/

    /*if (usage.getLine().getLine() == 163213) {
      System.out.println("Add line 163213");
    }*/

    /*if (unsafes.contains(id)) {
      return;
    }*/

    if (!Stat.containsKey(id)) {
      uset = new UsageSet();
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
        } else {
          uset.remove(oldUsage);
        }
      }
      if (unsafeDetector.isUnsafeCase(uset, usage)) {
        //unsafes.add(id);
        containsUnrefinedUnsafes = true;
      }
    }
    uset.add(usage);
    //System.out.println("Add unsafe: " + id + ", " + unsafes.size());
    //unsafes.add(id);
  }

  public boolean isTarget() {
    return false;
    //return containsUnrefinedUnsafes;
  }

  public Map<SingleIdentifier, UsageSet> getStatistics() {
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
    unsafes = null;
    Set<UsageInfo> toDelete = new HashSet<>();
    Set<SingleIdentifier> idToDelete = new HashSet<>();

    for (SingleIdentifier id : Stat.keySet()) {
      UsageSet uset = Stat.get(id);
      if (uset.isTrueUnsafe()) {
        for (UsageInfo uinfo : uset) {
          if (uinfo.isRefined()) {
            uinfo.setKeyState(null);
          } else {
            toDelete.add(uinfo);
          }
        }
        uset.removeAll(toDelete);
        toDelete.clear();
      } else {
        idToDelete.add(id);
      }
    }
    for (SingleIdentifier id : idToDelete) {
      Stat.remove(id);
    }
  }

  public void removeState(UsageStatisticsState pUstate) {
    List<UsageInfo> uset;
    List<Pair<List<UsageInfo>, UsageInfo>> toDelete = new LinkedList<>(); //Not set! Some usages and sets can be equals but referes to different ids
    //try {
    for (SingleIdentifier id : Stat.keySet()) {
      uset = Stat.get(id);
      for (UsageInfo uinfo : uset) {
        AbstractState keyState = uinfo.getKeyState();
        if (keyState == null) {
          //It means, that this state is covered by other one and we havn't added it to reachedSet, remove it
          if (!uinfo.isRefined()) {
            toDelete.add(Pair.of(uset, uinfo));
          }
          //System.out.println("Delete " + uinfo + " due to null");
        } else if (AbstractStates.extractStateByType(keyState, UsageStatisticsState.class).equals(pUstate)) {
          //System.out.println("Delete " + uinfo + " due to keyState");
          if (!uinfo.isRefined()) {
            toDelete.add(Pair.of(uset, uinfo));
          }
        }
      }
    }
    /*} catch (NullPointerException e) {
      System.out.println("Null!");
    }*/

    for (Pair<List<UsageInfo>, UsageInfo> pair : toDelete) {
      pair.getFirst().remove(pair.getSecond());
    }
  }

  public SingleIdentifier check(SingleIdentifier refinementId) {

    if (refinementId == null) {
      return null;
    }
    UsageSet uset = Stat.get(refinementId);
    if (uset.isTrueUnsafe() || !unsafeDetector.containsUnsafe(uset, SearchMode.FALSE)) {
      if (!unsafeDetector.containsUnsafe(uset, SearchMode.TRUE)) {
        unsafes.remove(refinementId);
        //TODO May be remove only empty
        Stat.remove(refinementId);
      } else {
        uset.setUnsafe();
      }
      for (SingleIdentifier id : unsafes) {
        uset = Stat.get(id);
        if (uset.isTrueUnsafe()) {
         continue;
        }
        return id;
      }
      containsUnrefinedUnsafes = false;
      return null;
    } else {
      return refinementId;
    }
  }

  /*public void reset() {
    List<UsageInfo> uset;
    Set<UsageInfo> toDelete = new HashSet<>();
    Set<SingleIdentifier> idToDelete = new HashSet<>();
    for (SingleIdentifier id : Stat.keySet()) {
      uset = Stat.get(id);
      for (UsageInfo uinfo : uset) {
        if (!uinfo.isRefined()) {
          toDelete.add(uinfo);
        }
      }
      uset.removeAll(toDelete);
      if (uset.size() == 0) {
        idToDelete.add(id);
      }
      toDelete.clear();
    }
    for (SingleIdentifier id : idToDelete) {
      Stat.remove(id);
    }
  }*/
}
