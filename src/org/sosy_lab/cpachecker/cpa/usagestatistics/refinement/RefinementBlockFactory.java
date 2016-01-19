/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usagestatistics.refinement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMCPA;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsCPA;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageInfoSet;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

@Options(prefix="cpa.usagestatistics")
public class RefinementBlockFactory {

  public static enum RefinementBlockTypes {
    IdentifierIterator,
    PointIterator,
    UsageIterator,
    PathIterator,
    PredicateRefiner,
    CallstackFilter;
  }

  private static enum currentInnerBlockType {
    ExtendedARGPath,
    UsageInfoSet,
    SingleIdentifier,
    UsageInfo,
    ReachedSet;
  }

  private final static String CLASS_PREFIX = "org.sosy_lab.cpachecker.cpa.usagestatistics.refinement";
  Map<ARGState, ARGState> subgraphStatesToReachedState = new HashMap<>();
  final ConfigurableProgramAnalysis cpa;
  Configuration config;

  @Option(name = "refinementChain", description = "The order of refinement blocks")
  List<RefinementBlockTypes> RefinementChain;

  public RefinementBlockFactory(ConfigurableProgramAnalysis pCpa, Configuration pConfig) throws InvalidConfigurationException {
    //subgraphStatesToReachedState = pSubgraphStatesToReachedState;
    cpa = pCpa;
    config = pConfig;
    pConfig.inject(this);
  }

  public ConfigurableRefinementBlock<ReachedSet> create() throws InvalidConfigurationException {
    BAMCPA bam = CPAs.retrieveCPA(cpa, BAMCPA.class);
    BAMTransferRelation bamTransfer = bam.getTransferRelation();
    UsageStatisticsCPA usCPA = CPAs.retrieveCPA(cpa, UsageStatisticsCPA.class);
    LogManager logger = usCPA.getLogger();

    //Tricky way to create the chain, but it is difficult to dynamically know the parameter types
    RefinementInterface currentBlock = new RefinementPairStub();
    currentInnerBlockType currentBlockType = currentInnerBlockType.ExtendedARGPath;

    for (int i = RefinementChain.size() - 1; i >= 0; i--) {

      switch (RefinementChain.get(i)) {
        case IdentifierIterator:
          if (currentBlockType == currentInnerBlockType.SingleIdentifier) {
            currentBlock = new IdentifierIterator((ConfigurableRefinementBlock<SingleIdentifier>) currentBlock,
                config, cpa, bamTransfer);
            currentBlockType = currentInnerBlockType.ReachedSet;
          } else {
            throwException("Identifier iterator", currentBlock.getClass().getSimpleName());
          }
          break;

        case PointIterator:
          if (currentBlockType == currentInnerBlockType.UsageInfoSet) {
            currentBlock = new PointIterator((ConfigurableRefinementBlock<Pair<UsageInfoSet, UsageInfoSet>>) currentBlock,
                null);
            currentBlockType = currentInnerBlockType.SingleIdentifier;
          } else {
            throwException("Point iterator", currentBlock.getClass().getSimpleName());
          }
          break;

        case UsageIterator:
          if (currentBlockType == currentInnerBlockType.UsageInfo) {
            currentBlock = new UsagePairIterator((ConfigurableRefinementBlock<Pair<UsageInfo, UsageInfo>>) currentBlock,
                logger);
            currentBlockType = currentInnerBlockType.UsageInfoSet;
          } else {
            throwException("Usage iterator", currentBlock.getClass().getSimpleName());
          }
          break;

        case PathIterator:
          if (currentBlockType == currentInnerBlockType.ExtendedARGPath) {
            currentBlock = new PathPairIterator((ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>>) currentBlock,
                subgraphStatesToReachedState, bamTransfer);
            currentBlockType = currentInnerBlockType.UsageInfo;
          } else {
            throwException("Path iterator", currentBlock.getClass().getSimpleName());
          }
          break;

        case PredicateRefiner:
          if (currentBlockType == currentInnerBlockType.ExtendedARGPath) {
            currentBlock = new PredicateRefinerAdapter((ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>>) currentBlock,
                cpa, null);
            subgraphStatesToReachedState = ((PredicateRefinerAdapter)currentBlock).getInternalMapForStates();
          } else {
            throwException("Predicate refiner", currentBlock.getClass().getSimpleName());
          }
          break;

        case CallstackFilter:
          if (currentBlockType == currentInnerBlockType.ExtendedARGPath) {
            currentBlock = new CallstackFilter((ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>>) currentBlock,
                config);
          } else {
            throwException("Callstack filter", currentBlock.getClass().getSimpleName());
          }
          break;

        default:
          throw new InvalidConfigurationException("The type " + RefinementChain.get(i) + " is not supported");
      }
    }
    if (currentBlockType == currentInnerBlockType.ReachedSet) {
      return (ConfigurableRefinementBlock<ReachedSet>) currentBlock;
    } else {
      throw new InvalidConfigurationException("The first block is not take a reached set as parameter");
    }
  }

  private void throwException(String currentBlock, String previousBlock) throws InvalidConfigurationException {
    throw new InvalidConfigurationException(currentBlock + " can not precede the " + previousBlock);
  }
}
