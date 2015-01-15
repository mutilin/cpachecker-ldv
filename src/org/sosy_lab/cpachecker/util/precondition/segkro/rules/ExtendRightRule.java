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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;


public class ExtendRightRule extends PatternBasedRule {

  public ExtendRightRule(Solver pSolver, SmtAstMatcher pMatcher) {
    super(pSolver, pMatcher);
  }

  @Override
  protected void setupPatterns() {
    // (exists ((x Int)) (and (not (= (select b x) 0)) (>= x i) (<= x j)))

    premises.add(new PatternBasedPremise(
        or(
          matchExistsQuantBind("exists",
              and(
                GenericPatterns.array_at_index_matcher("f", quantified("var"), PropositionType.ALL),
                match(">=",
                    matchAnyWithAnyArgsBind(quantified("x")),
                    matchAnyWithAnyArgsBind("i")),
                match("<=",
                    matchAnyWithAnyArgsBind(quantified("x")),
                    matchAnyWithAnyArgsBind("j")))),

          matchForallQuantBind("forall",
              and(
                GenericPatterns.array_at_index_matcher("f", quantified("var"), PropositionType.ALL),
                match(">=",
                    matchAnyWithAnyArgsBind(quantified("x")),
                    matchAnyWithAnyArgsBind("i")),
                match("<=",
                    matchAnyWithAnyArgsBind(quantified("x")),
                    matchAnyWithAnyArgsBind("j"))))
        )));

    premises.add(new PatternBasedPremise(
        or(
            matchBind("<", "ext",
                matchAnyWithAnyArgsBind("=j"),
                matchAnyWithAnyArgsBind("k")),
            matchBind("<=", "ext",
                matchAnyWithAnyArgsBind("=j"),
                matchAnyWithAnyArgsBind("k")),
            matchBind("=", "ext",
                matchAnyWithAnyArgsBind("=j"),
                matchAnyWithAnyArgsBind("k"))

    )));
  }

  @Override
  protected boolean satisfiesConstraints(Map<String, Formula> pAssignment) throws SolverException, InterruptedException {
    final IntegerFormula j = (IntegerFormula) Preconditions.checkNotNull(pAssignment.get("j"));
    final IntegerFormula k = (IntegerFormula) Preconditions.checkNotNull(pAssignment.get("k"));
    final BooleanFormula ext = (BooleanFormula) Preconditions.checkNotNull(pAssignment.get("ext"));

    // (exists ((x Int)) (and (= (select |copy::b@1| x) 0) (>= x 0) (<= x 1))),
    // (> al@1 0)
    //
    // j <= k
    // k: al
    // j: 1
    // ext: (> al@1 0)

    // 1 <= al AND al@1 > 0 SAT
    // k >= j AND ext SAT

    return !solver.isUnsat(bfm.and(ifm.greaterOrEquals(k, j), ext));
  }

  @Override
  protected Collection<BooleanFormula> deriveConclusion(Map<String, Formula> pAssignment) {
    final BooleanFormula f = (BooleanFormula) pAssignment.get("f");
    final IntegerFormula i = (IntegerFormula) pAssignment.get("i");
    final IntegerFormula k = (IntegerFormula) pAssignment.get("k");

    final IntegerFormula xBound = (IntegerFormula) pAssignment.get(quantified("var"));
    final Formula xBoundParent = pAssignment.get(parentOf(quantified("var")));
    final IntegerFormula xNew = ifm.makeVariable("x");
    final BooleanFormula fNew = (BooleanFormula) substituteInParent(xBoundParent, xBound, xNew, f);

    final BooleanFormula xConstraint =  bfm.and(
        ifm.greaterOrEquals(xNew, i),
        ifm.lessOrEquals(xNew, k));

    if (pAssignment.containsKey("forall")) {
      return Lists.newArrayList(qfm.forall(Lists.newArrayList(xNew), bfm.and(fNew, xConstraint)));
    } else {
      return Lists.newArrayList(qfm.exists(Lists.newArrayList(xNew), bfm.and(fNew, xConstraint)));
    }
  }
}
