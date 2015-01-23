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
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;

public class UnrefinedUsagePointSet implements AbstractUsagePointSet {
  private final Set<UsagePoint> topUsages;
  private final Map<UsagePoint, AbstractUsageInfoSet> detailInformation;
  private final Set<UsagePoint> falsePoints;
  
  public UnrefinedUsagePointSet() {
    topUsages = new TreeSet<>();
    detailInformation = new HashMap<>();
    falsePoints = new TreeSet<>();
  }
  
  private UnrefinedUsagePointSet(Set<UsagePoint> top, Map<UsagePoint, AbstractUsageInfoSet> detail, Set<UsagePoint> fPoints) {
    topUsages = top;
    detailInformation = detail;
    falsePoints = fPoints;
  }
  
  public void add(UsageInfo newInfo) {
    AbstractUsageInfoSet tmpSet;
    UnrefinedUsageInfoSet targetSet;
    UsagePoint newPoint = newInfo.getUsagePoint();
    if (falsePoints.contains(newPoint)) {
      return;
    }
    if (detailInformation.containsKey(newPoint)) {
      tmpSet = detailInformation.get(newPoint);
      if (!(tmpSet instanceof UnrefinedUsageInfoSet)) {
        //Do not add to true or false points: when we clean precision, we can add smth false
        return;
      } 
      targetSet = (UnrefinedUsageInfoSet) tmpSet;
    } else {
      targetSet = new UnrefinedUsageInfoSet();
      detailInformation.put(newPoint, targetSet);
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
          newPoint.addCoveredUsage(point);
        } else if (point.isHigher(newPoint)) {
          point.addCoveredUsage(newPoint);
          return;
        }
      }
      topUsages.add(newPoint);
    }
  }
  
  private boolean isUnsafe(Set<UsagePoint> points) {
    if (points.size() >= 1) {
      Iterator<UsagePoint> iterator = points.iterator();
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
    AbstractUsageInfoSet firstSet = detailInformation.get(iterator.next());
    AbstractUsageInfoSet secondSet;
    if (iterator.hasNext()) {
      UsagePoint point = iterator.next();
      secondSet = detailInformation.get(point);
    } else {
      //One write usage is also unsafe, as we consider the function to be able to run in parallel with itself
      secondSet = firstSet;
    }
    return Pair.of(firstSet.getOneExample(), secondSet.getOneExample());
  }
  
  public boolean checkTrueUnsafe() {
    if (!isUnsafe()) {
      return false;
    }
    
    Iterator<UsagePoint> iterator = topUsages.iterator();
    Set<UsagePoint> refinedPoints = new TreeSet<>();
    UsagePoint tmpPoint = iterator.next();
    while (tmpPoint.isTrue()) {
      refinedPoints.add(tmpPoint);
      if (iterator.hasNext()) {
        tmpPoint = iterator.next();
      } else {
        break;
      }
    }
    return isUnsafe(refinedPoints);
  }
  
  public boolean isTrueUnsafe() {
    return false;
  }

  public int size() {
    int result = 0;
    
    for (UsagePoint point : detailInformation.keySet()) {
      result += detailInformation.get(point).size();
    }
    
    return result;
  }
  
  public void reset() {
    Iterator<UsagePoint> iterator = topUsages.iterator();
    while (iterator.hasNext()) {
      UsagePoint point = iterator.next();
      if (!point.isTrue()) {
        iterator.remove();
      }
    }
    for (UsagePoint point : detailInformation.keySet()) {
      detailInformation.get(point).reset();
      if (point.keyUsage != null) {
        point.keyUsage.resetKeyState();
      }
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

  public AbstractUsageInfoSet getUsageInfo(UsagePoint next) {
    return detailInformation.get(next);
  }

  public void markAsTrue(UsageInfo uinfo) {
    
    UsagePoint p = uinfo.getUsagePoint();
    assert topUsages.contains(p);
    
    topUsages.remove(p);
    p.markAsTrue();
    topUsages.add(p);
    detailInformation.put(p, new RefinedUsageInfoSet(uinfo));
  }
  
  public UnrefinedUsagePointSet clone() {
    return new UnrefinedUsagePointSet(new TreeSet<>(topUsages), new HashMap<>(detailInformation), new TreeSet<>(falsePoints));
  }
  
  public void remove(UsagePoint currentUsagePoint) {
    detailInformation.remove(currentUsagePoint);
    if (topUsages.contains(currentUsagePoint)) {
      topUsages.remove(currentUsagePoint);
      for (UsagePoint point : currentUsagePoint.getCoveredUsages()) {
        if (!falsePoints.contains(point)) {
          add(point);
        }
      }
    } else {
      for (UsagePoint point : topUsages) {
        point.removeRecursively(currentUsagePoint);
      }
    }
  }

  public void checkAllEmptyPoints() {
    //We can't delete directly, use special method 'remove()'
    Set<UsagePoint> toDelete = new HashSet<>();
    for (UsagePoint point : detailInformation.keySet()) {
      AbstractUsageInfoSet set = detailInformation.get(point);
      if (set.size() == 0) {
        //On current stage of refinement there are not usages added, so this point is considered to be false
        toDelete.add(point);
      }
    }
    for (UsagePoint point : toDelete) {
      remove(point);
      falsePoints.add(point);
    }
  }

  public RefinedUsagePointSet asTrueUnsafe() {
    Iterator<UsagePoint> iterator = topUsages.iterator();
    UsagePoint first = iterator.next();
    if (iterator.hasNext()) {
      UsagePoint second = iterator.next();
      if (second.isTrue()) {
        return RefinedUsagePointSet.create((RefinedUsageInfoSet)detailInformation.get(first),
            (RefinedUsageInfoSet)detailInformation.get(second));
      }
    }
    return RefinedUsagePointSet.create((RefinedUsageInfoSet)detailInformation.get(first));
  }

  @Override
  public boolean isUnsafe() {
    return isUnsafe(topUsages);
  }
}
