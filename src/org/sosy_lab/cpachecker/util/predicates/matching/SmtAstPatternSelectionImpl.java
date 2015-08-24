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
package org.sosy_lab.cpachecker.util.predicates.matching;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.sosy_lab.solver.api.Formula;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


public class SmtAstPatternSelectionImpl implements SmtAstPatternSelection {

  private final ImmutableList<SmtAstPatternSelectionElement> patterns;
  private final LogicalConnection relation;
  private final ImmutableMap<String, Formula> defaultBindings;

  public SmtAstPatternSelectionImpl(
      LogicalConnection pRelation,
      Iterable<SmtAstPatternSelectionElement> pDisjuncts,
      Map<String,Formula> pDefaultBindings) {
    this.relation = pRelation;
    this.patterns = ImmutableList.copyOf(pDisjuncts);
    this.defaultBindings = ImmutableMap.copyOf(pDefaultBindings);
  }

  @Override
  public List<SmtAstPatternSelectionElement> getPatterns() {
    return patterns;
  }

  @Override
  public LogicalConnection getRelationship() {
    return relation;
  }

  @Override
  public Iterator<SmtAstPatternSelectionElement> iterator() {
    return getPatterns().iterator();
  }

  @Override
  public Map<String, Formula> getDefaultBindings() {
    return defaultBindings;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    try {
      SmtAstPatternPrinter.print(sb, this);
    } catch (IOException e) {
      throw new AssertionError("StringBuilder threw IOException", e);
    }
    return sb.toString();
  }

}
