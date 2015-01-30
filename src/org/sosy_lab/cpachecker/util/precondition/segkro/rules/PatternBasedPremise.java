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

import org.sosy_lab.cpachecker.util.precondition.segkro.interfaces.Premise;
import org.sosy_lab.cpachecker.util.predicates.matching.SmtAstPatternSelection;

import com.google.common.base.Preconditions;


public class PatternBasedPremise implements Premise {

  private final SmtAstPatternSelection patternSelection;

  public PatternBasedPremise(SmtAstPatternSelection pPatternSelection) {
    super();

    patternSelection = Preconditions.checkNotNull(pPatternSelection);
  }

  public SmtAstPatternSelection getPatternSelection() {
    return patternSelection;
  }

  @Override
  public String toString() {
    return patternSelection.toString();
  }

}
