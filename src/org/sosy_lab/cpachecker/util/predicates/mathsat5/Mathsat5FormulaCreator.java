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
package org.sosy_lab.cpachecker.util.predicates.mathsat5;

import static com.google.common.base.Preconditions.checkArgument;
import static org.sosy_lab.cpachecker.util.predicates.mathsat5.Mathsat5NativeApi.*;

import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFormulaCreator;


class Mathsat5FormulaCreator extends AbstractFormulaCreator<Long, Long, Long> {

  public Mathsat5FormulaCreator(final Long msatEnv) {
    super(msatEnv,
        msat_get_bool_type(msatEnv),
        msat_get_integer_type(msatEnv),
        msat_get_rational_type(msatEnv));
  }

  @Override
  public Long makeVariable(Long type, String varName) {
    long funcDecl = msat_declare_function(getEnv(), varName, type);
    return msat_make_constant(getEnv(), funcDecl);
  }

  @Override
  public Long extractInfo(Formula pT) {
    return Mathsat5FormulaManager.getMsatTerm(pT);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends Formula> FormulaType<T> getFormulaType(T pFormula) {
    if (pFormula instanceof BitvectorFormula) {
      long type = msat_term_get_type(extractInfo(pFormula));
      checkArgument(msat_is_bv_type(getEnv(), type),
          "BitvectorFormula with actual type " + msat_type_repr(type) + ": " + pFormula);
      return (FormulaType<T>) FormulaType.getBitvectorTypeWithSize(
          msat_get_bv_type_size(getEnv(), type));
    }
    return super.getFormulaType(pFormula);
  }

  @Override
  public FormulaType<?> getFormulaType(Long pFormula) {
    long env = getEnv();
    long type = msat_term_get_type(pFormula);
    if (msat_is_bool_type(env, type)) {
      return FormulaType.BooleanType;
    } else if (msat_is_integer_type(env, type)) {
      return FormulaType.IntegerType;
    } else if (msat_is_rational_type(env, type)) {
      return FormulaType.RationalType;
    } else if (msat_is_bv_type(env, type)) {
      return FormulaType.getBitvectorTypeWithSize(msat_get_bv_type_size(env, type));
    }
    throw new IllegalArgumentException("Unknown formula type");
  }

  @Override
  public Long getBittype(int pBitwidth) {
    return msat_get_bv_type(getEnv(), pBitwidth);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends Formula> T encapsulate(FormulaType<T> pType, Long pTerm) {
    if (pType.isBooleanType()) {
      return (T)new Mathsat5BooleanFormula(pTerm);
    } else if (pType.isIntegerType()) {
      return (T)new Mathsat5IntegerFormula(pTerm);
    } else if (pType.isRationalType()) {
      return (T)new Mathsat5RationalFormula(pTerm);
    } else if (pType.isBitvectorType()) {
      return (T)new Mathsat5BitvectorFormula(pTerm);
    }
    throw new IllegalArgumentException("Cannot create formulas of type " + pType + " in MathSAT");
  }

  @Override
  public BooleanFormula encapsulateBoolean(Long pTerm) {
    return new Mathsat5BooleanFormula(pTerm);
  }

  @Override
  public BitvectorFormula encapsulateBitvector(Long pTerm) {
    return new Mathsat5BitvectorFormula(pTerm);
  }
}
