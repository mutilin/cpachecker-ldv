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
package org.sosy_lab.cpachecker.cpa.PropertyChecker;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.pcc.PropertyChecker;
import org.sosy_lab.cpachecker.pcc.propertychecker.PropertyCheckerBuilder;

@Options
public class PropertyCheckerCPA extends AbstractSingleWrapperCPA{

  @Option(secure=true,
      name = "cpa.propertychecker.className",
      description = "Qualified name for class which checks that the computed abstraction adheres to the desired property.")
  private String checkerClass = "org.sosy_lab.cpachecker.pcc.propertychecker.DefaultPropertyChecker";
  @Option(secure=true,
      name = "cpa.propertychecker.parameters",
      description = "List of parameters for constructor of propertychecker.className. Parameter values are " +
          "specified in the order the parameters are defined in the respective constructor. Every parameter value is finished " +
          "with \",\". The empty string represents an empty parameter list.")
  private String checkerParamList = "";

  private PropertyChecker propChecker;

  public PropertyCheckerCPA(ConfigurableProgramAnalysis pCpa, Configuration pConfig)
      throws InvalidConfigurationException {
    super(pCpa);
    pConfig.inject(this);
    propChecker = PropertyCheckerBuilder.buildPropertyChecker(checkerClass, checkerParamList);
  }

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(PropertyCheckerCPA.class);
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return getWrappedCpa().getAbstractDomain();
  }

  @Override
  public TransferRelation getTransferRelation() {
    return getWrappedCpa().getTransferRelation();
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
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) {
    return getWrappedCpa().getInitialState(pNode, pPartition);
  }

  public PropertyChecker getPropChecker() {
    return propChecker;
  }

}
