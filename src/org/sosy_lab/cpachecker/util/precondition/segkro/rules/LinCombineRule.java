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
package org.sosy_lab.cpachecker.util.precondition.segkro.rules;

import static org.sosy_lab.cpachecker.util.predicates.matching.SmtAstPatternBuilder.*;

import java.util.Collection;
import java.util.Map;

import org.sosy_lab.cpachecker.exceptions.SolverException;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;
import org.sosy_lab.cpachecker.util.predicates.matching.SmtAstMatcher;

import com.google.common.collect.Lists;


public class LinCombineRule extends PatternBasedRule {

  public LinCombineRule(Solver pSolver, SmtAstMatcher pMatcher) {
    super(pSolver, pMatcher);
  }

  @Override
  protected void setupPatterns() {
    premises.add(new PatternBasedPremise(
        or (
          match("not",
            match("<",
                matchAnyWithAnyArgsBind("zero1"),
                matchAnyWithAnyArgsBind("a"))),
          match(">=",
              matchAnyWithAnyArgsBind("zero1"),
              matchAnyWithAnyArgsBind("a")),
          match("not",
              match("<",
                  match("-",
                      matchAnyWithAnyArgsBind("zero1"),
                      matchAnyWithAnyArgsBind("a")),
                  matchAnyWithAnyArgsBind("zero2"))),
          match(">=",
              match("-",
                  matchAnyWithAnyArgsBind("zero1"),
                  matchAnyWithAnyArgsBind("a")),
              matchAnyWithAnyArgsBind("zero2"))
          )));

    premises.add(new PatternBasedPremise(
        or (
          match(">",
              matchAnyWithAnyArgsBind("b"),
              matchAnyWithAnyArgsBind("zero1")),
          match("not",
              match("<=",
                  matchAnyWithAnyArgsBind("b"),
                  matchAnyWithAnyArgsBind("zero1"))),
          match("<",
              match("-",
                  matchAnyWithAnyArgsBind("zero1"),
                  matchAnyWithAnyArgsBind("b")),
              matchAnyWithAnyArgsBind("zero2")),
          match("not",
              match(">=",
                  match("-",
                      matchAnyWithAnyArgsBind("zero1"),
                      matchAnyWithAnyArgsBind("b")),
                  matchAnyWithAnyArgsBind("zero2"))),
          match(">=",
              match("-",
                  matchAnyWithAnyArgsBind("b"),
                  matchAnyWithAnyArgsBind("one")),
              matchAnyWithAnyArgsBind("zero2")),
          match("not",
              match("<",
                  match("-",
                      matchAnyWithAnyArgsBind("b"),
                      matchAnyWithAnyArgsBind("one")),
                  matchAnyWithAnyArgsBind("zero2")))
          )));
  }

  @Override
  protected boolean satisfiesConstraints(Map<String, Formula> pAssignment)
      throws SolverException, InterruptedException {

    final Formula a = pAssignment.get("a");
    final Formula b = pAssignment.get("b");

    if (a.equals(b)) {
      return false;
    }

    final String[] zeroEq = {"zero1", "zero2"};

    for (String zId: zeroEq) {
      final Formula f = pAssignment.get(zId);

      if (f != null) {
        if (!(f instanceof IntegerFormula)) {
          return false;
        }
        final IntegerFormula z = (IntegerFormula) f;

        if (solver.isUnsat(ifm.equal(z, ifm.makeNumber(0)))) {
          return false;
        }
      }
    }

    final String[] oneEq = {"one"};

    for (String oId: oneEq) {
      final Formula f = pAssignment.get(oId);

      if (f != null) {
        if (!(f instanceof IntegerFormula)) {
          return false;
        }
        final IntegerFormula o = (IntegerFormula) f;

        if (solver.isUnsat(ifm.equal(o, ifm.makeNumber(0)))) {
          return false;
        }
      }
    }

    return true;
  }


  @Override
  protected Collection<BooleanFormula> deriveConclusion(Map<String, Formula> pAssignment) {
    final IntegerFormula a = (IntegerFormula) pAssignment.get("a");
    final IntegerFormula b = (IntegerFormula) pAssignment.get("b");

    return Lists.newArrayList(
        ifm.lessThan(a, b));
  }


}
