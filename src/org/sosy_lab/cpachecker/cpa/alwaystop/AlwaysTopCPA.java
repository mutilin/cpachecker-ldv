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
package org.sosy_lab.cpachecker.cpa.alwaystop;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;

/**
 * Implementation of a CPA with only one element in the abstract state space.
 * Contains various assertions that may be used to test algorithms and wrapper CPAs.
 */
public enum AlwaysTopCPA implements ConfigurableProgramAnalysis {

  INSTANCE;
  
  private static class AlwaysTopCPAFactory extends AbstractCPAFactory {

    @Override
    public ConfigurableProgramAnalysis createInstance() {
      return INSTANCE;
    }
  }

  public static CPAFactory factory() {
    return new AlwaysTopCPAFactory();
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return AlwaysTopDomain.INSTANCE;
  }

  @Override
  public AbstractElement getInitialElement(CFANode pNode) {
    return AlwaysTopElement.INSTANCE;
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode) {
    return AlwaysTopPrecision.INSTANCE;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return AlwaysTopMergeOperator.INSTANCE;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return AlwaysTopPrecisionAdjustment.INSTANCE;
  }

  @Override
  public StopOperator getStopOperator() {
    return AlwaysTopStopOperator.INSTANCE;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return AlwaysTopTransferRelation.INSTANCE;
  }

}
