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

import java.util.Iterator;
import java.util.logging.Level;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;


public class RefineableUsageComputer {

  private final UsageContainer container;
  private final UsageCache cache;
  private final Iterator<SingleIdentifier> idIterator;
  private Iterator<UsageInfo>  usageIterator;
  private Iterator<UsagePoint>  usagePointIterator;
  private UsageList currentRefineableUsageList;
  private UsageInfoSet currentRefineableUsageInfoSet;
  private UsagePoint currentUsagePoint;
  private final LogManager logger;
  //Self-checking
  private boolean waitRefinementResult;
  
  RefineableUsageComputer(UsageContainer c, LogManager l) {
    container = c;
    cache = new UsageCallstackCache();
    idIterator = container.getUnsafeIterator();
    logger = l;
    waitRefinementResult = false;
  }

  public void setResultOfRefinement(UsageInfo uinfo, boolean result) {

    assert (waitRefinementResult);

    if (!result) {
      logger.log(Level.INFO, "Usage " + uinfo + " is not reachable, remove it from container");
      cache.add(uinfo);
    } else {
      logger.log(Level.INFO, "Usage " + uinfo + " is reachable, mark it as true");
      //TODO Not necessary to refine all usages, contained the same lock set
      currentRefineableUsageInfoSet.markAsRefined(uinfo);
      currentUsagePoint.markAsTrue(); 
      if (currentRefineableUsageList.isTrueUnsafe()) {
        usagePointIterator = null;
      }
      usageIterator = null;
    }
    waitRefinementResult = false;
  }

  public UsageInfo getNextRefineableUsage() {
    UsageInfo potentialUsage = null;
    UsageInfo resultUsage = null;

    assert (!waitRefinementResult);

    while (resultUsage == null) {
      while (usageIterator == null || !usageIterator.hasNext()) {
        if (currentUsagePoint != null && !currentUsagePoint.isTrue()) {
          currentRefineableUsageList.remove(currentUsagePoint);
          if (!currentRefineableUsageList.isUnsafe()) {
            //May be we remove all 'write' accesses, so move to other id
            usagePointIterator = null;
          } else {
            usagePointIterator = currentRefineableUsageList.getPointIterator();
          }
        }
        while (usagePointIterator == null || !usagePointIterator.hasNext()) {
          if (idIterator.hasNext()) {
            SingleIdentifier id = idIterator.next();
            currentRefineableUsageList = container.getUsages(id);
            usagePointIterator = currentRefineableUsageList.getPointIterator();
          } else {
            return null;
          }
        }
        currentUsagePoint = usagePointIterator.next();
        if (currentUsagePoint.isTrue()) {
          usageIterator = null;
          continue;
        }
        currentRefineableUsageInfoSet = currentRefineableUsageList.getUsageInfo(currentUsagePoint);
        usageIterator = currentRefineableUsageInfoSet.getIterator();
      }
      potentialUsage = usageIterator.next();
      if (isRefineableUsage(potentialUsage)) {
        resultUsage = potentialUsage;
      }
    }

    waitRefinementResult = true;
    return resultUsage;
  }

  private boolean isRefineableUsage(UsageInfo target) {
    //target can be read-accessed and hasn't got any pair usage for unsafe, so this check is necessary
    if (target.isRefined()) {
      return false;
    }

    if (cache.contains(target)) {
      return false;
    }
    return true;
  }
}
