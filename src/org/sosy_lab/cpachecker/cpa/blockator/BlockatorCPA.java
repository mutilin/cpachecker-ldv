/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.blockator;

import java.util.Collection;
import org.sosy_lab.common.configuration.ClassOption;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.blocks.builder.BlockPartitioningBuilder;
import org.sosy_lab.cpachecker.cfa.blocks.builder.FunctionAndLoopPartitioning;
import org.sosy_lab.cpachecker.cfa.blocks.builder.PartitioningHeuristic;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPAException;

@Options(prefix = "cpa.blockator")
public class BlockatorCPA extends AbstractSingleWrapperCPA {
  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(BlockatorCPA.class);
  }

  @Option(
      secure = true,
      description =
          "Type of partitioning (FunctionAndLoopPartitioning or DelayedFunctionAndLoopPartitioning)\n"
              + "or any class that implements a PartitioningHeuristic"
  )
  @ClassOption(packagePrefix = "org.sosy_lab.cpachecker.cfa.blocks.builder")
  private PartitioningHeuristic.Factory blockHeuristic = FunctionAndLoopPartitioning::new;

  private BlockPartitioning partitioning;
  private Reducer reducer;
  private LogManager logger;
  private BlockatorStateRegistry stateRegistry;
  private BlockatorCacheManager cacheManager;
  private BlockatorStatistics statistics;

  private BlockatorTransferRelation transferRelation;
  private BlockatorPrecisionAdjustment precisionAdjustment;
  private BlockatorStopOperator stopOperator;
  private BlockatorMergeOperator mergeOperator;

  protected BlockatorCPA(
      ConfigurableProgramAnalysis pCpa,
      Configuration config,
      LogManager pLogger,
      CFA pCfa) throws InvalidConfigurationException, CPAException {
    super(pCpa);
    config.inject(this, BlockatorCPA.class);

    if (!(pCpa instanceof ConfigurableProgramAnalysisWithBAM)) {
      throw new IllegalArgumentException("BlockatorCPA should wrap BAM-capable CPA");
    }

    logger = pLogger;
    reducer = ((ConfigurableProgramAnalysisWithBAM) pCpa).getReducer();

    final BlockPartitioningBuilder blockBuilder = new BlockPartitioningBuilder();
    PartitioningHeuristic heuristic = blockHeuristic.create(pLogger, pCfa, config);
    partitioning = heuristic.buildPartitioning(blockBuilder);
    ((ConfigurableProgramAnalysisWithBAM) pCpa).setPartitioning(partitioning);

    stateRegistry = new BlockatorStateRegistry();
    cacheManager = new BlockatorCacheManager(reducer, stateRegistry);
    statistics = new BlockatorStatistics();

    transferRelation = new BlockatorTransferRelation(this, pCpa.getTransferRelation());
    precisionAdjustment = new BlockatorPrecisionAdjustment(this, pCpa.getPrecisionAdjustment());
    stopOperator = new BlockatorStopOperator(this, pCpa.getStopOperator());
    mergeOperator = new BlockatorMergeOperator(this, pCpa.getMergeOperator());
  }

  public BlockPartitioning getPartitioning() {
    return partitioning;
  }

  public Reducer getReducer() {
    return reducer;
  }

  public LogManager getLogger() {
    return logger;
  }

  public BlockatorStateRegistry getStateRegistry() {
    return stateRegistry;
  }

  public BlockatorCacheManager getCacheManager() {
    return cacheManager;
  }

  public BlockatorStatistics getStatistics() {
    return statistics;
  }

  @Override
  public AbstractState getInitialState(
      CFANode node, StateSpacePartition partition) throws InterruptedException {
    AbstractState ret = super.getInitialState(node, partition);
    stateRegistry.put(ret, new BlockatorState());
    return ret;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  @Override
  public StopOperator getStopOperator() {
    return stopOperator;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return mergeOperator;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    super.collectStatistics(pStatsCollection);
    pStatsCollection.add(statistics);
  }
}
