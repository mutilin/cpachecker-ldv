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
/*
 * This file was automatically generated by extract_java_stub.py
 * Wrapper for the MathSAT API for Java
 */
package org.sosy_lab.cpachecker.util.predicates.mathsat5;

import java.util.NoSuchElementException;

import javax.annotation.CheckReturnValue;

import com.google.common.base.Strings;
import com.google.common.collect.UnmodifiableIterator;


@SuppressWarnings("unused")
class Mathsat5NativeApi {

  // msat_result
  private static final int MSAT_UNKNOWN = -1;
  private static final int MSAT_UNSAT = 0;
  private static final int MSAT_SAT = 1;

  // msat_truth_value
  public static final int MSAT_UNDEF = -1;
  public static final int MSAT_FALSE = 0;
  public static final int MSAT_TRUE = 1;

  /**
   * OptiMathSAT codes for queries on objective items
   */
  public static final int MSAT_OPTIMUM = 0;
  public static final int MSAT_INITIAL_LOWER = 1;
  public static final int MSAT_INITIAL_UPPER = 2;
  public static final int MSAT_FINAL_LOWER = 3;
  public static final int MSAT_FINAL_UPPER = 4;
  public static final int MSAT_FINAL_ERROR = 5;

  /**
   * OptiMathSAT objective type, either minimize or maximize
   */
  public static final int MSAT_OBJECTIVE_MINIMIZE = -1;
  public static final int MSAT_OBJECTIVE_MAXIMIZE =  1;

  interface AllSatModelCallback {

    void callback(long[] model) throws InterruptedException;
  }

  interface TerminationTest {
    boolean shouldTerminate() throws InterruptedException;
  }

  // wrappers for some of the native methods with a different number
  // of arguments
  public static int msat_all_sat(long e, long[] important,
      AllSatModelCallback func) throws InterruptedException {

    return msat_all_sat(e, important, important.length, func);
  }

  /**
   * Solve environment and check for satisfiability.
   * Return true if sat, false if unsat.
   */
  public static boolean msat_check_sat(long e) throws InterruptedException {
    return processSolveResult(e, msat_solve(e));
  }

  public static boolean msat_check_sat_with_assumptions(long e, long[] assumptions) throws InterruptedException {
    return processSolveResult(e,
        msat_solve_with_assumptions(e, assumptions, assumptions.length));
  }

  private static boolean processSolveResult(long e, int resultCode) throws IllegalStateException {
    switch (resultCode) {
    case MSAT_SAT:
      return true;
    case MSAT_UNSAT:
      return false;
    default:
      String msg = Strings.emptyToNull(msat_last_error_message(e));
      String code = (resultCode == MSAT_UNKNOWN) ? "\"unknown\"" : resultCode + "";
      throw new IllegalStateException("msat_solve returned " + code
          + (msg != null ? ": " + msg : ""));
    }
  }

  public static ModelIterator msat_create_ModelIterator(long e) {
    return new ModelIterator(msat_create_model_iterator(e));
  }

  static class NamedTermsWrapper {
    final long[] terms;
    final String[] names;

    NamedTermsWrapper(long[] pTerms, String[] pNames) {
      terms = pTerms;
      names = pNames;
    }
  }

  public static class ModelIterator extends UnmodifiableIterator<long[]> {

    private final long i;

    private ModelIterator(long pI) {
      i = pI;
    }

    @Override
    public boolean hasNext() {
      return msat_model_iterator_has_next(i);
    }

    @Override
    public long[] next() {
      long[] t = new long[1];
      long[] v = new long[1];
      if (msat_model_iterator_next(i, t, v)) {
        throw new NoSuchElementException();
      }
      return new long[] { t[0], v[0] };
    }

    public void free() {
      msat_destroy_model_iterator(i);
    }
  }

  public static long msat_get_interpolant(long e, int[] groups_of_a) {
    return msat_get_interpolant(e, groups_of_a, groups_of_a.length);
  }

  /*
   * Environment creation
   */
  public static native long msat_create_config();
  public static native void msat_destroy_config(long cfg);
  public static native long msat_create_env(long cfg);
  public static native long msat_create_shared_env(long cfg, long sibling);
  public static native void msat_destroy_env(long e);

  @CheckReturnValue
  private static native int msat_set_option(long cfg, String option, String value);
  public static void msat_set_option_checked(long cfg, String option, String value)
                                                throws IllegalArgumentException {
    int retval = msat_set_option(cfg, option, value);
    if (retval != 0) {
      throw new IllegalArgumentException("Could not set Mathsat option \""+option+"="+value+"\", error code " + retval);
    }
  }

  public static native long msat_get_bool_type(long e);
  public static native long msat_get_rational_type(long e);
  public static native long msat_get_integer_type(long e);
  public static native long msat_get_bv_type(long e, int size);
  public static native long msat_get_array_type(long e, long itp, long etp);
  public static native long msat_get_fp_type(long e, int exp_with, int mant_with);
  public static native long msat_get_fp_roundingmode_type(long e);
  public static native long msat_get_simple_type(long e, String name);
  public static native long msat_get_function_type(long e, long[] paramTypes, int size, long returnType);

  public static native boolean msat_is_bool_type(long e, long t);
  public static native boolean msat_is_rational_type(long e, long t);
  public static native boolean msat_is_integer_type(long e, long t);
  public static native boolean msat_is_bv_type(long e, long t);
  public static native int msat_get_bv_type_size(long e, long t);
  public static native boolean msat_is_array_type(long e, long t);
  public static native boolean msat_is_fp_type(long e, long t);
  public static native int msat_get_fp_type_exp_width(long e, long t);
  public static native int msat_get_fp_type_mant_width(long e, long t);
  public static native boolean msat_is_fp_roundingmode_type(long e, long t);

  public static native boolean msat_type_equals(long t1, long t2);
  public static native String msat_type_repr(long t);

  public static native long msat_declare_function(long e, String name, long t);


  /*
   * Term creation
   */
  public static native long msat_make_true(long e);
  public static native long msat_make_false(long e);
  public static native long msat_make_iff(long e, long t1, long t2);
  public static native long msat_make_or(long e, long t1, long t2);
  public static native long msat_make_and(long e, long t1, long t2);
  public static native long msat_make_not(long e, long t1);
  public static native long msat_make_equal(long e, long t1, long t2);
  public static native long msat_make_leq(long e, long t1, long t2);
  public static native long msat_make_plus(long e, long t1, long t2);
  public static native long msat_make_times(long e, long t1, long t2);
  public static native long msat_make_floor(long e, long t);
  public static native long msat_make_number(long e, String num_rep);
  public static native long msat_make_int_modular_congruence(long e, long modulo, long t1, long t2);
  public static native long msat_make_term_ite(long e, long c, long tt, long te);
  public static native long msat_make_constant(long e, long var);
  public static native long msat_make_uf(long e, long func, long[] args);
  public static native long msat_make_array_read(long e, long arr, long idx);
  public static native long msat_make_array_write(long e, long arr, long idx, long elem);
  public static native long msat_make_array_const(long e, long arrayType, long elem);
  public static native long msat_make_int_to_bv(long e, int width, long t);
  public static native long msat_make_int_from_ubv(long e, long t);
  public static native long msat_make_int_from_sbv(long e, long t);
  public static native long msat_make_bv_number(long e, String numRep, int width, int base);
  public static native long msat_make_bv_concat(long e, long t1, long t2);

  /**
   * Returns a term representing the selection of t[msb:lsb].
   * @param e   The environment of the definition
   * @param msb   The most significant bit of the selection.
   * @param lsb   The least significant bit of the selection.
   * @param t   The argument.
   * @return a term representing the selection of t[msb:lsb].
   */
  public static native long msat_make_bv_extract(long e, int msb, int lsb, long t);
  public static native long msat_make_bv_or(long e, long t1, long t2);
  public static native long msat_make_bv_xor(long e, long t1, long t2);
  public static native long msat_make_bv_and(long e, long t1, long t2);
  public static native long msat_make_bv_not(long e, long t);
  public static native long msat_make_bv_lshl(long e, long t1, long t2);
  public static native long msat_make_bv_lshr(long e, long t1, long t2);
  public static native long msat_make_bv_ashr(long e, long t1, long t2);
  public static native long msat_make_bv_zext(long e, int amount, long t);
  public static native long msat_make_bv_sext(long e, int amount, long t);
  public static native long msat_make_bv_plus(long e, long t1, long t2);
  public static native long msat_make_bv_minus(long e, long t1, long t2);
  public static native long msat_make_bv_neg(long e, long t);
  public static native long msat_make_bv_times(long e, long t1, long t2);
  public static native long msat_make_bv_udiv(long e, long t1, long t2);
  public static native long msat_make_bv_urem(long e, long t1, long t2);
  public static native long msat_make_bv_sdiv(long e, long t1, long t2);
  public static native long msat_make_bv_srem(long e, long t1, long t2);
  public static native long msat_make_bv_ult(long e, long t1, long t2);
  public static native long msat_make_bv_uleq(long e, long t1, long t2);
  public static native long msat_make_bv_slt(long e, long t1, long t2);
  public static native long msat_make_bv_sleq(long e, long t1, long t2);
  public static native long msat_make_bv_rol(long e, int size, long t);
  public static native long msat_make_bv_ror(long e, int size, long t);
  public static native long msat_make_bv_comp(long e, long t1, long t2);
  public static native long msat_make_fp_roundingmode_nearest_even(long e);
  public static native long msat_make_fp_roundingmode_zero(long e);
  public static native long msat_make_fp_roundingmode_plus_inf(long e);
  public static native long msat_make_fp_roundingmode_minus_inf(long e);
  public static native long msat_make_fp_equal(long e, long t1, long t2);
  public static native long msat_make_fp_lt(long e, long t1, long t2);
  public static native long msat_make_fp_leq(long e, long t1, long t2);
  public static native long msat_make_fp_neg(long e, long t);
  public static native long msat_make_fp_plus(long e, long rounding, long t1, long t2);
  public static native long msat_make_fp_minus(long e, long rounding, long t1, long t2);
  public static native long msat_make_fp_times(long e, long rounding, long t1, long t2);
  public static native long msat_make_fp_div(long e, long rounding, long t1, long t2);
  public static native long msat_make_fp_cast(long e, long exp_w, long mant_w, long rounding, long t);
  public static native long msat_make_fp_to_bv(long e, long width, long rounding, long t);
  public static native long msat_make_fp_from_sbv(long e, long exp_w, long mant_w, long rounding, long t);
  public static native long msat_make_fp_from_ubv(long e, long exp_w, long mant_w, long rounding, long t);
  public static native long msat_make_fp_as_ieeebv(long e, long t);
  public static native long msat_make_fp_from_ieeebv(long e, long exp_w, long mant_w, long t);
  public static native long msat_make_fp_isnan(long e, long t);
  public static native long msat_make_fp_isinf(long e, long t);
  public static native long msat_make_fp_iszero(long e, long t);
  public static native long msat_make_fp_issubnormal(long e, long t);
  public static native long msat_make_fp_plus_inf(long e, long exp_w, long mant_w);
  public static native long msat_make_fp_minus_inf(long e, long exp_w, long mant_w);
  public static native long msat_make_fp_nan(long e, long exp_w, long mant_w);
  public static native long msat_make_fp_rat_number(long e, String numRep, long exp_w, long mant_w, long rounding);
  public static native long msat_make_fp_bits_number(long e, String bitRep, long exp_w, long mant_w);
  public static native long msat_make_term(long e, long d, long[] args);
  public static native long msat_make_copy_from(long e, long t, long src);

 /*
  * Term access and navigation
  */
  public static native int msat_term_id(long t);
  public static native int msat_term_arity(long t);
  public static native long msat_term_get_arg(long t, int n);
  public static native long msat_term_get_type(long t);
  public static native boolean msat_term_is_true(long e, long t);
  public static native boolean msat_term_is_false(long e, long t);
  public static native boolean msat_term_is_boolean_constant(long e, long t);
  public static native boolean msat_term_is_atom(long e, long t);
  public static native boolean msat_term_is_number(long e, long t);
  public static native boolean msat_term_is_and(long e, long t);
  public static native boolean msat_term_is_or(long e, long t);
  public static native boolean msat_term_is_not(long e, long t);
  public static native boolean msat_term_is_iff(long e, long t);
  public static native boolean msat_term_is_term_ite(long e, long t);
  public static native boolean msat_term_is_constant(long e, long t);
  public static native boolean msat_term_is_uf(long e, long t);
  public static native boolean msat_term_is_equal(long e, long t);
  public static native boolean msat_term_is_leq(long e, long t);
  public static native boolean msat_term_is_plus(long e, long t);
  public static native boolean msat_term_is_times(long e, long t);
  public static native boolean msat_term_is_floor(long e, long t);
  public static native boolean msat_term_is_array_read(long e, long t);
  public static native boolean msat_term_is_array_write(long e, long t);
  public static native boolean msat_term_is_array_const(long e, long t);
  public static native boolean msat_term_is_int_to_bv(long e, long t);
  public static native boolean msat_term_is_int_from_ubv(long e, long t);
  public static native boolean msat_term_is_int_from_sbv(long e, long t);
  public static native boolean msat_term_is_bv_concat(long e, long t);
  public static native boolean msat_term_is_bv_extract(long e, long t);
  public static native boolean msat_term_is_bv_or(long e, long t);
  public static native boolean msat_term_is_bv_xor(long e, long t);
  public static native boolean msat_term_is_bv_and(long e, long t);
  public static native boolean msat_term_is_bv_not(long e, long t);
  public static native boolean msat_term_is_bv_plus(long e, long t);
  public static native boolean msat_term_is_bv_minus(long e, long t);
  public static native boolean msat_term_is_bv_times(long e, long t);
  public static native boolean msat_term_is_bv_neg(long e, long t);
  public static native boolean msat_term_is_bv_udiv(long e, long t);
  public static native boolean msat_term_is_bv_urem(long e, long t);
  public static native boolean msat_term_is_bv_sdiv(long e, long t);
  public static native boolean msat_term_is_bv_srem(long e, long t);
  public static native boolean msat_term_is_bv_ult(long e, long t);
  public static native boolean msat_term_is_bv_uleq(long e, long t);
  public static native boolean msat_term_is_bv_slt(long e, long t);
  public static native boolean msat_term_is_bv_sleq(long e, long t);
  public static native boolean msat_term_is_bv_lshl(long e, long t);
  public static native boolean msat_term_is_bv_lshr(long e, long t);
  public static native boolean msat_term_is_bv_ashr(long e, long t);
  public static native boolean msat_term_is_bv_zext(long e, long t);
  public static native boolean msat_term_is_bv_sext(long e, long t);
  public static native boolean msat_term_is_bv_rol(long e, long t);
  public static native boolean msat_term_is_bv_ror(long e, long t);
  public static native boolean msat_term_is_bv_comp(long e, long t);
  //public static native int msat_visit_term(long e, msat_visit_term_callback func)
  public static native long msat_find_decl(long e, String symbol);

  public static native long msat_term_get_decl(long t);
  public static native int msat_decl_id(long d);
  public static native long msat_decl_get_return_type(long d);
  public static native int msat_decl_get_arity(long d);
  public static native long msat_decl_get_arg_type(long d, int n);
  public static native String msat_decl_repr(long d);
  public static native String msat_decl_get_name(long d);
  public static native String msat_term_repr(long t);
  /*
   * Parsing and writing formulas.
   */
  public static native long msat_from_string(long e, String data);
  public static native long msat_from_smtlib1(long e, String data);
  public static native long msat_from_smtlib2(long e, String data);
  public static native String msat_to_smtlib1(long e, long t);
  public static native String msat_to_smtlib2(long e, long t);
  public static native String msat_to_smtlib2_term(long e, long t);
  public static native String msat_named_list_to_smtlib2(long e, NamedTermsWrapper w);
  public static native NamedTermsWrapper msat_named_list_from_smtlib2(long e, String s);


  /*
   * Problem solving
   */
  public static native void msat_push_backtrack_point(long e);
  public static native void msat_pop_backtrack_point(long e);
  //public static native int msat_num_backtrack_points(long e)
  public static native void msat_reset_env(long e);
  public static native void msat_assert_formula(long e, long formula);
  //public static native int msat_add_preferred_for_branching(long e, long termBoolvar);
  //public static native int msat_clear_preferred_for_branching(long e)
  private static native int msat_solve(long e) throws InterruptedException;
  private static native int msat_solve_with_assumptions(long e, long[] assumptions, int numAssumptions) throws InterruptedException;
  private static native int msat_all_sat(long e, long[] important, int num_important,
      AllSatModelCallback func) throws InterruptedException;
  //private static native int msat_solve_diversify(long e, DiversifyModelCallback func, long userData)
  public static native long[] msat_get_asserted_formulas(long e);
  public static native long[] msat_get_theory_lemmas(long e);


  /*
   * Interpolation
   */
  public static native int msat_create_itp_group(long e);
  public static native void msat_set_itp_group(long e, int group);
  private static native long msat_get_interpolant(long e, int[] groups_of_a, int n);

  /*
   * Model computation
   */
  public static native long msat_get_model_value(long e, long term);
  private static native long msat_create_model_iterator(long e);
  private static native boolean msat_model_iterator_has_next(long i);
  private static native boolean msat_model_iterator_next(long i, long[] t, long[] v);
  private static native void msat_destroy_model_iterator(long i);


  /*
   * Unsat core computation
   */
  public static native long[] msat_get_unsat_assumptions(long e);
  public static native long[] msat_get_unsat_core(long e);


  /*
   * Special functions
   */
  public static native long msat_set_termination_test(long e, TerminationTest callback);
  public static native void msat_free_termination_test(long t);
  public static native String msat_get_version();
  public static native String msat_last_error_message(long e);

  /** Optimization **/

/**
 * OptiMathSAT - objectives creation
 */

  /**
   * Push on the stack the new objective 'min(term)' with optional
   * optimization local interval [lower, upper[
   *
   * @param e msat_env The environment in which to operate.
   * @param term msat_term The term to be minimized.
   * @param lower The string representing the value of an initial lower bound.
   * @param upper The string representing the value of an initial upper bound.
   */
  public static native void msat_push_minimize(long e, long term, String lower, String upper);

  /**
   * Push on the stack the new objective 'max(term)' with optional
   * optimization local interval ]local, upper]
   *
   * @param e msat_env The environment in which to operate.
   * @param term msat_term The term to be maximized.
   * @param lower The string representing the value of an initial lower bound.
   * @param upper The string representing the value of an initial upper bound.
   */
  public static native void msat_push_maximize(long e, long term, String lower,
      String upper);

  /**
   * Push on the stack the new objective 'min(max(term0), ..., max(termN))'
   * with optional optimization local interval ]lower, upper]
   *
   * @param e msat_env The environment in which to operate.
   * @param len size_t The size of terms.
   * @param terms msat_term[] The array of terms to be optimized.
   * @param lower The string representing the value of an initial lower bound.
   * @param upper The string representing the value of an initial upper bound.
   */
  public static native void msat_push_minmax(long e, int len, long[] terms,
      String lower, String upper);

  /**
   * Push on the stack the new objective 'max(min(term0), ..., min(termN))'
   * with optional optimization local interval [lower, upper[
   *
   * @param e msat_env The environment in which to operate.
   * @param len size_t The size of terms.
   * @param terms msat_term[] The array of terms to be optimized.
   * @param lower The string representing the value of an initial lower bound.
   * @param upper The string representing the value of an initial upper bound.
   */
  public static native void msat_push_maxmin(long e, int len, long[] terms,
      String lower, String upper);

  /**
   * \brief Associate a weight to a term declaration with respect to a MaxSMT
   * group identified by a common id label. Assert-soft constraints are ineffective
   * unless the id label is used by an objective that is pushed on the stack
   *
   * \param e msat_env The environment in which to operate.
   * \param term msat_term The term to which a weight is attached.
   * \param weight msat_term The weight of not satisfying this soft-clause.
   * \param upper The MaxSMT sum onto which the weight contribution is added.
   */
  public static native void msat_assert_soft_formula(long e, long term, long weight,
      String id);

/**
 * OptiMathSAT - objective stack iterator
 */

  /**
   * Creates an objective iterator
   * NOTE: an objective iterator, and any of its references, should only be
   * instantiated after a ::msat_solve call, and prior to any further
   * push/pop/assert_formula action. Otherwise, the behaviour is undefined.
   * @param e msat_env The environment in use
   * @return msat_objective_iterator an iterator for the current objectives
   */
  public static native long msat_create_objective_iterator(long e);

  /**
   * Checks whether {@code i} can be incremented
   * @param i msat_objective_iterator An objective iterator
   * @return nonzero if \a i can be incremented, zero otherwise
   */
  public static native int msat_objective_iterator_has_next(long i);

  /**
   * Returns the next objective, and increments the given iterator
   * @param i msat_objective_iterator The objective iterator to increment.
   * @param o msat_objective* Output value for the next objective in the stack.
   * @return nonzero in case of error.
   */
  public static native int msat_objective_iterator_next(long i, long[] o);

  /**
   * Destroys an objective iterator.
   * @param i msat_objective_iterator the iterator to destroy.
   */
  public static native void msat_destroy_objective_iterator(long i);

  /**
   * OptiMathSAT - functions for objective state inspection
   */

  /**
   * Returns the optimization search state of the given objective
   * @param e msat_env The environment in which to operate.
   * @param o msat_objective The objective.
   * @return msat_result ::MSAT_SAT if objective has a solution, ::MSAT_UNSAT if objective
   * is unsatisfiable, and ::MSAT_UNKNOWN if there was some error or if
   * satisfiability/optimality could not be determined.
   */
  public static native int msat_objective_result(long e, long o);

  /**
   * Returns the term which is optimized by the objective
   * @param e msat_env The environment in which to operate.
   * @param o msat_objective The objective.
   * @return msat_term representation of the objective function
   */
  public static native long msat_objective_get_term(long e, long o);

  /**
   * Returns the objective optimization type (min or max)
   * @param e msat_env The environment in which to operate.
   * @param o msat_objective The objective.
   * @return msat_objective_type ::MSAT_OBJECTIVE_MINIMIZE or ::MSAT_OBJECTIVE_MAXIMIZE
   */
  public static native long msat_objective_get_type(long e, long o);

  /**
   * Load into memory the model associated with the given objective,
   * provided that it is satisfiable.
   * @param e msat_env The environment in which to operate.
   * @param o msat_objective The objective providing the model.
   */
  public static native void msat_set_model(long e, long o);

/**
 * Returns optimization search statistics.
 * @return A string which provides some search statistics information
 *         on the optimization search of the given objective.
 *         The string must be deallocated by the user with ::msat_free().
 */
  public static native String msat_objective_get_search_stats(long e, long o);

  /**
   * Determines if the given objective value is unbounded.
   * @param e msat_env The environment in which to operate.
   * @param o msat_objective The objective providing the value.
   * @param i msat_objective_value The objective field to be tested.
   * @return 1 if unbounded, 0 if not, -1 on error.
   */
  public static native int msat_objective_value_is_unbounded(long e, long o, int i);

  /**
   * Determines if the given objective value is +INF.
   * @param e msat_env The environment in which to operate.
   * @param o msat_objective The objective providing the value.
   * @param i msat_objective_value The objective field to be tested.
   * @return 1 if +INF, 0 if not, -1 on error.
   */
  public static native int msat_objective_value_is_plus_inf(long e, long o, int i);

  /**
   * Determines if the given objective value is -INF.
   * @param e msat_env The environment in which to operate.
   * @param o msat_objective The objective providing the value.
   * @param i msat_objective_value The objective field to be tested.
   * @return 1 if -INF, 0 if not, -1 on error.
   */
  public static native int msat_objective_value_is_minus_inf(long e, long o, int i);

  /**
   * Determines if the given objective value is strict,
   *    (e.g. if term(i) = k and strict(i) = TRUE, then actual value of 'i' is k+epsilon,
   *    with epsilon being any small positive value)
   * @param e msat_env The environment in which to operate.
   * @param o msat_objective The objective providing the value.
   * @param i msat_objective_value The objective field to be tested.
   * @return 1 if strict, 0 if not, -1 on error.
   */
  public static native int msat_objective_value_is_strict(long e, long o, int i);

  /**
   * Returns term representation of the given objective value.
   * NOTE: the representation IS imprecise if objective value is strict.
   * @param e msat_env The environment in which to operate.
   * @param o msat_objective The objective providing the value.
   * @param i msat_objective_value The objective field to retrieve.
   * @return msat_term term associated to the objective value, or msat_error_term on error.
   */
  public static native long msat_objective_value_term(long e, long o, int i);

/**
 * Returns a string representation of the given objective value.
 * @param e msat_env The environment in which to operate.
 * @param o msat_objective The objective providing the value.
 * @param i msat_objective_value The objective field to retrieve.
 * @return a string representing the objective value.
 *         The string must be deallocated by the user with ::msat_free().
 */
  public static native String msat_objective_value_repr(long e, long o, int i);
}
