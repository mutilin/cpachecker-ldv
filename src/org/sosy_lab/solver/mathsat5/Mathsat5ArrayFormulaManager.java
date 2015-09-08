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
package org.sosy_lab.solver.mathsat5;

import static org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.*;

import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.FormulaType.ArrayFormulaType;
import org.sosy_lab.solver.basicimpl.AbstractArrayFormulaManager;

class Mathsat5ArrayFormulaManager extends AbstractArrayFormulaManager<Long, Long, Long> {

  private final long mathsatEnv;

  public Mathsat5ArrayFormulaManager(
      Mathsat5FormulaCreator pCreator) {
    super(pCreator);
    this.mathsatEnv = pCreator.getEnv();
  }

  @Override
  protected Long select(Long pArray, Long pIndex) {
    return msat_make_array_read(mathsatEnv, pArray, pIndex);
  }

  @Override
  protected Long store(Long pArray, Long pIndex, Long pValue) {
    return msat_make_array_write(mathsatEnv, pArray, pIndex, pValue);
  }

  @Override
  protected <TI extends Formula, TE extends Formula> Long internalMakeArray(
      String pName, FormulaType<TI> pIndexType,
      FormulaType<TE> pElementType) {
    //throw new UnsupportedOperationException("Please implement me!");
    final ArrayFormulaType<TI, TE> arrayFormulaType = FormulaType.getArrayType(
        pIndexType, pElementType);
    final Long mathsatArrayType = toSolverType(arrayFormulaType);

    return getFormulaCreator().makeVariable(mathsatArrayType, pName);
  }

  @Override
  protected Long equivalence(Long pArray1, Long pArray2) {
    return msat_make_equal(mathsatEnv, pArray1, pArray2);
  }

}
