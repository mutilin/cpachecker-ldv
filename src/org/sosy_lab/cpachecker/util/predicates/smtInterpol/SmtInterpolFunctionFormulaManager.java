/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.predicates.smtInterpol;

import static com.google.common.collect.FluentIterable.from;

import java.lang.reflect.Array;
import java.util.List;

import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FunctionFormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.RationalFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFunctionFormulaManager;

import com.google.common.base.Function;

import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;


public class SmtInterpolFunctionFormulaManager extends AbstractFunctionFormulaManager<Term> {

  private SmtInterpolFormulaCreator creator;
  private SmtInterpolEnvironment env;

  public SmtInterpolFunctionFormulaManager(
      SmtInterpolFormulaCreator creator,
      SmtInterpolUnsafeFormulaManager unsafeManager) {
    super(creator, unsafeManager);
    this.creator = creator;
    this.env = creator.getEnv();
  }

  @Override
  public <TFormula extends Formula> Term createUninterpretedFunctionCallImpl(FunctionFormulaType<TFormula> pFuncType,
      List<Term> pArgs) {
    SmtInterpolFunctionType<TFormula> interpolType = (SmtInterpolFunctionType<TFormula>) pFuncType;
    Term[] args = pArgs.toArray((Term[])Array.newInstance(Term.class, pArgs.size()));
    String funcDecl = interpolType.getFuncDecl();
    return
        this.<SmtInterpolUnsafeFormulaManager>getUnsafeManager().createUIFCallImpl(funcDecl, args);
  }

  public Sort toSmtInterpolType (org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType<? extends Formula> formulaType){
    Class<?> clazz = formulaType.getInterfaceType();
    Sort t;
    if (clazz==BooleanFormula.class) {
      t = creator.getBoolType();
    } else if (clazz == RationalFormula.class) {
      t = creator.getNumberType();
    } else if (clazz == BitvectorFormula.class) {
      FormulaType.BitvectorType bitPreciseType = (FormulaType.BitvectorType) formulaType;
      t = creator.getBittype(bitPreciseType.getSize());
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }
    return t;
  }

  @Override
  public <T extends Formula> SmtInterpolFunctionType<T>
    createFunction(
        String pName,
        FormulaType<T> pReturnType,
        List<FormulaType<? extends Formula>> pArgs) {
    org.sosy_lab.cpachecker.util.predicates.interfaces.FunctionFormulaType<T> formulaType
      = super.createFunction(pName, pReturnType, pArgs);


    List<Sort> types =
      from(pArgs)
      .transform(new Function<FormulaType<? extends Formula>,Sort>(){

        @Override
        public Sort apply(FormulaType<? extends Formula> pArg0) {
          return toSmtInterpolType(pArg0);
        }})
        .toImmutableList();
    Sort[] msatTypes = types.toArray((Sort[])Array.newInstance(Sort.class, types.size()));

    Sort returnType = toSmtInterpolType(pReturnType);
    String decl = createFunctionImpl(pName, returnType, msatTypes);

    return new SmtInterpolFunctionType<>(formulaType.getReturnType(), formulaType.getArgumentTypes(), decl);
  }

  public String createFunctionImpl(String pName, Sort returnType, Sort[] msatTypes) {
    env.declareFun(pName, msatTypes, returnType);
    return pName;
  }

  @Override
  public <T extends Formula> boolean isUninterpretedFunctionCall(FunctionFormulaType<T> pFuncType, Term f) {
    boolean isUf = getUnsafeManager().isUF(f);
    if (!isUf) return false;

    // TODO check if exactly the given func
    return isUf;
  }

}
