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

import java.math.BigDecimal;
import java.math.BigInteger;

import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.NumeralFormula;
import org.sosy_lab.solver.api.NumeralFormula.RationalFormula;

import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;

class SmtInterpolRationalFormulaManager extends SmtInterpolNumeralFormulaManager<NumeralFormula, RationalFormula> {

  SmtInterpolRationalFormulaManager(
          SmtInterpolFormulaCreator pCreator,
          SmtInterpolFunctionFormulaManager pFunctionManager) {
    super(pCreator, pFunctionManager);
  }

  @Override
  public FormulaType<RationalFormula> getFormulaType() {
    return FormulaType.RationalType;
  }

  @Override
  protected Term makeNumberImpl(long i) {
    return getFormulaCreator().getEnv().decimal(BigDecimal.valueOf(i));
  }

  @Override
  protected Term makeNumberImpl(BigInteger pI) {
    return getFormulaCreator().getEnv().decimal(new BigDecimal(pI));
  }

  @Override
  protected Term makeNumberImpl(String pI) {
    return getFormulaCreator().getEnv().decimal(pI);
  }

  @Override
  protected Term makeNumberImpl(Rational pI) {
    return getFormulaCreator().getEnv().getTheory().rational(
        pI.getNum(), pI.getDen()
    );
  }

  @Override
  protected Term makeNumberImpl(double pNumber) {
    return getFormulaCreator().getEnv().decimal(BigDecimal.valueOf(pNumber));
  }

  @Override
  protected Term makeNumberImpl(BigDecimal pNumber) {
    return getFormulaCreator().getEnv().decimal(pNumber);
  }

  @Override
  protected Term makeVariableImpl(String varName) {
    Sort t = getFormulaCreator().getRationalType();
    return getFormulaCreator().makeVariable(t, varName);
  }
}
