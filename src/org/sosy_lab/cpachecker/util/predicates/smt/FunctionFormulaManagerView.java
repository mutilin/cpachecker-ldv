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
package org.sosy_lab.cpachecker.util.predicates.smt;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.FluentIterable.from;

import java.util.Arrays;
import java.util.List;

import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.FunctionFormulaManager;
import org.sosy_lab.solver.api.UfDeclaration;

import com.google.common.base.Function;


public class FunctionFormulaManagerView extends BaseManagerView implements FunctionFormulaManager {

  private final FunctionFormulaManager manager;

  FunctionFormulaManagerView(FormulaWrappingHandler pWrappingHandler,
      FunctionFormulaManager pManager) {
    super(pWrappingHandler);
    this.manager = pManager;
  }

  private static class ReplaceUninterpretedFunctionDeclaration<T extends Formula> extends UfDeclaration<T> {

    private final UfDeclaration<?> wrapped;

    ReplaceUninterpretedFunctionDeclaration(
        UfDeclaration<?> wrapped,
        FormulaType<T> pReturnType,
        List<FormulaType<?>> pArgumentTypes) {
      super(pReturnType, pArgumentTypes);
      this.wrapped = checkNotNull(wrapped);
    }

    @Override
    public int hashCode() {
      return 17 + wrapped.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof ReplaceUninterpretedFunctionDeclaration)) {
        return false;
      }
      ReplaceUninterpretedFunctionDeclaration<?> other =
          (ReplaceUninterpretedFunctionDeclaration<?>)obj;

      return wrapped.equals(other.wrapped);
    }
  }

  @Override
  public <T extends Formula> UfDeclaration<T> declareUninterpretedFunction(
      String pName, FormulaType<T> pReturnType, List<FormulaType<?>> pArgs) {
    List<FormulaType<?>> newArgs = unwrapType(pArgs);
    FormulaType<?> ret = unwrapType(pReturnType);
    UfDeclaration<?> func = manager.declareUninterpretedFunction(pName, ret, newArgs);

    return new ReplaceUninterpretedFunctionDeclaration<>(func, pReturnType, pArgs);
  }

  @Override
  public <T extends Formula> UfDeclaration<T> declareUninterpretedFunction(
      String pName, FormulaType<T> pReturnType, FormulaType<?>... pArgs) {
    return declareUninterpretedFunction(pName, pReturnType, Arrays.asList(pArgs));
  }


  public <T extends Formula> T declareAndCallUninterpretedFunction(
      String pName, int idx, FormulaType<T> pReturnType, List<Formula> pArgs) {
    String name = FormulaManagerView.makeName(pName, idx);
    return declareAndCallUninterpretedFunction(name, pReturnType, pArgs);
  }

  public <T extends Formula> T declareAndCallUninterpretedFunction(
      String pName, int pIdx, FormulaType<T> pReturnType, Formula... pArgs) {
    return declareAndCallUninterpretedFunction(pName, pIdx, pReturnType, Arrays.asList(pArgs));
  }


  @Override
  public <T extends Formula> T declareAndCallUninterpretedFunction(
      String name, FormulaType<T> pReturnType, List<Formula> pArgs) {

    List<FormulaType<?>> argTypes = from(pArgs).
      transform(
          new Function<Formula, FormulaType<?>>() {
            @Override
            public FormulaType<?> apply(Formula pArg0) {
              return getFormulaType(pArg0);
            }}).toList();


    UfDeclaration<T> func = declareUninterpretedFunction(name, pReturnType, argTypes);
    return callUninterpretedFunction(func, pArgs);
  }

  public <T extends Formula> T declareAndCallUninterpretedFunction(
      String pName, FormulaType<T> pReturnType, Formula... pArgs) {
    return declareAndCallUninterpretedFunction(pName, pReturnType, Arrays.asList(pArgs));
  }


  @Override
  public <T extends Formula> T callUninterpretedFunction(
      UfDeclaration<T> pFuncType, List<? extends Formula> pArgs) {

    ReplaceUninterpretedFunctionDeclaration<T> rep = (ReplaceUninterpretedFunctionDeclaration<T>)pFuncType;

    Formula f = manager.callUninterpretedFunction(rep.wrapped, unwrap(pArgs));

    return wrap(pFuncType.getReturnType(), f);
  }

  @Override
  public <T extends Formula> T callUninterpretedFunction(
      UfDeclaration<T> pFuncType, Formula... pArgs) {
    return callUninterpretedFunction(pFuncType, Arrays.asList(pArgs));
  }
}
