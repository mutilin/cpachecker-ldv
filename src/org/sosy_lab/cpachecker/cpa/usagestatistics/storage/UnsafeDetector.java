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
      Iterator<UsagePoint> iterator = unrefinedSet.getTopUsages().iterator();
      AbstractUsageInfoSet firstSet, secondSet;
      UsagePoint point = iterator.next();

      firstSet = set.getUsageInfo(point);
      if (!ignoreEmptyLockset) {
        if (iterator.hasNext()) {
          point = iterator.next();
          secondSet = set.getUsageInfo(point);
        } else {
          //One write usage is also unsafe, as we consider the function to be able to run in parallel with itself
          secondSet = firstSet;
        }
      } else {
        while(point.locks.isEmpty()) {
          //skip accesses without locks
          //We must find point with locks, if we check, that this is an unsafe case
          point = iterator.next();
        }
        secondSet = set.getUsageInfo(point);
        //TODO It is possible to obtain a safe pair, but this case has a very small chance. Wait for real bugs.
      }
      return Pair.of(firstSet.getOneExample(), secondSet.getOneExample());
    }
  }

  private boolean isUnsafe(SortedSet<UsagePoint> points) {
    if (points.size() >= 2) {
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
        while (iterator.hasNext() && !lockSet.isEmpty()) {
          lockSet.retainAll(iterator.next().locks);
        }
        return lockSet.isEmpty();
      }
    }
    return false;
  }
}
