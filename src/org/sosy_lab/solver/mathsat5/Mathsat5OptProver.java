package org.sosy_lab.solver.mathsat5;

import static org.sosy_lab.solver.mathsat5.Mathsat5FormulaManager.getMsatTerm;
import static org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.*;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.sosy_lab.common.UniqueIdGenerator;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.Model;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.OptEnvironment;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

class Mathsat5OptProver  extends Mathsat5AbstractProver implements OptEnvironment{
  private UniqueIdGenerator idGenerator = new UniqueIdGenerator();

  /**
   * Number of the objective -> objective pointer.
   */
  private List<Long> objectives = null;

  /**
   * ID given to user -> number of the objective.
   * Size corresponds to the number of currently existing objectives.
   */
  private Map<Integer, Integer> objectiveMap;

  /**
   * Stack of the objective maps.
   * Some duplication, but shouldn't be too important.
   */
  private Deque<ImmutableMap<Integer, Integer>> stack;

  Mathsat5OptProver(Mathsat5FormulaManager pMgr) {
    super(pMgr, createConfig(), true, false);
    objectiveMap = new HashMap<>();
    stack = new LinkedList<>();
  }

  private static long createConfig() {
    long cfg = msat_create_config();
    msat_set_option_checked(cfg, "model_generation", "true");
    return cfg;
  }

  @Override
  public void addConstraint(BooleanFormula constraint) {
    msat_assert_formula(curEnv, getMsatTerm(constraint));
  }

  @Override
  public int maximize(Formula objective) {
    // todo: code duplication.
    int id = idGenerator.getFreshId();
    objectiveMap.put(id, objectiveMap.size());
    msat_push_maximize(
        curEnv, getMsatTerm(objective), null, null
    );
    return id;
  }


  @Override
  public int minimize(Formula objective) {
    int id = idGenerator.getFreshId();
    objectiveMap.put(id, objectiveMap.size());
    msat_push_minimize(
        curEnv, getMsatTerm(objective), null, null
    );
    return id;
  }

  @Override
  public OptStatus check()
      throws InterruptedException, SolverException {
    boolean out = msat_check_sat(curEnv);
    if (out) {
      if (!objectiveMap.isEmpty()) {
        objectives = new ArrayList<>();
        long it = msat_create_objective_iterator(curEnv);
        while (msat_objective_iterator_has_next(it) != 0) {
          long[] objectivePtr = new long[1];
          int status = msat_objective_iterator_next(it, objectivePtr);
          assert status == 0;
          objectives.add(objectivePtr[0]);
        }
        msat_destroy_objective_iterator(it);
      }
      return OptStatus.OPT;
    } else {
      return OptStatus.UNSAT;
    }
  }

  @Override
  public void push() {
    msat_push_backtrack_point(curEnv);
    stack.add(ImmutableMap.copyOf(objectiveMap));
  }

  @Override
  public void pop() {
    msat_pop_backtrack_point(curEnv);
    objectiveMap = new HashMap<>(stack.pop());
  }

  @Override
  public Optional<Rational> upper(int handle, Rational epsilon) {
    return getValue(handle);
  }

  @Override
  public Optional<Rational> lower(int handle, Rational epsilon) {
    return getValue(handle);
  }

  private Optional<Rational> getValue(int handle) {
    // todo: use epsilon if the bound is non-strict.

    long objective = objectives.get(objectiveMap.get(handle));
    int isUnbounded = msat_objective_value_is_unbounded(curEnv, objective, MSAT_OPTIMUM);
    if (isUnbounded == 1) {
      return Optional.absent();
    }
    assert isUnbounded == 0;
    String objectiveValue = msat_objective_value_repr(curEnv, objective, MSAT_OPTIMUM);
    return Optional.of(Rational.ofString(objectiveValue));
  }

  @Override
  public Model getModel() throws SolverException {

    // Get to the last objective in the stack.
    // todo: code duplication.
    long it = msat_create_objective_iterator(curEnv);
    long[] objectivePtr = new long[1];
    while (msat_objective_iterator_has_next(it) != 0) {
      int status = msat_objective_iterator_next(it, objectivePtr);
      assert status == 0;
    }
    msat_destroy_objective_iterator(it);
    assert objectivePtr[0] != 0;

    msat_set_model(curEnv, objectivePtr[0]);
    return super.getModel();
  }

  @Override
  public Formula evaluate(Formula f) {
    throw new UnsupportedOperationException("Mathsat solver does not support evaluation");
  }

  @Override
  public String dump() {
    throw new UnsupportedOperationException("Mathsat solver does not constraint dumping");
  }

  @Override
  public void close() {
    super.close();
  }
}
