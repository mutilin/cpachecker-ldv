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


public class LogicalAnd<ConstantType> implements InvariantsFormula<ConstantType> {

  private final InvariantsFormula<ConstantType> operand1;

  private final InvariantsFormula<ConstantType> operand2;

  private LogicalAnd(InvariantsFormula<ConstantType> pOperand1, InvariantsFormula<ConstantType> pOperand2) {
    this.operand1 = pOperand1;
    this.operand2 = pOperand2;
  }

  public InvariantsFormula<ConstantType> getOperand1() {
    return this.operand1;
  }

  public InvariantsFormula<ConstantType> getOperand2() {
    return this.operand2;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof LogicalAnd) {
      LogicalAnd<?> other = (LogicalAnd<?>) o;
      return getOperand1().equals(other.getOperand1()) && getOperand2().equals(other.getOperand2()) || getOperand1().equals(other.getOperand2()) && getOperand2().equals(other.getOperand1());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getOperand1().hashCode() & getOperand2().hashCode();
  }

  @Override
  public String toString() {
    return String.format("(%s && %s)", getOperand1(), getOperand2());
  }

  @Override
  public <ReturnType> ReturnType accept(InvariantsFormulaVisitor<ConstantType, ReturnType> pVisitor) {
    return pVisitor.visit(this);
  }

  static <ConstantType> LogicalAnd<ConstantType> of(InvariantsFormula<ConstantType> pOperand1, InvariantsFormula<ConstantType> pOperand2) {
    return new LogicalAnd<>(pOperand1, pOperand2);
  }

}
