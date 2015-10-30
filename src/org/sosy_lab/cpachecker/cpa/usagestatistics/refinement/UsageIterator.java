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

import java.util.Iterator;
import java.util.logging.Level;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.RefinementResult.RefinementStatus;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnrefinedUsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnrefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnsafeDetector;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsagePoint;
import org.sosy_lab.cpachecker.exceptions.CPAException;


public class UsageIterator extends WrappedConfigurableRefinementBlock<UnrefinedUsagePointSet, UsageInfo> {
  private final UsageContainer container;
  private final UnsafeDetector detector;
  private Iterator<UsagePoint>  usagePointIterator;
  private UnrefinedUsageInfoSet currentRefineableUsageInfoSet;
  private UsagePoint currentUsagePoint;
  private UsageInfo currentUsageInfo;
  private final LogManager logger;

  public UsageIterator(ConfigurableRefinementBlock<UsageInfo> pWrapper, UsageContainer c, LogManager l) {
    super(pWrapper);
    container = c;
    detector = c.getUnsafeDetector();
    logger = l;
  }

  @Override
  public RefinementResult call(UnrefinedUsagePointSet pInput) throws CPAException, InterruptedException {
    AbstractUsageInfoSet refineableUsageInfoSet;

    usagePointIterator = pInput.getPointIterator();

    while (usagePointIterator.hasNext()) {
      currentUsagePoint = usagePointIterator.next();
      refineableUsageInfoSet = pInput.getUsageInfo(currentUsagePoint);

      if (!refineableUsageInfoSet.isTrue()) {
        currentRefineableUsageInfoSet = (UnrefinedUsageInfoSet)refineableUsageInfoSet;
        while (currentRefineableUsageInfoSet.size() > 0) {
          currentUsageInfo = currentRefineableUsageInfoSet.getOneExample();

          RefinementResult result = wrappedRefiner.call(currentUsageInfo);

          if (result.status == RefinementStatus.FALSE) {
            logger.log(Level.FINE, "Usage " + currentUsageInfo + " is not reachable, remove it from container");
            currentRefineableUsageInfoSet.remove(currentUsageInfo);
          } else if (result.status == RefinementStatus.TRUE) {
            logger.log(Level.FINE, "Usage " + currentUsageInfo + " is reachable, mark it as true");
            ARGPath path = (ARGPath)result.getInfo(PathIterator.class);
            //TODO May be, .asEdgesList? One more edge.
            pInput.markAsReachableUsage(currentUsageInfo, path.getInnerEdges());
            if (detector.isTrueUnsafe(pInput)) {
              container.setAsRefined(pInput);
              return RefinementResult.createTrue();
            }
            //switch to another point
            break;
          } else {
            //TODO What?
          }
        }
        if (currentRefineableUsageInfoSet.size() == 0) {
          //There are no usages in the point
          pInput.remove(currentUsagePoint);
          if (!detector.isUnsafe(pInput)) {
            //May be we remove all 'write' accesses, so move to other id
            return RefinementResult.createFalse();
          } else {
            //We change the tree structure, recreate an iterator
            usagePointIterator = pInput.getPointIterator();
          }
        }
      }
    }
    return RefinementResult.createUnknown();
  }

}
