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
package org.sosy_lab.solver.test;

import static com.google.common.truth.Truth.*;
import static com.google.common.truth.TruthJUnit.assume;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.sosy_lab.solver.FormulaManagerFactory.Solvers;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.api.ArrayFormula;
import org.sosy_lab.solver.api.BitvectorFormula;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.FormulaType.NumeralType;
import org.sosy_lab.solver.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.solver.api.NumeralFormula.RationalFormula;
import org.sosy_lab.solver.api.UninterpretedFunctionDeclaration;
import org.sosy_lab.solver.mathsat5.Mathsat5FormulaManager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@RunWith(Parameterized.class)
public class SolverTheoriesTest extends SolverBasedTest0 {

  @Parameters(name="{0}")
  public static Object[] getAllSolvers() {
    return Solvers.values();
  }

  @Parameter(0)
  public Solvers solver;

  @Override
  protected Solvers solverToUse() {
    return solver;
  }

  @Test
  public void intTest1() throws Exception {
    IntegerFormula a = imgr.makeVariable("int_a");
    IntegerFormula num = imgr.makeNumber(2);

    BooleanFormula f = imgr.equal(imgr.add(a, a), num);
    assert_().about(BooleanFormula()).that(f).isSatisfiable();
  }

  @Test
  public void intTest2() throws Exception {
    IntegerFormula a = imgr.makeVariable("int_b");
    IntegerFormula num = imgr.makeNumber(1);

    BooleanFormula f = imgr.equal(imgr.add(a, a), num);
    assert_().about(BooleanFormula()).that(f).isUnsatisfiable();
  }

  @Test
  public void intTest3_DivMod() throws Exception {
    assume().withFailureMessage("Solver " + solverToUse() + " does not support the operations MOD and DIV")
        .that(solver == Solvers.MATHSAT5 ).isFalse();

    IntegerFormula a = imgr.makeVariable("int_a");
    IntegerFormula b = imgr.makeVariable("int_b");

    IntegerFormula num10 = imgr.makeNumber(10);
    IntegerFormula num5 = imgr.makeNumber(5);
    IntegerFormula num3 = imgr.makeNumber(3);
    IntegerFormula num2 = imgr.makeNumber(2);
    IntegerFormula num1 = imgr.makeNumber(1);
    IntegerFormula num0 = imgr.makeNumber(0);

    BooleanFormula fa = imgr.equal(a, num10);
    BooleanFormula fb = imgr.equal(b, num2);
    BooleanFormula fADiv5 = imgr.equal(imgr.divide(a, num5), b);
    BooleanFormula fADiv3 = imgr.equal(imgr.divide(a, num3), num3);
    BooleanFormula fADivB = imgr.equal(imgr.divide(a, b), num5);
    BooleanFormula fAMod5 = imgr.equal(imgr.modulo(a, num5), num0);
    BooleanFormula fAMod3 = imgr.equal(imgr.modulo(a, num3), num1);

    // check division-by-constant, a=10 && b=2 && a/5=b
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADiv5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, bmgr.not(fADiv5)))).isUnsatisfiable();

    // check division-by-constant, a=10 && a/3=3
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADiv3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, bmgr.not(fADiv3)))).isUnsatisfiable();

    // check division-by-variable, a=10 && b=2 && a/b=5
    // TODO not all solvers support division-by-variable, we guarantee soundness by allowing any value, that yields SAT.
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADivB))).isSatisfiable();
    if (solver != Solvers.SMTINTERPOL) {
      assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,bmgr.not(fADivB)))).isUnsatisfiable();
    }

    // check modulo-by-constant, a=10 && a%5=0
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fAMod5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, bmgr.not(fAMod5)))).isUnsatisfiable();

    // check modulo-by-constant, a=10 && a%3=1
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fAMod3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, bmgr.not(fAMod3)))).isUnsatisfiable();
  }

  @Test
  public void intTest3_DivMod_NegativeNumbers() throws Exception {
    assume().withFailureMessage("Solver " + solverToUse() + " does not support the operations MOD and DIV")
        .that(solver == Solvers.MATHSAT5 ).isFalse();

    IntegerFormula a = imgr.makeVariable("int_a");
    IntegerFormula b = imgr.makeVariable("int_b");

    IntegerFormula numNeg10 = imgr.makeNumber(-10);
    IntegerFormula num5 = imgr.makeNumber(5);
    IntegerFormula num4 = imgr.makeNumber(4);
    IntegerFormula numNeg4 = imgr.makeNumber(-4);
    IntegerFormula num3 = imgr.makeNumber(3);
    IntegerFormula numNeg3 = imgr.makeNumber(-3);
    IntegerFormula numNeg2 = imgr.makeNumber(-2);
    IntegerFormula num2 = imgr.makeNumber(2);
    IntegerFormula num0 = imgr.makeNumber(0);

    BooleanFormula fa = imgr.equal(a, numNeg10);
    BooleanFormula fb = imgr.equal(b, numNeg2);
    BooleanFormula fADiv5 = imgr.equal(imgr.divide(a, num5), b);
    BooleanFormula fADiv3 = imgr.equal(imgr.divide(a, num3), numNeg4);
    BooleanFormula fADivNeg3 = imgr.equal(imgr.divide(a, numNeg3), num4);
    BooleanFormula fADivB = imgr.equal(imgr.divide(a, b), num5);
    BooleanFormula fAMod5 = imgr.equal(imgr.modulo(a, num5), num0);
    BooleanFormula fAMod3 = imgr.equal(imgr.modulo(a, num3), num2);
    BooleanFormula fAModNeg3 = imgr.equal(imgr.modulo(a, numNeg3), num2);

    // integer-division for negative numbers is __not__ C99-conform!
    // SMTlib always rounds against +/- infinity.

    // check division-by-constant, a=-10 && b=-2 && a/5=b
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADiv5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, bmgr.not(fADiv5)))).isUnsatisfiable();

    // check division-by-constant, a=-10 && a/3=-4
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADiv3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, bmgr.not(fADiv3)))).isUnsatisfiable();

    // check division-by-constant, a=-10 && a/(-3)=4
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADivNeg3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, bmgr.not(fADivNeg3)))).isUnsatisfiable();

    // check division-by-variable, a=-10 && b=-2 && a/b=5
    // TODO not all solvers support division-by-variable, we guarantee soundness by allowing any value, that yields SAT.
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADivB))).isSatisfiable();
    if (solver != Solvers.SMTINTERPOL) {
      assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,bmgr.not(fADivB)))).isUnsatisfiable();
    }

    // check modulo-by-constant, a=-10 && a%5=0
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fAMod5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, bmgr.not(fAMod5)))).isUnsatisfiable();

    // check modulo-by-constant, a=-10 && a%3=2
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fAMod3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, bmgr.not(fAMod3)))).isUnsatisfiable();

    // check modulo-by-constant, a=-10 && a%(-3)=2
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fAModNeg3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, bmgr.not(fAModNeg3)))).isUnsatisfiable();
  }

  @Test
  public void intTestBV_DivMod() throws Exception {
    requireBitvectors();

    BitvectorFormula a = bvmgr.makeVariable(32, "int_a");
    BitvectorFormula b = bvmgr.makeVariable(32, "int_b");

    BitvectorFormula num10 = bvmgr.makeBitvector(32, 10);
    BitvectorFormula num5 = bvmgr.makeBitvector(32, 5);
    BitvectorFormula num3 = bvmgr.makeBitvector(32, 3);
    BitvectorFormula num2 = bvmgr.makeBitvector(32, 2);
    BitvectorFormula num1 = bvmgr.makeBitvector(32, 1);
    BitvectorFormula num0 = bvmgr.makeBitvector(32, 0);

    BooleanFormula fa = bvmgr.equal(a, num10);
    BooleanFormula fb = bvmgr.equal(b, num2);
    BooleanFormula fADiv5 = bvmgr.equal(bvmgr.divide(a, num5, true), b);
    BooleanFormula fADiv3 = bvmgr.equal(bvmgr.divide(a, num3, true), num3);
    BooleanFormula fADivB = bvmgr.equal(bvmgr.divide(a, b, true), num5);
    BooleanFormula fAMod5 = bvmgr.equal(bvmgr.modulo(a, num5, true), num0);
    BooleanFormula fAMod3 = bvmgr.equal(bvmgr.modulo(a, num3, true), num1);

    // check division-by-constant, a=10 && b=2 && a/5=b
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADiv5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, bmgr.not(fADiv5)))).isUnsatisfiable();

    // check division-by-constant, a=10 && a/3=3
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADiv3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, bmgr.not(fADiv3)))).isUnsatisfiable();

    // check division-by-variable, a=10 && b=2 && a/b=5
    // TODO not all solvers support division-by-variable, we guarantee soundness by allowing any value, that yields SAT.
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADivB))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,bmgr.not(fADivB)))).isUnsatisfiable();

    // check modulo-by-constant, a=10 && a%5=0
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fAMod5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, bmgr.not(fAMod5)))).isUnsatisfiable();

    // check modulo-by-constant, a=10 && a%3=1
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fAMod3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, bmgr.not(fAMod3)))).isUnsatisfiable();
  }

  @Test
  public void intTestBV_DivMod_NegativeNumbers() throws Exception {
    requireBitvectors();

    BitvectorFormula a = bvmgr.makeVariable(32, "int_a");
    BitvectorFormula b = bvmgr.makeVariable(32, "int_b");

    BitvectorFormula numNeg10 = bvmgr.makeBitvector(32, -10);
    BitvectorFormula num5 = bvmgr.makeBitvector(32, 5);
    BitvectorFormula num3 = bvmgr.makeBitvector(32, 3);
    BitvectorFormula numNeg3 = bvmgr.makeBitvector(32, -3);
    BitvectorFormula numNeg2 = bvmgr.makeBitvector(32, -2);
    BitvectorFormula numNeg1 = bvmgr.makeBitvector(32, -1);
    BitvectorFormula num0 = bvmgr.makeBitvector(32, 0);

    BooleanFormula fa = bvmgr.equal(a, numNeg10);
    BooleanFormula fb = bvmgr.equal(b, numNeg2);
    BooleanFormula fADiv5 = bvmgr.equal(bvmgr.divide(a, num5, true), b);
    BooleanFormula fADiv3 = bvmgr.equal(bvmgr.divide(a, num3, true), numNeg3);
    BooleanFormula fADivNeg3 = bvmgr.equal(bvmgr.divide(a, numNeg3, true), num3);
    BooleanFormula fADivB = bvmgr.equal(bvmgr.divide(a, b, true), num5);
    BooleanFormula fAMod5 = bvmgr.equal(bvmgr.modulo(a, num5, true), num0);
    BooleanFormula fAMod3 = bvmgr.equal(bvmgr.modulo(a, num3, true), numNeg1);
    BooleanFormula fAModNeg3 = bvmgr.equal(bvmgr.modulo(a, numNeg3, true), numNeg1);

    // bitvector-division for negative numbers is C99-conform!

    // check division-by-constant, a=-10 && b=-2 && a/5=b
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADiv5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, bmgr.not(fADiv5)))).isUnsatisfiable();

    // check division-by-constant, a=-10 && a/3=-3
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADiv3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, bmgr.not(fADiv3)))).isUnsatisfiable();

    // check division-by-constant, a=-10 && a/(-3)=3
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADivNeg3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, bmgr.not(fADivNeg3)))).isUnsatisfiable();

    // check division-by-variable, a=-10 && b=-2 && a/b=5
    // TODO not all solvers support division-by-variable, we guarantee soundness by allowing any value, that yields SAT.
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fb, fADivB))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,bmgr.not(fADivB)))).isUnsatisfiable();

    // check modulo-by-constant, a=-10 && a%5=0
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fAMod5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, bmgr.not(fAMod5)))).isUnsatisfiable();

    // check modulo-by-constant, a=-10 && a%3=-1
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fAMod3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, bmgr.not(fAMod3)))).isUnsatisfiable();

    // check modulo-by-constant, a=-10 && a%(-3)=-1
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, fAModNeg3))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa, bmgr.not(fAModNeg3)))).isUnsatisfiable();
  }

  @Test
  public void intTest4_ModularCongruence() throws Exception {
    IntegerFormula a = imgr.makeVariable("int_a");
    IntegerFormula b = imgr.makeVariable("int_b");
    IntegerFormula c = imgr.makeVariable("int_c");
    IntegerFormula d = imgr.makeVariable("int_d");
    IntegerFormula num10 = imgr.makeNumber(10);
    IntegerFormula num5 = imgr.makeNumber(5);
    IntegerFormula num0 = imgr.makeNumber(0);
    IntegerFormula numNeg5 = imgr.makeNumber(-5);

    BooleanFormula fa = imgr.equal(a, num10);
    BooleanFormula fb = imgr.equal(b, num5);
    BooleanFormula fc = imgr.equal(c, num0);
    BooleanFormula fd = imgr.equal(d, numNeg5);

    // we have equal results modulo 5
    BooleanFormula fConb5 = imgr.modularCongruence(a, b, 5);
    BooleanFormula fConc5 = imgr.modularCongruence(a, c, 5);
    BooleanFormula fCond5 = imgr.modularCongruence(a, d, 5);

    // we have different results modulo 7
    BooleanFormula fConb7 = imgr.modularCongruence(a, b, 7);
    BooleanFormula fConc7 = imgr.modularCongruence(a, c, 7);
    BooleanFormula fCond7 = imgr.modularCongruence(a, d, 7);

    // check modular congruence, a=10 && b=5 && (a mod 5 = b mod 5)
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,fConb5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,bmgr.not(fConb5)))).isUnsatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fc,fConc5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fc,bmgr.not(fConc5)))).isUnsatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fd,fCond5))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fd,bmgr.not(fCond5)))).isUnsatisfiable();

    // check modular congruence, a=10 && b=5 && (a mod 7 != b mod 7)
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,fConb7))).isUnsatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,bmgr.not(fConb7)))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fc,fConc7))).isUnsatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fc,bmgr.not(fConc7)))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fd,fCond7))).isUnsatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fd,bmgr.not(fCond7)))).isSatisfiable();
  }

  @Test
  public void intTest4_ModularCongruence_NegativeNumbers() throws Exception {
    IntegerFormula a = imgr.makeVariable("int_a");
    IntegerFormula b = imgr.makeVariable("int_b");
    IntegerFormula c = imgr.makeVariable("int_c");
    IntegerFormula num8 = imgr.makeNumber(8);
    IntegerFormula num3 = imgr.makeNumber(3);
    IntegerFormula numNeg2 = imgr.makeNumber(-2);

    BooleanFormula fa = imgr.equal(a, num8);
    BooleanFormula fb = imgr.equal(b, num3);
    BooleanFormula fc = imgr.equal(c, numNeg2);
    BooleanFormula fConb = imgr.modularCongruence(a, b, 5);
    BooleanFormula fConc = imgr.modularCongruence(a, c, 5);

    // check modular congruence, a=10 && b=5 && (a mod 5 = b mod 5)
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,fConb))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,bmgr.not(fConb)))).isUnsatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fc,fConc))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fc,bmgr.not(fConc)))).isUnsatisfiable();
  }

  @Test
  public void intTestBV_ModularCongruence() throws Exception {
    requireBitvectors();

    BitvectorFormula a = bvmgr.makeVariable(32, "int_a");
    BitvectorFormula b = bvmgr.makeVariable(32, "int_b");
    BitvectorFormula c = bvmgr.makeVariable(32, "int_c");
    BitvectorFormula d = bvmgr.makeVariable(32, "int_d");
    BitvectorFormula num10 = bvmgr.makeBitvector(32, 10);
    BitvectorFormula num5 = bvmgr.makeBitvector(32, 5);
    BitvectorFormula num0 = bvmgr.makeBitvector(32, 0);
    BitvectorFormula numNeg5 = bvmgr.makeBitvector(32, -5);

    BooleanFormula fa = bvmgr.equal(a, num10);
    BooleanFormula fb = bvmgr.equal(b, num5);
    BooleanFormula fc = bvmgr.equal(c, num0);
    BooleanFormula fd = bvmgr.equal(d, numNeg5);
    BooleanFormula fConb = bvmgr.modularCongruence(a, b, 5);
    BooleanFormula fConc = bvmgr.modularCongruence(a, c, 5);
    BooleanFormula fCond = bvmgr.modularCongruence(a, d, 5);

    // check modular congruence, a=10 && b=5 && (a mod 5 = b mod 5)
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,fConb))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,bmgr.not(fConb)))).isUnsatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fc,fConc))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fc,bmgr.not(fConc)))).isUnsatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fd,fCond))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fd,bmgr.not(fCond)))).isUnsatisfiable();
  }

  @Test
  public void intTestBV_ModularCongruence_NegativeNumbers() throws Exception {
    requireBitvectors();

    BitvectorFormula a = bvmgr.makeVariable(32, "int_a");
    BitvectorFormula b = bvmgr.makeVariable(32, "int_b");
    BitvectorFormula c = bvmgr.makeVariable(32, "int_c");
    BitvectorFormula num8 = bvmgr.makeBitvector(32, 8);
    BitvectorFormula num3 = bvmgr.makeBitvector(32, 3);
    BitvectorFormula numNeg2 = bvmgr.makeBitvector(32, -2);

    BooleanFormula fa = bvmgr.equal(a, num8);
    BooleanFormula fb = bvmgr.equal(b, num3);
    BooleanFormula fc = bvmgr.equal(c, numNeg2);
    BooleanFormula fConb = bvmgr.modularCongruence(a, b, 5);
    BooleanFormula fConc = bvmgr.modularCongruence(a, c, 5);

    // check modular congruence, a=10 && b=5 && (a mod 5 = b mod 5)
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,fConb))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fb,bmgr.not(fConb)))).isUnsatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fc,fConc))).isSatisfiable();
    assert_().about(BooleanFormula()).that(bmgr.and(Lists.newArrayList(fa,fc,bmgr.not(fConc)))).isUnsatisfiable();
  }

  @Test
  public void realTest() throws Exception {
    requireRationals();

    RationalFormula a = rmgr.makeVariable("int_c");
    RationalFormula num = rmgr.makeNumber(1);

    BooleanFormula f = rmgr.equal(rmgr.add(a, a), num);
    assert_().about(BooleanFormula()).that(f).isSatisfiable();
  }

  @Test
  public void test_BitvectorIsZeroAfterShiftLeft() throws Exception {
    requireBitvectors();

    BitvectorFormula one = bvmgr.makeBitvector(32, 1);

    // unsigned char
    BitvectorFormula a = bvmgr.makeVariable(8, "char_a");
    BitvectorFormula b = bvmgr.makeVariable(8, "char_b");
    BitvectorFormula rightOp = bvmgr.makeBitvector(32, 7);

    // 'cast' a to unsigned int
    a = bvmgr.extend(a, 32 - 8, false);
    b = bvmgr.extend(b, 32 - 8, false);
    a = bvmgr.or(a, one);
    b = bvmgr.or(b, one);
    a = bvmgr.extract(a, 7, 0, true);
    b = bvmgr.extract(b, 7, 0, true);
    a = bvmgr.extend(a, 32 - 8, false);
    b = bvmgr.extend(b, 32 - 8, false);

    a = bvmgr.shiftLeft(a, rightOp);
    b = bvmgr.shiftLeft(b, rightOp);
    a = bvmgr.extract(a, 7, 0, true);
    b = bvmgr.extract(b, 7, 0, true);
    BooleanFormula f = bmgr.not(bvmgr.equal(a, b));

    assert_().about(BooleanFormula()).that(f).isUnsatisfiable();
  }

  @Test
  public void testUfWithBoolType() throws SolverException, InterruptedException {
    UninterpretedFunctionDeclaration<BooleanFormula> uf = fmgr.declareUninterpretedFunction("fun_ib", FormulaType.BooleanType, FormulaType.IntegerType);
    BooleanFormula uf0 = fmgr.callUninterpretedFunction(uf, ImmutableList.of(imgr.makeNumber(0)));
    BooleanFormula uf1 = fmgr.callUninterpretedFunction(uf, ImmutableList.of(imgr.makeNumber(1)));
    BooleanFormula uf2 = fmgr.callUninterpretedFunction(uf, ImmutableList.of(imgr.makeNumber(2)));

    BooleanFormula f01 = bmgr.xor(uf0, uf1);
    BooleanFormula f02 = bmgr.xor(uf0, uf2);
    BooleanFormula f12 = bmgr.xor(uf1, uf2);
    assert_().about(BooleanFormula()).that(f01).isSatisfiable();
    assert_().about(BooleanFormula()).that(f02).isSatisfiable();
    assert_().about(BooleanFormula()).that(f12).isSatisfiable();

    BooleanFormula f = bmgr.and(ImmutableList.of(f01, f02, f12));
    assert_().about(BooleanFormula()).that(f).isUnsatisfiable();
  }

  @Test @Ignore
  public void testUfWithBoolArg() throws SolverException, InterruptedException {
    // Not all SMT solvers support UFs with boolean arguments.
    // We can simulate this with "uf(ite(p,0,1))", but currently we do not need this.
    // Thus this test is disabled and the following is enabled.

    UninterpretedFunctionDeclaration<IntegerFormula> uf = fmgr.declareUninterpretedFunction("fun_bi", FormulaType.IntegerType, FormulaType.BooleanType);
    IntegerFormula ufTrue = fmgr.callUninterpretedFunction(uf, ImmutableList.of(bmgr.makeBoolean(true)));
    IntegerFormula ufFalse = fmgr.callUninterpretedFunction(uf, ImmutableList.of(bmgr.makeBoolean(false)));

    BooleanFormula f = bmgr.not(imgr.equal(ufTrue, ufFalse));
    assertThat(f.toString()).isEmpty();
    assert_().about(BooleanFormula()).that(f).isSatisfiable();
  }

  @Test(expected=IllegalArgumentException.class)
  public void testUfWithBoolArg_unsupported() throws SolverException, InterruptedException {
    fmgr.declareUninterpretedFunction("fun_bi", FormulaType.IntegerType, FormulaType.BooleanType);
  }


  @Test
  public void quantifierEliminationTest1() throws Exception {
    requireQuantifiers();

    IntegerFormula var_B = imgr.makeVariable("b");
    IntegerFormula var_C = imgr.makeVariable("c");
    IntegerFormula num_2 = imgr.makeNumber(2);
    IntegerFormula num_1000 = imgr.makeNumber(1000);
    BooleanFormula eq_c_2 = imgr.equal(var_C, num_2);
    IntegerFormula minus_b_c = imgr.subtract(var_B, var_C);
    BooleanFormula gt_bMinusC_1000 = imgr.greaterThan(minus_b_c, num_1000);
    BooleanFormula and_cEq2_bMinusCgt1000 = bmgr.and(eq_c_2, gt_bMinusC_1000);

    BooleanFormula f = qmgr.exists(Lists.<Formula>newArrayList(var_C), and_cEq2_bMinusCgt1000);
    BooleanFormula result = qmgr.eliminateQuantifiers(f);
    assertThat(result.toString()).doesNotContain("exists");
    assertThat(result.toString()).doesNotContain("c");

    BooleanFormula expected = imgr.greaterOrEquals(var_B, imgr.makeNumber(1003));
    assert_().about(BooleanFormula()).that(result).isEquivalentTo(expected);
  }

  @Test @Ignore
  public void quantifierEliminationTest2() throws Exception {
    requireQuantifiers();

    IntegerFormula i1 = imgr.makeVariable("i@1");
    IntegerFormula j1 = imgr.makeVariable("j@1");
    IntegerFormula j2 = imgr.makeVariable("j@2");
    IntegerFormula a1 = imgr.makeVariable("a@1");

    IntegerFormula _1 = imgr.makeNumber(1);
    IntegerFormula _minus1 = imgr.makeNumber(-1);

    IntegerFormula _1_plus_a1 = imgr.add(_1, a1);
    BooleanFormula not_j1_eq_minus1 = bmgr.not(imgr.equal(j1, _minus1));
    BooleanFormula i1_eq_1_plus_a1 = imgr.equal(i1, _1_plus_a1);

    IntegerFormula j2_plus_a1 = imgr.add(j2, a1);
    BooleanFormula j1_eq_j2_plus_a1 = imgr.equal(j1, j2_plus_a1);

    BooleanFormula fm = bmgr.and(Lists.newArrayList(
            i1_eq_1_plus_a1,
            not_j1_eq_minus1,
            j1_eq_j2_plus_a1));

    BooleanFormula q = qmgr.exists(Lists.<Formula>newArrayList(j1), fm);
    BooleanFormula result = qmgr.eliminateQuantifiers(q);
    assertThat(result.toString()).doesNotContain("exists");
    assertThat(result.toString()).doesNotContain("j@1");

    BooleanFormula expected = bmgr.not(imgr.equal(i1, j2));
    assert_().about(BooleanFormula()).that(result).isEquivalentTo(expected);
  }

  @Test
  public void testGetFormulaType() {
    BooleanFormula _boolVar = bmgr.makeVariable("boolVar");
    assertThat(mgr.getFormulaType(_boolVar)).isEqualTo(FormulaType.BooleanType);

    IntegerFormula _intVar = imgr.makeNumber(1);
    assertThat(mgr.getFormulaType(_intVar)).isEqualTo(FormulaType.IntegerType);

    requireArrays();
    ArrayFormula<IntegerFormula, IntegerFormula> _arrayVar = amgr.makeArray("b", NumeralType.IntegerType, NumeralType.IntegerType);
    assertThat(mgr.getFormulaType(_arrayVar))
      .isInstanceOf(FormulaType.ArrayFormulaType.class);
  }

  @Test
  public void testMakeIntArray() {
    requireArrays();

    IntegerFormula _i = imgr.makeVariable("i");
    IntegerFormula _1 = imgr.makeNumber(1);
    IntegerFormula _i_plus_1 = imgr.add(_i, _1);

    ArrayFormula<IntegerFormula, IntegerFormula> _b = amgr.makeArray("b", NumeralType.IntegerType, NumeralType.IntegerType);
    IntegerFormula _b_at_i_plus_1 = amgr.select(_b, _i_plus_1);

    if (solver == Solvers.MATHSAT5) {
      // Mathsat5 has a different internal representation of the formula
      assertThat(_b_at_i_plus_1.toString()).isEqualTo(
          "(`read_int_int` b (`+_int` i 1))");
    } else {
      assertThat(_b_at_i_plus_1.toString()).isEqualTo(
          "(select b (+ i 1))"); // Compatibility to all solvers not guaranteed
    }
  }

  @Test
  public void testMakeBitVectorArray() {
    requireArrays();
    requireBitvectors();

    BitvectorFormula _i = mgr.getBitvectorFormulaManager().makeVariable(64, "i");
    ArrayFormula<BitvectorFormula, BitvectorFormula> _b = amgr.makeArray("b", FormulaType.getBitvectorTypeWithSize(64), FormulaType.getBitvectorTypeWithSize(32));
    BitvectorFormula _b_at_i = amgr.select(_b, _i);

    if (solver == Solvers.MATHSAT5) {
      // Mathsat5 has a different internal representation of the formula
      assertThat(_b_at_i.toString()).isEqualTo(
         "(`read_<BitVec, 64, >_<BitVec, 32, >` b i)");
    } else {
      assertThat(_b_at_i.toString()).isEqualTo(
          "(select b i)"); // Compatibility to all solvers not guaranteed
    }
  }
}
