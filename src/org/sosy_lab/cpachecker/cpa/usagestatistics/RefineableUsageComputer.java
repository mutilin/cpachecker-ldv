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

import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Level;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;


public class RefineableUsageComputer {

  private final UsageContainer container;
  private final UsageCache cache;
  private final Iterator<SingleIdentifier> idIterator;
  private Iterator<UsageInfo>  usageIterator;
  private final UnsafeDetector unsafeDetector;
  private UsageList currentRefineableUsageList;
  private final LogManager logger;
  //Self-checking
  private boolean waitRefinementResult;
  
  public final Timer listTimer = new Timer();
  public final Timer listTimerInit = new Timer();
  public final Timer usageTimer = new Timer();
  public final Timer optimizationTimer = new Timer();

  RefineableUsageComputer(UsageContainer c, LogManager l) {
    container = c;
    cache = new UsageCallstackCache();
    idIterator = container.getUnsafeIterator();
    if (idIterator.hasNext()) {
      UsageList originUsageList = container.getUsages(idIterator.next());
      //Collections.sort(originUsageList);
      currentRefineableUsageList = (UsageList) originUsageList.clone();
      usageIterator = originUsageList.iterator();
    }
    unsafeDetector = new PairwiseUnsafeDetector(null);
    logger = l;
    waitRefinementResult = false;
  }

  public void setResultOfRefinement(UsageInfo uinfo, boolean result) {

    assert (waitRefinementResult);

    if (!result) {
      logger.log(Level.INFO, "Usage " + uinfo + " is not reachable, remove it from container");
      cache.add(uinfo);
      currentRefineableUsageList.remove(uinfo);
    } else {
      logger.log(Level.INFO, "Usage " + uinfo + " is reachable, mark it as true");
      //TODO Not necessary to refine all usages, contained the same lock set
      uinfo.setRefineFlag();
    }
    waitRefinementResult = false;
  }

  public UsageInfo getNextRefineableUsage() {
    UsageInfo potentialUsage = null;
    UsageInfo resultUsage = null;

    assert (!waitRefinementResult);

    if (usageIterator == null || idIterator == null) {
      logger.log(Level.WARNING, "No unsafes were found");
      return null;
    }

    while (resultUsage == null) {
      //We check refineablility on TestList, because we cannot delete from iteriable one
    	listTimerInit.start();
      while (!usageIterator.hasNext() || !isRefineableUsageList(currentRefineableUsageList)) {
        listTimerInit.stop();
        if (idIterator.hasNext()) {
          listTimer.start();
          SingleIdentifier id = idIterator.next();
          UsageList originUsageList = container.getUsages(id);
          //Collections.sort(originUsageList);
          currentRefineableUsageList = (UsageList) originUsageList.clone();
          usageIterator = originUsageList.iterator();
          listTimer.stop();
        } else {
          return null;
        }
        listTimerInit.start();
      }
      listTimerInit.stop();
      potentialUsage = usageIterator.next();
      if (isRefineableUsage(potentialUsage)) {
        resultUsage = potentialUsage;
      }
    }

    waitRefinementResult = true;
    return resultUsage;
  }

  private boolean isRefineableUsageList(UsageList ulist) {
    if (ulist.isTrueUnsafe()) {
      return false;
    } else if (!unsafeDetector.containsUnrefinedUnsafeUsage(ulist)) {
      //These false-checks are 10-times more often, then true-checks
      return false;
    } else if (unsafeDetector.containsTrueUnsafe(ulist)) {
      return false;
    }
    return true;
  }

  private boolean isRefineableUsage(UsageInfo target) {
    //target can be read-accessed and hasn't got any pair usage for unsafe, so this check is necessary
  	usageTimer.start();
    if (!unsafeDetector.isUnsafeCase(currentRefineableUsageList, target) ||
        target.isRefined()) {
    	usageTimer.stop();
      return false;
    }

    if (cache.contains(target)) {
      currentRefineableUsageList.remove(target);
      usageTimer.stop();
      return false;
    }
    usageTimer.stop();
    optimizationTimer.start();
    //Optimization: if we know, that usage with locks is true, we don't need to refine the same lock set
    //Important to found other usage. If there are usages only with the same locks - refine them
    boolean foundDifferentLocks = false;
    boolean foundEqualLocks = false;
    for (UsageInfo uinfo : currentRefineableUsageList) {
      if (target.intersect(uinfo)) {
        if (uinfo.isRefined() &&
          //This point is important: we can throw away write access, but then don't find pair of unsafe usages (must be one write access)
          !(target.getAccess() == Access.WRITE && uinfo.getAccess() == Access.READ)) {
        	if (foundDifferentLocks) {
        	  optimizationTimer.stop();
            return false;
        	} else {
        	  foundEqualLocks = true;
        	}
        }
      }
      if (!target.intersect(uinfo)) {
        if (!uinfo.isRefined()) {
          if (foundEqualLocks) {
            optimizationTimer.stop();
            return false;
          } else {
            foundDifferentLocks = true;
          }
        }
      }
    }
    optimizationTimer.stop();
    return true;
  }
  
  public void printTimer() {
    System.out.println("Time for list check										" + listTimer);
    System.out.println("Time for list init                    " + listTimerInit);
    System.out.println("Time for usage check									" + usageTimer);
    System.out.println("Time for optimization									" + optimizationTimer);
  }
}
