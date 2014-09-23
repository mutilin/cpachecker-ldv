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

import static com.google.common.base.Preconditions.checkNotNull;

import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.RationalFormula;


/**
 * This is the central instance for encapsulating formulas.
 * With instances of this class you can also change the internal solver-types used by the solver.
 * @param <TFormulaInfo> the solver specific type.
 * @param <TType> the solver specific type for formula-types.
 * @param <TEnv> the solver specific environment.
 */
public abstract class AbstractFormulaCreator<TFormulaInfo, TType, TEnv> implements FormulaCreator<TFormulaInfo> {

  private final TType boolType;
  private final TType integerType;
  private final TType realType;
  private final TEnv environment;

  public TEnv getEnv() {
    return environment;
  }

  protected AbstractFormulaCreator(
      TEnv env,
      TType boolType,
      TType pIntegerType,
      TType pRealType
      ) {
    this.boolType = boolType;
    this.integerType = pIntegerType;
    this.realType = pRealType;
    this.environment = env;
  }

  @SuppressWarnings("unchecked")
  @Override
  public TFormulaInfo extractInfo(Formula pT) {
    return ((AbstractFormula<TFormulaInfo>)pT).getFormulaInfo();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends Formula> FormulaType<T> getFormulaType(T formula) {
    checkNotNull(formula);
    FormulaType<?> t;
    if (formula instanceof BooleanFormula) {
      t = FormulaType.BooleanType;
    } else if (formula instanceof IntegerFormula) {
      t = FormulaType.IntegerType;
    } else if (formula instanceof RationalFormula) {
      t = FormulaType.RationalType;
    } else if (formula instanceof BitvectorFormula) {
      throw new UnsupportedOperationException("SMT solvers with support for bitvectors needs to overwrite FormulaCreator.getFormulaType()");
    } else {
      throw new IllegalArgumentException("Formula with unexpected type " + formula.getClass());
    }
    return (FormulaType<T>) t;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Formula> T encapsulate(Class<T> pClazz, TFormulaInfo pTerm) {
    AbstractFormula<TFormulaInfo> f;
    if (pClazz == BitvectorFormula.class) {
      f = new BitvectorFormulaImpl<>(pTerm);
    } else if (pClazz == IntegerFormula.class) {
      f = new IntegerFormulaImpl<>(pTerm);
    } else if (pClazz == RationalFormula.class) {
      f = new RationalFormulaImpl<>(pTerm);
    } else if (pClazz == BooleanFormula.class) {
      f = new BooleanFormulaImpl<>(pTerm);
    } else {
      throw new IllegalArgumentException("invalid interface type");
    }

    return (T)f;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends Formula> T encapsulate(FormulaType<T> pType, TFormulaInfo pTerm) {
    if (pType.isBooleanType()) {
      return (T)new BooleanFormulaImpl<>(pTerm);
    } else if (pType.isIntegerType()) {
      return (T)new IntegerFormulaImpl<>(pTerm);
    } else if (pType.isRationalType()) {
      return (T)new RationalFormulaImpl<>(pTerm);
    } else if (pType.isBitvectorType()) {
      return (T)new BitvectorFormulaImpl<>(pTerm);
    }
    throw new IllegalArgumentException("Cannot create formulas of type " + pType + " in MathSAT");
  }

  @Override
  public BooleanFormula encapsulateBoolean(TFormulaInfo pTerm) {
    return new BooleanFormulaImpl<>(pTerm);
  }

  @Override
  public BitvectorFormula encapsulateBitvector(TFormulaInfo pTerm) {
    return new BitvectorFormulaImpl<>(pTerm);
  }

  public abstract TType getBittype(int bitwidth);

  public TType getBoolType() {
    return boolType;
  }

  public TType getIntegerType() {
    return integerType;
  }

  public TType getRealType() {
    return realType;
  }

  public abstract TFormulaInfo makeVariable(TType type, String varName) ;
}
