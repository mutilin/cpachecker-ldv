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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.Model;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.ProverEnvironment;
import org.sosy_lab.solver.basicimpl.LongArrayBackedList;
import org.sosy_lab.solver.z3.Z3NativeApiConstants.Z3_LBOOL;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;

class Z3TheoremProver implements ProverEnvironment {

  private final ShutdownNotifier shutdownNotifier;
  private final Z3FormulaManager mgr;
  private long z3context;
  private long z3solver;
  private final Z3SmtLogger smtLogger;
  private int level = 0;
  private int track_no = 0;

  private static final String UNSAT_CORE_TEMP_VARNAME = "UNSAT_CORE_%d";

  private final Map<String, BooleanFormula> storedConstraints;

  Z3TheoremProver(Z3FormulaManager pMgr, long z3params,
      ShutdownNotifier pShutdownNotifier, boolean generateUnsatCore) {
    mgr = pMgr;
    z3context = mgr.getEnvironment();
    z3solver = mk_solver(z3context);
    solver_inc_ref(z3context, z3solver);
    solver_set_params(z3context, z3solver, z3params);
    smtLogger = mgr.getSmtLogger();
    if (generateUnsatCore) {
      storedConstraints = new HashMap<>();
    } else {
      storedConstraints = null;
    }
    shutdownNotifier = pShutdownNotifier;
  }

  @Override
  public Void push(BooleanFormula f) {
    track_no++;
    level++;

    Preconditions.checkArgument(z3context != 0);
    solver_push(z3context, z3solver);
    long e = Z3FormulaManager.getZ3Expr(f);

    if (mgr.simplifyFormulas) {
      e = simplify(z3context, e);
      inc_ref(z3context, e);
    }

    if (storedConstraints != null) { // Unsat core generation is on.
      String varName = String.format(UNSAT_CORE_TEMP_VARNAME, track_no);
      // TODO: can we do with no casting?
      Z3BooleanFormula t =
          (Z3BooleanFormula) mgr.getBooleanFormulaManager().makeVariable(
              varName);

      solver_assert_and_track(z3context, z3solver, e, t.getFormulaInfo());
      storedConstraints.put(varName, f);
    } else {
      solver_assert(z3context, z3solver, e);
    }

    smtLogger.logPush(1);
    smtLogger.logAssert(e);
    return null;
  }

  @Override
  public void pop() {
    level--;

    Preconditions.checkArgument(solver_get_num_scopes(z3context, z3solver) >= 1);
    solver_pop(z3context, z3solver, 1);

    smtLogger.logPop(1);
  }

  @Override
  public boolean isUnsat() throws Z3SolverException, InterruptedException {
    int result = solver_check(z3context, z3solver);
    shutdownNotifier.shutdownIfNecessary();
    Preconditions.checkArgument(result != Z3_LBOOL.Z3_L_UNDEF.status);

    smtLogger.logCheck();

    return result == Z3_LBOOL.Z3_L_FALSE.status;
  }

  @Override
  public Model getModel() throws SolverException {
    return Z3Model.createZ3Model(mgr, z3context, z3solver);
  }

  @Override
  public List<BooleanFormula> getUnsatCore() {
    if (storedConstraints == null) {
      throw new UnsupportedOperationException(
          "Option to generate the UNSAT core wasn't enabled when creating" +
          " the prover environment."
      );
    }

    List<BooleanFormula> constraints = new ArrayList<>();
    long ast_vector = solver_get_unsat_core(z3context, z3solver);
    ast_vector_inc_ref(z3context, ast_vector);
    for (int i=0; i<ast_vector_size(z3context, ast_vector); i++) {
      long ast = ast_vector_get(z3context, ast_vector, i);
      BooleanFormula f = mgr.encapsulateBooleanFormula(ast);

      constraints.add(storedConstraints.get(
          mgr.getUnsafeFormulaManager().getName(f)
      ));
    }
    ast_vector_dec_ref(z3context, ast_vector);
    return constraints;
  }

  @Override
  public void close() {
    Preconditions.checkArgument(z3context != 0);
    Preconditions.checkArgument(z3solver != 0);
    Preconditions.checkArgument(solver_get_num_scopes(z3context, z3solver) >= 0,
        "a negative number of scopes is not allowed");

    while (level > 0) { // TODO do we need this?
      pop();
    }

    //solver_reset(z3context, z3solver);
    solver_dec_ref(z3context, z3solver);
    z3context = 0;
    z3solver = 0;
  }


  @Override
  public <T> T allSat(AllSatCallback<T> callback,
      List<BooleanFormula> important)
      throws InterruptedException, SolverException {
    // unpack formulas to terms
    long[] importantFormulas = new long[important.size()];
    int i = 0;
    for (BooleanFormula impF : important) {
      importantFormulas[i++] = Z3FormulaManager.getZ3Expr(impF);
    }

    solver_push(z3context, z3solver);
    smtLogger.logPush(1);
    smtLogger.logCheck();

    while (solver_check(z3context, z3solver) == Z3_LBOOL.Z3_L_TRUE.status) {
      long[] valuesOfModel = new long[importantFormulas.length];
      long z3model = solver_get_model(z3context, z3solver);

      smtLogger.logGetModel();

      for (int j = 0; j < importantFormulas.length; j++) {
        long funcDecl = get_app_decl(z3context, importantFormulas[j]);
        long valueOfExpr = model_get_const_interp(z3context, z3model, funcDecl);

        if (isOP(z3context, valueOfExpr, Z3_OP_FALSE)) {
          valuesOfModel[j] = mk_not(z3context, importantFormulas[j]);
          inc_ref(z3context, valuesOfModel[j]);
        } else {
          valuesOfModel[j] = importantFormulas[j];
        }
      }

      callback.apply(new LongArrayBackedList<BooleanFormula>(valuesOfModel) {
        @Override
        protected BooleanFormula convert(long pE) {
          return mgr.encapsulateBooleanFormula(pE);
        }
      });

      long negatedModel = mk_not(z3context, mk_and(z3context, valuesOfModel));
      inc_ref(z3context, negatedModel);
      solver_assert(z3context, z3solver, negatedModel);

      smtLogger.logAssert(negatedModel);
      smtLogger.logCheck();
    }

    // we pushed some levels on assertionStack, remove them and delete solver
    solver_pop(z3context, z3solver, 1);
    smtLogger.logPop(1);
    return callback.getResult();
  }

  @Override
  public Formula evaluate(Formula f) {
    Z3Formula input = (Z3Formula) f;
    long z3model = solver_get_model(z3context, z3solver);
    model_inc_ref(z3context, z3model);

    PointerToLong out = new PointerToLong();
    boolean status = model_eval(z3context, z3model, input.getFormulaInfo(),
        true, out);
    Verify.verify(status, "Error during model evaluation");

    Formula outValue = mgr.getFormulaCreator().encapsulate(
        mgr.getFormulaType(f), out.value
    );

    model_dec_ref(z3context, z3model);
    return outValue;
  }
}
