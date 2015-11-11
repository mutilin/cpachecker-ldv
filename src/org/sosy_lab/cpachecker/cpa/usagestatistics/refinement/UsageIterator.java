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
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.refinement.RefinementResult.RefinementStatus;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnrefinedUsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnrefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnsafeDetector;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsagePoint;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;


public class UsageIterator extends WrappedConfigurableRefinementBlock<SingleIdentifier, UsageInfo> {
  private UsageContainer container;
  private UnsafeDetector detector;
  private final LogManager logger;

  //Statistics
  private Timer totalTimer = new Timer();
  private int numberOfUsagesRefined = 0;

  public UsageIterator(ConfigurableRefinementBlock<UsageInfo> pWrapper, UsageContainer c, LogManager l) {
    super(pWrapper);
    if (c != null) {
      container = c;
      detector = c.getUnsafeDetector();
    }
    logger = l;
  }

  @Override
  public RefinementResult call(SingleIdentifier pInput) throws CPAException, InterruptedException {
    AbstractUsageInfoSet refineableUsageInfoSet;
    Iterator<UsagePoint>  usagePointIterator;
    UnrefinedUsageInfoSet currentRefineableUsageInfoSet;
    UsagePoint currentUsagePoint;
    UsageInfo currentUsageInfo;

    totalTimer.start();
    AbstractUsagePointSet pointSet = container.getUsages(pInput);

    assert (pointSet instanceof UnrefinedUsagePointSet);

    UnrefinedUsagePointSet currentUsagePointSet = (UnrefinedUsagePointSet)pointSet;

    usagePointIterator = currentUsagePointSet.getPointIterator();

    PredicatePrecision completePrecision = PredicatePrecision.empty();

    RefinementResult result;

    try {
      while (usagePointIterator.hasNext()) {
        currentUsagePoint = usagePointIterator.next();
        refineableUsageInfoSet = currentUsagePointSet.getUsageInfo(currentUsagePoint);

        if (!refineableUsageInfoSet.isTrue()) {
          currentRefineableUsageInfoSet = (UnrefinedUsageInfoSet)refineableUsageInfoSet;
          while (currentRefineableUsageInfoSet.size() > 0) {
            currentUsageInfo = currentRefineableUsageInfoSet.getOneExample();

            numberOfUsagesRefined++;
            totalTimer.stop();
            result = wrappedRefiner.call(currentUsageInfo);
            totalTimer.start();

            if (result.status == RefinementStatus.FALSE) {
              logger.log(Level.FINE, "Usage " + currentUsageInfo + " is not reachable, remove it from container");
              currentRefineableUsageInfoSet.remove(currentUsageInfo);
              PredicatePrecision precision = (PredicatePrecision)result.getInfo(PathIterator.class);
              if (precision != null) {
                completePrecision = completePrecision.mergeWith(precision);
              }
            } else if (result.status == RefinementStatus.TRUE) {
              logger.log(Level.FINE, "Usage " + currentUsageInfo + " is reachable, mark it as true");
              Pair<ARGPath, PredicatePrecision> pair = (Pair<ARGPath, PredicatePrecision>)result.getInfo(PathIterator.class);
              ARGPath path = pair.getFirst();
              PredicatePrecision precision = pair.getSecond();
              if (precision != null) {
                completePrecision = completePrecision.mergeWith(precision);
              }
              //TODO May be, .asEdgesList? One more edge.
              currentUsagePointSet.markAsReachableUsage(currentUsageInfo, path.getInnerEdges());
              if (detector.isTrueUnsafe(currentUsagePointSet)) {
                container.setAsRefined(currentUsagePointSet);
                result = RefinementResult.createTrue();
                result.addInfo(UsageIterator.class, completePrecision);
                return result;
              }
              //switch to another point
              break;
            } else {
              //TODO What?
            }
          }
          if (currentRefineableUsageInfoSet.size() == 0) {
            //There are no usages in the point
            currentUsagePointSet.remove(currentUsagePoint);
            if (!detector.isUnsafe(currentUsagePointSet)) {
              //May be we remove all 'write' accesses, so move to other id
              result = RefinementResult.createFalse();
              result.addInfo(UsageIterator.class, completePrecision);
              return result;
            } else {
              //We change the tree structure, recreate an iterator
              usagePointIterator = currentUsagePointSet.getPointIterator();
            }
          }
        }
      }
      result = RefinementResult.createUnknown();
      result.addInfo(UsageIterator.class, completePrecision);
      return result;
    } finally {
      totalTimer.stop();
    }
  }

  @Override
  protected void handleStartSignal(Class<? extends RefinementInterface> pCallerClass, Object pData) {
    if (pCallerClass.equals(IdentifierIterator.class)) {
      assert pData instanceof UsageContainer;
      container = (UsageContainer) pData;
      detector = container.getUnsafeDetector();
    }
  }

  @Override
  public void printStatistics(PrintStream pOut) {
    pOut.println("--UsageIterator--");
    pOut.println("Timer for block:           " + totalTimer);
    pOut.println("Number of usages refined:           " + numberOfUsagesRefined);
    wrappedRefiner.printStatistics(pOut);
  }

}
