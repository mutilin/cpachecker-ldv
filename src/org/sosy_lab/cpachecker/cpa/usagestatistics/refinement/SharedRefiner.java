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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.cpa.local.LocalState;
import org.sosy_lab.cpachecker.cpa.local.LocalTransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

public class SharedRefiner extends GenericSinglePathRefiner {

  LocalTransferRelation transferRelation;

  public SharedRefiner(ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>> pWrapper, LocalTransferRelation RelationForSharedRefiner) {
    super(pWrapper);
    transferRelation = RelationForSharedRefiner;
    // TODO Auto-generated constructor stub
  }

  @Override
  protected RefinementResult call(ExtendedARGPath pPath) throws CPAException, InterruptedException {
    // TODO Auto-generated method stub
    RefinementResult result;
    List<CFAEdge> edges  = pPath.getFullPath();
    SingletonPrecision emptyPrecision = SingletonPrecision.getInstance();
    LocalState initialState = new LocalState(null);

    Collection<LocalState> successors = Collections.singleton(initialState);
    for (CFAEdge edge : edges) {
      assert(successors.size() <= 1);
      Iterator<LocalState> sharedIterator= successors.iterator();
      //TODO Important! Final state is not a state of usage. Think about.
      if ( sharedIterator.hasNext()) {
        successors = transferRelation.getAbstractSuccessorsForEdge(sharedIterator.next(),
            emptyPrecision, edge);
      } else {
        //Strange situation
        break;
      }
    }
    Iterator<LocalState> sharedIterator= successors.iterator();
    LocalState finalState = sharedIterator.next();
    assert (finalState != null);
    SingleIdentifier usageId = pPath.getUsageInfo().getId();
    if (finalState.getType(usageId) == LocalState.DataType.LOCAL) {
      result = RefinementResult.createFalse();
    } else{
      result = RefinementResult.createTrue();
    }
    return result;
  }



}
