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
package org.sosy_lab.cpachecker.cpa.predicate;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sosy_lab.cpachecker.util.AbstractStates.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.algorithm.invariants.InvariantGenerator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState.ComputeAbstractionState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.predicates.AbstractionFormula;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;

import com.google.common.collect.ImmutableMap;

public class PredicatePrecisionAdjustment implements PrecisionAdjustment {

  // statistics
  final Timer totalPrecTime = new Timer();
  final Timer computingAbstractionTime = new Timer();

  int numAbstractions = 0;
  int numAbstractionsFalse = 0;
  int maxBlockSize = 0;

  private final LogManager logger;
  private final PredicateAbstractionManager formulaManager;
  private final PathFormulaManager pathFormulaManager;
  private final FormulaManagerView fmgr;
  private final BooleanFormulaManagerView bfmgr;

  private @Nullable InvariantGenerator invariantGenerator;
  private @Nullable Map<CFANode, BooleanFormula> invariants = null;

  public PredicatePrecisionAdjustment(PredicateCPA pCpa,
      InvariantGenerator pInvariantGenerator) {

    logger = pCpa.getLogger();
    formulaManager = pCpa.getPredicateManager();
    pathFormulaManager = pCpa.getPathFormulaManager();
    fmgr = pCpa.getSolver().getFormulaManager();
    bfmgr = fmgr.getBooleanFormulaManager();

    invariantGenerator = checkNotNull(pInvariantGenerator);
  }

  @Override
  public PrecisionAdjustmentResult prec(
      AbstractState pElement, Precision pPrecision,
      UnmodifiableReachedSet pElements, AbstractState fullState) throws CPAException, InterruptedException {

    totalPrecTime.start();
    try {
      PredicateAbstractState element = (PredicateAbstractState)pElement;

      if (element instanceof ComputeAbstractionState) {
        PredicatePrecision precision = (PredicatePrecision)pPrecision;

        return computeAbstraction((ComputeAbstractionState)element, precision);
      } else {
        return PrecisionAdjustmentResult.create(
            element, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE);
      }


    } finally {
      totalPrecTime.stop();
    }
  }

  /**
   * Compute an abstraction.
   */
  private PrecisionAdjustmentResult computeAbstraction(
      ComputeAbstractionState element,
      PredicatePrecision precision) throws CPAException, InterruptedException {

    AbstractionFormula abstractionFormula = element.getAbstractionFormula();
    PersistentMap<CFANode, Integer> abstractionLocations = element.getAbstractionLocationsOnPath();
    PathFormula pathFormula = element.getPathFormula();
    CFANode loc = element.getLocation();
    Integer newLocInstance = firstNonNull(abstractionLocations.get(loc), 0) + 1;

    numAbstractions++;
    logger.log(Level.FINEST, "Computing abstraction at instance", newLocInstance, "of node", loc, "in path.");

    maxBlockSize = Math.max(maxBlockSize, pathFormula.getLength());

    // get invariants and add them
    extractInvariants();
    BooleanFormula invariant = invariants.get(loc);
    if (invariant != null) {
      pathFormula = pathFormulaManager.makeAnd(pathFormula, invariant);
    }

    AbstractionFormula newAbstractionFormula = null;

    // compute new abstraction
    computingAbstractionTime.start();
    try {
      Set<AbstractionPredicate> preds = precision.getPredicates(loc, newLocInstance);

      // compute a new abstraction with a precision based on `preds`
      newAbstractionFormula = formulaManager.buildAbstraction(
          loc, abstractionFormula, pathFormula, preds);
    } finally {
      computingAbstractionTime.stop();
    }

    // if the abstraction is false, return bottom (represented by empty set)
    if (newAbstractionFormula.isFalse()) {
      numAbstractionsFalse++;
      logger.log(Level.FINEST, "Abstraction is false, node is not reachable");
      return PrecisionAdjustmentResult.bottom();
    }

    // create new empty path formula
    PathFormula newPathFormula = pathFormulaManager.makeEmptyPathFormula(pathFormula);

    // initialize path formula with current invariants
    if (invariant != null) {
      newPathFormula = pathFormulaManager.makeAnd(newPathFormula, invariant);
    }

    // update abstraction locations map
    abstractionLocations = abstractionLocations.putAndCopy(loc, newLocInstance);

    PredicateAbstractState state =
        PredicateAbstractState.mkAbstractionState(newPathFormula,
            newAbstractionFormula, abstractionLocations);
    return PrecisionAdjustmentResult.create(
        state, precision, PrecisionAdjustmentResult.Action.CONTINUE);
  }

  private void extractInvariants() throws CPAException {
    if (invariants != null) {
      return; // already done
    }

    try {

      UnmodifiableReachedSet reached = invariantGenerator.get();
      if (reached.isEmpty()) {
        invariants = ImmutableMap.of(); // no invariants available
        return;
      }

      invariants = new HashMap<>();

      for (AbstractState state : reached) {
        BooleanFormula invariant = extractReportedFormulas(fmgr, state);

        if (!bfmgr.isTrue(invariant)) {
          CFANode loc = extractLocation(state);
          BooleanFormula oldInvariant = invariants.get(loc);
          if (oldInvariant != null) {
            invariant = bfmgr.or(invariant, oldInvariant);
          }

          invariants.put(loc, invariant);
        }
      }

      invariants = ImmutableMap.copyOf(invariants);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      invariants = ImmutableMap.of(); // no invariants available

    } finally {
      invariantGenerator = null; // to allow GC'ing it and the ReachedSet
    }
  }

  void setInitialLocation(CFANode initialLocation) {
    invariantGenerator.start(initialLocation);
  }
}
