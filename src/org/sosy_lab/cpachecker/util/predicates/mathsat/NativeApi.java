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
/*
 * This file was automatically generated by extract_java_stub.py
 * Wrapper for the MathSAT API for Java
 */
package org.sosy_lab.cpachecker.util.predicates.mathsat;

class NativeApi {
    static {
        System.loadLibrary("mathsatj");
    }

    // msat_type
    public static final int MSAT_BOOL = 0;
    public static final int MSAT_U = 1;
    public static final int MSAT_INT = 2;
    public static final int MSAT_REAL = 3;
    public static final int MSAT_BV = 4;

    // msat_theory
    public static final int MSAT_UF = 0;
    public static final int MSAT_IDL = 1;
    public static final int MSAT_RDL = 2;
    public static final int MSAT_LIA = 3;
    public static final int MSAT_LRA = 4;
    public static final int MSAT_WORD = 5;

    // msat_theory_combination
    public static final int MSAT_COMB_NONE = 0;
    public static final int MSAT_COMB_ACK = 1;
    public static final int MSAT_COMB_DTC = 2;

    // msat_result
    public static final int MSAT_UNKNOWN = -1;
    public static final int MSAT_UNSAT = 0;
    public static final int MSAT_SAT = 1;

    // msat_truth_value
    public static final int MSAT_UNDEF = -1;
    public static final int MSAT_FALSE = 0;
    public static final int MSAT_TRUE = 1;

    interface AllSatModelCallback {
      void callback(long[] model);
    }

    // error checking functions
    public static boolean MSAT_ERROR_TERM(long t) { return t == 0; }
    public static boolean MSAT_ERROR_DECL(long d) { return d == 0; }
    public static boolean MSAT_ERROR_MODEL_ITERATOR(long i) { return i == 0; }
    public static long MSAT_MAKE_ERROR_TERM() { return 0; }

    // wrappers for some of the native methods with a different number
    // of arguments
    public static long msat_declare_uif(long e, String name, int out_type,
                                       int[] args_types) {
      return msat_declare_uif(e, name, out_type, args_types.length, args_types);
    }

    public static int msat_all_sat(long e, long[] important,
                                   AllSatModelCallback func) {
        return msat_all_sat(e, important, important.length, func, 0);
    }

    // returns a pair (t, v), or null in case of errors
    public static long[] msat_model_iterator_next(long i) {
       long[] t = new long[1];
       long[] v = new long[1];
       int s = msat_model_iterator_next(i, t, v);
       if (s == -1) {
        return null;
      }
       return new long[]{ t[0], v[0] };
    }

    public static long[] msat_get_theory_lemmas(long e) {
        return msat_get_theory_lemmas(e, 0);
    }

    public static int msat_tcc_all_sat(long e, long[] important,
                                       AllSatModelCallback func) {
        return msat_tcc_all_sat(e, important, important.length, func, 0);
    }

    public static long msat_get_interpolant(long e, int[] groups_of_a) {
        return msat_get_interpolant(e, groups_of_a, groups_of_a.length);
    }

    public static native void msat_set_verbosity(int level);
    public static native long msat_create_env();
    public static native long msat_create_shared_env(long sibling);
    public static native void msat_reset_env(long e);
    public static native void msat_destroy_env(long e);
    public static native int msat_set_option(long e, String option, String value);
    public static native long msat_declare_variable(long e, String name, int type);
    private static native long msat_declare_uif(long e, String name, int out_type, int num_args, int[] args_types);
    public static native long msat_make_true(long e);
    public static native long msat_make_false(long e);
    public static native long msat_make_iff(long e, long t1, long t2);
    public static native long msat_make_implies(long e, long t1, long t2);
    public static native long msat_make_or(long e, long t1, long t2);
    public static native long msat_make_xor(long e, long t1, long t2);
    public static native long msat_make_and(long e, long t1, long t2);
    public static native long msat_make_not(long e, long t1);
    public static native long msat_make_equal(long e, long t1, long t2);
    public static native long msat_make_lt(long e, long t1, long t2);
    public static native long msat_make_gt(long e, long t1, long t2);
    public static native long msat_make_leq(long e, long t1, long t2);
    public static native long msat_make_geq(long e, long t1, long t2);
    public static native long msat_make_plus(long e, long t1, long t2);
    public static native long msat_make_minus(long e, long t1, long t2);
    public static native long msat_make_times(long e, long t1, long t2);
    public static native long msat_make_negate(long e, long t);
    public static native long msat_make_number(long e, String num_rep);
    public static native long msat_make_ite(long e, long c, long tt, long te);
    public static native long msat_make_variable(long e, long var);
    public static native long msat_make_uif(long e, long func, long[] args);
    public static native long msat_make_bv_concat(long e, long t1, long t2);
    public static native long msat_make_bv_select(long e, long t, int msb, int lsb);
    public static native long msat_make_bv_or(long e, long t1, long t2);
    public static native long msat_make_bv_xor(long e, long t1, long t2);
    public static native long msat_make_bv_and(long e, long t1, long t2);
    public static native long msat_make_bv_not(long e, long t);
    public static native long msat_make_bv_lsl(long e, long t1, long t2);
    public static native long msat_make_bv_lsr(long e, long t1, long t2);
    public static native long msat_make_bv_asr(long e, long t1, long t2);
    public static native long msat_make_bv_zext(long e, long t, int width);
    public static native long msat_make_bv_sext(long e, long t, int width);
    public static native long msat_make_bv_plus(long e, long t1, long t2);
    public static native long msat_make_bv_minus(long e, long t1, long t2);
    public static native long msat_make_bv_times(long e, long t1, long t2);
    public static native long msat_make_bv_udiv(long e, long t1, long t2);
    public static native long msat_make_bv_urem(long e, long t1, long t2);
    public static native long msat_make_bv_sdiv(long e, long t1, long t2);
    public static native long msat_make_bv_srem(long e, long t1, long t2);
    public static native long msat_make_bv_smod(long e, long t1, long t2);
    public static native long msat_make_bv_ult(long e, long t1, long t2);
    public static native long msat_make_bv_uleq(long e, long t1, long t2);
    public static native long msat_make_bv_ugt(long e, long t1, long t2);
    public static native long msat_make_bv_ugeq(long e, long t1, long t2);
    public static native long msat_make_bv_slt(long e, long t1, long t2);
    public static native long msat_make_bv_sleq(long e, long t1, long t2);
    public static native long msat_make_bv_sgt(long e, long t1, long t2);
    public static native long msat_make_bv_sgeq(long e, long t1, long t2);
    public static native long msat_from_string(long e, String repr);
    public static native long msat_from_string_and_name(long e, String repr, String name);
    public static native long msat_make_copy_from(long e, long t, long src);
    public static native long msat_replace_args(long e, long t, long[] newargs);
    public static native long msat_from_msat(long e, String data);
    public static native long msat_from_smt(long e, String data);
    public static native long msat_from_foci(long e, String data);
    public static native String msat_to_msat(long e, long term);
    public static native String msat_to_smtlib(long e, long term);
    public static native int msat_term_id(long t);
    public static native int msat_term_arity(long t);
    public static native long msat_term_get_arg(long t, int n);
    public static native int msat_term_get_type(long t);
    public static native int msat_term_is_true(long t);
    public static native int msat_term_is_false(long t);
    public static native int msat_term_is_boolean_var(long t);
    public static native int msat_term_is_atom(long t);
    public static native int msat_term_is_number(long t);
    public static native int msat_term_is_and(long t);
    public static native int msat_term_is_or(long t);
    public static native int msat_term_is_not(long t);
    public static native int msat_term_is_iff(long t);
    public static native int msat_term_is_implies(long t);
    public static native int msat_term_is_xor(long t);
    public static native int msat_term_is_bool_ite(long t);
    public static native int msat_term_is_term_ite(long t);
    public static native int msat_term_is_variable(long t);
    public static native int msat_term_is_uif(long t);
    public static native int msat_term_is_equal(long t);
    public static native int msat_term_is_lt(long t);
    public static native int msat_term_is_leq(long t);
    public static native int msat_term_is_gt(long t);
    public static native int msat_term_is_geq(long t);
    public static native int msat_term_is_plus(long t);
    public static native int msat_term_is_minus(long t);
    public static native int msat_term_is_times(long t);
    public static native int msat_term_is_negate(long t);
    public static native int msat_term_is_bv_concat(long t);
    public static native int msat_term_is_bv_select(long t);
    public static native int msat_term_is_bv_or(long t);
    public static native int msat_term_is_bv_xor(long t);
    public static native int msat_term_is_bv_and(long t);
    public static native int msat_term_is_bv_not(long t);
    public static native int msat_term_is_bv_lsl(long t);
    public static native int msat_term_is_bv_lsr(long t);
    public static native int msat_term_is_bv_asr(long t);
    public static native int msat_term_is_bv_zext(long t);
    public static native int msat_term_is_bv_sext(long t);
    public static native int msat_term_is_bv_plus(long t);
    public static native int msat_term_is_bv_minus(long t);
    public static native int msat_term_is_bv_times(long t);
    public static native int msat_term_is_bv_udiv(long t);
    public static native int msat_term_is_bv_urem(long t);
    public static native int msat_term_is_bv_sdiv(long t);
    public static native int msat_term_is_bv_srem(long t);
    public static native int msat_term_is_bv_smod(long t);
    public static native int msat_term_is_bv_ult(long t);
    public static native int msat_term_is_bv_uleq(long t);
    public static native int msat_term_is_bv_ugt(long t);
    public static native int msat_term_is_bv_ugeq(long t);
    public static native int msat_term_is_bv_slt(long t);
    public static native int msat_term_is_bv_sleq(long t);
    public static native int msat_term_is_bv_sgt(long t);
    public static native int msat_term_is_bv_sgeq(long t);
    public static native long msat_term_get_decl(long t);
    public static native int msat_decl_get_return_type(long d);
    public static native int msat_decl_get_arity(long d);
    public static native int msat_decl_get_arg_type(long d, int n);
    public static native String msat_decl_get_name(long d);
    public static native String msat_term_get_name(long e, long t);
    public static native String msat_term_repr(long t);
    public static native int msat_add_theory(long e, int t);
    public static native int msat_set_theory_combination(long e, int which);
    public static native int msat_push_backtrack_point(long e);
    public static native int msat_pop_backtrack_point(long e);
    public static native int msat_assert_formula(long e, long formula);
    public static native int msat_solve(long e);
    private static native int msat_all_sat(long e, long[] important, int num_important, AllSatModelCallback func, int user_data);
    public static native long msat_get_model_value(long e, long term);
    public static native long msat_create_model_iterator(long e);
    public static native int msat_model_iterator_has_next(long i);
    private static native int msat_model_iterator_next(long i, long[] t, long[] v);
    public static native void msat_destroy_model_iterator(long i);
    private static native long[] msat_get_theory_lemmas(long e, int out);
    public static native long msat_get_unsat_core(long e);
    public static native long msat_create_tcc_env(long e, int non_chrono_backtracking);
    public static native void msat_destroy_tcc_env(long e);
    public static native int msat_tcc_add_theory(long e, int t);
    public static native int msat_tcc_add_constraint(long e, long atom);
    public static native int msat_tcc_assert_formula(long e, long formula);
    public static native void msat_tcc_assume(long e, long atom, int positive);
    public static native void msat_tcc_undo(long e, long atom);
    public static native void msat_tcc_undo_all(long e);
    public static native int msat_tcc_check(long e, int approx, long[] to_undo);
    public static native int msat_tcc_get_value(long e, long atom);
    public static native int msat_tcc_get_reason(long e, long atom, long[] reason);
    public static native int msat_tcc_has_implied(long e);
    public static native int msat_tcc_get_implied(long e, long[] implied);
    public static native int msat_tcc_solve(long e, long[] to_undo);
    private static native int msat_tcc_all_sat(long e, long[] important, int num_important, AllSatModelCallback func, int user_data);
    public static native int msat_init_interpolation(long e);
    public static native int msat_create_itp_group(long e);
    public static native int msat_set_itp_group(long e, int group);
    private static native long msat_get_interpolant(long e, int[] groups_of_a, int n);

}
