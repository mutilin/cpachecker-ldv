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
package org.sosy_lab.cpachecker.util.predicates.z3.matching;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.z3.matching.SmtAstPatternSelection.LogicalConnection;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;


public class SmtAstPatternBuilder {

  // Idea: Large parts of SMT formulas are described using functions.
  //  --> We can simplify the matching problem!!
  //
  //  Considering let-statements is not necessary because the are "only"
  //    used for representing common sub formulas, i.e., with multiple entry-edges (DAG)
  //
  //  We operate on the DAG

  /**
   * Match a specific function. This might be either a
   *  ... nullary (constant, variable)
   *  ... unary ('boolean not', ...)
   *  ... binary (equality, implication, different arithmetic operations, ...
   *  ... ternary (ITE, ...)
   *  ... n-ary (and, or, ... user-defined functions, ...)
   *
   * @param pFunction         String symbol that represents the function (as declared)
   * @param argumentMatchers  Matchers for the arguments of the function
   *
   * @return  A description of the pattern that matches the specified sub-formula within the AST
   */
  public static SmtAstPattern match(Comparable<?> pFunction, SmtAstPattern... argumentMatchers) {
    return new SmtFunctionApplicationPattern(
        Optional.<Comparable<?>>of(pFunction),
        Optional.<String>absent(),
        and(argumentMatchers));
  }

  /**
   * Matches only if all argument patterns of an arbitrary function match.
   *
   * The root node of the AST is also considered as a function with childs!!
   *
   * @param argumentMatchers  The child patterns.
   */
  public static SmtAstPattern match(SmtAstPattern... argumentMatchers) {
    return new SmtFunctionApplicationPattern(
        Optional.<Comparable<?>>absent(),
        Optional.<String>absent(),
        and(argumentMatchers));
  }

  /**
   * The same as described in {@link #match}, but binds the matching formula to a variable.
   */
  public static SmtAstPattern matchBind(Comparable<?> pFunction, String pBindMatchTo, SmtAstPattern... argumentMatchers) {
    return new SmtFunctionApplicationPattern(
        Optional.<Comparable<?>>of(pFunction),
        Optional.of(pBindMatchTo),
        and(argumentMatchers));
  }

  /**
   * Matches any function application.
   *
   * @return  Pattern.
   */
  public static SmtAstPattern matchAny() {
    return new SmtFunctionApplicationPattern(
        Optional.<Comparable<?>>absent(),
        Optional.<String>absent(),
        and());
  }

  /**
   * The same as described in {@link #matchAny}, but binds the matching formula to a variable.
   */
  public static SmtAstPattern matchAnyBind(String pBindMatchTo) {
    return new SmtFunctionApplicationPattern(
        Optional.<Comparable<?>>absent(),
        Optional.<String>of(pBindMatchTo),
        and());
  }

  /**
   * Matches only if NONE of the patterns matches on the arguments of the specific function application.
   *
   * @param pFunction
   * @param argumentMatchers
   * @return
   */
  public static SmtAstPattern matchIfNot(Comparable<?> pFunction, SmtAstPattern... argumentMatchers) {
    return new SmtFunctionApplicationPattern(
        Optional.<Comparable<?>>of(pFunction),
        Optional.<String>absent(),
        none(argumentMatchers));
  }

  /**
   * Matches only if NONE of the patterns matches an arbitrary function application.
   *
   * @param quantorBodyMatchers
   * @return
   */
  public static SmtAstPattern matchIfNot(SmtAstPattern... pMatchers) {
    return new SmtFunctionApplicationPattern(
        Optional.<Comparable<?>>absent(),
        Optional.<String>absent(),
        none(pMatchers));
  }

  public static SmtAstPattern matchNullaryBind(String pBindMatchTo) {
    return new SmtFunctionApplicationPattern(
        Optional.<Comparable<?>>absent(),
        Optional.<String>of(pBindMatchTo),
        and());
  }

  public static SmtAstPatternSelection or(SmtAstPattern... pDisjuncts) {
    return new SmtAstPatternSelectionImpl(
        LogicalConnection.OR,
        Arrays.asList(pDisjuncts),
        Collections.<String,Formula>emptyMap());
  }

  public static SmtAstPatternSelection and(SmtAstPattern... pDisjuncts) {
    return new SmtAstPatternSelectionImpl(
        LogicalConnection.AND,
        Arrays.asList(pDisjuncts),
        Collections.<String,Formula>emptyMap());
  }

  public static SmtAstPatternSelection none(SmtAstPattern... pDisjuncts) {
    return new SmtAstPatternSelectionImpl(
        LogicalConnection.NONE,
        Arrays.asList(pDisjuncts),
        Collections.<String,Formula>emptyMap());
  }

  public static SmtAstPatternSelection withDefaultBinding(String pVariableName, Formula pDefaultBinding, SmtAstPatternSelection pSelection) {
    Map<String,Formula> defaultBindings = Maps.newHashMap(pSelection.getDefaultBindings());
    defaultBindings.put(pVariableName, pDefaultBinding);

    return new SmtAstPatternSelectionImpl(
        pSelection.getRelationship(),
        pSelection.getPatterns(),
        defaultBindings);
  }

}
