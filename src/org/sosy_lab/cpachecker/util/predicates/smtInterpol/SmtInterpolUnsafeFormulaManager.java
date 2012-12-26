/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.predicates.smtInterpol;

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractUnsafeFormulaManager;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;


public class SmtInterpolUnsafeFormulaManager extends AbstractUnsafeFormulaManager<Term> {

  private Set<Term> uifs = new HashSet<>();
  private SmtInterpolEnvironment env;
  private SmtInterpolFormulaCreator creator;

  public SmtInterpolUnsafeFormulaManager(
      SmtInterpolFormulaCreator pCreator) {
    super(pCreator);
    this.creator = pCreator;
    this.env = pCreator.getEnv();

  }

  /** ApplicationTerms can be wrapped with "|".
   * This function removes those chars. */
  static String dequote(String s) {
   return s.replace("|", "");
  }

 /** ApplicationTerms can be wrapped with "|".
   * This function replaces those chars with "\"". */
  // TODO: Check where this was used in the past.
  @SuppressWarnings("unused")
  private static String convertQuotes(String s) {
    return s.replace("|", "\"");
  }

  @Override
  public Formula encapsulateUnsafe(Term pL) {
    return new AbstractFormula<>(pL);
  }

  @Override
  public boolean isAtom(Term t) {
    return SmtInterpolUtil.isAtom(t);
  }

  @Override
  public int getArity(Term pT) {
    return SmtInterpolUtil.getArity(pT);
  }

  @Override
  public Term getArg(Term pT, int pN) {
    return SmtInterpolUtil.getArg(pT, pN);
  }

  @Override
  public boolean isVariable(Term pT) {
    return SmtInterpolUtil.isVariable(pT);
  }

  @Override
  public boolean isUF(Term t) {
    return uifs.contains(t);
  }

  @Override
  public String getName(Term t) {
    if (SmtInterpolUtil.isVariable(t)){
      return dequote( t.toString() );
    }else if (uifs.contains(t)) {
      return ((ApplicationTerm)t).getFunction().toString();
    } else {
      throw new IllegalArgumentException("The Term " + t + " has no name!");
    }
  }

  @Override
  public Term replaceArgs(Term pT, List<Term> newArgs) {
    return SmtInterpolUtil.replaceArgs(env, pT, newArgs.toArray((Term[]) Array.newInstance(Term.class, newArgs.size())));
  }

  @Override
  public Term replaceName(Term t, String pNewName) {

    if (SmtInterpolUtil.isVariable(t)){
      return creator.makeVariable(t.getSort(), pNewName);
    }else if (uifs.contains(t)) {
      ApplicationTerm at = (ApplicationTerm) t;
      Term[] args = at.getParameters();
      Sort[] sorts = new Sort[args.length];
      for (int i = 0; i < sorts.length; i++) {
        sorts[i] = args[i].getSort();
      }
      env.declareFun(pNewName, sorts, t.getSort());
      return createUIFCallImpl(pNewName, args);
    } else {
      throw new IllegalArgumentException("The Term " + t + " has no name!");
    }
  }

  public Term createUIFCallImpl(String funcDecl, Term[] args) {
    Term ufc = env.term(funcDecl, args);
    uifs.add(ufc);
    return ufc;
  }

  @Override
  public boolean isNumber(Term pT) {
    return SmtInterpolUtil.isNumber(pT);
  }
}
