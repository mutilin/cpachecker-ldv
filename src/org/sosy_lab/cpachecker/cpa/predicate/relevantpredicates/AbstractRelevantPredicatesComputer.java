/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;

import com.google.common.collect.Maps;

public abstract class AbstractRelevantPredicatesComputer<T> implements RelevantPredicatesComputer {

  private final Map<Pair<T, AbstractionPredicate>, Boolean> relevantPredicates = Maps.newHashMap();

  @Override
  public Collection<AbstractionPredicate> getRelevantPredicates(Block context, Collection<AbstractionPredicate> predicates) {
    Collection<AbstractionPredicate> result = new HashSet<AbstractionPredicate>(predicates.size());

    T precomputeResult = precompute(context, predicates);

    for(AbstractionPredicate predicate : predicates) {
      if (isRelevant0(precomputeResult, predicate)) {
        result.add(predicate);
      }
    }
    return result;
  }

  private boolean isRelevant0(T pPrecomputeResult, AbstractionPredicate pPredicate) {

    // lookup in cache
    Pair<T, AbstractionPredicate> key = Pair.of(pPrecomputeResult, pPredicate);
    Boolean cacheResult = relevantPredicates.get(key);
    if (cacheResult != null) {
      return cacheResult;
    }

    boolean result;
    String predicateString = pPredicate.getSymbolicAtom().toString();
    if (predicateString.contains("false") || predicateString.contains("retval")  || predicateString.contains("nondet")) {
      result = true;
    } else {
      result = isRelevant(pPrecomputeResult, pPredicate);
    }

    relevantPredicates.put(key, result);
    return result;
  }

  protected abstract boolean isRelevant(T pPrecomputeResult, AbstractionPredicate pPredicate);

  protected abstract T precompute(Block pContext, Collection<AbstractionPredicate> pPredicates);

  @Override
  public Collection<AbstractionPredicate> getIrrelevantPredicates(Block context, Collection<AbstractionPredicate> predicates) {

    Collection<AbstractionPredicate> result = new HashSet<AbstractionPredicate>(predicates);
    result.removeAll(getRelevantPredicates(context, predicates));

    return result;
  }
}
