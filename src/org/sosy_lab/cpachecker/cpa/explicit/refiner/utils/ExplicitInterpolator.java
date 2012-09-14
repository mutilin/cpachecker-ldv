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
package org.sosy_lab.cpachecker.cpa.explicit.refiner.utils;

import static com.google.common.collect.Iterables.skip;

import java.util.Collection;
import java.util.Map;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.Path;
import org.sosy_lab.cpachecker.cpa.explicit.ExplicitPrecision;
import org.sosy_lab.cpachecker.cpa.explicit.ExplicitState;
import org.sosy_lab.cpachecker.cpa.explicit.ExplicitTransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CounterexampleAnalysisFailed;
import org.sosy_lab.cpachecker.util.VariableClassification;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

public class ExplicitInterpolator {

  private Configuration config = null;

  private ExplicitTransferRelation transfer = null;

  /**
   * This method acts as the constructor of the class.
   */
  public ExplicitInterpolator() throws CPAException {
    try {
      config    = Configuration.builder().build();
      transfer  = new ExplicitTransferRelation(config);
    }

    catch(InvalidConfigurationException e) {
      throw new CounterexampleAnalysisFailed("Invalid configuration for checking path: " + e.getMessage(), e);
    }
  }


  private boolean isFeasible = false;

  public Pair<ARGState, CFAEdge> blockingElement = null;
public boolean DEBUG = false;
  /**
   * This method derives an interpolant for the given error path and interpolation state.
   *
   * @param errorPath the path to check
   * @param offset offset of the state at where to start the current interpolation
   * @param currentVariable the variable on which the interpolation is performed on
   * @param inputInterpolant the input interpolant
   * @throws CPAException
   * @throws InterruptedException
   */
  public Pair<String, Long> deriveInterpolant(
      Path errorPath,
      int offset,
      String currentVariable,
      Map<String, Long> currentInterpolant) throws CPAException, InterruptedException {
    try {
      ExplicitState successor     = new ExplicitState(currentInterpolant);
      ExplicitPrecision precision = new ExplicitPrecision("", config, Optional.<VariableClassification>absent());

      Long currentVariableValue     = null;
      boolean performedAbstraction = false;

      Pair<ARGState, CFAEdge> interpolationState = errorPath.get(offset);

      for(Pair<ARGState, CFAEdge> pathElement : skip(errorPath, offset)) {

        if(interpolationState == blockingElement) {
          return null;
        }

        Collection<ExplicitState> successors = transfer.getAbstractSuccessors(
            successor,
            precision,
            pathElement.getSecond());

        successor = extractSuccessorState(successors);

        // there is no successor, but current path element is not an error state => error path is spurious
        if(successor == null && !pathElement.getFirst().isTarget()) {
          //currentInterpolant.remove(currentVariable);

          if(isFeasible) {
            blockingElement = pathElement;
          }

          isFeasible = false;
if(DEBUG) System.out.println("\t\tinfeasible");
          return Pair.<String, Long>of(currentVariable, null);
        }

        // remove the value of the current variable from the successor
        if(!performedAbstraction) {
          performedAbstraction = true;

          if(successor.contains(currentVariable)) {
            currentVariableValue = successor.getValueFor(currentVariable);
          }
          successor.forget(currentVariable);
        }
      }
/*
      if(currentVariableValue == null) {
        currentInterpolant.remove(currentVariable);
      } else {
        currentInterpolant.put(currentVariable, currentVariableValue);
      }
*/
      isFeasible = true;
if(DEBUG) System.out.println("\t\tFEASABLE");
      // path is feasible
      return Pair.<String, Long>of(currentVariable, currentVariableValue);
    } catch(InvalidConfigurationException e) {
      throw new CounterexampleAnalysisFailed("Invalid configuration for checking path: " + e.getMessage(), e);
    }
  }

  /**
   * This method returns whether or not the last error path was feasible.
   *
   * @return whether or not the last error path was feasible
   */
  public boolean isFeasible() {
    return isFeasible;
  }

  /**
   * This method extracts the single successor out of the (hopefully singleton) successor collection.
   *
   * @param successors the collection of successors
   * @return the successor, or null if none exists
   */
  private ExplicitState extractSuccessorState(Collection<ExplicitState> successors) {
    if(successors.isEmpty()) {
      return null;
    }
    else {
      assert(successors.size() == 1);
      return Lists.newArrayList(successors).get(0);
    }
  }
}