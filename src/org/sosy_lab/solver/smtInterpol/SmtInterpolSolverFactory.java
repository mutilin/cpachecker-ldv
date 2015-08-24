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
package org.sosy_lab.solver.smtInterpol;

import javax.annotation.Nullable;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.io.PathCounterTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.solver.FormulaManagerFactory;
import org.sosy_lab.solver.FormulaManagerFactory.SolverFactory;
import org.sosy_lab.solver.api.FormulaManager;

/**
 * Entry point for loading SmtInterpol.
 *
 * Do not access this class directly, it needs to be loaded via
 * {@link FormulaManagerFactory}
 * because SmtInterpol needs to have it's own class loader.
 */
public class SmtInterpolSolverFactory implements SolverFactory {

  // Needs to have default constructor because FormulaManagerFactory
  // calls it reflectively

  @Override
  public FormulaManager create(Configuration pConfig, LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      @Nullable PathCounterTemplate solverLogfile, long randomSeed) throws InvalidConfigurationException {
    final Thread currentThread = Thread.currentThread();
    final ClassLoader contextClassLoader = currentThread.getContextClassLoader();
    try {
      currentThread.setContextClassLoader(SmtInterpolSolverFactory.class.getClassLoader());
      return SmtInterpolFormulaManager.create(pConfig, pLogger, pShutdownNotifier, solverLogfile, randomSeed);
    } finally {
      currentThread.setContextClassLoader(contextClassLoader);
    }
  }
}
