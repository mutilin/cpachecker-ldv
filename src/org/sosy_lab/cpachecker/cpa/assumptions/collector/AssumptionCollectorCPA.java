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

import org.sosy_lab.cpachecker.util.assumptions.AssumptionWithLocation;
import org.sosy_lab.cpachecker.util.assumptions.AssumptionSymbolicFormulaManagerImpl;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.SymbolicFormulaManager;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;

import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;

/**
 * CPA used to capture the assumptions that ought to be dumped.
 *
 * Note that once the CPA algorithm has finished running, a call
 * to dumpInvariants() is needed to process the reachable states
 * and produce the actual invariants.
 *
 * @author g.theoduloz
 */
public class AssumptionCollectorCPA extends AbstractSingleWrapperCPA {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(AssumptionCollectorCPA.class);
  }

  private final AssumptionCollectorDomain abstractDomain;
  private final MergeOperator mergeOperator;
  private final StopOperator stopOperator;
  private final TransferRelation transferRelation;
  private final SymbolicFormulaManager symbolicFormulaManager;
  private final PrecisionAdjustment precisionAdjustment;

  private AssumptionCollectorCPA(ConfigurableProgramAnalysis cpa,
            Configuration config, LogManager logger) throws InvalidConfigurationException
  {
    super(cpa);
    symbolicFormulaManager = AssumptionSymbolicFormulaManagerImpl.createSymbolicFormulaManager(config, logger);
    abstractDomain = new AssumptionCollectorDomain(getWrappedCpa().getAbstractDomain());
    mergeOperator = new AssumptionCollectorMerge(getWrappedCpa());
    stopOperator = new AssumptionCollectorStop(getWrappedCpa());
    transferRelation = new AssumptionCollectorTransferRelation(this);
    precisionAdjustment = new AssumptionCollectorPrecisionAdjustment(getWrappedCpa());
  }

  public SymbolicFormulaManager getSymbolicFormulaManager()
  {
    return symbolicFormulaManager;
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return abstractDomain;
  }

  @Override
  public AbstractElement getInitialElement(CFAFunctionDefinitionNode node) {
    AbstractElement wrappedInitialElement = getWrappedCpa().getInitialElement(node);
    return new AssumptionCollectorElement(wrappedInitialElement, AssumptionWithLocation.TRUE, false);
  }

  @Override
  public MergeOperator getMergeOperator() {
    return mergeOperator;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  @Override
  public StopOperator getStopOperator() {
    return stopOperator;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }
}
