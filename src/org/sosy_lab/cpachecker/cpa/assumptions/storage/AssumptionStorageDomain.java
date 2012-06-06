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
package org.sosy_lab.cpachecker.cpa.assumptions.storage;

import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;

public class AssumptionStorageDomain implements AbstractDomain {

  private final FormulaManager formulaManager;

  public AssumptionStorageDomain(
      FormulaManager pFormulaManager) {
    formulaManager = pFormulaManager;
  }

  @Override
  public AbstractState join(AbstractState pElement1, AbstractState pElement2) {

    AssumptionStorageState storageElement1= (AssumptionStorageState)pElement1;
    AssumptionStorageState storageElement2 = (AssumptionStorageState)pElement2;

    // create the disjunction of the stop formulas
    // however, if one of them is true, we would loose the information from the other
    // so handle these special cases separately
    Formula stopFormula1 = storageElement1.getStopFormula();
    Formula stopFormula2 = storageElement2.getStopFormula();
    Formula newStopFormula;
    if (stopFormula1.isTrue()) {
      newStopFormula = stopFormula2;
    } else if (stopFormula2.isTrue()) {
      newStopFormula = stopFormula1;
    } else {
      newStopFormula = formulaManager.makeOr(stopFormula1, stopFormula2);
    }

    return new AssumptionStorageState(
        formulaManager.makeAnd(storageElement1.getAssumption(),
                               storageElement2.getAssumption()),
        newStopFormula);
  }

  @Override
  public boolean isLessOrEqual(AbstractState pElement1, AbstractState pElement2) {
    throw new UnsupportedOperationException();
  }
}