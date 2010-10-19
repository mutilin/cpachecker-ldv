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
package org.sosy_lab.cpachecker.util.assumptions;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.util.assumptions.AssumptionSymbolicFormulaManagerImpl.DummySSAMap;
import org.sosy_lab.cpachecker.util.symbpredabstraction.SSAMap;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.SymbolicFormula;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.SymbolicFormulaManager;

/**
 * Representation of an assumption formula talking about data
 * (not location). Immutable data structure.
 * @author g.theoduloz
 */
public class Assumption {

  private static SymbolicFormulaManager manager = AssumptionSymbolicFormulaManagerImpl.getSymbolicFormulaManager();
  {
    // hopefully we have an instance already
    assert manager != null;
  }

  public static final Assumption TRUE = new Assumption();
  public static final Assumption FALSE = new Assumption(manager.makeFalse(), false, null);

  private final SymbolicFormula dischargeableAssumption;
  private final SymbolicFormula otherAssumption;
  private SSAMap ssaMap;

  public Assumption(SymbolicFormula dischargeable, SymbolicFormula rest, SSAMap pSSAMap)
  {
    dischargeableAssumption = dischargeable;
    otherAssumption = rest;
    ssaMap = pSSAMap;
  }

  public Assumption(SymbolicFormula assumption, boolean isDischargeable, SSAMap pSSAMap)
  {
    dischargeableAssumption = isDischargeable ? assumption : manager.makeTrue();
    otherAssumption = isDischargeable ? manager.makeTrue() : assumption;
    ssaMap = pSSAMap;
  }

  /** Constructs an invariant corresponding to true */
  public Assumption()
  {
    dischargeableAssumption = manager.makeTrue();
    otherAssumption = manager.makeTrue();
    ssaMap = null;
  }

  public SymbolicFormula getDischargeableFormula() {
    return dischargeableAssumption;
  }

  public SymbolicFormula getOtherFormula() {
    return otherAssumption;
  }

  /**
   * Return a formula representing all assumptions
   * contained in this invariant
   */
  public SymbolicFormula getAllFormula() {
    return manager.makeAnd(dischargeableAssumption, otherAssumption);
  }

  /**
   * Conjunct this invariant with an other invariant and
   * return the result
   */
  public Assumption and(Assumption other)
  {
    // shortcut
    if (this == TRUE)
      return other;
    else if (other == TRUE)
      return this;
    
    SymbolicFormula newDischargeable = manager.makeAnd(dischargeableAssumption, other.dischargeableAssumption);
    SymbolicFormula newOther = manager.makeAnd(otherAssumption, other.otherAssumption);
    SSAMap ssaMap1 = this.getSsaMap();
    SSAMap ssaMap2 = other.getSsaMap();
    if(ssaMap1 == null) ssaMap1 = new DummySSAMap().build();
    if(ssaMap2 == null) ssaMap2 = new DummySSAMap().build();
    SSAMap newSSAMap = SSAMap.merge(ssaMap1, ssaMap2);
    return new Assumption(newDischargeable, newOther, newSSAMap);
  }

  /**
   * Check whether an invariant is true
   */
  public boolean isTrue() {
    // shortcut
    if (this == TRUE)
      return true;
    else
      return dischargeableAssumption.isTrue()
      && otherAssumption.isTrue();
  }

  public boolean isFalse() {
    if (this == FALSE)
      return true;
    else if (this == TRUE)
      return false;
    else
      return dischargeableAssumption.isFalse()
      || otherAssumption.isFalse();
  }
  
  public SSAMap getSsaMap() {
    return ssaMap;
  }

  public void setSSAMap(SSAMap pSSAMap){
    ssaMap = pSSAMap;
  }
  
  /**
   * Return an assumption with location for the given
   * location
   */
  public AssumptionWithLocation atLocation(CFANode node)
  {
    if (isTrue())
      return AssumptionWithLocation.TRUE;
    else
      return new AssumptionWithSingleLocation(node, this);
  }
  
  @Override
  public String toString() {
    return "Formula: " + dischargeableAssumption;
    //+ " SSAMap: " + ssaMap;
  }
  
}
