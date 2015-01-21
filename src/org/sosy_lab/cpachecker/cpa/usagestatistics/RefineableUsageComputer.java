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
      if (!usageIterator.hasNext()) {
        //There are no usages in the point
        currentRefineableUsageList.remove(currentUsagePoint);
        if (!currentRefineableUsageList.isUnsafe()) {
          //May be we remove all 'write' accesses, so move to other id
          usagePointIterator = null;
        } else {
          usagePointIterator = currentRefineableUsageList.clone().getPointIterator();
        }
      }
    } else {
      logger.log(Level.INFO, "Usage " + uinfo + " is reachable, mark it as true");
      currentRefineableUsageList.markAsTrue(uinfo); 
      if (currentRefineableUsageList.isTrueUnsafe()) {
        usagePointIterator = null;
      }
      usageIterator = null;
    }
    waitRefinementResult = false;
  }

  public UsageInfo getNextRefineableUsage() {
    UsageInfo resultUsage = null;

    assert (!waitRefinementResult);

    do {
      while (usageIterator == null || !usageIterator.hasNext()) {
        do {
          while (usagePointIterator == null || !usagePointIterator.hasNext()) {
            if (idIterator.hasNext()) {
              SingleIdentifier id = idIterator.next();
              currentRefineableUsageList = container.getUsages(id);
              if (!currentRefineableUsageList.isUnsafe() || currentRefineableUsageList.isTrueUnsafe()) {
                continue;
              }
              usagePointIterator = currentRefineableUsageList.clone().getPointIterator();
            } else {
              return null;
            }
          }
          currentUsagePoint = usagePointIterator.next();
        } while (currentUsagePoint.isTrue());
        UsageInfoSet refineableUsageInfoSet = currentRefineableUsageList.getUsageInfo(currentUsagePoint);
        usageIterator = refineableUsageInfoSet.getIterator();
      }
      resultUsage = usageIterator.next();
    } while (cache.contains(resultUsage));

    waitRefinementResult = true;
    return resultUsage;
  }
}
