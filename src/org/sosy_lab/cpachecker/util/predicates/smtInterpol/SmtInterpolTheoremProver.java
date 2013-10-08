/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
import static com.google.common.base.Preconditions.*;
import static org.sosy_lab.cpachecker.util.predicates.smtInterpol.SmtInterpolUtil.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.sosy_lab.common.NestedTimer;
import org.sosy_lab.common.Timer;
import org.sosy_lab.cpachecker.core.Model;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.util.predicates.AbstractionManager.RegionCreator;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.ProverEnvironment;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;
import org.sosy_lab.cpachecker.util.predicates.interfaces.RegionManager.RegionBuilder;

import com.google.common.base.Preconditions;

import de.uni_freiburg.informatik.ultimate.logic.Term;

class SmtInterpolTheoremProver implements ProverEnvironment {

  private final SmtInterpolFormulaManager mgr;
  private final ShutdownNotifier shutdownNotifier;
  private SmtInterpolEnvironment env;
  private final List<Term> assertedTerms;

  SmtInterpolTheoremProver(SmtInterpolFormulaManager pMgr, ShutdownNotifier pShutdownNotifier) {
    this.mgr = pMgr;
    this.shutdownNotifier = checkNotNull(pShutdownNotifier);

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
    return SmtInterpolModel.createSmtInterpolModel(mgr, assertedTerms);
  }

  @Override
  public void pop() {
    Preconditions.checkNotNull(env);
    assertedTerms.remove(assertedTerms.size()-1); // remove last term
    env.pop(1);
  }

  @Override
  public void push(BooleanFormula f) {
    Preconditions.checkNotNull(env);
    final Term t = mgr.getTerm(f);
    assertedTerms.add(t);
    env.push(1);
    env.assertTerm(t);
  }

  @Override
  public void close() {
    Preconditions.checkNotNull(env);
    env.pop(assertedTerms.size());
    assertedTerms.clear();
    env = null;
  }

  @Override
  public AllSatResult allSat(Collection<BooleanFormula> formulas,
                             RegionCreator rmgr, Timer solveTime, NestedTimer enumTime) throws InterruptedException {
    checkNotNull(rmgr);
    checkNotNull(solveTime);
    checkNotNull(enumTime);
    checkArgument(!formulas.isEmpty());

    SmtInterpolEnvironment allsatEnv = env;
    checkNotNull(allsatEnv);

    // create new allSatResult
    SmtInterpolAllSatCallback result = new SmtInterpolAllSatCallback(rmgr, solveTime, enumTime);

    // unpack formulas to terms
    Term[] importantTerms = new Term[formulas.size()];
    int i = 0;
    for (BooleanFormula impF : formulas) {

      importantTerms[i++] = mgr.getTerm(impF);
    }

    solveTime.start();
    try {
      allsatEnv.push(1);
      for (Term[] model : allsatEnv.checkAllSat(importantTerms)) {
        shutdownNotifier.shutdownIfNecessary();
        result.callback(model);
      }
      shutdownNotifier.shutdownIfNecessary();
      allsatEnv.pop(1);

    } finally {
      if (solveTime.isRunning()) {
        solveTime.stop();
      } else {
        enumTime.stopOuter();
      }
    }

    return result;
  }

  /**
   * callback used to build the predicate abstraction of a formula
   */
  class SmtInterpolAllSatCallback implements AllSatResult {
    private final RegionCreator rmgr;
    private final RegionBuilder builder;

    private final Timer solveTime;
    private final NestedTimer enumTime;
    private Timer regionTime = null;

    private int count = 0;

    private Region formula = null;

    public SmtInterpolAllSatCallback(RegionCreator rmgr, Timer pSolveTime, NestedTimer pEnumTime) {
      this.rmgr = rmgr;
      this.solveTime = pSolveTime;
      this.enumTime = pEnumTime;
      builder = rmgr.newRegionBuilder(shutdownNotifier);
    }

    @Override
    public int getCount() {
      return count;
    }

    @Override
    public Region getResult() throws InterruptedException {
      if (formula == null) {
        enumTime.startBoth();
        try {
          formula = builder.getResult();
          builder.close();
        } finally {
          enumTime.stopBoth();
        }
      }
      return formula;
    }

    public void callback(Term[] model) {
      if (count == 0) {
        solveTime.stop();
        enumTime.startOuter();
        regionTime = enumTime.getInnerTimer();
      }

      regionTime.start();

      // the abstraction is created simply by taking the disjunction
      // of all the models found by msat_all_sat, and storing them in a BDD
      // first, let's create the BDD corresponding to the model
      builder.startNewConjunction();
      for (Term t : model) {
        if (isNot(t)) {
          t = getArg(t, 0);
          builder.addNegativeRegion(rmgr.getPredicate(encapsulate(t)));
        } else {
          builder.addPositiveRegion(rmgr.getPredicate(encapsulate(t)));
        }
      }
      builder.finishConjunction();

      count++;

      regionTime.stop();
    }

    private BooleanFormula encapsulate(Term pT) {
      return mgr.encapsulateBooleanFormula(pT);
    }
  }
}
