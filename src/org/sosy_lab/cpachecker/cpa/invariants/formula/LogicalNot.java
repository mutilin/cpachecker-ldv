/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.invariants.formula;


/**
 * Instances of this class represent logical negations of invariants formulae.
 *
 * @param <ConstantType> the type of the constants used in the formulae.
 */
public class LogicalNot<ConstantType> extends AbstractFormula<ConstantType> implements InvariantsFormula<ConstantType> {

  /**
   * The formula logically negated by this formula.
   */
  private final InvariantsFormula<ConstantType> negated;

  /**
   * Creates a new logical negation of the given formula.
   *
   * @param pToNegate the formula to logically negate.
   */
  private LogicalNot(InvariantsFormula<ConstantType> pToNegate) {
    this.negated = pToNegate;
  }

  /**
   * The formula logically negated by this formula.
   *
   * @return the formula logically negated by this formula.
   */
  public InvariantsFormula<ConstantType> getNegated() {
    return this.negated;
  }

  @Override
  public String toString() {
    InvariantsFormula<ConstantType> negated = getNegated();
    if (negated instanceof LogicalNot<?>) {
      return ((LogicalNot<?>) negated).getNegated().toString();
    }
    if (negated instanceof Equal<?>) {
      Equal<?> equation = (Equal<?>) negated;
      return String.format("(%s != %s) ",
          equation.getOperand1(), equation.getOperand2());
    }
    if (negated instanceof LessThan<?>) {
      LessThan<?> lessThan = (LessThan<?>) negated;
      return String.format("(%s >= %s) ",
          lessThan.getOperand1(), lessThan.getOperand2());
    }
    if (negated instanceof LogicalAnd<?>) {
      LogicalAnd<?> and = (LogicalAnd<?>) negated;
      final String left;
      if (and.getOperand1() instanceof LogicalNot<?>) {
        left = ((LogicalNot<?>) and.getOperand1()).getNegated().toString();
      } else {
        left = String.format("(!%s)", and.getOperand1());
      }
      final String right;
      if (and.getOperand2() instanceof LogicalNot<?>) {
        right = ((LogicalNot<?>) and.getOperand2()).getNegated().toString();
      } else {
        right = String.format("(!%s)", and.getOperand2());
      }
      return String.format("(%s || %s)", left, right);
    }
    return String.format("(!%s)", negated);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof LogicalNot<?>) {
      return getNegated().equals(((LogicalNot<?>) o).getNegated());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return -getNegated().hashCode();
  }

  @Override
  public <ReturnType> ReturnType accept(InvariantsFormulaVisitor<ConstantType, ReturnType> pVisitor) {
    return pVisitor.visit(this);
  }

  @Override
  public <ReturnType, ParamType> ReturnType accept(
      ParameterizedInvariantsFormulaVisitor<ConstantType, ParamType, ReturnType> pVisitor, ParamType pParameter) {
    return pVisitor.visit(this, pParameter);
  }

  /**
   * Gets an invariants formula representing the logical negation of the given
   * operand.
   *
   * @param pToNegate the invariants formula to negate.
   *
   * @return an invariants formula representing the logical negation of the given
   * operand.
   */
  static <ConstantType> LogicalNot<ConstantType> of(InvariantsFormula<ConstantType> pToNegate) {
    return new LogicalNot<>(pToNegate);
  }

}
