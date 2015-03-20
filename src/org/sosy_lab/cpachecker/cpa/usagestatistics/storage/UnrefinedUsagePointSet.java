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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;
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

  private UnrefinedUsagePointSet(TreeSet<UsagePoint> top, Map<UsagePoint, UnrefinedUsageInfoSet> detail,
      Map<UsagePoint, RefinedUsageInfoSet> trueUsages) {
    topUsages = top;
    unrefinedInformation = detail;
    refinedInformation = trueUsages;
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

  private boolean isUnsafe(SortedSet<UsagePoint> points) {
    if (points.size() >= 1) {
      Iterator<UsagePoint> iterator = points.iterator();
      UsagePoint point = iterator.next();
      Set<LockIdentifier> lockSet = null;
      if (point.isTrue() && point.access == Access.READ) {
        //The first one may be read access if it is refined
        lockSet = new HashSet<>(point.locks);
        if (iterator.hasNext()) {
          point = iterator.next();
        } else {
          return false;
        }
      }
      if (point.access == Access.WRITE) {
        if (lockSet == null) {
          lockSet = new HashSet<>(point.locks);
        } else {
          lockSet.retainAll(point.locks);
        }
        while (iterator.hasNext() && !lockSet.isEmpty()) {
          lockSet.retainAll(iterator.next().locks);
        }
        return lockSet.isEmpty();
      }
    }
    return false;
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
  public Pair<UsageInfo, UsageInfo> getUnsafePair() {
    assert isUnsafe();

    Iterator<UsagePoint> iterator = topUsages.iterator();
    AbstractUsageInfoSet firstSet = getUsageInfo(iterator.next());
    AbstractUsageInfoSet secondSet;
    if (iterator.hasNext()) {
      UsagePoint point = iterator.next();
      secondSet = getUsageInfo(point);
    } else {
      //One write usage is also unsafe, as we consider the function to be able to run in parallel with itself
      secondSet = firstSet;
    }
    return Pair.of(firstSet.getOneExample(), secondSet.getOneExample());
  }

<<<<<<< HEAD
  public boolean checkTrueUnsafe() {
=======
  @Override
  public boolean isTrueUnsafe() {
    //Is called at the end, so return true even if we have only one refined usage
>>>>>>> branchForMerge
    if (!isUnsafe()) {
      return false;
    }

<<<<<<< HEAD
    boolean result = checkRefinedUsages();
=======
    boolean result = isUnsafe(new TreeSet<>(refinedInformation.keySet()));
>>>>>>> branchForMerge
    if (result) {
      if (refinedInformation.size() > 1 || topUsages.size() == 1) {
        return true;
      } else {
        //Try to refine the second usage, if it is possible
        return false;
      }
    } else {
      return false;
    }
  }
<<<<<<< HEAD

  private boolean checkRefinedUsages() {
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

  @Override
  public boolean isTrueUnsafe() {
    //Is called at the end, so return true even if we have only one refined usage
    return checkRefinedUsages();
  }
=======
>>>>>>> branchForMerge

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
    return topUsages.iterator();
  }

  @Override
  public int getNumberOfTopUsagePoints() {
    return topUsages.size();
  }

<<<<<<< HEAD
  public void markAsTrue(UsageInfo uinfo) {
=======
  public void markAsReachableUsage(UsageInfo uinfo) {
>>>>>>> branchForMerge

    UsagePoint p = uinfo.getUsagePoint();
    assert topUsages.contains(p);

    topUsages.remove(p);
    p.markAsTrue();
    topUsages.add(p);
    unrefinedInformation.remove(p);
    refinedInformation.put(p, new RefinedUsageInfoSet(uinfo));
  }

  @Override
  public UnrefinedUsagePointSet clone() {
    return new UnrefinedUsagePointSet(new TreeSet<>(topUsages), new HashMap<>(unrefinedInformation), new HashMap<>(refinedInformation));
  }

  public void remove(UsagePoint currentUsagePoint) {
    unrefinedInformation.remove(currentUsagePoint);
    topUsages.remove(currentUsagePoint);
    for (UsagePoint point : currentUsagePoint.getCoveredUsages()) {
      add(point);
    }
  }

  public RefinedUsagePointSet asTrueUnsafe() {
    //TODO Wrong! First two refined usages may not create an unsafe pair
    Iterator<UsagePoint> iterator = topUsages.iterator();
    UsagePoint first = iterator.next();
    if (iterator.hasNext()) {
      UsagePoint second = iterator.next();
      if (refinedInformation.containsKey(second)) {
        return RefinedUsagePointSet.create(refinedInformation.get(first), refinedInformation.get(second));
      }
    }
    return RefinedUsagePointSet.create(refinedInformation.get(first));
  }

  @Override
  public boolean isUnsafe() {
    return isUnsafe(topUsages);
  }
}
