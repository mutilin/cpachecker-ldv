/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.assumptions.collector;

import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.assumptions.AssumptionWithLocation;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.SymbolicFormulaManager;

public class AssumptionCollectorDomain implements AbstractDomain {

  private final SymbolicFormulaManager symbolicFormulaManager;
  
  public AssumptionCollectorDomain(
      SymbolicFormulaManager pSymbolicFormulaManager) {
    symbolicFormulaManager = pSymbolicFormulaManager;
  }

  @Override
  public AbstractElement join(AbstractElement pElement1, AbstractElement pElement2) {

    AssumptionCollectorElement collectorElement1= (AssumptionCollectorElement)pElement1;
    AssumptionCollectorElement collectorElement2 = (AssumptionCollectorElement)pElement2;

    return new AssumptionCollectorElement(
        AssumptionWithLocation.and(collectorElement1.getCollectedAssumptions(),
                                   collectorElement2.getCollectedAssumptions(),
                                   symbolicFormulaManager),
        (collectorElement1.isStop() || collectorElement2.isStop()));
  }

  @Override
  public boolean satisfiesPartialOrder(AbstractElement pElement1, AbstractElement pElement2) throws CPAException {
    throw new UnsupportedOperationException();
  }
}