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
package org.sosy_lab.cpachecker.cpa.boundedrecursion;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.abm.ABMLockCPA;

@Options(prefix="cpa.boundedrecursion")
public class BoundedRecursionCPA extends AbstractSingleWrapperCPA {

  //private BoundedRecursionDomain abstractDomain;
  //private MergeOperator mergeOperator;
  //private StopOperator stopOperator;
  private TransferRelation transferRelation;
  //private PrecisionAdjustment precisionAdjustment;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(BoundedRecursionCPA.class);
  }

  private BoundedRecursionCPA(ConfigurableProgramAnalysis pCpa, CFA pCfa, LogManager pLogger, Configuration pConfig) throws InvalidConfigurationException {
    super(pCpa);
    if (pCpa instanceof ABMLockCPA)
      ((ABMLockCPA)pCpa).getTransferRelation().changeAlgorithm(this, pConfig);
    //this.abstractDomain = new BoundedRecursionDomain(pCpa.getAbstractDomain());
    //this.mergeOperator = initializeMergeOperator();
    //this.stopOperator = getWrappedCpa().getStopOperator();
    this.transferRelation = new BoundedRecursionTransferRelation(pCpa.getTransferRelation(), pConfig, pLogger);
    //this.precisionAdjustment = new BoundedRecursionPrecisionAdjustment(pCpa.getPrecisionAdjustment());
  }

  /*private MergeOperator initializeMergeOperator() {
    return MergeSepOperator.getInstance();
  }*/

  @Override
  public AbstractDomain getAbstractDomain() {
    return getWrappedCpa().getAbstractDomain();
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return getWrappedCpa().getMergeOperator();
  }

  @Override
  public StopOperator getStopOperator() {
    return getWrappedCpa().getStopOperator();
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return getWrappedCpa().getPrecisionAdjustment();
  }

  @Override
  public AbstractState getInitialState(CFANode pNode) {
    return getWrappedCpa().getInitialState(pNode);
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode) {
    return getWrappedCpa().getInitialPrecision(pNode);
  }
}
