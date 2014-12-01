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
package org.sosy_lab.cpachecker.util.predicates.z3;

import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractArrayFormulaManager;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;


class Z3ArrayFormulaManager extends AbstractArrayFormulaManager<Long, Long, Long> {

  private final long z3context;

  private final Table<Long, Long, Long> allocatedArraySorts = HashBasedTable.create();

  Z3ArrayFormulaManager(Z3FormulaCreator creator) {
    super(creator);
    this.z3context = creator.getEnv();
  }

  @Override
  protected Long select(Long pArray, Long pIndex) {
    try {
      final long term = Z3NativeApi.mk_select(z3context, pArray, pIndex);
      Z3NativeApi.inc_ref(z3context, term);

      return term;

    } catch (IllegalArgumentException ae) {
      int errorCode = Z3NativeApi.get_error_code(z3context);
      throw new IllegalArgumentException(
          String.format("Errorcode: %d, msg: %s",
              errorCode,
              Z3NativeApi.get_error_msg_ex(z3context, errorCode)));
    }
  }

  @Override
  protected Long store(Long pArray, Long pIndex, Long pValue) {
    final long term = Z3NativeApi.mk_store(z3context, pArray, pIndex, pValue);
    Z3NativeApi.inc_ref(z3context, term);
    return term;
  }

  @Override
  protected <TI extends Formula, TE extends Formula> Long internalMakeArray(String pName, FormulaType<TI> pIndexType,
      FormulaType<TE> pElementType) {

    final long indexType = toSolverType(pIndexType);
    final long elementType = toSolverType(pElementType);

    Long allocatedArraySort = allocatedArraySorts.get(indexType, elementType);
    if (allocatedArraySort == null) {
      allocatedArraySort = Z3NativeApi.mk_array_sort(z3context, indexType, elementType);
      Z3NativeApi.inc_ref(z3context, allocatedArraySort);
      allocatedArraySorts.put(indexType, elementType, allocatedArraySort);
    }

    final long arrayTerm = getFormulaCreator().makeVariable(allocatedArraySort, pName);
    Z3NativeApi.inc_ref(z3context, arrayTerm);

    return arrayTerm;
  }

}
