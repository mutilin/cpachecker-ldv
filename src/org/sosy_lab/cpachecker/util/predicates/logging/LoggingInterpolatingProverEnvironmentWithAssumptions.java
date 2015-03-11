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
package org.sosy_lab.cpachecker.util.predicates.logging;

import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.exceptions.SolverException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.InterpolatingProverEnvironmentWithAssumptions;


public class LoggingInterpolatingProverEnvironmentWithAssumptions<T> extends LoggingInterpolatingProverEnvironment<T>
                                                                     implements InterpolatingProverEnvironmentWithAssumptions<T> {

  private final InterpolatingProverEnvironmentWithAssumptions<T> wrapped;
  private final LogManager logger;

  public LoggingInterpolatingProverEnvironmentWithAssumptions(LogManager pLogger,
      InterpolatingProverEnvironmentWithAssumptions<T> pIpe) {
    super(pLogger, pIpe);
    wrapped = pIpe;
    logger = pLogger;
  }

  @Override
  public boolean isUnsatWithAssumptions(List<BooleanFormula> pAssumptions) throws SolverException, InterruptedException {
    logger.log(Level.FINE, "assumptions:", pAssumptions);
    boolean result = wrapped.isUnsatWithAssumptions(pAssumptions);
    logger.log(Level.FINE, "unsat-check returned:", result);
    return result;
  }
}
