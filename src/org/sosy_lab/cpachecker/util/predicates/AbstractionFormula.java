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
package org.sosy_lab.cpachecker.util.predicates;

import java.io.Serializable;

import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;

/**
 * Instances of this class should hold a state formula (the result of an
 * abstraction computation) in several representations:
 * First, as an abstract region (usually this would be a BDD).
 * Second, as a symbolic formula.
 * Third, again as a symbolic formula, but this time all variables have names
 * which include their SSA index at the time of the abstraction computation.
 *
 * Additionally the formula for the block immediately before the abstraction
 * computation is stored (this also has SSA indices as it is a path formula,
 * even if it is not of the type PathFormula).
 *
 * Abstractions are not considered equal even if they have the same formula.
 */
public class AbstractionFormula implements Serializable {

  private static final long serialVersionUID = -7756517128231447936L;
  private transient final Region region;
  private final BooleanFormula formula;
  private final BooleanFormula instantiatedFormula;

  /**
   * The formula of the block directly before this abstraction.
   * (This formula was used to create this abstraction).
   */
  private final BooleanFormula blockFormula;

  private static int nextId = 0;
  private final int id = nextId++;
  private BooleanFormulaManager mgr;

  public AbstractionFormula(
      FormulaManager mgr,
      Region pRegion, BooleanFormula pFormula,
      BooleanFormula pInstantiatedFormula, BooleanFormula pBlockFormula) {
    this.mgr = mgr.getBooleanFormulaManager();
    this.region = pRegion;
    this.formula = pFormula;
    this.instantiatedFormula = pInstantiatedFormula;
    this.blockFormula = pBlockFormula;
  }

  public boolean isTrue() {
    return mgr.isTrue(formula);
  }

  public boolean isFalse() {
    return mgr.isFalse(formula);
  }

  public Region asRegion() {
    return region;
  }

  /**
   * Returns the formula representation where all variables do not have SSA indices.
   */
  public BooleanFormula asFormula() {
    return formula;
  }

  /**
   * Returns the formula representation where all variables DO have SSA indices.
   */
  public BooleanFormula asInstantiatedFormula() {
    return instantiatedFormula;
  }

  public BooleanFormula getBlockFormula() {
    return blockFormula;
  }

  @Override
  public String toString() {
    // we print the formula only when it is small
    String abs = "";
    if (region.isTrue()) {
      abs = ": true";
    } else if (region.isFalse()) {
      abs = ": false";
    }
    return "ABS" + id + abs;
  }
}
