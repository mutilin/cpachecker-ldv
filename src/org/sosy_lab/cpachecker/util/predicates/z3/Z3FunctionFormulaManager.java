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
package org.sosy_lab.cpachecker.util.predicates.z3;

import static org.sosy_lab.cpachecker.util.predicates.z3.Z3NativeApi.*;

import java.util.List;

import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.UninterpretedFunctionDeclaration;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFunctionFormulaManager;

import com.google.common.primitives.Longs;

class Z3FunctionFormulaManager extends AbstractFunctionFormulaManager<Long, Long, Long> {

  private final Z3UnsafeFormulaManager unsafeManager;
  private final long z3context;
  private final Z3SmtLogger smtLogger;

  Z3FunctionFormulaManager(
      Z3FormulaCreator creator,
      Z3UnsafeFormulaManager unsafeManager,
      Z3SmtLogger smtLogger) {
    super(creator, unsafeManager);
    this.z3context = creator.getEnv();
    this.unsafeManager = unsafeManager;
    this.smtLogger = smtLogger;
  }

  @Override
  public <TFormula extends Formula> Long createUninterpretedFunctionCallImpl(
      UninterpretedFunctionDeclaration<TFormula> pFuncType,
      List<Long> pArgs) {
    Z3UninterpretedFunctionDeclaration<TFormula> func = (Z3UninterpretedFunctionDeclaration<TFormula>) pFuncType;
    long[] args = Longs.toArray(pArgs);
    long funcDecl = func.getFuncDecl();
    return unsafeManager.createUIFCallImpl(funcDecl, args);
  }

  @Override
  public <T extends Formula> Z3UninterpretedFunctionDeclaration<T> declareUninterpretedFunction(
        String pName,
        FormulaType<T> pReturnType,
        List<FormulaType<?>> pArgs) {

    long symbol = mk_string_symbol(z3context, pName);
    long[] sorts = new long[pArgs.size()];
    for (int i = 0; i < pArgs.size(); i++) {
      sorts[i] = toSolverType(pArgs.get(i));
    }
    long returnType = toSolverType(pReturnType);
    long func = mk_func_decl(z3context, symbol, sorts, returnType);
    inc_ref(z3context, func);

    smtLogger.logFunctionDeclaration(symbol, sorts, returnType);

    return new Z3UninterpretedFunctionDeclaration<>(pReturnType, pArgs, func);
  }
}
