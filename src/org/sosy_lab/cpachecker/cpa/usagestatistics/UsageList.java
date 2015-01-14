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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;

public class UsageList {
  private final Set<UsagePoint> disjointUsages;
  private final Map<UsagePoint, UsageInfoSet> detailInformation;
  private boolean isTrueUnsafe = false;
  
  public UsageList() {
    disjointUsages = new TreeSet<>();
    detailInformation = new HashMap<>();
  }
  
  public void add(UsageInfo newInfo) {
    UsageInfoSet targetSet;
    UsagePoint newPoint = newInfo.getUsagePoint();
    if (detailInformation.containsKey(newPoint)) {
      targetSet = detailInformation.get(newPoint);
      if (targetSet.isTrue()) {
        newPoint.markAsTrue();
      }
    } else {
      targetSet = new UsageInfoSet();
      detailInformation.put(newPoint, targetSet);
    }
    add(newPoint);
    targetSet.add(newInfo);
  }
  
  private void add(UsagePoint newPoint) {
    if (!disjointUsages.contains(newPoint)) {
      //Put newPoint in the right place in tree
      for (UsagePoint point : disjointUsages) {
        if (newPoint.isHigherOrEqual(point)) {
          //We have checked, that new point isn't contained in the set
          assert !newPoint.equals(point);
          disjointUsages.remove(point);
          newPoint.addCoveredUsage(point);
          //TODO May be we should check all usages and build full tree
          break;
        } else if (point.isHigherOrEqual(newPoint)) {
          //We have checked, that new point isn't contained in the set
          assert !newPoint.equals(point);
          point.addCoveredUsage(newPoint);
          return;
        }
      }
    }
    disjointUsages.add(newPoint);
  }
  
  public boolean isUnsafe() {
    if (disjointUsages.size() > 1 && disjointUsages.iterator().next().access == Access.WRITE) {
      return true;
    } else {
      return false;
    }
  }
  
  public Pair<UsageInfo, UsageInfo> getUnsafePair() {
    assert isUnsafe();
    
    Iterator<UsagePoint> iterator = disjointUsages.iterator();
    UsageInfoSet firstSet = detailInformation.get(iterator.next());
    UsageInfoSet secondSet = detailInformation.get(iterator.next());
    return Pair.of(firstSet.getOneExample(), secondSet.getOneExample());
  }
  
  public boolean isTrueUnsafe() {
    if (!isTrueUnsafe) {
      if (!isUnsafe()) {
        return false;
      }
      
      Iterator<UsagePoint> iterator = disjointUsages.iterator();
      UsagePoint firstPoint = iterator.next();
      UsagePoint secondPoint = iterator.next();
      
      boolean result = (firstPoint.isTrue() && secondPoint.isTrue());
      if (result) {
        isTrueUnsafe = true;
      }
      return result;
    } else {
      return true;
    }
  }

  public int size() {
    int result = 0;
    
    for (UsagePoint point : detailInformation.keySet()) {
      result += detailInformation.get(point).size();
    }
    
    return result;
  }
  
  public void reset() {
    Set<UsagePoint> toDelete = new HashSet<>();
    for (UsagePoint point : disjointUsages) {
      if (!point.isTrue()) {
        toDelete.add(point);
      }
    }
    disjointUsages.removeAll(toDelete);
    for (UsagePoint point : detailInformation.keySet()) {
      detailInformation.get(point).reset();
    }
  }

  public void remove(UsageStatisticsState pUstate) {
    for (UsagePoint point : detailInformation.keySet()) {
      detailInformation.get(point).remove(pUstate);
    }
  }

  public Iterator<UsagePoint> getPointIterator() {
    return disjointUsages.iterator();
  }

  public UsageInfoSet getUsageInfo(UsagePoint next) {
    return detailInformation.get(next);
  }

  public void remove(UsagePoint currentUsagePoint) {
    UsageInfoSet tmpSet = detailInformation.get(currentUsagePoint);
    assert tmpSet.hasNoRefinedUsages();
    
    detailInformation.remove(currentUsagePoint);
    disjointUsages.remove(currentUsagePoint);
    for (UsagePoint point : currentUsagePoint.getCoveredUsages()) {
      add(point);
    }
  }
}
