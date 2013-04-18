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

import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFormulaCreator;

import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;


public class SmtInterpolFormulaCreator extends AbstractFormulaCreator<Term, Sort, SmtInterpolEnvironment> {

  public SmtInterpolFormulaCreator(
      SmtInterpolEnvironment pMathsatEnv,
      Sort pBoolType,
      Sort pNumberType,
      AbstractFormulaCreator.CreateBitType<Sort> pBittype) {
    super(pMathsatEnv, pBoolType, pNumberType, pBittype);
  }

  @Override
  public Term makeVariable(Sort type, String varName) {
    SmtInterpolEnvironment env = getEnv();
    env.declareFun(varName, new Sort[]{}, type);
    return env.term(varName);
  }
}
