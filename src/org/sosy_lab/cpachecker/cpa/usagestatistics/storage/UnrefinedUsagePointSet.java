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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsState;

public class UnrefinedUsagePointSet implements AbstractUsagePointSet {
  private final TreeSet<UsagePoint> topUsages;
  private final Map<UsagePoint, UnrefinedUsageInfoSet> unrefinedInformation;
  private final Map<UsagePoint, RefinedUsageInfoSet> refinedInformation;

  public UnrefinedUsagePointSet() {
    topUsages = new TreeSet<>();
    unrefinedInformation = new HashMap<>();
    refinedInformation = new HashMap<>();
  }

  public void add(UsageInfo newInfo) {
    UnrefinedUsageInfoSet targetSet;
    UsagePoint newPoint = newInfo.getUsagePoint();
    if (refinedInformation.containsKey(newPoint)) {
      return;
    }
    if (unrefinedInformation.containsKey(newPoint)) {
      targetSet = unrefinedInformation.get(newPoint);
    } else {
      targetSet = new UnrefinedUsageInfoSet();
      unrefinedInformation.put(newPoint, targetSet);
    }
    add(newPoint);
    targetSet.add(newInfo);
  }

  private void add(UsagePoint newPoint) {
    if (!topUsages.contains(newPoint)) {
      //Put newPoint in the right place in tree
      Iterator<UsagePoint> iterator = topUsages.iterator();
      while (iterator.hasNext()) {
        UsagePoint point = iterator.next();
        if (newPoint.isHigher(point)) {
          iterator.remove();
          if (!refinedInformation.containsKey(newPoint)) {
            newPoint.addCoveredUsage(point);
          }
        } else if (point.isHigher(newPoint)) {
          if (!refinedInformation.containsKey(point)) {
            point.addCoveredUsage(newPoint);
          }
          return;
        }
      }
      topUsages.add(newPoint);
    }
  }

  @Override
  public AbstractUsageInfoSet getUsageInfo(UsagePoint point) {
    if (refinedInformation.containsKey(point)) {
      return refinedInformation.get(point);
    } else {
      return unrefinedInformation.get(point);
    }
  }

  @Override
  public int size() {
    int result = 0;

    for (UsagePoint point : refinedInformation.keySet()) {
      result += refinedInformation.get(point).size();
    }
    for (UsagePoint point : unrefinedInformation.keySet()) {
      result += unrefinedInformation.get(point).size();
    }

    return result;
  }

  public void reset() {
    Iterator<UsagePoint> iterator = topUsages.iterator();
    while (iterator.hasNext()) {
      UsagePoint point = iterator.next();
      if (!refinedInformation.containsKey(point)) {
        iterator.remove();
      }
    }
    unrefinedInformation.clear();
  }

  @Override
  public void remove(UsageStatisticsState pUstate) {
    //Attention! Use carefully. May not work
    for (UsagePoint point : new TreeSet<>(unrefinedInformation.keySet())) {
      UnrefinedUsageInfoSet uset = unrefinedInformation.get(point);
      boolean b = uset.remove(pUstate);
      if (b) {
        if (uset.getUsages().isEmpty()) {
          unrefinedInformation.remove(point);
        }
        //May be two usages related to the same state. This is abstractState !
        //return;
      }
    }
  }

  public Iterator<UsagePoint> getPointIterator() {
    return new TreeSet<>(topUsages).iterator();
  }

  @Override
  public int getNumberOfTopUsagePoints() {
    return topUsages.size();
  }

  public void markAsReachableUsage(UsageInfo uinfo,  List<CFAEdge> path) {

    UsagePoint p = uinfo.getUsagePoint();
    assert topUsages.contains(p);

    topUsages.remove(p);
    p.markAsTrue(path);
    topUsages.add(p);
    unrefinedInformation.remove(p);
    refinedInformation.put(p, new RefinedUsageInfoSet(uinfo, path));
  }

  public void remove(UsagePoint currentUsagePoint) {
    unrefinedInformation.remove(currentUsagePoint);
    topUsages.remove(currentUsagePoint);
    for (UsagePoint point : currentUsagePoint.getCoveredUsages()) {
      add(point);
    }
  }

  SortedSet<UsagePoint> getTopUsages() {
    return topUsages;
  }

  Set<UsagePoint> getRefinedInformation() {
    return refinedInformation.keySet();
  }
}
