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
package org.sosy_lab.cpachecker.cpa.usagestatistics.refinement;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsageInfoSet;


public class UsagePairIterator extends GenericIterator<Pair<AbstractUsageInfoSet, AbstractUsageInfoSet>, Pair<UsageInfo, UsageInfo>>{
  private final LogManager logger;

  //internal state
  private Iterator<UsageInfo> firstUsageIterator;
  private Iterator<UsageInfo> secondUsageIterator;
  private UsageInfo firstUsage = null;

  public UsagePairIterator(ConfigurableRefinementBlock<Pair<UsageInfo, UsageInfo>> pWrapper, LogManager l) {
    super(pWrapper);
    logger = l;
  }

  @Override
  protected void init(Pair<AbstractUsageInfoSet, AbstractUsageInfoSet> pInput) {
    AbstractUsageInfoSet firstUsageInfoSet, secondUsageInfoSet;
    firstUsageInfoSet = pInput.getFirst();
    secondUsageInfoSet = pInput.getSecond();

    Iterable<UsageInfo> firstUsages = firstUsageInfoSet.getUsages();
    Iterable<UsageInfo> secondUsages = secondUsageInfoSet.getUsages();

    firstUsageIterator = firstUsages.iterator();
    secondUsageIterator = secondUsages.iterator();

    firstUsage = null;
   /* UsageInfo first = firstUsageIterator.next();
    UsageInfo second = secondUsageIterator.next();

    if (first == second) {
      firstUsageIterator.remove();
      secondUsageIterator.remove();
    }*/

  }

  @Override
  protected Pair<UsageInfo, UsageInfo> getNext(Pair<AbstractUsageInfoSet, AbstractUsageInfoSet> pInput) {
    if (firstUsage == null) {
      //first call - initialize it
      if (firstUsageIterator.hasNext()) {
        firstUsage = firstUsageIterator.next();
      } else {
        return null;
      }
    }

    Pair<UsageInfo, UsageInfo> result = checkSecondIterator();

    if (result != null) {
      return result;
    } else {
      if (firstUsageIterator.hasNext()) {
        firstUsage = firstUsageIterator.next();
        AbstractUsageInfoSet secondUsageInfoSet = pInput.getSecond();
        Iterable<UsageInfo> secondUsages = secondUsageInfoSet.getUsages();
        secondUsageIterator = secondUsages.iterator();
        return checkSecondIterator();
      } else {
        return null;
      }
    }
  }

  private Pair<UsageInfo, UsageInfo> checkSecondIterator() {
    while (secondUsageIterator.hasNext()) {
      UsageInfo secondUsage = secondUsageIterator.next();
      if (!secondUsage.isReachable()) {
        //It may happens if we refine to same sets (first == second)
        //It is normal, just skip
      } else {
        return Pair.of(firstUsage, secondUsage);
      }
    }
    return null;
  }

  @Override
  protected void finalize(Pair<AbstractUsageInfoSet, AbstractUsageInfoSet> pInput,
      Pair<UsageInfo, UsageInfo> usagePair, RefinementResult r) {
    UsageInfo first = usagePair.getFirst();
    UsageInfo second = usagePair.getSecond();
    if (!second.isReachable()) {
      logger.log(Level.FINE, "Usage " + secondUsageIterator + " is not reachable, remove it from container");
      secondUsageIterator.remove();
    }
    if (!first.isReachable()) {
      logger.log(Level.FINE, "Usage " + firstUsageIterator + " is not reachable, remove it from container");
      firstUsageIterator.remove();
      firstUsage = null;
      AbstractUsageInfoSet secondUsageInfoSet = pInput.getSecond();
      Iterable<UsageInfo> secondUsages = secondUsageInfoSet.getUsages();
      secondUsageIterator = secondUsages.iterator();
    }
  }

  @Override
  protected void printDetailedStatistics(PrintStream pOut) {
    pOut.println("--UsagePairIterator--");
  }
}
