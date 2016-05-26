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
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath.PathIterator;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.refinement.EdgeInterpolator;
import org.sosy_lab.cpachecker.util.refinement.Interpolant;
import org.sosy_lab.cpachecker.util.refinement.InterpolationTree;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SMGPathInterpolator {

  /**
   * the offset in the path from where to cut-off the subtree, and restart the analysis
   */
  protected int interpolationOffset = -1;

  private final StatCounter totalInterpolations   = new StatCounter("Number of interpolations");
  private final StatInt totalInterpolationQueries = new StatInt(StatKind.SUM, "Number of interpolation queries");

  private final ShutdownNotifier shutdownNotifier;
  private final SMGEdgeInterpolator interpolator;
  private final SMGInterpolantManager interpolantManager;


  public SMGPathInterpolator(ShutdownNotifier pShutdownNotifier,
      SMGInterpolantManager pInterpolantManager,
      SMGEdgeInterpolator pInterpolator) {
    shutdownNotifier = pShutdownNotifier;
    interpolantManager = pInterpolantManager;
    interpolator = pInterpolator;
  }

  public Map<ARGState, SMGInterpolant> performInterpolation(ARGPath pErrorPath,
      SMGInterpolant pInterpolant) throws InterruptedException, CPAException {
    totalInterpolations.inc();

    interpolationOffset = -1;

//    ARGPath errorPathPrefix = performRefinementSelection(pErrorPath, pInterpolant);

    Map<ARGState, SMGInterpolant> interpolants =
        performEdgeBasedInterpolation(pErrorPath, pInterpolant);

    propagateFalseInterpolant(pErrorPath, pErrorPath, interpolants);

    return interpolants;
  }

  /**
   * This method propagates the interpolant "false" to all states that are in
   * the original error path, but are not anymore in the (shorter) prefix.
   *
   * The property that every state on the path beneath the first state with an
   * false interpolant is needed by some code in ValueAnalysisInterpolationTree
   * a subclass of {@link InterpolationTree}, i.e., for global refinement. This
   * property could also be enforced there, but interpolant creation should only
   * happen during interpolation, and not in the data structure holding the interpolants.
   *
   * @param errorPath the original error path
   * @param pErrorPathPrefix the possible shorter error path prefix
   * @param pInterpolants the current interpolant map
   */
  private final void propagateFalseInterpolant(final ARGPath errorPath,
      final ARGPath pErrorPathPrefix,
      final Map<ARGState, SMGInterpolant> pInterpolants) {
    if (pErrorPathPrefix.size() < errorPath.size()) {
      PathIterator it = errorPath.pathIterator();
      for (int i = 0; i < pErrorPathPrefix.size(); i++) {
        it.advance();
      }
      for (ARGState state : it.getSuffixInclusive().asStatesList()) {
        pInterpolants.put(state, interpolantManager.getFalseInterpolant());
      }
    }
  }

  /**
   * This method performs interpolation on each edge of the path, using the
   * {@link EdgeInterpolator} given to this object at construction.
   *
   * @param pErrorPathPrefix the error path prefix to interpolate
   * @param pInterpolant an initial interpolant
   *    (only non-trivial when interpolating error path suffixes in global refinement)
   * @return the mapping of {@link ARGState}s to {@link Interpolant}
   */
  private Map<ARGState, SMGInterpolant> performEdgeBasedInterpolation(
      ARGPath pErrorPathPrefix,
      SMGInterpolant pInterpolant
  ) throws InterruptedException, CPAException {

//    pErrorPathPrefix = sliceErrorPath(pErrorPathPrefix);

    Map<ARGState, SMGInterpolant> pathInterpolants = new LinkedHashMap<>(pErrorPathPrefix.size());

    PathIterator pathIterator = pErrorPathPrefix.pathIterator();

    List<SMGInterpolant> interpolants = new ArrayList<>();
    interpolants.add(pInterpolant);

    while (pathIterator.hasNext()) {

      List<SMGInterpolant> resultingInterpolants = new ArrayList<>();

      for(SMGInterpolant interpolant : interpolants) {
        shutdownNotifier.shutdownIfNecessary();

        // interpolate at each edge as long as the previous interpolant is not false
        if (!interpolant.isFalse()) {
          List<SMGInterpolant> deriveResult = interpolator.deriveInterpolant(
              pathIterator.getOutgoingEdge(),
              pathIterator.getPosition(),
              pInterpolant);
          resultingInterpolants.addAll(deriveResult);
        } else {
          resultingInterpolants.add(interpolantManager.getFalseInterpolant());
        }

        totalInterpolationQueries.setNextValue(interpolator.getNumberOfInterpolationQueries());

        if (!interpolant.isTrivial() && interpolationOffset == -1) {
          interpolationOffset = pathIterator.getIndex();
        }

        pathIterator.advance();
      }

      SMGInterpolant jointResultInterpolant = joinInterpolants(resultingInterpolants);

      pathInterpolants.put(pathIterator.getAbstractState(), jointResultInterpolant);
      interpolants.clear();
      interpolants.addAll(resultingInterpolants);
    }

    return pathInterpolants;
  }

  private SMGInterpolant joinInterpolants(List<SMGInterpolant> pResultingInterpolants) {

    SMGInterpolant result = null;

    for (SMGInterpolant interpolant : pResultingInterpolants) {
      if (result == null) {
        result = interpolant;
      } else {
        result = result.join(interpolant);
      }
    }

    return result;
  }
}