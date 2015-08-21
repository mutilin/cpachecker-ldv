/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.smg.refiner;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cpa.smg.SMGCPA;
import org.sosy_lab.cpachecker.cpa.smg.SMGState;
import org.sosy_lab.cpachecker.cpa.smg.SMGStateInformation;
import org.sosy_lab.cpachecker.util.refinement.FeasibilityChecker;
import org.sosy_lab.cpachecker.util.refinement.GenericEdgeInterpolator;
import org.sosy_lab.cpachecker.util.refinement.InterpolantManager;
import org.sosy_lab.cpachecker.util.refinement.StrongestPostOperator;

public class SMGEdgeInterpolator extends GenericEdgeInterpolator<SMGState, SMGStateInformation, SMGInterpolant> {

  public SMGEdgeInterpolator(StrongestPostOperator<SMGState> pStrongestPostOperator,
      FeasibilityChecker<SMGState> pFeasibilityChecker,
      SMGCPA pSMGCPA,
      Configuration pConfig,
      ShutdownNotifier pShutdownNotifier,
      CFA pCfa, InterpolantManager<SMGState, SMGInterpolant> pSmgInterpolantManager) throws InvalidConfigurationException {
    super(pStrongestPostOperator, pFeasibilityChecker,
        pSmgInterpolantManager,
        pSMGCPA.getInitialState(pCfa.getMainFunction()),
        SMGCPA.class, pConfig,
        pShutdownNotifier, pCfa);
  }

}
