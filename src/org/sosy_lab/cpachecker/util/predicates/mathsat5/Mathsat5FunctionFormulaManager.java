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

import static org.sosy_lab.cpachecker.util.predicates.mathsat5.Mathsat5NativeApi.*;

import java.util.List;

import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFunctionFormulaManager;

import com.google.common.primitives.Longs;

class Mathsat5FunctionFormulaManager extends AbstractFunctionFormulaManager<Long, Long, Long, Long> {

  private final long mathsatEnv;

  public Mathsat5FunctionFormulaManager(
      Mathsat5FormulaCreator pCreator,
      Mathsat5UnsafeFormulaManager unsafeManager) {
    super(pCreator, unsafeManager);
    this.mathsatEnv = pCreator.getEnv();
  }

  public long createUIFCallImpl(long funcDecl, long[] args) {
    return msat_make_uf(mathsatEnv, funcDecl, args);
  }

  @Override
  protected Long createUninterpretedFunctionCallImpl(Long funcDecl, List<Long> pArgs) {
    long[] args = Longs.toArray(pArgs);
    return createUIFCallImpl(funcDecl, args);
  }

  @Override
  public <T extends Formula> Long declareUninterpretedFunctionImpl(
        String pName, FormulaType<T> pReturnType, List<FormulaType<?>> pArgs) {
    long[] types = new long[pArgs.size()];
    for (int i = 0; i < types.length; i++) {
      types[i] = toSolverType(pArgs.get(i));
    }
    long returnType = toSolverType(pReturnType);
    return createFunctionImpl(pName, returnType, types);
  }

  public long createFunctionImpl(String pName, long returnType, long[] msatTypes) {
    long msatFuncType = msat_get_function_type(mathsatEnv, msatTypes, msatTypes.length, returnType);
    long decl = msat_declare_function(mathsatEnv, pName, msatFuncType);
    return decl;
  }
}
