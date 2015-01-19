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
package org.sosy_lab.cpachecker.util.precondition.segkro.rules.tests;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;
import org.sosy_lab.cpachecker.exceptions.SolverException;
import org.sosy_lab.cpachecker.util.precondition.segkro.rules.ExtendRightRule;
import org.sosy_lab.cpachecker.util.predicates.interfaces.ArrayFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType.NumeralType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;

import com.google.common.collect.Lists;


public class ExtendRightRuleTest0 extends AbstractRuleTest0 {

  private ExtendRightRule err;

  private IntegerFormula _0;
  private IntegerFormula _1;
  private IntegerFormula _i;
  private IntegerFormula _n;
  private IntegerFormula _j;
  private IntegerFormula _x;

  private ArrayFormula<IntegerFormula, IntegerFormula> _b;

  private BooleanFormula _b_at_x_NOTEQ_0;
  private IntegerFormula _k;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    err = new ExtendRightRule(solver, matcher);

    _0 = ifm.makeNumber(0);
    _1 = ifm.makeNumber(1);
    _i = ifm.makeVariable("i");
    _n = ifm.makeVariable("n");
    _j = ifm.makeVariable("j");
    _x = ifm.makeVariable("x");
    _k = ifm.makeVariable("k");
    _b = afm.makeArray("b", NumeralType.IntegerType, NumeralType.IntegerType);

    _b_at_x_NOTEQ_0 = bfm.not(ifm.equal(afm.select(_b, _x), _0));
  }

  @Test
  public void testConclusion1() throws SolverException, InterruptedException {

    BooleanFormula _x_range = bfm.and(
        ifm.greaterOrEquals(_x, _i),
        ifm.lessOrEquals(_x, _j));

    BooleanFormula _right_ext = ifm.lessOrEquals(_j, _k);

    BooleanFormula _EXISTS_x = qmgr.exists(
        Lists.newArrayList(_x),
        bfm.and(Lists.newArrayList(
            _b_at_x_NOTEQ_0,
            _x_range)));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(
        Lists.newArrayList(
            _EXISTS_x,
            _right_ext));

    assertThat(result).isNotEmpty();
  }

  @Test  @Ignore
  public void testConclusion2() throws SolverException, InterruptedException {

    BooleanFormula _x_range = bfm.and(
        ifm.greaterOrEquals(_x, _i),
        ifm.lessOrEquals(_x, _j));

    BooleanFormula _right_ext = ifm.lessOrEquals(_j, _k);

    BooleanFormula _FORALL_x = qmgr.forall(
        Lists.newArrayList(_x),
        bfm.and(Lists.newArrayList(
            _b_at_x_NOTEQ_0,
            _x_range)));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(
        Lists.newArrayList(
            _FORALL_x,
            _right_ext));

    assertThat(result).isNotEmpty();
  }

  @Test
  public void testConclusion3() throws SolverException, InterruptedException {
    IntegerFormula boundVar = ifm.makeVariable("x");
    List<BooleanFormula> input = Lists.newArrayList(
        qfm.exists(
            Lists.newArrayList(boundVar),
            bfm.and(Lists.newArrayList(
                bfm.not(ifm.equal(afm.select(_b, boundVar), ifm.makeNumber(0))),
                ifm.greaterOrEquals(boundVar, _i),
                ifm.lessThan(boundVar, _i)))),
        ifm.lessThan(_k, _i));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(input);

    assertThat(result).isEmpty();
  }

  @Test
  public void testConclusion4() throws SolverException, InterruptedException {
    IntegerFormula boundVar = ifm.makeVariable("x");
    List<BooleanFormula> input = Lists.newArrayList(
        qfm.exists(
            Lists.newArrayList(boundVar),
            bfm.and(Lists.newArrayList(
                bfm.not(ifm.equal(afm.select(_b, boundVar), ifm.makeNumber(0))),
                ifm.greaterOrEquals(boundVar, _0),
                ifm.lessThan(boundVar, _i)))),
        ifm.lessThan(_i, _k));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(input);

    assertThat(result).isEmpty();
  }

  @Test
  public void testConclusion5() throws SolverException, InterruptedException {
    IntegerFormula boundVar = ifm.makeVariable("x");
    List<BooleanFormula> input = Lists.newArrayList(
        qfm.exists(
            Lists.newArrayList(boundVar),
            bfm.and(Lists.newArrayList(
                bfm.not(ifm.equal(afm.select(_b, boundVar), ifm.makeNumber(0))),
                ifm.greaterOrEquals(boundVar, _0),
                ifm.lessOrEquals(boundVar, _i)))),
        ifm.lessOrEquals(_i, _k));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(input);

    assertThat(result).isNotEmpty();
  }

  @Test  @Ignore
  public void testConclusion6() throws SolverException, InterruptedException {
    //  (forall ((x Int))
    //        (and (not (= (select |init::M@1| x) |init::e@1|))
    //             (>= x |init::i@1|)
    //             (<= x |init::i@1|)))


    IntegerFormula boundVar = ifm.makeVariable("x");
    List<BooleanFormula> input = Lists.newArrayList(
        qfm.forall(
            Lists.newArrayList(boundVar),
            bfm.and(Lists.newArrayList(
                bfm.not(ifm.equal(afm.select(_b, boundVar), ifm.makeNumber(0))),
                ifm.greaterOrEquals(boundVar, _i),
                ifm.lessOrEquals(boundVar, _i)))),
        ifm.lessOrEquals(_i, _k));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(input);

    assertThat(result).isNotEmpty();
  }

  @Test
  public void testConclusion7() throws SolverException, InterruptedException {

    IntegerFormula boundVar = ifm.makeVariable("x");
    List<BooleanFormula> input = Lists.newArrayList(
        qfm.exists(
            Lists.newArrayList(boundVar),
            bfm.and(Lists.newArrayList(
                bfm.not(ifm.equal(afm.select(_b, boundVar), ifm.makeNumber(0))),
                ifm.greaterOrEquals(boundVar, _i),
                ifm.lessOrEquals(boundVar, _i)))),
        ifm.lessOrEquals(_i, ifm.subtract(_k, ifm.makeNumber(1))));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(input);

    assertThat(result).isNotEmpty();
  }

  @Test
  public void testConclusion8() throws SolverException, InterruptedException {
    // i+1 <= n
    // exists x in {i..i+1} . b[x] = 0
    // ----- should result in ------
    // exists x in {i..n} . b[x] = 0

    IntegerFormula boundVar = ifm.makeVariable("x");
    List<BooleanFormula> input = Lists.newArrayList(
        qfm.exists(
            Lists.newArrayList(boundVar),
            bfm.and(Lists.newArrayList(
                ifm.equal(afm.select(_b, boundVar), ifm.makeNumber(0)),
                ifm.greaterOrEquals(boundVar, _i),
                ifm.lessOrEquals(boundVar, ifm.add(_i, ifm.makeNumber(1)))))),
        ifm.lessOrEquals(ifm.add(_i, ifm.makeNumber(1)), _n));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(input);

    assertThat(result).isNotEmpty();
  }

  @Test
  public void testConclusion9() throws SolverException, InterruptedException {
    // (exists ((x Int)) (and (= (select |copy::b@1| x) 0) (>= x 0) (<= x 1)))
    // (<= (+ 0 1) al@1)

    IntegerFormula boundVar = ifm.makeVariable("x");
    List<BooleanFormula> input = Lists.newArrayList(
        qfm.exists(
            Lists.newArrayList(boundVar),
            bfm.and(Lists.newArrayList(
                ifm.equal(afm.select(_b, boundVar), ifm.makeNumber(0)),
                ifm.greaterOrEquals(boundVar, _0),
                ifm.lessOrEquals(boundVar, ifm.makeNumber(1))))),
        ifm.lessOrEquals(ifm.add(_0, _1), _n));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(input);

    assertThat(result).isNotEmpty();
  }

  @Test
  public void testConclusion10() throws SolverException, InterruptedException {
    // (<= (+ 0 1) |init::n@1|)
    // (exists ((x Int)) (and (not (= (select |init::M@1| x) |init::e@1|)) (>= x 1) (<= x 1)))

    IntegerFormula boundVar = ifm.makeVariable("x");
    List<BooleanFormula> input = Lists.newArrayList(
        qfm.exists(
            Lists.newArrayList(boundVar),
            bfm.and(Lists.newArrayList(
                bfm.not(ifm.equal(afm.select(_b, boundVar), _k)),
                ifm.greaterOrEquals(boundVar, _1),
                ifm.lessOrEquals(boundVar, ifm.makeNumber(1))))),
        ifm.lessOrEquals(ifm.add(_0, _1), _n));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(input);

    assertThat(result).isNotEmpty();
  }

  @Test @Ignore
  public void testConclusion11() throws SolverException, InterruptedException {
    // (< 0 al@1)
    // (forall ((x Int)) (and (not (= (select |copy::b@1| x) 0)) (>= x 0) (<= x 0)))

    List<BooleanFormula> input = Lists.newArrayList(
        qfm.exists(_x, _0, _0, bfm.not(ifm.equal(afm.select(_b, _x),  _0))),
        ifm.lessThan(_0, _n));

    Set<BooleanFormula> result = err.applyWithInputRelatingPremises(input);

    assertThat(result).isNotEmpty();
  }


}
