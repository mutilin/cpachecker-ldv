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
package org.sosy_lab.solver.smtInterpol;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.Model;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.ProverEnvironment;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import de.uni_freiburg.informatik.ultimate.logic.Term;

class SmtInterpolTheoremProver implements ProverEnvironment {

  private final SmtInterpolFormulaManager mgr;
  private SmtInterpolEnvironment env;
  private final List<Term> assertedTerms;

  private final Function<Term, BooleanFormula> encapsulateBoolean = new Function<Term, BooleanFormula>() {
    @Override
    public BooleanFormula apply(Term pInput) {
      return mgr.encapsulateBooleanFormula(pInput);
    }
  };

  SmtInterpolTheoremProver(SmtInterpolFormulaManager pMgr) {
    mgr = pMgr;
    assertedTerms = new ArrayList<>();
    env = mgr.createEnvironment();
    checkNotNull(env);
  }

  @Override
  public boolean isUnsat() throws InterruptedException {
    return !env.checkSat();
  }

  @Override
  public Model getModel() {
    Preconditions.checkNotNull(env);
    return SmtInterpolModel.createSmtInterpolModel(env, assertedTerms);
  }

  @Override
  public void pop() {
    Preconditions.checkNotNull(env);
    assertedTerms.remove(assertedTerms.size() - 1); // remove last term
    env.pop(1);
  }

  @Override
  public Void push(BooleanFormula f) {
    Preconditions.checkNotNull(env);
    final Term t = mgr.extractInfo(f);
    assertedTerms.add(t);
    env.push(1);
    env.assertTerm(t);
    return null;
  }

  @Override
  public void close() {
    Preconditions.checkNotNull(env);
    if (!assertedTerms.isEmpty()) {
      env.pop(assertedTerms.size());
      assertedTerms.clear();
    }
    env = null;
  }

  @Override
  public List<BooleanFormula> getUnsatCore() {
    Preconditions.checkNotNull(env);
    Term[] terms = env.getUnsatCore();
    return Lists.transform(Arrays.asList(terms), encapsulateBoolean);
  }


  @Override
  public <T> T allSat(AllSatCallback<T> callback,
      List<BooleanFormula> important)
      throws InterruptedException, SolverException {
    Term[] importantTerms = new Term[important.size()];
    int i = 0;
    for (BooleanFormula impF : important) {
      importantTerms[i++] = mgr.extractInfo(impF);
    }
    for (Term[] model : env.checkAllSat(importantTerms)) {
      callback.apply(Lists.transform(Arrays.asList(model), encapsulateBoolean));
    }
    return callback.getResult();
  }

  @Override
  public Formula evaluate(Formula f) {
    throw new UnsupportedOperationException("SmtInterpol does not support model "
        + "evaluation");
  }
}
