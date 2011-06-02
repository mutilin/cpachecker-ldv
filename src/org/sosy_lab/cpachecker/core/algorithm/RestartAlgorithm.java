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
package org.sosy_lab.cpachecker.core.algorithm;

import java.util.Collection;
import java.util.List;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class RestartAlgorithm implements Algorithm, StatisticsProvider {

  private final List<Algorithm> algorithms;
  private Algorithm currentAlgorithm;

  public RestartAlgorithm(List<Algorithm> algorithms, Configuration config, LogManager logger) throws InvalidConfigurationException, CPAException {
    this.algorithms = algorithms;
  }

  @Override
  public ConfigurableProgramAnalysis getCPA() {
    return currentAlgorithm.getCPA();
  }

  @Override
  public boolean run(ReachedSet reached) throws CPAException,
      InterruptedException {

    boolean sound = true;

    int idx = 0;

    boolean continueAnalysis;
    do {
      continueAnalysis = false;

      currentAlgorithm = algorithms.get(idx++);

      // run algorithm
      sound &= currentAlgorithm.run(reached);

      if(!sound){
        // TODO we need to create a new reached set here
        // or modify the reached set

        continueAnalysis = true;
      }

    } while (continueAnalysis);

    return sound;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    // TODO user wrapper statictics
  }
}
