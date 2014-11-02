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
 *  See the License for the specifi  c language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */

package org.sosy_lab.cpachecker.pcc.strategy.partitioning;


import java.util.List;
import java.util.Set;

import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.interfaces.pcc.BalancedGraphPartitioner;
import org.sosy_lab.cpachecker.pcc.strategy.partialcertificate.PartialReachedSetDirectedGraph;

@Options(prefix = "pcc.partitioning.fm")
public class FiducciaMattheysesBalancedGraphPartitioner implements BalancedGraphPartitioner {

  private final ShutdownNotifier shutdownNotifier;

  @Option(description = "Heuristic for computing an initial partitioning of proof (e.g. as required by Fiduccia-Mattheyses algorithm heuristic")
  private InitPartitioningHeuristics initialPartitioningStrategy = InitPartitioningHeuristics.RANDOM;

  public enum InitPartitioningHeuristics {
    RANDOM
  }

  @Option(description = "Balance criterion for pairwise optimization of partitions")
  private double balanceCriterion = 4.5d;

  private final BalancedGraphPartitioner partitioner;

  public FiducciaMattheysesBalancedGraphPartitioner(ShutdownNotifier pShutdownNotifier) {

    shutdownNotifier = pShutdownNotifier;

    switch (initialPartitioningStrategy) {
      // TODO support better strategies for initial partitioning
      default: // RANDOM
        partitioner = new RandomBalancedGraphPartitioner();
    }

  }

  @Override
  public List<Set<Integer>> computePartitioning(int pNumPartitions, PartialReachedSetDirectedGraph pGraph)
      throws InterruptedException {

    // TODO insert assertions

    /* Create initial partition which is going to be optimized later on */
    List<Set<Integer>> partition = partitioner.computePartitioning(pNumPartitions, pGraph);

    /* Optimize partitions pairwisely with FM algorithm */
    // TODO find better strategy or/and make this parallel
    for(Set<Integer> v1 : partition) {
      for(Set<Integer> v2 : partition) {
        if(v1 != v2) {
          shutdownNotifier.shutdownIfNecessary();
          FiducciaMattheysesAlgorithm fm = new FiducciaMattheysesAlgorithm(balanceCriterion, v1, v2, pGraph);
          long gain;
          do {
            shutdownNotifier.shutdownIfNecessary();
            gain = fm.improvePartitions();
          } while(gain > 0);
        }
      }
    }
    return partition;
  }

}
