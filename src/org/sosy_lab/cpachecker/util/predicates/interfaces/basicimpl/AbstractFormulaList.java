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
package org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl;

import java.util.Arrays;
import java.util.List;

import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaList;

import com.google.common.collect.ImmutableList;

/**
 * A generic FormulaList implementation used to minimize the code changes for legacy code.
 */
public class AbstractFormulaList implements FormulaList {
  private final List<Formula> terms;

  public AbstractFormulaList(Formula... terms) {
    this(Arrays.asList( terms ));
  }

  public AbstractFormulaList(List<Formula> terms) {
    this.terms = ImmutableList.copyOf(terms);
  }

  /**
   * Do not modify the returned array, for performance reasons it's not copied!
   */
  public List<Formula> getTerms() {
    return terms;
  }

  @Override
  public String toString() {
    return Arrays.toString(terms.toArray());
  }

  @Override
  public boolean equals(Object pObj) {
    if (!(pObj instanceof AbstractFormulaList)) {
      return false;
    }
    return terms.equals(((AbstractFormulaList)pObj).terms);
  }

  @Override
  public int hashCode() {
    return terms.hashCode();
  }
}
