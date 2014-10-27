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
import java.util.Set;

import org.sosy_lab.cpachecker.exceptions.SolverException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.InterpolatingProverEnvironment;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class Mathsat5InterpolatingProver extends Mathsat5AbstractProver implements InterpolatingProverEnvironment<Integer> {

  private final boolean useSharedEnv;

  public Mathsat5InterpolatingProver(Mathsat5FormulaManager pMgr, boolean shared) {
    super(pMgr, createConfig(pMgr), shared, false);
    useSharedEnv = shared;
  }

  private static long createConfig(Mathsat5FormulaManager mgr) {
    long cfg = msat_create_config();
    msat_set_option_checked(cfg, "interpolation", "true");
    msat_set_option_checked(cfg, "model_generation", "true");
    msat_set_option_checked(cfg, "theory.bv.eager", "false");

    return cfg;
  }

  @Override
  public boolean isUnsat() throws InterruptedException, SolverException {
    Preconditions.checkState(curEnv != 0);
    try {
      return !msat_check_sat(curEnv);
    } catch (IllegalStateException e) {
      String msg = Strings.nullToEmpty(e.getMessage());
      if (msg.contains("too many iterations")
          || msg.contains("impossible to build a suitable congruence graph!")) {
        // This is not a bug in CPAchecker, but a problem of MathSAT which happens during interpolation
        throw new SolverException(e.getMessage(), e);
      }
      throw e;
    }
  }

  @Override
  public Integer push(BooleanFormula f) {
    Preconditions.checkState(curEnv != 0);
    long t = Mathsat5FormulaManager.getMsatTerm(f);
    //long t = ((Mathsat5Formula)f).getTerm();
    if (!useSharedEnv) {
      t = msat_make_copy_from(curEnv, t, mgr.getEnvironment());
    }
    int group = msat_create_itp_group(curEnv);
    msat_push_backtrack_point(curEnv);
    msat_set_itp_group(curEnv, group);
    msat_assert_formula(curEnv, t);
    return group;
  }

  @Override
  public BooleanFormula getInterpolant(List<Integer> formulasOfA) {
    Preconditions.checkState(curEnv != 0);

    int[] groupsOfA = new int[formulasOfA.size()];
    int i = 0;
    for (Integer f : formulasOfA) {
      groupsOfA[i++] = f;
    }
    long itp = msat_get_interpolant(curEnv, groupsOfA);

    if (!useSharedEnv) {
      itp = msat_make_copy_from(mgr.getEnvironment(), itp, curEnv);
    }
    return mgr.encapsulateBooleanFormula(itp);
  }

  @Override
  public List<BooleanFormula> getSeqInterpolants(List<Set<Integer>> partitionedFormulas) {
    // TODO is fallback to loop sound?

    //final List<BooleanFormula> itps = new ArrayList<>();
    //for (int i = 0; i < partitionedFormulas.size(); i++) {
    //  itps.add(getInterpolant(Lists.newArrayList(Iterables.concat(partitionedFormulas.subList(0, i)))));
    //}
    //return itps;

    throw new UnsupportedOperationException("directly receiving an inductive sequence of interpolants is not supported." +
            "Use another solver or another strategy for interpolants.");
  }
}
