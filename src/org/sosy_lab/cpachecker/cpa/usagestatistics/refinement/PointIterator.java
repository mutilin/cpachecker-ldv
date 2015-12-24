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

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnrefinedUsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnrefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnsafeDetector;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsagePoint;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;


public class PointIterator extends GenericIterator<SingleIdentifier, Pair<AbstractUsageInfoSet, AbstractUsageInfoSet>>{

  private UsageContainer container;
  private UnsafeDetector detector;

  //Internal state
  private Pair<UsagePoint, UsagePoint> pairToRefine;
  private UnrefinedUsagePointSet currentUsagePointSet;

  public PointIterator(ConfigurableRefinementBlock<Pair<AbstractUsageInfoSet, AbstractUsageInfoSet>> pWrapper, UsageContainer c) {
    super(pWrapper);
    if (c != null) {
      container = c;
      detector = c.getUnsafeDetector();
    }
  }

  @Override
  protected void init(SingleIdentifier id) {
    AbstractUsagePointSet pointSet = container.getUsages(id);

    assert (pointSet instanceof UnrefinedUsagePointSet);

    currentUsagePointSet = (UnrefinedUsagePointSet)pointSet;
  }

  @Override
  protected Pair<AbstractUsageInfoSet, AbstractUsageInfoSet> getNext(SingleIdentifier pInput) {
    if (detector.isUnsafe(currentUsagePointSet)) {
      pairToRefine = detector.getUnsafePointPair(currentUsagePointSet);

      AbstractUsageInfoSet firstUsageInfoSet = currentUsagePointSet.getUsageInfo(pairToRefine.getFirst());
      UnrefinedUsageInfoSet firstSet = (UnrefinedUsageInfoSet) firstUsageInfoSet;
      AbstractUsageInfoSet secondUsageInfoSet = currentUsagePointSet.getUsageInfo(pairToRefine.getSecond());
      UnrefinedUsageInfoSet secondSet = (UnrefinedUsageInfoSet) secondUsageInfoSet;

      if (firstSet == secondSet) {
        //To avoid concurrent modification
        secondSet = secondSet.clone();
      }

      return Pair.of((AbstractUsageInfoSet)firstSet, (AbstractUsageInfoSet)secondSet);
    } else {
      return null;
    }
  }

  @Override
  protected void finalize(SingleIdentifier id, Pair<AbstractUsageInfoSet, AbstractUsageInfoSet> pPair, RefinementResult r) {
    AbstractUsageInfoSet firstUsageInfoSet = pPair.getFirst();
    AbstractUsageInfoSet secondUsageInfoSet = pPair.getSecond();

    if (firstUsageInfoSet.size() == 0) {
      //No reachable usages - remove point
      currentUsagePointSet.remove(pairToRefine.getFirst());
    }
    if (secondUsageInfoSet.size() == 0) {
      //No reachable usages - remove point
      currentUsagePointSet.remove(pairToRefine.getSecond());
    }
  }


  @Override
  protected void handleUpdateSignal(Class<? extends RefinementInterface> pCallerClass, Object pData) {
    if (pCallerClass.equals(IdentifierIterator.class)) {
      assert pData instanceof UsageContainer;
      container = (UsageContainer) pData;
      detector = container.getUnsafeDetector();
    }
  }

  @Override
  protected void printDetailedStatistics(PrintStream pOut) {
    pOut.println("--PointIterator--");
  }
}
