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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Collection;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackCPA;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackTransferRelation;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsCPA;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsTransferRelation;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageContainer;
import org.sosy_lab.cpachecker.util.CPAs;
@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsCPA extends AbstractSingleWrapperCPA implements ConfigurableProgramAnalysisWithBAM {

  private final UsageStatisticsDomain abstractDomain;
  private final MergeOperator mergeOperator;
  private final StopOperator stopOperator;
  private final TransferRelation transferRelation;
  private final PrecisionAdjustment precisionAdjustment;
  private final Reducer reducer;
  private final UsageStatisticsCPAStatistics statistics;
  //Do not remove container from CPA - we clean all states while refinement
  private final UsageContainer container;
  private UsageStatisticsPrecision precision;
  private final CFA cfa;
  private final LogManager logger;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(UsageStatisticsCPA.class);
  }

  @Option(name="precisionReset", description="The value of marked unsafes, after which the precision should be cleaned")
  private int precisionReset = Integer.MAX_VALUE;

  private String outputFileName = "output/localsave";

  private UsageStatisticsCPA(ConfigurableProgramAnalysis pCpa, CFA pCfa, LogManager pLogger, Configuration pConfig) throws InvalidConfigurationException {
    super(pCpa);
    pConfig.inject(this);
    this.cfa = pCfa;
    this.abstractDomain = new UsageStatisticsDomain(pCpa.getAbstractDomain());
    this.mergeOperator = initializeMergeOperator();
    this.stopOperator = initializeStopOperator();

    LockStatisticsCPA LockCPA = (CPAs.retrieveCPA(this, LockStatisticsCPA.class));
    this.statistics = new UsageStatisticsCPAStatistics(pConfig, pLogger,
        LockCPA != null ? (LockStatisticsTransferRelation) LockCPA.getTransferRelation() : null);
    this.container = new UsageContainer(pConfig, pLogger);
    this.precisionAdjustment = new UsageStatisticsPrecisionAdjustment(pCpa.getPrecisionAdjustment());
    if (pCpa instanceof ConfigurableProgramAnalysisWithBAM) {
      Reducer wrappedReducer = ((ConfigurableProgramAnalysisWithBAM)pCpa).getReducer();
      if (wrappedReducer != null) {
        reducer = new UsageStatisticsReducer(wrappedReducer);
      } else {
        reducer = null;
      }
    } else {
      reducer = null;
    }
    logger = pLogger;
    this.transferRelation = new UsageStatisticsTransferRelation(pCpa.getTransferRelation(), pConfig, pLogger, statistics
        , (CallstackTransferRelation) (CPAs.retrieveCPA(this, CallstackCPA.class)).getTransferRelation());

    String tmpString = pConfig.getProperty("precision.path");
    if (tmpString != null) {
      outputFileName = tmpString;
    }
  }

  private MergeOperator initializeMergeOperator() {
    return MergeSepOperator.getInstance();
  }

  private StopOperator initializeStopOperator() {
    return new StopSepOperator(abstractDomain);
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
    return mergeOperator;
  }

  @Override
  public StopOperator getStopOperator() {
    return stopOperator;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode, StateSpacePartition p) {
    precision = new UsageStatisticsPrecision(this.getWrappedCpa().getInitialPrecision(pNode, p));
    PresisionParser parser = new PresisionParser(outputFileName, cfa);
    parser.parse(precision);
    return precision;
  }

  @Override
  public Reducer getReducer() {
    return reducer;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(statistics);
    super.collectStatistics(pStatsCollection);
  }

  public UsageStatisticsCPAStatistics getStats() {
    return statistics;
  }

  public LogManager getLogger() {
    return logger;
  }

  public int getThePrecisionCleaningLimit() {
    return precisionReset;
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) {
    return new UsageStatisticsState(getWrappedCpa().getInitialState(pNode, pPartition), container);
  }
}
