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
package org.sosy_lab.cpachecker.cpa.conditions.global;

import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.FlatLatticeDomain;
import org.sosy_lab.cpachecker.core.defaults.IdentityTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.SingletonAbstractElement;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.defaults.StaticPrecisionAdjustment;
import org.sosy_lab.cpachecker.core.defaults.StopAlwaysOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.conditions.AdjustableConditionCPA;


public class GlobalConditionsCPA implements ConfigurableProgramAnalysis, AdjustableConditionCPA {

  private final PrecisionAdjustment precisionAdjustment;
  private final GlobalConditionsThresholds thresholds;

  private final AbstractDomain domain;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(GlobalConditionsCPA.class);
  }

  private GlobalConditionsCPA(Configuration config, LogManager logger) throws InvalidConfigurationException {
    thresholds = new GlobalConditionsThresholds(config, logger);

    if (thresholds.isLimitEnabled()) {
      logger.log(Level.INFO, "Running CPAchecker with the following", thresholds);
      precisionAdjustment = new GlobalConditionsPrecisionAdjustment(logger, thresholds);

    } else {
      precisionAdjustment = StaticPrecisionAdjustment.getInstance();
    }

    domain = new FlatLatticeDomain(SingletonAbstractElement.INSTANCE);
  }

  @Override
  public boolean adjustPrecision() {
    return thresholds.adjustPrecision();
  }

  @Override
  public AbstractElement getInitialElement(CFANode pNode) {
    return SingletonAbstractElement.INSTANCE;
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode) {
    return SingletonPrecision.getInstance();
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return domain;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return MergeSepOperator.getInstance();
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  @Override
  public StopOperator getStopOperator() {
    return StopAlwaysOperator.getInstance();
  }

  @Override
  public TransferRelation getTransferRelation() {
    return IdentityTransferRelation.INSTANCE;
  }
}
