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
package org.sosy_lab.cpachecker.cpa.invariants.formula;

import java.math.BigInteger;
import java.util.Map;

import javax.annotation.Nullable;

import org.sosy_lab.cpachecker.cpa.invariants.BitVectorInfo;
import org.sosy_lab.cpachecker.cpa.invariants.CompoundInterval;
import org.sosy_lab.cpachecker.cpa.invariants.SimpleInterval;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * Instances of this class are compound state invariants visitors used to
 * convert the visited invariants formulae into bit vector formulae.
 */
public class ToCodeFormulaVisitor implements
    ParameterizedNumeralFormulaVisitor<CompoundInterval, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>>, String>,
    ParameterizedBooleanFormulaVisitor<CompoundInterval, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>>, String> {

  /**
   * The formula evaluation visitor used to evaluate compound state invariants
   * formulae to compound states.
   */
  private final FormulaEvaluationVisitor<CompoundInterval> evaluationVisitor;

  /**
   * Creates a new visitor for converting compound state invariants formulae to
   * bit vector formulae by using the given formula manager, and evaluation visitor.
   *
   * @param pEvaluationVisitor the formula evaluation visitor used to evaluate
   * compound state invariants formulae to compound states.
   */
  public ToCodeFormulaVisitor(FormulaEvaluationVisitor<CompoundInterval> pEvaluationVisitor) {
    this.evaluationVisitor = pEvaluationVisitor;
  }

  /**
   * Evaluates the given compound state invariants formula and tries to convert
   * the resulting value into a bit vector formula.
   *
   * @param pFormula the formula to evaluate.
   * @param pEnvironment the environment to evaluate the formula in.
   *
   * @return a bit vector formula representing the evaluation of the given
   * formula or <code>null</code> if the evaluation of the given formula could
   * not be represented as a bit vector formula.
   */
  private @Nullable String evaluate(NumeralFormula<CompoundInterval> pFormula, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    CompoundInterval intervals = pFormula.accept(this.evaluationVisitor, pEnvironment);
    if (intervals.isSingleton()) {
      return asBitVectorFormula(pFormula.getBitVectorInfo(), intervals.getValue());
    }
    return null;
  }

  /**
   * Encodes the given value as a bit vector formula using the given bit vector
   * information.
   *
   * @param pBitVectorInfo the bit vector information.
   * @param pValue the value.
   *
   * @return a bit vector formula representing the given value as a bit vector
   * with the given size.
   */
  private String asBitVectorFormula(BitVectorInfo pBitVectorInfo, BigInteger pValue) {
    int size = pBitVectorInfo.getSize();
    BigInteger value = pValue;
    // Get only the [size] least significant bits
    BigInteger upperExclusive = BigInteger.valueOf(2).pow(size);
    boolean negative = value.signum() < 0;
    if (negative && !value.equals(upperExclusive.shiftRight(1).negate())) {
      value = value.negate();
      value = value.and(BigInteger.valueOf(2).pow(size - 1).subtract(BigInteger.valueOf(1))).negate();
    } else if (!negative) {
      value = value.and(BigInteger.valueOf(2).pow(size).subtract(BigInteger.valueOf(1)));
    }
    return value.toString();
  }

  @Override
  public String visit(Add<CompoundInterval> pAdd, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    String summand1 = pAdd.getSummand1().accept(this, pEnvironment);
    String summand2 = pAdd.getSummand2().accept(this, pEnvironment);
    if (summand1 == null || summand2 == null) {
      return evaluate(pAdd, pEnvironment);
    }
    return "(" + summand1 + " + " + summand2 + ")";
  }

  @Override
  public String visit(BinaryAnd<CompoundInterval> pAnd, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return evaluate(pAnd, pEnvironment);
  }

  @Override
  public String visit(BinaryNot<CompoundInterval> pNot, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return evaluate(pNot, pEnvironment);
  }

  @Override
  public String visit(BinaryOr<CompoundInterval> pOr, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return evaluate(pOr, pEnvironment);
  }

  @Override
  public String visit(BinaryXor<CompoundInterval> pXor, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return evaluate(pXor, pEnvironment);
  }

  @Override
  public String visit(Constant<CompoundInterval> pConstant, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return evaluate(pConstant, pEnvironment);
  }

  @Override
  public String visit(Divide<CompoundInterval> pDivide, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    String numerator = pDivide.getNumerator().accept(this, pEnvironment);
    String denominator = pDivide.getDenominator().accept(this, pEnvironment);
    if (numerator == null || denominator == null) {
      return evaluate(pDivide, pEnvironment);
    }
    return "(" + numerator + " / " + denominator + ")";
  }

  @Override
  public String visit(Exclusion<CompoundInterval> pExclusion,
      Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return evaluate(pExclusion, pEnvironment);
  }

  @Override
  public String visit(Modulo<CompoundInterval> pModulo, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    String numerator = pModulo.getNumerator().accept(this, pEnvironment);
    String denominator = pModulo.getDenominator().accept(this, pEnvironment);
    if (numerator == null || denominator == null) {
      return evaluate(pModulo, pEnvironment);
    }
    return "(" + numerator + " % " + denominator + ")";
  }

  @Override
  public String visit(Multiply<CompoundInterval> pMultiply, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    String factor1 = pMultiply.getFactor1().accept(this, pEnvironment);
    String factor2 = pMultiply.getFactor2().accept(this, pEnvironment);
    if (factor1 == null || factor2 == null) {
      return evaluate(pMultiply, pEnvironment);
    }
    return "(" + factor1 + " * " + factor2 + ")";
  }

  @Override
  public String visit(ShiftLeft<CompoundInterval> pShiftLeft, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return evaluate(pShiftLeft, pEnvironment);
  }

  @Override
  public String visit(ShiftRight<CompoundInterval> pShiftRight, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return evaluate(pShiftRight, pEnvironment);
  }

  @Override
  public String visit(Union<CompoundInterval> pUnion, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return evaluate(pUnion, pEnvironment);
  }

  @Override
  public String visit(Variable<CompoundInterval> pVariable, Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return pVariable.getMemoryLocation().getIdentifier();
  }

  @Override
  public String visit(Cast<CompoundInterval> pCast,
      Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    BitVectorInfo sourceInfo = pCast.getCasted().getBitVectorInfo();
    BitVectorInfo targetInfo = pCast.getBitVectorInfo();
    int sourceSize = sourceInfo.getSize();
    int targetSize = targetInfo.getSize();
    String sourceFormula = pCast.getCasted().accept(this, pEnvironment);
    if (sourceSize == targetSize || sourceFormula == null) {
      return sourceFormula;
    }
    // TODO correct casts
    return sourceFormula;
  }

  @Override
  public String visit(IfThenElse<CompoundInterval> pIfThenElse,
      Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    BooleanConstant<CompoundInterval> conditionEval = pIfThenElse.getCondition().accept(evaluationVisitor, pEnvironment);
    if (BooleanConstant.isTrue(conditionEval)) {
      return pIfThenElse.getPositiveCase().accept(this, pEnvironment);
    }
    if (BooleanConstant.isFalse(conditionEval)) {
      return pIfThenElse.getNegativeCase().accept(this, pEnvironment);
    }

    String conditionFormula = pIfThenElse.getCondition().accept(this, pEnvironment);
    if (conditionFormula == null) {
      return InvariantsFormulaManager.INSTANCE.union(pIfThenElse.getPositiveCase(), pIfThenElse.getNegativeCase()).accept(this, pEnvironment);
    }

    String positiveCaseFormula = pIfThenElse.getPositiveCase().accept(this, pEnvironment);
    if (positiveCaseFormula == null) {
      return null;
    }
    String negativeCaseFormula = pIfThenElse.getNegativeCase().accept(this, pEnvironment);
    if (negativeCaseFormula == null) {
      return null;
    }
    return "("
         + conditionFormula
         + " ? "
         + positiveCaseFormula
         + " : "
         + negativeCaseFormula + ")";
  }

  public static Integer getSize(NumeralFormula<CompoundInterval> pFormula) {
    return pFormula.getBitVectorInfo().getSize();
  }

  @Override
  public String visit(Equal<CompoundInterval> pEqual,
      Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    BitVectorInfo bitVectorInfo = pEqual.getOperand1().getBitVectorInfo();
    String operand1 = pEqual.getOperand1().accept(this, pEnvironment);
    String operand2 = pEqual.getOperand2().accept(this, pEnvironment);
    if (operand1 == null && operand2 == null) {
      return null;
    }
    if (operand1 == null || operand2 == null) {
      final String left;
      final NumeralFormula<CompoundInterval> right;
      if (operand1 != null) {
        left = operand1;
        right = pEqual.getOperand2();
      } else {
        left = operand2;
        right = pEqual.getOperand1();
      }
      CompoundInterval rightValue = right.accept(evaluationVisitor, pEnvironment);
      String bf = getFalse();
      for (SimpleInterval interval : rightValue.getIntervals()) {
        String intervalFormula = getTrue();
        if (interval.isSingleton()) {
          String value = asBitVectorFormula(bitVectorInfo, interval.getLowerBound());
          intervalFormula = and(intervalFormula, equal(left, value));
        } else {
          if (interval.hasLowerBound()) {
            String lb = asBitVectorFormula(bitVectorInfo, interval.getLowerBound());
            intervalFormula = and(intervalFormula, greaterEqual(left, lb));
          }
          if (interval.hasUpperBound()) {
            String ub = asBitVectorFormula(bitVectorInfo, interval.getUpperBound());
            intervalFormula = and(intervalFormula, lessEqual(left, ub));
          }
        }
        bf = or(bf, intervalFormula);
      }
      return bf;
    }
    return equal(operand1, operand2);
  }

  @Override
  public String visit(LessThan<CompoundInterval> pLessThan,
      Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    BitVectorInfo bitVectorInfo = pLessThan.getOperand1().getBitVectorInfo();
    String operand1 = pLessThan.getOperand1().accept(this, pEnvironment);
    String operand2 = pLessThan.getOperand2().accept(this, pEnvironment);
    if (operand1 == null && operand2 == null) {
      return null;
    }
    if (operand1 == null || operand2 == null) {
      final String left;
      final NumeralFormula<CompoundInterval> right;
      final boolean lessThan;
      if (operand1 != null) {
        left = operand1;
        right = pLessThan.getOperand2();
        lessThan = true;
      } else {
        left = operand2;
        right = pLessThan.getOperand1();
        lessThan = false;
      }
      CompoundInterval rightValue = right.accept(evaluationVisitor, pEnvironment);
      if (rightValue.isBottom()) {
        return getFalse();
      }
      if (lessThan) {
        if (rightValue.hasUpperBound()) {
          return lessThan(left, asBitVectorFormula(bitVectorInfo, rightValue.getUpperBound()));
        }
      } else if (rightValue.hasLowerBound()) {
        return greaterThan(left, asBitVectorFormula(bitVectorInfo, rightValue.getLowerBound()));
      }
      return null;
    }
    return operand1 + "<" + operand2;
  }

  private static final String lessThan(String pLess, String pMore) {
    return String.format("(%s < %s)", pLess, pMore);
  }

  private static final String lessEqual(String pLess, String pMore) {
    return String.format("(%s <= %s)", pLess, pMore);
  }

  private static final String greaterThan(String pLess, String pMore) {
    return String.format("(%s > %s)", pLess, pMore);
  }

  private static final String greaterEqual(String pLess, String pMore) {
    return String.format("(%s >= %s)", pLess, pMore);
  }

  private static final String equal(String pLess, String pMore) {
    return String.format("(%s == %s)", pLess, pMore);
  }

  private static final String and(String pLess, String pMore) {
    if (pLess.equals(getFalse()) || pMore.equals(getTrue())) {
      return pLess;
    }
    if (pMore.equals(getFalse()) || pLess.equals(getTrue())) {
      return pMore;
    }
    return String.format("(%s && %s)", pLess, pMore);
  }

  private static final String or(String pLess, String pMore) {
    if (pLess.equals(getFalse()) || pMore.equals(getTrue())) {
      return pMore;
    }
    if (pMore.equals(getFalse()) || pLess.equals(getTrue())) {
      return pLess;
    }
    return String.format("(%s || %s)", pLess, pMore);
  }

  private static final String not(String pOp) {
    if (pOp.equals(getFalse())) {
      return getTrue();
    }
    if (pOp.equals(getTrue())) {
      return getFalse();
    }
    return String.format("(!%s)", pOp);
  }

  private static final String getTrue() {
    return "(1)";
  }

  private static final String getFalse() {
    return "(0)";
  }

  @Override
  public String visit(LogicalAnd<CompoundInterval> pAnd,
      Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return and(pAnd.getOperand1().accept(this, pEnvironment), pAnd.getOperand2().accept(this, pEnvironment));
  }

  @Override
  public String visit(LogicalNot<CompoundInterval> pNot,
      Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return not(pNot.getNegated().accept(this, pEnvironment));
  }

  @Override
  public String visitFalse(Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return getFalse();
  }

  @Override
  public String visitTrue(Map<? extends MemoryLocation, ? extends NumeralFormula<CompoundInterval>> pEnvironment) {
    return getTrue();
  }

}
