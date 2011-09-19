/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.impact;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.defaults.StaticPrecisionAdjustment;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.predicate.BlockOperator;
import org.sosy_lab.cpachecker.util.predicates.ExtendedFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.PathFormulaManagerImpl;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.TheoremProver;
import org.sosy_lab.cpachecker.util.predicates.mathsat.MathsatFactory;
import org.sosy_lab.cpachecker.util.predicates.mathsat.MathsatFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.mathsat.MathsatTheoremProver;

public class ImpactCPA implements ConfigurableProgramAnalysis {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ImpactCPA.class).withOptions(BlockOperator.class);
  }

  private final Configuration config;
  private final LogManager logger;

  private final ExtendedFormulaManager fmgr;
  private final PathFormulaManager pfmgr;
  private final TheoremProver prover;

  private final ImpactAbstractDomain abstractDomain;
  private final ImpactTransferRelation transferRelation;

  private ImpactCPA(Configuration pConfig, LogManager pLogger, BlockOperator blk) throws InvalidConfigurationException {
    config = pConfig;
    logger = pLogger;

    MathsatFormulaManager msatFmgr = MathsatFactory.createFormulaManager(config, logger);
    fmgr = new ExtendedFormulaManager(msatFmgr, pConfig, pLogger);

    pfmgr = new PathFormulaManagerImpl(fmgr, config, logger);
    prover = new MathsatTheoremProver(msatFmgr);

    abstractDomain = new ImpactAbstractDomain(fmgr, prover);
    transferRelation = new ImpactTransferRelation(fmgr, pfmgr, blk);
  }

  LogManager getLogManager() {
    return logger;
  }

  Configuration getConfiguration() {
    return config;
  }

  ExtendedFormulaManager getFormulaManager() {
    return fmgr;
  }

  PathFormulaManager getPathFormulaManager() {
    return pfmgr;
  }

  TheoremProver getTheoremProver() {
    return prover;
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return abstractDomain;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return MergeSepOperator.getInstance();
  }

  @Override
  public StopOperator getStopOperator() {
    return new StopSepOperator(abstractDomain);
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return StaticPrecisionAdjustment.getInstance();
  }

  @Override
  public AbstractElement getInitialElement(CFANode pNode) {
    return new ImpactAbstractElement.AbstractionElement(pfmgr.makeEmptyPathFormula(), fmgr.makeTrue(), pfmgr.makeEmptyPathFormula());
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode) {
    return SingletonPrecision.getInstance();
  }
}