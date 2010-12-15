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
package org.sosy_lab.cpachecker.cpa.assumptions.storage;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.sosy_lab.cpachecker.util.AbstractElements;
import org.sosy_lab.cpachecker.util.assumptions.AssumptionReportingElement;
import org.sosy_lab.cpachecker.util.assumptions.AvoidanceReportingElement;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;

import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;

/**
 * Transfer relation and strengthening for the DumpInvariant CPA
 * @author g.theoduloz
 */
public class AssumptionStorageTransferRelation implements TransferRelation {

  private final Collection<AbstractElement> topElementSet;

  private final FormulaManager formulaManager;
  
  public AssumptionStorageTransferRelation(FormulaManager pManager, AbstractElement topElement) {
    formulaManager = pManager;
    topElementSet = Collections.singleton(topElement);
  }

  @Override
  public Collection<? extends AbstractElement> getAbstractSuccessors(
      AbstractElement pElement, Precision pPrecision, CFAEdge pCfaEdge) {
    AssumptionStorageElement element = (AssumptionStorageElement)pElement;

    // If we must stop, then let's stop by returning an empty set
    if (element.isStop()) {
      return Collections.emptySet();
    }
    
    return topElementSet;
  }

  @Override
  public Collection<? extends AbstractElement> strengthen(AbstractElement el, List<AbstractElement> others, CFAEdge edge, Precision p) {
    assert ((AssumptionStorageElement)el).getAssumption().isTrue();
    
    Formula assumption = formulaManager.makeTrue();
    
    for (AbstractElement element : AbstractElements.asIterable(others)) {
      if (element instanceof AssumptionReportingElement) {
        Formula inv = ((AssumptionReportingElement)element).getAssumption();
        assumption = formulaManager.makeAnd(assumption, inv);
      }

      // process stop flag
      if (element instanceof AvoidanceReportingElement) {
        boolean stop = ((AvoidanceReportingElement)element).mustDumpAssumptionForAvoidance();
        if (stop) {
          assumption = formulaManager.makeFalse();
          break;
        }
      }
    }
    
    if (assumption.isTrue()) {
      return null;
    } else {      
      return Collections.singleton(new AssumptionStorageElement(assumption));
    }
  }
}