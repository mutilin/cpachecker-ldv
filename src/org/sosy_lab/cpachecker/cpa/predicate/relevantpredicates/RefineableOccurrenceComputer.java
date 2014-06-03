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
package org.sosy_lab.cpachecker.cpa.predicate.relevantpredicates;

import java.util.Set;

import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;


/**
 * Computes set of irrelevant predicates of a block by identifying the variables that do not occur in the block.
 */

public class RefineableOccurrenceComputer extends OccurrenceComputer implements RefineableRelevantPredicatesComputer {

  private final SetMultimap<Block, AbstractionPredicate> definitelyRelevantPredicates = HashMultimap.create();

  public RefineableOccurrenceComputer(FormulaManagerView pFmgr) {
    super(pFmgr);
  }

  @Override
  protected boolean isRelevant(Block context, AbstractionPredicate predicate) {
    Set<AbstractionPredicate> relevantPredicates = definitelyRelevantPredicates.get(context);
    if (relevantPredicates != null && relevantPredicates.contains(predicate)) {
      return true;
    }

    return super.isRelevant(context, predicate);
  }

  @Override
  public void considerPredicateAsRelevant(Block pBlock,
      AbstractionPredicate pPredicate) {
    definitelyRelevantPredicates.put(pBlock, pPredicate);
    CachingRelevantPredicatesComputer.removeCacheEntriesForBlock(pBlock, relevantPredicates);
  }
}