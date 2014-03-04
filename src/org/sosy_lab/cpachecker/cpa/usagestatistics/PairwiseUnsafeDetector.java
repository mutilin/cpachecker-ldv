/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

/**
 * This class implements simple analysis, when all lines are compared
 * pairwise only with set of locks, and all different ones are written
 * in statistics
 */

@Options(prefix="cpa.usagestatistics.unsafedetector")
public class PairwiseUnsafeDetector implements UnsafeDetector {
  /*@Option(description = "variables, which will be unsafes even only with read access (they can be changed invisibly)")
  private Set<String> detectByReadAccess;*/

  public PairwiseUnsafeDetector(Configuration config)  {
	  //config.inject(this);
  }

  @Override
  public Collection<SingleIdentifier> getUnsafes(Map<SingleIdentifier, List<UsageInfo>> stat) {
    Collection<SingleIdentifier> unsafe = new HashSet<>();

    for (SingleIdentifier id : stat.keySet()) {
      List<UsageInfo> uset = stat.get(id);
      if (isUnsafeId(uset) && !unsafe.contains(id)) {
        unsafe.add(id);
      }
    }
    return unsafe;
  }

  @Override
  public Pair<UsageInfo, UsageInfo> getUnsafePair(List<UsageInfo> uinfo) throws HandleCodeException {
    Collections.sort(uinfo, new UsageInfo.UsageComparator());

    for (UsageInfo info1 : uinfo) {
      for (UsageInfo info2 : uinfo) {
        if (!info1.intersect(info2)) {
          return Pair.of(info1, info2);
        }
      }
    }
    throw new HandleCodeException("Can't find example of unsafe cases");
  }

  public boolean isUnsafeId(List<UsageInfo> uset) {
    for (UsageInfo uinfo : uset) {
      for (UsageInfo uinfo2 : uset) {
        if (!uinfo.intersect(uinfo2)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String getDescription() {
    return "All lines with different mutexes were printed";
  }

  @Override
  public boolean isUnsafeCase(List<UsageInfo> oldUsages, UsageInfo newUsage) {
    for (UsageInfo old : oldUsages) {
      if (!newUsage.intersect(old)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean containsUnsafe(List<UsageInfo> pList, boolean pCheckAll) {
    for (UsageInfo uinfo : pList) {
      for (UsageInfo uinfo2 : pList) {
        if (uinfo2.isRefined() && !pCheckAll) {
          continue;
        }
        if (!uinfo.intersect(uinfo2)) {
          return true;
        }
      }
    }
    return false;
  }

}
