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
package org.sosy_lab.cpachecker.util.predicates.princess;

import static com.google.common.collect.Iterables.getOnlyElement;

import java.io.IOException;

import org.sosy_lab.common.Appender;
import org.sosy_lab.common.Appenders;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.io.PathCounterTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFormulaManager;

import ap.parser.IExpression;

public class PrincessFormulaManager extends AbstractFormulaManager<IExpression, PrincessEnvironment.Type, PrincessEnvironment> {

  private PrincessFormulaManager(
          PrincessEnvironment pEnv,
          PrincessFormulaCreator pCreator,
          PrincessUnsafeFormulaManager pUnsafeManager,
          PrincessFunctionFormulaManager pFunctionManager,
          PrincessBooleanFormulaManager pBooleanManager,
          PrincessIntegerFormulaManager pIntegerManager) {
    super(pEnv, pCreator, pUnsafeManager, pFunctionManager, pBooleanManager, pIntegerManager, null, null);
  }

  public static PrincessFormulaManager create(Configuration config, LogManager logger,
      ShutdownNotifier pShutdownNotifier, PathCounterTemplate pLogfileTemplate) throws InvalidConfigurationException {

    PrincessEnvironment env = new PrincessEnvironment(config, logger, pLogfileTemplate);

    PrincessFormulaCreator creator = new PrincessFormulaCreator(env,
        PrincessEnvironment.Type.BOOL, PrincessEnvironment.Type.INT, PrincessEnvironment.Type.INT);

    // Create managers
    PrincessUnsafeFormulaManager unsafeManager = new PrincessUnsafeFormulaManager(creator);
    PrincessFunctionFormulaManager functionTheory = new PrincessFunctionFormulaManager(creator, unsafeManager);
    PrincessBooleanFormulaManager booleanTheory = new PrincessBooleanFormulaManager(creator);
    PrincessIntegerFormulaManager integerTheory = new PrincessIntegerFormulaManager(creator, functionTheory);

    return new PrincessFormulaManager(env, creator, unsafeManager, functionTheory,
            booleanTheory, integerTheory);
  }

  BooleanFormula encapsulateBooleanFormula(IExpression t) {
    return getFormulaCreator().encapsulate(BooleanFormula.class, t);
  }

  @Override
  public BooleanFormula parse(String pS) throws IllegalArgumentException {
    return encapsulateBooleanFormula(getOnlyElement(getEnvironment().parseStringToTerms(pS)));
  }


  @Override
  public Appender dumpFormula(final IExpression formula) {
    return new Appenders.AbstractAppender() {

      @Override
      public void appendTo(Appendable out) throws IOException {
        // TODO declare variables
        out.append("(assert ");
        out.append(formula.toString());
        out.append(")");
      }
    };
  }

  @Override
  public String getVersion() {
    return getEnvironment().getVersion();
  }

  @Override
  protected IExpression getTerm(Formula pF) {
    // for visibility
    return super.getTerm(pF);
  }
}
