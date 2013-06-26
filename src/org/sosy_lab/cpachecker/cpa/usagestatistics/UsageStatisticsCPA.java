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

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeJoinOperator;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.StopJoinOperator;
import org.sosy_lab.cpachecker.core.defaults.StopNeverOperator;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithABM;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackCPA;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackTransferRelation;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsCPA;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsTransferRelation;
import org.sosy_lab.cpachecker.util.CPAs;
@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsCPA extends AbstractSingleWrapperCPA implements ConfigurableProgramAnalysisWithABM {

  private UsageStatisticsDomain abstractDomain;
  private MergeOperator mergeOperator;
  private StopOperator stopOperator;
  private TransferRelation transferRelation;
  private PrecisionAdjustment precisionAdjustment;
  private final Reducer reducer;
  private final UsageStatisticsCPAStatistics statistics;
  private UsageStatisticsPrecision precision;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(UsageStatisticsCPA.class);
  }

  @Option(name="merge", toUppercase=true, values={"SEP", "JOIN"},
      description="which merge operator to use for LockStatisticsCPA")
  private String mergeType = "SEP";

  @Option(name="stop", toUppercase=true, values={"SEP", "JOIN", "NEVER"},
      description="which stop operator to use for LockStatisticsCPA")
  private String stopType = "SEP";

  private String outputFileName = "output/localsave";

  @Option(description="do we need to collect statistics to generate file for Lcov")
  private boolean covering = false;

  private UsageStatisticsCPA(ConfigurableProgramAnalysis pCpa, CFA pCfa, LogManager pLogger, Configuration pConfig) throws InvalidConfigurationException {
    super(pCpa);
    pConfig.inject(this);
    this.abstractDomain = new UsageStatisticsDomain(pCpa.getAbstractDomain());
    this.mergeOperator = initializeMergeOperator();
    this.stopOperator = initializeStopOperator();

    this.precisionAdjustment = new UsageStatisticsPrecisionAdjustment(pCpa.getPrecisionAdjustment());
    if (pCpa instanceof ConfigurableProgramAnalysisWithABM) {
      Reducer wrappedReducer = ((ConfigurableProgramAnalysisWithABM)pCpa).getReducer();
      if (wrappedReducer != null) {
        reducer = new UsageStatisticsReducer(wrappedReducer);
      } else {
        reducer = null;
      }
    } else {
      reducer = null;
    }
    this.statistics = new UsageStatisticsCPAStatistics(pConfig);
    this.transferRelation = new UsageStatisticsTransferRelation(pCpa.getTransferRelation(), pConfig, statistics
        , (CallstackTransferRelation) (CPAs.retrieveCPA(this, CallstackCPA.class)).getTransferRelation());

    LockStatisticsCPA LockCpa = ((WrapperCPA) getWrappedCpa()).retrieveWrappedCpa(LockStatisticsCPA.class);
    if (LockCpa != null) {
      ((LockStatisticsTransferRelation)LockCpa.getTransferRelation()).getFunctionHandler().setUsCPA(this);
    }

    PresisionParser parser = new PresisionParser(outputFileName, pCfa);
    this.precision = parser.parse();
  }

  private MergeOperator initializeMergeOperator() {
    if(mergeType.equals("SEP")) {
      return MergeSepOperator.getInstance();
    }

    else if(mergeType.equals("JOIN")) {
      return new MergeJoinOperator(abstractDomain);
    }

    return null;
  }

  private StopOperator initializeStopOperator() {
    if(stopType.equals("SEP")) {
      return new StopSepOperator(abstractDomain);
    }

    else if(stopType.equals("JOIN")) {
      return new StopJoinOperator(abstractDomain);
    }

    else if(stopType.equals("NEVER")) {
      return new StopNeverOperator();
    }
    return null;
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
  public AbstractState getInitialState(CFANode pNode) {
    return new UsageStatisticsState(getWrappedCpa().getInitialState(pNode));
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode) {
    precision.setPrecision(this.getWrappedCpa().getInitialPrecision(pNode));
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
}
