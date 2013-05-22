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
package org.sosy_lab.cpachecker.cpa.explicit.refiner;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Timer;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.IAExpression;
import org.sosy_lab.cpachecker.cfa.ast.IAStatement;
import org.sosy_lab.cpachecker.cfa.ast.IAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.explicit.refiner.utils.ExplicitInterpolator;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

@Options(prefix="cpa.explicit.refiner")
public class ExplicitInterpolationBasedExplicitRefiner implements Statistics {
  /**
   * whether or not to do lazy-abstraction, i.e., when true, the re-starting node
   * for the re-exploration of the ARG will be the node closest to the root
   * where new information is made available through the current refinement
   */
  @Option(description="whether or not to do lazy-abstraction")
  private boolean doLazyAbstraction = false;

  @Option(description="whether or not to avoid restarting at assume edges after a refinement")
  private boolean avoidAssumes = false;

  /**
   * the offset in the path from where to cut-off the subtree, and restart the analysis
   */
  private int interpolationOffset = -1;

  // statistics
  private int numberOfRefinements           = 0;
  private int numberOfSuccessfulRefinements = 0;
  private int numberOfInterpolations        = 0;
  private Timer timerInterpolation          = new Timer();

  protected ExplicitInterpolationBasedExplicitRefiner(Configuration config, PathFormulaManager pathFormulaManager)
      throws InvalidConfigurationException {
    config.inject(this);
  }

  protected Multimap<CFANode, String> determinePrecisionIncrement(UnmodifiableReachedSet reachedSet,
      ARGPath errorPath) throws CPAException {
    timerInterpolation.start();
    numberOfRefinements++;

    ExplicitInterpolator interpolator     = new ExplicitInterpolator();
    Map<String, Long> currentInterpolant  = new HashMap<>();
    Multimap<CFANode, String> increment   = HashMultimap.create();
    interpolationOffset                   = -1;

    for (int i = 0; i < errorPath.size(); i++) {
      CFAEdge currentEdge = errorPath.get(i).getSecond();

      if (currentEdge instanceof BlankEdge) {
        continue;
      }
      else if (currentEdge instanceof CFunctionReturnEdge) {
        currentEdge = ((CFunctionReturnEdge)currentEdge).getSummaryEdge();
      }

      // do interpolation
      Map<String, Long> inputInterpolant = new HashMap<>(currentInterpolant);
      try {
        Set<Pair<String, Long>> interpolant = interpolator.deriveInterpolant(errorPath, i, inputInterpolant);
        numberOfInterpolations += interpolator.getNumberOfInterpolations();

        // early stop once we are past the first statement that made a path feasible for the first time
        if (interpolant == null) {
          timerInterpolation.stop();
          return increment;
        }
        for (Pair<String, Long> element : interpolant) {
          if (element.getSecond() == null) {
            currentInterpolant.remove(element.getFirst());
          } else {
            currentInterpolant.put(element.getFirst(), element.getSecond());
          }
        }
      }
      catch (InterruptedException e) {
        throw new CPAException("Explicit-Interpolation failed: ", e);
      }

      // remove variables from the interpolant that belong to the scope of the returning function
      // this is done one iteration after returning from the function, as the special FUNCTION_RETURN_VAR is needed that long
      if (i > 0 && errorPath.get(i - 1).getSecond().getEdgeType() == CFAEdgeType.ReturnStatementEdge) {
        currentInterpolant = clearInterpolant(currentInterpolant, errorPath.get(i - 1).getSecond().getSuccessor().getFunctionName());
      }

      // add the current interpolant to the increment
      for (String variableName : currentInterpolant.keySet()) {
        if (interpolationOffset == -1) {
          interpolationOffset = i + 1;
          numberOfSuccessfulRefinements++;
        }

        increment.put(currentEdge.getSuccessor(), variableName);
      }
    }

    timerInterpolation.stop();
    return increment;
  }

  /**
   * This method removes variables from the interpolant that belong to the scope of the given function.
   *
   * @param currentInterpolant the current interpolant
   * @param functionName the name of the function for which to remove variables
   * @return the current interpolant with the respective variables removed
   */
  private Map<String, Long> clearInterpolant(Map<String, Long> currentInterpolant, String functionName) {
    for (Iterator<String> variableNames = currentInterpolant.keySet().iterator(); variableNames.hasNext(); ) {
      if (variableNames.next().startsWith(functionName + "::")) {
        variableNames.remove();
      }
    }

    return currentInterpolant;
  }

  /**
   * This method determines the new interpolation point.
   *
   * @param errorPath the error path from where to determine the interpolation point
   * @return the new interpolation point
   */
  protected Pair<ARGState, CFAEdge> determineInterpolationPoint(ARGPath errorPath, Multimap<CFANode, String> increment) {
    // if doing lazy abstraction, use the node closest to the root node where new information is present
    if (doLazyAbstraction) {
      // try to find a more suitable cut-off point when cut-off is an assume edge, and this should be avoided
      if(avoidAssumes && cutOffIsAssumeEdge(errorPath)) {
        HashSet<String> values = new HashSet<>(increment.values());

        for (Pair<ARGState, CFAEdge> currentElement : Lists.reverse(errorPath.subList(0, interpolationOffset - 1))) {
          CFAEdge currentEdge = currentElement.getSecond();

          switch (currentElement.getSecond().getEdgeType()) {
            case StatementEdge:
            case DeclarationEdge:
              if(isAssigningEdge(currentEdge, values)) {
                return errorPath.get(errorPath.indexOf(currentElement) + 1);
              }
              break;

            case MultiEdge:
              for (CFAEdge singleEdge : ((MultiEdge)currentEdge)) {
                if (isAssigningEdge(singleEdge, values)) {
                  return errorPath.get(errorPath.indexOf(currentElement) + 1);
                }
              }
              break;

            default:
              break;
          }
        }
      }

      return errorPath.get(interpolationOffset);
    }

    // otherwise, just use the successor of the root node
    else {
      return errorPath.get(1);
    }
  }

  /**
   * This method checks whether or not the current cut-off point is at an assume edge.
   *
   * @param errorPath the error path
   * @return true, if the current cut-off point is at an assume edge, else false
   */
  private boolean cutOffIsAssumeEdge(ARGPath errorPath) {
    return errorPath.get(Math.max(1, interpolationOffset - 1)).getSecond().getEdgeType() == CFAEdgeType.AssumeEdge;
  }

  /**
   * This method determines whether or not the current edge is assigning any of the given variables.
   *
   * @param currentEdge the current edge to inspect
   * @param variableNames the collection of variables to check for
   * @return true, if any of the given variables is assigned in the given edge
   */
  private boolean isAssigningEdge(CFAEdge currentEdge, Set<String> variableNames) {
    if(currentEdge.getEdgeType() == CFAEdgeType.StatementEdge) {
      IAStatement statement = ((AStatementEdge)currentEdge).getStatement();

      if (statement instanceof IAssignment) {
        IAExpression assignedVariable = ((IAssignment)statement).getLeftHandSide();
        if((assignedVariable instanceof AIdExpression) && variableNames.contains(((AIdExpression)assignedVariable).getName())) {
          return true;
        }
      }
    }

    else if (currentEdge.getEdgeType() == CFAEdgeType.DeclarationEdge) {
      ADeclarationEdge declEdge = ((ADeclarationEdge)currentEdge);
      if(declEdge.getDeclaration() instanceof CVariableDeclaration) {
        String declaredVariable = ((CVariableDeclaration)declEdge.getDeclaration()).getQualifiedName();
        if(variableNames.contains(declaredVariable)) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public String getName() {
    return "Explicit Interpolation-Based Refiner";
  }

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
    out.println("  number of explicit refinements:                      " + numberOfRefinements);
    out.println("  number of successful explicit refinements:           " + numberOfSuccessfulRefinements);
    out.println("  number of explicit interpolations:                   " + numberOfInterpolations);
    out.println("  max. time for singe interpolation:                   " + timerInterpolation.printMaxTime());
    out.println("  total time for interpolation:                        " + timerInterpolation);
  }
}