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
package org.sosy_lab.cpachecker.util.expressions;

import java.util.Collections;
import java.util.Iterator;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

public class Or<LeafType> extends AbstractExpressionTree<LeafType>
    implements Iterable<ExpressionTree<LeafType>> {

  private ImmutableSortedSet<ExpressionTree<LeafType>> operands;

  private Or(ImmutableSortedSet<ExpressionTree<LeafType>> pOperands) {
    assert Iterables.size(pOperands) >= 2;
    assert !Iterables.contains(pOperands, ExpressionTrees.getFalse());
    assert !Iterables.contains(pOperands, ExpressionTrees.getTrue());
    assert !FluentIterable.from(pOperands).anyMatch(Predicates.instanceOf(Or.class));
    operands = pOperands;
  }

  @Override
  public Iterator<ExpressionTree<LeafType>> iterator() {
    return operands.iterator();
  }

  @Override
  public <T, E extends Throwable> T accept(ExpressionTreeVisitor<LeafType, T, E> pVisitor)
      throws E {
    return pVisitor.visit(this);
  }

  @Override
  public int hashCode() {
    return operands.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (this == pObj) {
      return true;
    }
    if (pObj instanceof Or) {
      return operands.equals(((Or<?>) pObj).operands);
    }
    return false;
  }

  public static <LeafType> ExpressionTree<LeafType> of(
      Iterable<ExpressionTree<LeafType>> pOperands) {
    // If one of the operands is true, return true
    if (Iterables.contains(pOperands, ExpressionTrees.getTrue())) {
      return ExpressionTrees.getTrue();
    }
    // Filter out trivial operands and flatten the hierarchy
    ImmutableSortedSet<ExpressionTree<LeafType>> operands =
        FluentIterable.from(pOperands)
            .filter(Predicates.not(Predicates.equalTo(ExpressionTrees.<LeafType>getFalse())))
            .transformAndConcat(
                new Function<ExpressionTree<LeafType>, Iterable<ExpressionTree<LeafType>>>() {

                  @Override
                  public Iterable<ExpressionTree<LeafType>> apply(
                      ExpressionTree<LeafType> pOperand) {
                    if (pOperand instanceof Or) {
                      return (Or<LeafType>) pOperand;
                    }
                    return Collections.singleton(pOperand);
                  }
                }).toSortedSet(ExpressionTrees.<LeafType>getComparator());
    // If there are no operands, return the neutral element
    if (operands.isEmpty()) {
      return ExpressionTrees.getFalse();
    }
    // If there is only one operand, return it
    if (operands.size() == 1) {
      return operands.iterator().next();
    }
    return new Or<>(operands);
  }

}
