/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.cpa.local.LocalState;
import org.sosy_lab.cpachecker.cpa.local.LocalTransferRelation;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

public class SharedRefiner extends GenericSinglePathRefiner {

  private LocalTransferRelation transferRelation;

  //Debug counter
  private int counter = 0;

  private int numOfFalseResults = 0;

  public SharedRefiner(ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>> pWrapper, LocalTransferRelation RelationForSharedRefiner) {
    super(pWrapper);
    transferRelation = RelationForSharedRefiner;
    // TODO Auto-generated constructor stub
  }

  @Override
  protected RefinementResult call(ExtendedARGPath pPath) throws CPAException, InterruptedException {
    // TODO Auto-generated method stub
    RefinementResult result = RefinementResult.createUnknown();
    List<CFAEdge> edges  = pPath.getFullPath();
    SingletonPrecision emptyPrecision = SingletonPrecision.getInstance();
    LocalState initialState = new LocalState(null);

    Collection<LocalState> successors = Collections.singleton(initialState);
    UsageInfo sharedUsage = pPath.getUsageInfo();
    for (CFAEdge edge : edges) {
      assert(successors.size() <= 1);
      Iterator<LocalState> sharedIterator= successors.iterator();
      if (sharedUsage.getLine().getLine() == edge.getLineNumber()) {
        LocalState usageState = sharedIterator.next();
        assert (usageState != null);
        SingleIdentifier usageId = pPath.getUsageInfo().getId();
        if (usageState.getType(usageId) == LocalState.DataType.LOCAL) {
          result = RefinementResult.createFalse();
          numOfFalseResults++;
        } else {
          result = RefinementResult.createTrue();
        }
        break;
      } else {
        //TODO Important! Final state is not a state of usage. Think about.
        if ( sharedIterator.hasNext()) {
          successors = transferRelation.getAbstractSuccessorsForEdge(sharedIterator.next(),
              emptyPrecision, edge);
        } else {
          //Strange situation
          counter++;
          result = RefinementResult.createUnknown();
          break;
        }
      }
    }
    return result;
  }

  @Override
  public void printAdditionalStatistics(PrintStream pOut) {
    pOut.println("--Shared Refiner--");
    pOut.println("Number of cases with empty successors: " + counter);
    pOut.println("Number of false results: " + numOfFalseResults);
  }

}
