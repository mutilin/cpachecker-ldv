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


public class Multiply<ConstantType> implements InvariantsFormula<ConstantType> {

  private final InvariantsFormula<ConstantType> factor1;

  private final InvariantsFormula<ConstantType> factor2;

  private Multiply(InvariantsFormula<ConstantType> pFactor1, InvariantsFormula<ConstantType> pFactor2) {
    this.factor1 = pFactor1;
    this.factor2 = pFactor2;
  }

  public InvariantsFormula<ConstantType> getFactor1() {
    return this.factor1;
  }

  public InvariantsFormula<ConstantType> getFactor2() {
    return this.factor2;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Multiply<?>) {
      Multiply<?> other = (Multiply<?>) o;
      return getFactor1().equals(other.getFactor1()) && getFactor2().equals(other.getFactor2()) || getFactor1().equals(other.getFactor2()) && getFactor2().equals(other.getFactor1());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getFactor1().hashCode() * getFactor2().hashCode();
  }

  @Override
  public String toString() {
    return String.format("(%s * %s)", getFactor1(), getFactor2());
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

  static <ConstantType> Multiply<ConstantType> of(InvariantsFormula<ConstantType> pFactor1, InvariantsFormula<ConstantType> pFactor2) {
    return new Multiply<>(pFactor1, pFactor2);
  }

}
