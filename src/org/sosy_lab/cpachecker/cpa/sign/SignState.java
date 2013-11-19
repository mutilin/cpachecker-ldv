/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.sign;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.TargetableWithPredicatedAnalysis;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Sets;



public class SignState implements AbstractState, TargetableWithPredicatedAnalysis{

  private static final boolean DEBUG = false;

  private static SignTargetChecker targetChecker;

  static void init(Configuration config) throws InvalidConfigurationException{
    targetChecker = new SignTargetChecker(config);
  }

  private SignMap signMap;

  public SignMap getSignMap() {
    return signMap;
  }

  public final static SignState TOP = new SignState(ImmutableMap.<String, SIGN>of());

  public SignState(ImmutableMap<String, SIGN> pPossibleSigns) {
    signMap = new SignMap(pPossibleSigns);
  }

  public SignState union(SignState pToJoin) {
    if(pToJoin.equals(this)) {
      return this;
    }
    if(this.equals(TOP) || pToJoin.equals(TOP)) {
      return TOP;
    }
    SignState result = SignState.TOP;
    for(String varIdent : Sets.union(signMap.keySet(), pToJoin.signMap.keySet())) {
      result = result.assignSignToVariable(varIdent,
          signMap.getSignForVariable(varIdent).combineWith(pToJoin.signMap.getSignForVariable(varIdent))); // TODO performance
    }
    return result;
  }

  public boolean isSubsetOf(SignState pSuperset) {
    if(pSuperset.equals(this) || pSuperset.equals(TOP)) {
      return true;
    }
    // is subset if for every variable all sign assumptions are considered in pSuperset
    for(String varIdent : Sets.union(signMap.keySet(), pSuperset.signMap.keySet())) {
      if(!signMap.getSignForVariable(varIdent).isSubsetOf(pSuperset.signMap.getSignForVariable(varIdent))) {
        return false;
      }
    }
    return true;
  }

  public SignState assignSignToVariable(String pVarIdent, SIGN sign) {
    Builder<String, SIGN> mapBuilder = ImmutableMap.builder();
    if(!sign.isAll()) {
      mapBuilder.put(pVarIdent, sign);
    }
    for(String varIdent : signMap.keySet()) {
      if(!varIdent.equals(pVarIdent)) {
        mapBuilder.put(varIdent, signMap.getSignForVariable(varIdent));
      }
    }
    return new SignState(mapBuilder.build());
  }

  public SignState removeSignAssumptionOfVariable(String pVarIdent) {
    return assignSignToVariable(pVarIdent, SIGN.ALL);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    String delim = ", ";
    builder.append("[");
    String loopDelim = "";
    for(String key : signMap.keySet()) {
      if(!DEBUG && (key.matches("\\w*::__CPAchecker_TMP_\\w*") || key.endsWith(SignTransferRelation.FUNC_RET_VAR)) ) {
        continue;
      }
      builder.append(loopDelim);
      builder.append(key + "->" + signMap.getSignForVariable(key));
      loopDelim = delim;
    }
    builder.append("]");
    return builder.toString();
  }

  @Override
  public boolean equals(Object pObj) {
    if(!(pObj instanceof SignState)) {
      return false;
    }
    return ((SignState)pObj).getSignMap().equals(this.getSignMap());
  }

  @Override
  public int hashCode() {
    return signMap.hashCode();
  }

  @Override
  public boolean isTarget() {
    return targetChecker == null? false: targetChecker.isTarget(this);
  }

  @Override
  public ViolatedProperty getViolatedProperty() throws IllegalStateException {
    if(isTarget()){
      return ViolatedProperty.OTHER;
    }
    return null;
  }

  @Override
  public BooleanFormula getErrorCondition(FormulaManagerView pFmgr) {
    return targetChecker== null? pFmgr.getBooleanFormulaManager().makeBoolean(false):targetChecker.getErrorCondition(this, pFmgr);
  }

}
