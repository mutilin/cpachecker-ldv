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
package org.sosy_lab.solver.smtInterpol;

import static com.google.common.base.Predicates.*;
import static com.google.common.collect.FluentIterable.from;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.UniqueIdGenerator;
import org.sosy_lab.solver.Model;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.InterpolatingProverEnvironment;

import com.google.common.base.Preconditions;

import de.uni_freiburg.informatik.ultimate.logic.Annotation;
import de.uni_freiburg.informatik.ultimate.logic.Term;

class SmtInterpolInterpolatingProver implements InterpolatingProverEnvironment<String> {

  protected final SmtInterpolFormulaManager mgr;
  private SmtInterpolEnvironment env;

  private final List<String> assertedFormulas; // Collection of termNames
  private final Map<String, Term> annotatedTerms; // Collection of termNames
  private static final String prefix = "term_"; // for termnames
  private static final UniqueIdGenerator termIdGenerator = new UniqueIdGenerator(); // for different termnames // TODO static?

  SmtInterpolInterpolatingProver(SmtInterpolFormulaManager pMgr) {
    mgr = pMgr;
    env = mgr.createEnvironment();
    assertedFormulas = new ArrayList<>();
    annotatedTerms = new HashMap<>();
  }

  @Override
  public String push(BooleanFormula f) {
    Preconditions.checkNotNull(env);

    Term t = mgr.extractInfo(f);
    //Term t = ((SmtInterpolFormula)f).getTerm();

    String termName = prefix + termIdGenerator.getFreshId();
    Term annotatedTerm = env.annotate(t, new Annotation(":named", termName));
    pushAndAssert(annotatedTerm);
    assertedFormulas.add(termName);
    annotatedTerms.put(termName, t);
    assert assertedFormulas.size() == annotatedTerms.size();
    return termName;
  }

  protected void pushAndAssert(Term annotatedTerm) {
    env.push(1);
    env.assertTerm(annotatedTerm);
  }

  @Override
  public void pop() {
    Preconditions.checkNotNull(env);
    String removed = assertedFormulas.remove(assertedFormulas.size()-1); // remove last term
    annotatedTerms.remove(removed);
    assert assertedFormulas.size() == annotatedTerms.size();
    env.pop(1);
  }

  @Override
  public boolean isUnsat() throws InterruptedException {
    return !env.checkSat();
  }

  @Override
  public BooleanFormula getInterpolant(List<String> pTermNamesOfA) {
    Preconditions.checkNotNull(env);

    Set<String> termNamesOfA = new HashSet<>(pTermNamesOfA);

    // calc difference: termNamesOfB := assertedFormulas - termNamesOfA
    Set<String> termNamesOfB = from(assertedFormulas)
                                 .filter(not(in(termNamesOfA)))
                                 .toSet();

    // build 2 groups:  (and A1 A2 A3...) , (and B1 B2 B3...)
    Term termA = buildConjunctionOfNamedTerms(termNamesOfA);
    Term termB = buildConjunctionOfNamedTerms(termNamesOfB);

    return getInterpolant(termA, termB);
  }

  @Override
  public List<BooleanFormula> getSeqInterpolants(List<Set<String>> partitionedTermNames) {
    Preconditions.checkNotNull(env);

    final Term[] formulas = new Term[partitionedTermNames.size()];
    for (int i = 0; i < formulas.length; i++) {
      formulas[i] = buildConjunctionOfNamedTerms(partitionedTermNames.get(i));
    }

    // get interpolants of groups
    final Term[] itps = env.getInterpolants(formulas);

    final List<BooleanFormula> result = new ArrayList<>();
    for (Term itp : itps) {
      result.add(mgr.encapsulateBooleanFormula(itp));
    }
    return result;
  }

  @Override
  public List<BooleanFormula> getTreeInterpolants(List<Set<String>> partitionedTermNames, int[] startOfSubTree) {
    Preconditions.checkNotNull(env);

    final Term[] formulas = new Term[partitionedTermNames.size()];
    for (int i = 0; i < formulas.length; i++) {
      formulas[i] = buildConjunctionOfNamedTerms(partitionedTermNames.get(i));
    }

    assert checkSubTrees(partitionedTermNames, startOfSubTree);

    // get interpolants of groups
    final Term[] itps = env.getTreeInterpolants(formulas, startOfSubTree);

    final List<BooleanFormula> result = new ArrayList<>();
    for (Term itp : itps) {
      result.add(mgr.encapsulateBooleanFormula(itp));
    }
    return result;
  }

  /** checks for a valid subtree-structure.
   * This code is taken from SMTinterpol itself, where it is disabled. */
  private static boolean checkSubTrees(List<Set<String>> partitionedTermNames, int[] startOfSubTree) {
    for (int i = 0; i < partitionedTermNames.size(); i++) {
      if (startOfSubTree[i] < 0) {
        throw new AssertionError("subtree array must not contain negative element");
      }
      int j = i;
      while (startOfSubTree[i] < j) {
        j = startOfSubTree[j - 1];
      }
      if (startOfSubTree[i] != j) {
        throw new AssertionError("malformed subtree array.");
      }
    }
    if (startOfSubTree[partitionedTermNames.size() - 1] != 0) {
      throw new AssertionError("malformed subtree array.");
    }

    return true;
  }

  protected BooleanFormula getInterpolant(Term termA, Term termB) {
    // get interpolant of groups
    Term[] itp = env.getInterpolants(new Term[] {termA, termB});
    assert itp.length == 1; // 2 groups -> 1 interpolant

    return mgr.encapsulateBooleanFormula(itp[0]);
  }

  private Term buildConjunctionOfNamedTerms(Set<String> termNames) {
    Term[] terms = new Term[termNames.size()];
    int i=0;
    for (String termName: termNames) {
      terms[i] = env.term(termName);
      i++;
    }
    if (terms.length > 1) {
      return env.term("and", terms);
    } else {
      assert terms.length != 0;
      return terms[0];
    }
  }

  @Override
  public void close() {
    Preconditions.checkNotNull(env);
    assert assertedFormulas.size() == annotatedTerms.size();
    if (!assertedFormulas.isEmpty()) {
      env.pop(assertedFormulas.size());
      assertedFormulas.clear();
      annotatedTerms.clear();
    }
    env = null;
  }

  @Override
  public Model getModel() {
    Preconditions.checkNotNull(env);
    assert assertedFormulas.size() == annotatedTerms.size();

    return SmtInterpolModel.createSmtInterpolModel(env, annotatedTerms.values());
  }
}