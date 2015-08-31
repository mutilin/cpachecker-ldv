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
package org.sosy_lab.cpachecker.util.predicates.interpolation;

import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.solver.api.InterpolatingProverEnvironment;
import org.sosy_lab.solver.api.InterpolatingProverEnvironmentWithAssumptions;
import org.sosy_lab.solver.test.SolverFormulaWithAssumptionsTest;

public class InterpolatingProverWithAssumptionsWrapperTest extends SolverFormulaWithAssumptionsTest {

  @Override
  protected ConfigurationBuilder createTestConfigBuilder() throws InvalidConfigurationException {
    return super.createTestConfigBuilder()
                .setOption("cpa.predicate.encodeFloatAs", "INTEGER");
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected <T> InterpolatingProverEnvironmentWithAssumptions<T> newEnvironmentForTest() throws InvalidConfigurationException {
    FormulaManagerView formulaView = new FormulaManagerView(factory, config, logger);
    final InterpolatingProverEnvironment<?> proverEnvironment = mgr.newProverEnvironmentWithInterpolation(false);
    return new InterpolatingProverWithAssumptionsWrapper(proverEnvironment, formulaView);
  }
}
