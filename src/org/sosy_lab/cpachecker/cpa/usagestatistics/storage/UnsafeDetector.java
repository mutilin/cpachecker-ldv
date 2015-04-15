/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;

import com.google.common.collect.Sets;

@Options(prefix="cpa.usagestatistics.unsafedetector")
public class UnsafeDetector {
  @Option(name="ignoreEmptyLockset", description="ignore unsafes only with empty callstacks")
  private boolean ignoreEmptyLockset = false;

  public UnsafeDetector(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
  }

  public boolean isTrueUnsafe(AbstractUsagePointSet set) {
    if (set instanceof RefinedUsagePointSet) {
      return true;
    }
    return isTrueUnsafe((UnrefinedUsagePointSet)set);
  }

  private boolean isTrueUnsafe(UnrefinedUsagePointSet set) {
    //Is called at the end, so return true even if we have only one refined usage
    if (!isUnsafe(set)) {
      return false;
    }

    Set<UsagePoint> refinedInformation = set.getRefinedInformation();

    boolean result = isUnsafe(new TreeSet<>(refinedInformation));

    if (result) {
      if (refinedInformation.size() > 1 || set.getTopUsages().size() == 1) {
        return true;
      } else {
        //Try to refine the second usage, if it is possible
        return false;
      }
    } else {
      return false;
    }
  }

  public boolean isUnsafe(AbstractUsagePointSet set) {
    if (set instanceof RefinedUsagePointSet) {
      return true;
    }
    return isUnsafe((UnrefinedUsagePointSet)set);
  }

  private boolean isUnsafe(UnrefinedUsagePointSet set) {
    return isUnsafe(set.getTopUsages());
  }

  public Pair<UsageInfo, UsageInfo> getUnsafePair(AbstractUsagePointSet set) {
    assert isUnsafe(set);

    if (set instanceof RefinedUsagePointSet) {
      return ((RefinedUsagePointSet)set).getUnsafePair();
    } else {
      assert set instanceof UnrefinedUsagePointSet;

      UnrefinedUsagePointSet unrefinedSet = (UnrefinedUsagePointSet) set;
      Pair<UsagePoint, UsagePoint> result = getUnsafePair(unrefinedSet.getTopUsages());

      return Pair.of(unrefinedSet.getUsageInfo(result.getFirst()).getOneExample(),
          unrefinedSet.getUsageInfo(result.getSecond()).getOneExample());
    }
  }

  private boolean isUnsafe(SortedSet<UsagePoint> points) {
    if (points.size() >= 1) {
      Iterator<UsagePoint> iterator = points.iterator();
      UsagePoint point = iterator.next();

      if(ignoreEmptyLockset && point.locks.isEmpty()) {
        //special case when we ignore intersection of empty sets
        //at least one lockSet should be nonempty

        //we go with while locks is empty until first nonempty
        //search for non empty lockset
        while(point.locks.isEmpty() && iterator.hasNext()) {
          //skip accesses without locks
          point = iterator.next();
        }
        if(!point.locks.isEmpty()) {
          //we already have an empty intersection
          //since we had an access without locks
          //and now have an access with some locks
          return true;
        } else {
          //only empty locksets
          return false;
        }
      }
      if (point.access == Access.WRITE) {
        Set<LockIdentifier> lockSet = new HashSet<>(point.locks);
        while (point.access == Access.WRITE) {
          lockSet.retainAll(point.locks);
          if (iterator.hasNext()) {
            point = iterator.next();
          } else {
            break;
          }
        }

        if (lockSet.isEmpty()) {
          return true;
        }

        /* There can be a situation
         * (l1, l2, write), (l1, read), (l2, read)
         * Thus, we should process writes and reads differentely
         */
        while (iterator.hasNext()) {
          if (Sets.intersection(lockSet, iterator.next().locks).isEmpty()) {
            return true;
          }
        }
        return false;
      }
    }
    return false;
  }

  public Pair<RefinedUsageInfoSet, RefinedUsageInfoSet> getTrueUnsafePair(UnrefinedUsagePointSet pSet) {
    Set<UsagePoint> refinedInformation = pSet.getRefinedInformation();

    Pair<UsagePoint, UsagePoint> result = getUnsafePair(new TreeSet<>(refinedInformation));
    //We must obtain refined sets
    RefinedUsageInfoSet set1 = (RefinedUsageInfoSet) pSet.getUsageInfo(result.getFirst());
    RefinedUsageInfoSet set2 = (RefinedUsageInfoSet) pSet.getUsageInfo(result.getSecond());
    return Pair.of(set1, set2);
  }

  private Pair<UsagePoint, UsagePoint> getUnsafePair(SortedSet<UsagePoint> set) {

    for (UsagePoint point1 : set) {
      for (UsagePoint point2 : set.tailSet(point1)) {
        if (point1.equals(point2)) {
          /* There can be an unsafe even with only one usage,
           * but at first we find two different usages
           */
          continue;
        }
        TreeSet<UsagePoint> unsafePair = new TreeSet<>();
        unsafePair.add(point1);
        unsafePair.add(point2);
        if (isUnsafe(unsafePair)) {
          return Pair.of(point1, point2);
        }
      }
    }
    //Now we find an unsafe only from one usage
    if (!ignoreEmptyLockset) {
      for (UsagePoint point1 : set) {
        TreeSet<UsagePoint> unsafePair = new TreeSet<>();
        unsafePair.add(point1);
        if (isUnsafe(unsafePair)) {
          return Pair.of(point1, point1);
        }
      }
    }
    //If we can not find an unsafe here, fail
    return null;
  }
 }
