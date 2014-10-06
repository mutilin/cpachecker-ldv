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
package org.sosy_lab.cpachecker.util.predicates.interfaces;

import java.math.BigInteger;
import java.util.List;

/**
 * This interface represents the Numeral-Theory
 *
 * @param <ParamFormulaType> formulaType of the parameters
 * @param <ResultFormulaType> formulaType of arithmetic results
 */
public interface NumeralFormulaManager
        <ParamFormulaType extends NumeralFormula,
         ResultFormulaType extends NumeralFormula>  {
  public ResultFormulaType makeNumber(long pI);
  public ResultFormulaType makeNumber(BigInteger pI);
  public ResultFormulaType makeNumber(String pI);

  public ResultFormulaType makeVariable(String pVar);

  public FormulaType<ResultFormulaType> getFormulaType();

  // ----------------- Arithmetic relations, return type NumeralFormula -----------------

  public ResultFormulaType negate(ParamFormulaType number);

  public ResultFormulaType add(ParamFormulaType number1, ParamFormulaType number2);
  public ResultFormulaType sum(List<ParamFormulaType> operands);

  public ResultFormulaType subtract(ParamFormulaType number1, ParamFormulaType number2);

  public ResultFormulaType divide(ParamFormulaType number1, ParamFormulaType number2);

  public ResultFormulaType modulo(ParamFormulaType number1, ParamFormulaType number2);

  public ResultFormulaType multiply(ParamFormulaType number1, ParamFormulaType number2);

  // ----------------- Numeric relations, return type BooleanFormula -----------------

  public BooleanFormula equal(ParamFormulaType number1, ParamFormulaType number2);
  public boolean isEqual(BooleanFormula number);

  public BooleanFormula greaterThan(ParamFormulaType number1, ParamFormulaType number2);

  public BooleanFormula greaterOrEquals(ParamFormulaType number1, ParamFormulaType number2);

  public BooleanFormula lessThan(ParamFormulaType number1, ParamFormulaType number2);

  public BooleanFormula lessOrEquals(ParamFormulaType number1, ParamFormulaType number2);
}
