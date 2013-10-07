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
package org.sosy_lab.cpachecker.util.predicates;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.Model;
import org.sosy_lab.cpachecker.core.Model.AssignableTerm;
import org.sosy_lab.cpachecker.core.Model.Variable;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.SolverException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.ProverEnvironment;
import org.sosy_lab.cpachecker.util.predicates.interpolation.CounterexampleTraceInfo;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

/**
 * This class can check feasibility of a simple path using an SMT solver.
 */
public class PathChecker {

  private final LogManager logger;
  private final PathFormulaManager pmgr;
  private final Solver solver;

  public PathChecker(LogManager pLogger, PathFormulaManager pPmgr, Solver pSolver) {
    logger = pLogger;
    pmgr = pPmgr;
    solver = pSolver;
  }

  public CounterexampleTraceInfo checkPath(List<CFAEdge> pPath) throws CPATransferException, InterruptedException {
    List<SSAMap> ssaMaps = new ArrayList<>(pPath.size());

    PathFormula pathFormula = pmgr.makeEmptyPathFormula();
    for (CFAEdge edge : pPath) {
      pathFormula = pmgr.makeAnd(pathFormula, edge);
      ssaMaps.add(pathFormula.getSsa());
    }

    BooleanFormula f = pathFormula.getFormula();

    try (ProverEnvironment thmProver = solver.newProverEnvironmentWithModelGeneration()) {
      thmProver.push(f);
      if (thmProver.isUnsat()) {
        return CounterexampleTraceInfo.infeasibleNoItp();
      } else {
        Model model = getModel(thmProver);
        model = model.withAssignmentInformation(extractVariableAssignment(pPath, ssaMaps, model));

        return CounterexampleTraceInfo.feasible(ImmutableList.of(f), model, ImmutableMap.<Integer, Boolean>of());
      }
    }
  }

  /**
   * Given a model and a path, extract the information when each variable
   * from the model was assigned.
   */
  private Multimap<CFAEdge, AssignableTerm> extractVariableAssignment(List<CFAEdge> pPath, List<SSAMap> pSsaMaps,
      Model pModel) {

    final Multimap<CFAEdge, AssignableTerm> assignedTermsPerEdge = ArrayListMultimap.create(pPath.size(), 1);

    for (AssignableTerm term : pModel.keySet()) {
      // Currently we cannot find out this information for UIFs
      // because for lookup in the SSAMap we need the parameter types.
      if (term instanceof Variable) {
        int index = findFirstOccurrenceOfVariable((Variable)term, pSsaMaps);
        if (index >= 0) {
          assignedTermsPerEdge.put(pPath.get(index), term);
        }
      }
    }

    return assignedTermsPerEdge;
  }

  /**
   * Search through an (ordered) list of SSAMaps
   * for the first index where a given variable appears.
   * @return -1 if the variable with the given index never occurs, or an index of pSsaMaps
   */
  private int findFirstOccurrenceOfVariable(Variable pVar, List<SSAMap> pSsaMaps) {
    // both indices are inclusive bounds of the range where we still need to look
    int lower = 0;
    int upper = pSsaMaps.size() - 1;

    int result = -1;

    // do binary search
    while (true) {
      if (upper-lower <= 0) {
        return result;
      }

      int index = lower + ((upper-lower) / 2);
      assert index >= lower;
      assert index <= upper;

      int ssaIndex = pSsaMaps.get(index).getIndex(pVar.getName());

      if (ssaIndex < pVar.getSSAIndex()) {
        lower = index + 1;
      } else if (ssaIndex > pVar.getSSAIndex()) {
        upper = index - 1;
      } else {
        // found a matching SSAMap,
        // but we keep looking whether there is another one with a smaller index
        assert result == -1 || result > index;
        result = index;
        upper = index - 1;
      }
    }
  }

  private <T> Model getModel(ProverEnvironment thmProver) {
    try {
      return thmProver.getModel();
    } catch (SolverException e) {
      logger.log(Level.WARNING, "Solver could not produce model, variable assignment of error path can not be dumped.");
      logger.logDebugException(e);
      return Model.empty();
    }
  }

}
