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

import java.util.Arrays;
import java.util.List;

import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FunctionFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.UninterpretedFunctionDeclaration;

import com.google.common.collect.Lists;

/**
 * This class simplifies the implementation of the FunctionFormulaManager by converting the types to the solver specific type.
 * It depends on UnsafeFormulaManager to make clear that the UnsafeFormulaManager should not depend on FunktionFormulaManager.
 * @param <TFormulaInfo> The solver specific type.
 * @param <TType> The solver specific type of formula-types.
 */
public abstract class AbstractFunctionFormulaManager<TFormulaInfo, TType, TEnv>
    extends AbstractBaseFormulaManager<TFormulaInfo, TType, TEnv>
    implements FunctionFormulaManager {

  private final AbstractUnsafeFormulaManager<TFormulaInfo, TType, TEnv> unsafeManager;

  protected AbstractFunctionFormulaManager(
      FormulaCreator<TFormulaInfo, TType, TEnv> pCreator,
      AbstractUnsafeFormulaManager<TFormulaInfo, TType, TEnv> unsafeManager) {
    super(pCreator);
    this.unsafeManager = unsafeManager;
  }

  @Override
  public <T extends Formula> UninterpretedFunctionDeclaration<T> declareUninterpretedFunction(
      String pName, FormulaType<T> pReturnType, FormulaType<?>... pArgs) {

    return declareUninterpretedFunction(pName, pReturnType, Arrays.asList(pArgs));
  }

  protected abstract <TFormula extends Formula> TFormulaInfo
    createUninterpretedFunctionCallImpl(UninterpretedFunctionDeclaration<TFormula> pFuncType, List<TFormulaInfo> pArgs);

  @Override
  public final <T extends Formula> T callUninterpretedFunction(UninterpretedFunctionDeclaration<T> pFuncType, List<? extends Formula> pArgs) {
    FormulaType<T> retType = pFuncType.getReturnType();
    List<TFormulaInfo> list = Lists.transform(pArgs, extractor);

    TFormulaInfo formulaInfo = createUninterpretedFunctionCallImpl(pFuncType, list);
    return unsafeManager.typeFormula(retType, formulaInfo);
  }

}
