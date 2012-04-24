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
package org.sosy_lab.cpachecker.cpa.explicit;

import java.io.PrintStream;

import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.explicit.refiner.DelegatingExplicitRefiner;
import org.sosy_lab.cpachecker.util.predicates.interpolation.AbstractInterpolationBasedRefiner;

public class ExplicitCPAStatistics implements Statistics {

  private AbstractInterpolationBasedRefiner<?, ?> refiner = null;

  public ExplicitCPAStatistics() {}

  @Override
  public String getName() {
    return "ExplicitCPA";
  }

  public void addRefiner(AbstractInterpolationBasedRefiner<?, ?> refiner) {
    this.refiner = refiner;
  }

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
    if(refiner != null) {
      if(refiner instanceof DelegatingExplicitRefiner) {
        ((DelegatingExplicitRefiner)refiner).printStatistics(out, result, reached);
      }
    }
  }
}
