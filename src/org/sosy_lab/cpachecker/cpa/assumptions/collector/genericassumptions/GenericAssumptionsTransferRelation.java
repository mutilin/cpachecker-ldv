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
package org.sosy_lab.cpachecker.cpa.assumptions.collector.genericassumptions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.assumptions.Assumption;
import org.sosy_lab.cpachecker.util.assumptions.AssumptionSymbolicFormulaManager;
import org.sosy_lab.cpachecker.util.symbpredabstraction.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.SymbolicFormula;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.SymbolicFormulaManager;

/**
 * Transfer relation for the generic assumption generator.
 * @author g.theoduloz
 */
public class GenericAssumptionsTransferRelation implements TransferRelation {

  /**
   * List of interfaces used to build the default
   * assumptions made by the model checker for
   * program operations.
   */
  protected final List<GenericAssumptionBuilder> assumptionBuilders;

  /**
   * Register the default set of assumption builders.
   * Modify this method to register new kind of assumptions.
   */
  private void registerDefaultAssumptionBuilders()
  {
    // arithmetic overflows
    assumptionBuilders.add(new ArithmeticOverflowAssumptionBuilder());
  }

  private final AssumptionSymbolicFormulaManager manager;
  private final SymbolicFormulaManager smgr;

  /**
   * Constructor
   */
  public GenericAssumptionsTransferRelation(AssumptionSymbolicFormulaManager manager, SymbolicFormulaManager smgr)
  {
    this.manager = manager;
    this.smgr = smgr;
    assumptionBuilders = new LinkedList<GenericAssumptionBuilder>();
    registerDefaultAssumptionBuilders();
  }

  private AbstractElement getAbstractSuccessor(AbstractElement el, CFAEdge edge, Precision p)
  throws CPATransferException
  {
    String function = (edge.getSuccessor() != null) 
    ? edge.getSuccessor().getFunctionName() : null;

    SymbolicFormula assumptionFormula = smgr.makeTrue();
    for (GenericAssumptionBuilder b : assumptionBuilders)
    {
      Pair<SymbolicFormula, SSAMapBuilder> pair = 
        manager.makeAnd(assumptionFormula, edge, b.assumptionsForEdge(edge), function);
      assumptionFormula = pair.getFirst(); 
    }
    return new GenericAssumptionsElement((new Assumption(assumptionFormula, true)).atLocation(edge.getPredecessor()));
  }

  @Override
  public Collection<AbstractElement> getAbstractSuccessors(
      AbstractElement pElement, Precision pPrecision, CFAEdge cfaEdge)
      throws CPATransferException
      {
    return Collections.singleton(getAbstractSuccessor(pElement, cfaEdge, pPrecision));
      }

  @Override
  public Collection<? extends AbstractElement> strengthen(
      AbstractElement el, List<AbstractElement> otherElements,
      CFAEdge edge, Precision p)
      throws CPATransferException
      {
    // TODO Improve strengthening for assumptions so that they
    //      may be discharged online
    return null;
      }

}
