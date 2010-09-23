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
package org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA;

import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA.util.symbpredabstraction.PathFormula;
import org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA.util.symbpredabstraction.interfaces.FormulaManager;


/**
 * Merge operator for symbolic predicate abstraction.
 * This is not a trivial merge operator in the sense that it implements
 * mergeSep and mergeJoin together. If the abstract element is on an
 * abstraction location we don't merge, otherwise we merge two elements
 * and update the {@link SymbPredAbsAbstractElement}'s pathFormula.
 *
 * @author Erkan
 */
public class SymbPredAbsMergeOperator implements MergeOperator {

  private final LogManager logger;
  private final FormulaManager formulaManager;

  long totalMergeTime = 0;

  public SymbPredAbsMergeOperator(SymbPredAbsCPA pCpa) {
    this.logger = pCpa.getLogger();
    formulaManager = pCpa.getFormulaManager();
  }

  @Override
  public AbstractElement merge(AbstractElement element1,
                               AbstractElement element2, Precision precision) {

    SymbPredAbsAbstractElement elem1 = (SymbPredAbsAbstractElement)element1;
    SymbPredAbsAbstractElement elem2 = (SymbPredAbsAbstractElement)element2;

    // this will be the merged element
    SymbPredAbsAbstractElement merged;

    if (elem1.isAbstractionNode() || elem2.isAbstractionNode()) {
      // we don't merge if this is an abstraction location
      merged = elem2;
    } else {
      // don't merge if the elements are in different blocks (they have different abstraction ids)
      if (elem1.getAbstractionId() != elem2.getAbstractionId()) {
        merged = elem2;
      
      } else {
        long start = System.currentTimeMillis();
        // create a new element, note that their abstraction formulas, initAbstractionFormula,
        // abstraction locations, artParents are same because they have the same
        // abstraction path
        assert (elem1.getAbstraction().equals(elem2.getAbstraction()));
        assert (elem1.getInitAbstractionFormula() == elem2.getInitAbstractionFormula());
        assert (elem1.getAbstractionLocation() == elem2.getAbstractionLocation());

        logger.log(Level.FINEST, "Merging two non-abstraction nodes.");

        PathFormula pathFormula = formulaManager.makeOr(elem1.getPathFormula(), elem2.getPathFormula());

        logger.log(Level.ALL, "New path formula is", pathFormula);
                
        merged = new SymbPredAbsAbstractElement(elem1.getAbstractionLocation(), 
            pathFormula, elem1.getInitAbstractionFormula(), elem1.getAbstraction(), 
            elem1.getAbstractionId(),
            Math.max(elem1.getSizeSinceAbstraction(), elem2.getSizeSinceAbstraction()));

        // now mark elem1 so that coverage check can find out it was merged
        elem1.setMergedInto(merged);
        
        long end = System.currentTimeMillis();
        totalMergeTime = totalMergeTime + (end - start);
      }
    }

    return merged;
  }

}
