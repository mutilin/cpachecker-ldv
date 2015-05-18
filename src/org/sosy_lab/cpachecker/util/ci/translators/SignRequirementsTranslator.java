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
package org.sosy_lab.cpachecker.util.ci.translators;

import java.util.ArrayList;
import java.util.List;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.cpa.sign.SIGN;
import org.sosy_lab.cpachecker.cpa.sign.SignState;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;

import com.google.common.base.Preconditions;


public class SignRequirementsTranslator extends CartesianRequirementsTranslator<SignState> {

  public SignRequirementsTranslator(Configuration pConfig, ShutdownNotifier pShutdownNotifier, LogManager pLog) {
    super(SignState.class, pConfig, pShutdownNotifier, pLog);
  }

  @Override
  protected List<String> getVarsInRequirements(SignState pRequirement) {
    return new ArrayList<>(pRequirement.getSignMapView().keySet());
  }

  @Override
  protected List<String> getListOfIndependentRequirements(SignState pRequirement, SSAMap pIndices) {
    List<String> list = new ArrayList<>();
    for (String var : pRequirement.getSignMapView().keySet()) {
      list.add(getRequirement(getVarWithIndex(var, pIndices),pRequirement.getSignMapView().get(var)));
    }
    return list;
  }

  public String getRequirement(String var, SIGN sign) {
    StringBuilder sb = new StringBuilder();
    Preconditions.checkArgument(sign != SIGN.EMPTY);
    Preconditions.checkArgument(sign != SIGN.ALL);

    if (sign.covers(SIGN.PLUS)) {
      sb.append("(> ");
      sb.append(var);
      sb.append(" 0)");

    } else if (sign.covers(SIGN.MINUS)) {
      sb.append("(< ");
      sb.append(var);
      sb.append(" 0)");

    } else if (sign.covers(SIGN.ZERO)) {
      sb.append("(= ");
      sb.append(var);
      sb.append(" 0)");

    } else if (sign.covers(SIGN.PLUSMINUS)) {
      sb.append("(or (> ");
      sb.append(var);
      sb.append(" 0) (< ");
      sb.append(var);
      sb.append(" 0))");

    } else if (sign.covers(SIGN.PLUS0)) {
      sb.append("(>= ");
      sb.append(var);
      sb.append(" 0)");

    } else if (sign.covers(SIGN.MINUS0)) {
      sb.append("(<= ");
      sb.append(var);
      sb.append(" 0)");
    }

    return sb.toString();
  }

}
