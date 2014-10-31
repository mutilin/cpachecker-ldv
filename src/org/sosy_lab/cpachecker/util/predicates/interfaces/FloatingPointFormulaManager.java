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

import java.math.BigDecimal;

/**
 * This interface represents the floating-foint theory
 */
public interface FloatingPointFormulaManager {
  public FloatingPointFormula makeNumber(double n, FormulaType.FloatingPointType type);
  public FloatingPointFormula makeNumber(BigDecimal n, FormulaType.FloatingPointType type);
  public FloatingPointFormula makeNumber(String n, FormulaType.FloatingPointType type);

  public FloatingPointFormula makeVariable(String pVar, FormulaType.FloatingPointType type);

  public FloatingPointFormula castTo(FloatingPointFormula number, FormulaType.FloatingPointType targetType);

  // ----------------- Arithmetic relations, return type NumeralFormula -----------------

  public FloatingPointFormula negate(FloatingPointFormula number);

  public FloatingPointFormula add(FloatingPointFormula number1, FloatingPointFormula number2);

  public FloatingPointFormula subtract(FloatingPointFormula number1, FloatingPointFormula number2);

  public FloatingPointFormula divide(FloatingPointFormula number1, FloatingPointFormula number2);

  public FloatingPointFormula multiply(FloatingPointFormula number1, FloatingPointFormula number2);

  // ----------------- Numeric relations, return type BooleanFormula -----------------

  public BooleanFormula equal(FloatingPointFormula number1, FloatingPointFormula number2);

  public BooleanFormula greaterThan(FloatingPointFormula number1, FloatingPointFormula number2);

  public BooleanFormula greaterOrEquals(FloatingPointFormula number1, FloatingPointFormula number2);

  public BooleanFormula lessThan(FloatingPointFormula number1, FloatingPointFormula number2);

  public BooleanFormula lessOrEquals(FloatingPointFormula number1, FloatingPointFormula number2);
}
