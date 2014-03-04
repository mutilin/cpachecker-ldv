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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;

@Options(prefix="cpa.usagestatistics")
public class UsageContainer {

  @Option(description = "variables, which will not be saved in statistics")
  private Set<String> skippedvariables = null;

  private UnsafeDetector unsafeDetector = null;

  private Map<SingleIdentifier, List<UsageInfo>> Stat;
  private boolean containsUnrefinedUnsafes;

  public final Set<SingleIdentifier> unsafes = new HashSet<>();

  public UsageContainer(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
    unsafeDetector = new PairwiseUnsafeDetector(config);
    Stat = new HashMap<>();
    containsUnrefinedUnsafes = false;
  }

  public void add(SingleIdentifier id, UsageInfo usage) {

    if (skippedvariables != null && skippedvariables.contains(id.getName())) {
      return;
    } else if (skippedvariables != null && id instanceof StructureIdentifier) {
      AbstractIdentifier owner = id;
      while (owner instanceof StructureIdentifier) {
        owner = ((StructureIdentifier)owner).getOwner();
        if (owner instanceof SingleIdentifier && skippedvariables.contains(((SingleIdentifier)owner).getName())) {
          return;
        }
      }
    }
    List<UsageInfo> uset;

    /*if (usage.getLine().getLine() == 163213) {
      System.out.println("Add line 163213");
    }*/

    /*if (unsafes.contains(id)) {
      return;
    }*/

    if (!Stat.containsKey(id)) {
      uset = new LinkedList<>();
      Stat.put(id, uset);
    } else {
      uset = Stat.get(id);
      if (uset.contains(usage)) {
        /*if (uset.get(uset.indexOf(usage)).isRefined()) {
          System.out.println("Try to replace refined usage");
        }*/
        return;
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
    //return false;
    return containsUnrefinedUnsafes;
  }

  public Map<SingleIdentifier, List<UsageInfo>> getStatistics() {
    return Stat;
  }

  public Collection<SingleIdentifier> getUnsafes() {
    return  unsafeDetector.getUnsafes(Stat);
  }

  public void removeState(UsageStatisticsState pUstate) {
    List<UsageInfo> uset;
    Set<Pair<List<UsageInfo>, UsageInfo>> toDelete = new HashSet<>();
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
          /*if (uinfo.getLine().getLine() == 168219) {
            System.out.println("Remove 168219 line");
          }*/
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

  public boolean check() {
    List<UsageInfo> uset;
    boolean result;
    for (SingleIdentifier id : Stat.keySet()) {
      uset = Stat.get(id);
      result = unsafeDetector.containsUnsafe(uset, false);
      if (result) {
        containsUnrefinedUnsafes = true;
        return true;
      }

    }
    containsUnrefinedUnsafes = false;
    return false;
  }

  public void reset() {
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
  }
}
