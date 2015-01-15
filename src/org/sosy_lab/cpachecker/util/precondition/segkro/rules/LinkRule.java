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
import org.sosy_lab.cpachecker.util.precondition.segkro.rules.GenericPatterns.PropositionType;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;
import org.sosy_lab.cpachecker.util.predicates.matching.SmtAstMatcher;

import com.google.common.collect.Lists;


public class LinkRule extends PatternBasedRule {

  public LinkRule(Solver pSolver, SmtAstMatcher pMatcher) {
    super(pSolver, pMatcher);
  }

  @Override
  protected void setupPatterns() {
    premises.add(new PatternBasedPremise(
        or(
          matchForallQuant(
              and(
                GenericPatterns.array_at_index_matcher("f", quantified("var1"), PropositionType.ALL),
                match(">=",
                    matchAnyWithAnyArgsBind(quantified("x1")),
                    matchAnyWithAnyArgsBind("j")),
                match("<=",
                    matchAnyWithAnyArgsBind(quantified("x1")),
                    matchAnyWithAnyArgsBind("i"))
    )))));

    premises.add(new PatternBasedPremise(or(
        matchForallQuant(
            and(
              GenericPatterns.array_at_index_matcher("f", quantified("var2"), PropositionType.ALL),
              match(">=",
                  matchAnyWithAnyArgsBind(quantified("x2")),
                  matchAnyWithAnyArgsBind("iPlusOneEq")),
              match("<=",
                  matchAnyWithAnyArgsBind(quantified("x2")),
                  matchAnyWithAnyArgsBind("k"))
    )))));
  }

  @Override
  protected boolean satisfiesConstraints(Map<String, Formula> pAssignment) throws SolverException, InterruptedException {
    final IntegerFormula i = (IntegerFormula) pAssignment.get("i");
    final IntegerFormula iPlusOneEq = (IntegerFormula) pAssignment.get("iPlusOneEq");

    return solver.isUnsat(bfm.not(ifm.equal(ifm.add(i, ifm.makeNumber(1)), iPlusOneEq)));
  }

  @Override
  protected Collection<BooleanFormula> deriveConclusion(Map<String, Formula> pAssignment) {
    final BooleanFormula f1 = (BooleanFormula) pAssignment.get("f");
    final IntegerFormula j = (IntegerFormula) pAssignment.get("j");
    final IntegerFormula k = (IntegerFormula) pAssignment.get("k");

    final IntegerFormula xBound = (IntegerFormula) pAssignment.get(quantified("var1"));
    final Formula xBoundParent = pAssignment.get(parentOf(quantified("var1")));
    final IntegerFormula xNew = ifm.makeVariable("x");
    final BooleanFormula fNew = (BooleanFormula) substituteInParent(xBoundParent, xBound, xNew, f1);

    final BooleanFormula xConstraint =  bfm.and(
        ifm.greaterOrEquals(xNew, j),
        ifm.lessOrEquals(xNew, k));

    return Lists.newArrayList(qfm.forall(Lists.newArrayList(xNew), bfm.and(fNew, xConstraint)));
//    if (pAssignment.containsKey("forall")) {
//      return Lists.newArrayList(qfm.forall(Lists.newArrayList(xNew), bfm.and(fNew, xConstraint)));
//    } else {
//      return Lists.newArrayList(qfm.exists(Lists.newArrayList(xNew), bfm.and(fNew, xConstraint)));
//    }

  }

}
