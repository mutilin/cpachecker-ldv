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
package org.sosy_lab.solver.z3;

import static org.sosy_lab.solver.z3.Z3NativeApi.*;
import static org.sosy_lab.solver.z3.Z3NativeApiConstants.*;

import java.util.Collection;

import org.sosy_lab.solver.basicimpl.AbstractBooleanFormulaManager;

import com.google.common.primitives.Longs;

class Z3BooleanFormulaManager extends AbstractBooleanFormulaManager<Long, Long, Long> {

  private final long z3context;

  Z3BooleanFormulaManager(Z3FormulaCreator creator) {
    super(creator);
    this.z3context = creator.getEnv();
  }

  @Override
  protected Long makeVariableImpl(String varName) {
    long type = getFormulaCreator().getBoolType();
    return getFormulaCreator().makeVariable(type, varName);
  }

  @Override
  protected Long makeBooleanImpl(boolean pValue) {
    if (pValue) {
      return mk_true(z3context);
    } else {
      return mk_false(z3context);
    }
  }

  @Override
  protected Long not(Long pParam) {
    return mk_not(z3context, pParam);
  }

  @Override
  protected Long and(Long pParam1, Long pParam2) {
    return mk_and(z3context, pParam1, pParam2);
  }

  @Override
  protected Long or(Long pParam1, Long pParam2) {
    return mk_or(z3context, pParam1, pParam2);
  }

  @Override
  protected Long orImpl(Collection<Long> params) {
    return mk_or(z3context, params.size(), Longs.toArray(params));
  }

  @Override
  protected Long andImpl(Collection<Long> params) {
    return mk_and(z3context, params.size(), Longs.toArray(params));
  }

  @Override
  protected Long xor(Long pParam1, Long pParam2) {
    return mk_xor(z3context, pParam1, pParam2);
  }

  @Override
  protected boolean isNot(Long pParam) {
    return isOP(z3context, pParam, Z3_OP_NOT);
  }

  @Override
  protected boolean isAnd(Long pParam) {
    return isOP(z3context, pParam, Z3_OP_AND);
  }

  @Override
  protected boolean isOr(Long pParam) {
    return isOP(z3context, pParam, Z3_OP_OR);
  }

  @Override
  protected boolean isXor(Long pParam) {
    return isOP(z3context, pParam, Z3_OP_XOR);
  }

  @Override
  protected Long equivalence(Long pBits1, Long pBits2) {
    return mk_eq(z3context, pBits1, pBits2);
  }

  @Override
  protected Long implication(Long pBits1, Long pBits2) {
    return mk_implies(z3context, pBits1, pBits2);
  }

  @Override
  protected boolean isTrue(Long pParam) {
    return isOP(z3context, pParam, Z3_OP_TRUE);
  }

  @Override
  protected boolean isFalse(Long pParam) {
    return isOP(z3context, pParam, Z3_OP_FALSE);
  }

  @Override
  protected Long ifThenElse(Long pCond, Long pF1, Long pF2) {
    return mk_ite(z3context, pCond, pF1, pF2);
  }

  @Override
  protected boolean isEquivalence(Long pParam) {
    return isOP(z3context, pParam, Z3_OP_IFF) || isOP(z3context, pParam, Z3_OP_EQ)
        && get_app_num_args(z3context,pParam) == 2
        && get_sort(z3context, get_app_arg(z3context, pParam, 0)) == Z3_BOOL_SORT
        && get_sort(z3context, get_app_arg(z3context, pParam, 1)) == Z3_BOOL_SORT;
  }

  @Override
  protected boolean isImplication(Long pParam) {
    return isOP(z3context, pParam, Z3_OP_IMPLIES);
  }

  @Override
  protected boolean isIfThenElse(Long pParam) {
    return isOP(z3context, pParam, Z3_OP_ITE);
  }

  /**
   * @param pParam Z3_ast
   * @return Z3_ast with the tactic applied.
   */
  @Override
  public Long applyTacticImpl(Long pParam, Tactic tactic) {
    return Z3NativeApiHelpers.applyTactic(z3context, pParam, tactic.getTacticName());
  }
}
