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
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;

public class UsageList {
  private final Set<UsagePoint> topUsages;
  private final Map<UsagePoint, UsageInfoSet> detailInformation;
  private boolean isTrueUnsafe = false;
  
  public UsageList() {
    topUsages = new TreeSet<>();
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
    Set<UsagePoint> toDelete = new HashSet<>();
    if (!topUsages.contains(newPoint)) {
      //Put newPoint in the right place in tree
      for (UsagePoint point : topUsages) {
        if (newPoint.isHigherOrEqual(point)) {
          //We have checked, that new point isn't contained in the set
          assert !newPoint.equals(point);
          toDelete.add(point);
          newPoint.addCoveredUsage(point);
        } else if (point.isHigherOrEqual(newPoint)) {
          //We have checked, that new point isn't contained in the set
          assert !newPoint.equals(point);
          point.addCoveredUsage(newPoint);
          return;
        }
      }
      topUsages.removeAll(toDelete);
      topUsages.add(newPoint);
    }
  }
  
  public boolean isUnsafe() {
    if (topUsages.size() > 1) {
      Iterator<UsagePoint> iterator = topUsages.iterator();
      UsagePoint point = iterator.next();
      if (point.access == Access.WRITE) {
        Set<LockIdentifier> lockSet = new HashSet<>(point.locks);
        while (iterator.hasNext() && !lockSet.isEmpty()) {
          lockSet.retainAll(iterator.next().locks);
        }
        if (lockSet.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }
  
  public Pair<UsageInfo, UsageInfo> getUnsafePair() {
    assert isUnsafe();
    
    Iterator<UsagePoint> iterator = topUsages.iterator();
    UsageInfoSet firstSet = detailInformation.get(iterator.next());
    UsageInfoSet secondSet;
    if (iterator.hasNext()) {
      secondSet = detailInformation.get(iterator.next());
    } else {
      //One write usage is also unsafe, as we consider the function to be able to run in parallel with itself
      secondSet = firstSet;
    }
    return Pair.of(firstSet.getOneExample(), secondSet.getOneExample());
  }
  
  public boolean isTrueUnsafe() {
    if (!isTrueUnsafe) {
      if (!isUnsafe()) {
        return false;
      }
      
      Iterator<UsagePoint> iterator = topUsages.iterator();
      boolean result = iterator.next().isTrue();
      if (iterator.hasNext()) {
        //One write usage is also unsafe, as we consider the function to be able to run in parallel with itself
        result &= iterator.next().isTrue();
      }
      
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
    for (UsagePoint point : topUsages) {
      if (!point.isTrue()) {
        toDelete.add(point);
      }
    }
    topUsages.removeAll(toDelete);
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
    return topUsages.iterator();
  }
  
  public int getNumberOfTopUsagePoints() {
    return topUsages.size();
  }

  public UsageInfoSet getUsageInfo(UsagePoint next) {
    return detailInformation.get(next);
  }

  public void remove(UsagePoint currentUsagePoint) {
    UsageInfoSet tmpSet = detailInformation.get(currentUsagePoint);
    assert tmpSet.hasNoRefinedUsages();
    
    detailInformation.remove(currentUsagePoint);
    topUsages.remove(currentUsagePoint);
    for (UsagePoint point : currentUsagePoint.getCoveredUsages()) {
      add(point);
    }
  }
}
