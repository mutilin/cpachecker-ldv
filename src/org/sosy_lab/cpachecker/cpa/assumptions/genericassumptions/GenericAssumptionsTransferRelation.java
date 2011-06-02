/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.assumptions.genericassumptions;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.assumptions.AssumptionManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;

import com.google.common.collect.ImmutableList;

/**
 * Transfer relation for the generic assumption generator.
 * @author g.theoduloz
 */
public class GenericAssumptionsTransferRelation implements TransferRelation {

  /**
   * List of interfaces used to build the default
   * assumptions made by the model checker for
   * program operations.
   *
   * Modify this to register new kind of assumptions.
   */
  private final List<GenericAssumptionBuilder> assumptionBuilders =
    ImmutableList.<GenericAssumptionBuilder>of(
        new ArithmeticOverflowAssumptionBuilder());

  private final AssumptionManager manager;

  /**
   * Constructor
   */
  public GenericAssumptionsTransferRelation(AssumptionManager manager)
  {
    this.manager = manager;
  }

  @Override
  public Collection<? extends AbstractElement> getAbstractSuccessors(AbstractElement el, Precision p, CFAEdge edge)
  throws CPATransferException
  {
    String function = (edge.getSuccessor() != null) ? edge.getSuccessor().getFunctionName() : null;

    Formula assumptionFormula = manager.makeTrue();
    for (GenericAssumptionBuilder b : assumptionBuilders)
    {
      assumptionFormula =
        manager.makeAnd(assumptionFormula, b.assumptionsForEdge(edge), function);
    }
    return Collections.singleton(new GenericAssumptionsElement(assumptionFormula));
  }

  @Override
  public Collection<? extends AbstractElement> strengthen(
      AbstractElement el, List<AbstractElement> otherElements,
      CFAEdge edge, Precision p)
      throws CPATransferException {
    // TODO Improve strengthening for assumptions so that they
    //      may be discharged online
    return null;
  }

}
