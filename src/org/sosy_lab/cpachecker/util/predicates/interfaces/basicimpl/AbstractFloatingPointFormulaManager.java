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
package org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl;

import java.math.BigDecimal;

import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FloatingPointFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FloatingPointFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType.FloatingPointType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormulaManager;

/**
 * Similar to the other Abstract*FormulaManager classes in this package,
 * this class serves as a helper for implementing {@link NumeralFormulaManager}.
 * It handles all the unwrapping and wrapping from and to the {@link Formula}
 * instances, such that the concrete class needs to handle only its own internal types.
 *
 * For {@link #multiply(NumeralFormula, NumeralFormula)},
 * {@link #divide(NumeralFormula, NumeralFormula)}, and
 * {@link #modulo(NumeralFormula, NumeralFormula)},
 * this class even offers an implementation based on UFs.
 * Sub-classes are supposed to override them
 * if they can implement these operations more precisely
 * (for example multiplication with constants should be supported by all solvers
 * and implemented by all sub-classes).
 */
public abstract class AbstractFloatingPointFormulaManager<TFormulaInfo, TType, TEnv>
  extends AbstractBaseFormulaManager<TFormulaInfo, TType, TEnv>
  implements FloatingPointFormulaManager {

  protected AbstractFloatingPointFormulaManager(
      FormulaCreator<TFormulaInfo, TType, TEnv> pCreator) {
    super(pCreator);
  }

  protected TFormulaInfo extractInfo(Formula pNumber) {
    return getFormulaCreator().extractInfo(pNumber);
  }

  protected FloatingPointFormula wrap(TFormulaInfo pTerm) {
    return getFormulaCreator().encapsulateFloatingPoint(pTerm);
  }

  protected BooleanFormula wrapBool(TFormulaInfo pTerm) {
    return getFormulaCreator().encapsulateBoolean(pTerm);
  }

  @Override
  public FloatingPointFormula makeNumber(double n, FormulaType.FloatingPointType type) {
    return wrap(makeNumberImpl(n, type));
  }
  protected abstract TFormulaInfo makeNumberImpl(double n, FormulaType.FloatingPointType type);

  @Override
  public FloatingPointFormula makeNumber(BigDecimal n, FormulaType.FloatingPointType type) {
    return wrap(makeNumberImpl(n, type));
  }
  protected abstract TFormulaInfo makeNumberImpl(BigDecimal n, FormulaType.FloatingPointType type);

  @Override
  public FloatingPointFormula makeNumber(String n, FormulaType.FloatingPointType type) {
    return wrap(makeNumberImpl(n, type));
  }
  protected abstract TFormulaInfo makeNumberImpl(String n, FormulaType.FloatingPointType type);

  @Override
  public FloatingPointFormula makeVariable(String pVar, FormulaType.FloatingPointType pType) {
    return wrap(makeVariableImpl(pVar, pType));
  }
  protected abstract TFormulaInfo makeVariableImpl(String pVar, FormulaType.FloatingPointType pType);

  @Override
  public FloatingPointFormula castTo(FloatingPointFormula pNumber, FloatingPointType pTargetType) {
    return wrap(castToImpl(extractInfo(pNumber), pTargetType));
  }
  protected abstract TFormulaInfo castToImpl(TFormulaInfo pNumber, FloatingPointType pTargetType);


  @Override
  public FloatingPointFormula negate(FloatingPointFormula pNumber) {
    TFormulaInfo param1 = extractInfo(pNumber);
    return wrap(negate(param1));
  }

  protected abstract TFormulaInfo negate(TFormulaInfo pParam1);


  @Override
  public FloatingPointFormula add(FloatingPointFormula pNumber1, FloatingPointFormula pNumber2) {
    TFormulaInfo param1 = extractInfo(pNumber1);
    TFormulaInfo param2 = extractInfo(pNumber2);

    return wrap(add(param1, param2));
  }

  protected abstract TFormulaInfo add(TFormulaInfo pParam1, TFormulaInfo pParam2);

  @Override
  public FloatingPointFormula subtract(FloatingPointFormula pNumber1, FloatingPointFormula pNumber2) {
    TFormulaInfo param1 = extractInfo(pNumber1);
    TFormulaInfo param2 = extractInfo(pNumber2);

    return wrap(subtract(param1, param2));
  }

  protected abstract TFormulaInfo subtract(TFormulaInfo pParam1, TFormulaInfo pParam2) ;


  @Override
  public FloatingPointFormula divide(FloatingPointFormula pNumber1, FloatingPointFormula pNumber2) {
    TFormulaInfo param1 = extractInfo(pNumber1);
    TFormulaInfo param2 = extractInfo(pNumber2);

    return wrap(divide(param1, param2));
  }

  protected abstract TFormulaInfo divide(TFormulaInfo pParam1, TFormulaInfo pParam2);


  @Override
  public FloatingPointFormula multiply(FloatingPointFormula pNumber1, FloatingPointFormula pNumber2) {
    TFormulaInfo param1 = extractInfo(pNumber1);
    TFormulaInfo param2 = extractInfo(pNumber2);

    return wrap(multiply(param1, param2));
  }

  protected abstract TFormulaInfo multiply(TFormulaInfo pParam1, TFormulaInfo pParam2);


  @Override
  public BooleanFormula equal(FloatingPointFormula pNumber1, FloatingPointFormula pNumber2) {
    TFormulaInfo param1 = extractInfo(pNumber1);
    TFormulaInfo param2 = extractInfo(pNumber2);

    return wrapBool(equal(param1, param2));
  }

  protected abstract TFormulaInfo equal(TFormulaInfo pParam1, TFormulaInfo pParam2);


  @Override
  public BooleanFormula greaterThan(FloatingPointFormula pNumber1, FloatingPointFormula pNumber2) {
    TFormulaInfo param1 = extractInfo(pNumber1);
    TFormulaInfo param2 = extractInfo(pNumber2);

    return wrapBool(greaterThan(param1, param2));
  }

  protected abstract TFormulaInfo greaterThan(TFormulaInfo pParam1, TFormulaInfo pParam2) ;


  @Override
  public BooleanFormula greaterOrEquals(FloatingPointFormula pNumber1, FloatingPointFormula pNumber2) {
    TFormulaInfo param1 = extractInfo(pNumber1);
    TFormulaInfo param2 = extractInfo(pNumber2);

    return wrapBool(greaterOrEquals(param1, param2));
  }

  protected abstract TFormulaInfo greaterOrEquals(TFormulaInfo pParam1, TFormulaInfo pParam2) ;

  @Override
  public BooleanFormula lessThan(FloatingPointFormula pNumber1, FloatingPointFormula pNumber2) {
    TFormulaInfo param1 = extractInfo(pNumber1);
    TFormulaInfo param2 = extractInfo(pNumber2);

    return wrapBool(lessThan(param1, param2));
  }

  protected abstract TFormulaInfo lessThan(TFormulaInfo pParam1, TFormulaInfo pParam2) ;


  @Override
  public BooleanFormula lessOrEquals(FloatingPointFormula pNumber1, FloatingPointFormula pNumber2) {
    TFormulaInfo param1 = extractInfo(pNumber1);
    TFormulaInfo param2 = extractInfo(pNumber2);

    return wrapBool(lessOrEquals(param1, param2));
  }

  protected abstract TFormulaInfo lessOrEquals(TFormulaInfo pParam1, TFormulaInfo pParam2);
}
