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
package org.sosy_lab.cpachecker.cpa.explicit;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.sosy_lab.common.Files;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.WrapperPrecision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.AbstractARGBasedRefiner;
import org.sosy_lab.cpachecker.util.AbstractStates;

import com.google.common.base.Joiner;

@Options(prefix="cpa.explicit")
public class ExplicitCPAStatistics implements Statistics {

  @Option(description="target file to hold the exported precision")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path precisionFile = null;

  private final ExplicitCPA cpa;

  private AbstractARGBasedRefiner refiner = null;

  public ExplicitCPAStatistics(ExplicitCPA cpa) throws InvalidConfigurationException {
    this.cpa = cpa;

    this.cpa.getConfiguration().inject(this, ExplicitCPAStatistics.class);
  }

  @Override
  public String getName() {
    return "ExplicitCPA";
  }

  public void addRefiner(AbstractARGBasedRefiner refiner) {
    this.refiner = refiner;
  }

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
    int maxNumberOfVariables            = 0;
    int maxNumberOfGlobalVariables      = 0;

    long totalNumberOfVariables         = 0;
    long totalNumberOfGlobalVariables   = 0;

    for (AbstractState currentAbstractState : reached) {
      ExplicitState currentState = AbstractStates.extractStateByType(currentAbstractState, ExplicitState.class);

      int numberOfVariables         = currentState.getConstantsMap().size();
      int numberOfGlobalVariables   = getNumberOfGlobalVariables(currentState);

      totalNumberOfVariables        = totalNumberOfGlobalVariables + numberOfVariables;
      totalNumberOfGlobalVariables  = totalNumberOfGlobalVariables + numberOfGlobalVariables;

      maxNumberOfVariables          = Math.max(maxNumberOfVariables, numberOfVariables);
      maxNumberOfGlobalVariables    = Math.max(maxNumberOfGlobalVariables, numberOfGlobalVariables);
    }

    out.println("Max. number of variables: " + maxNumberOfVariables);
    out.println("Max. number of globals variables: " + maxNumberOfGlobalVariables);

    out.println("Avg. number of variables: " + ((totalNumberOfVariables * 10000) / reached.size()) / 10000.0);
    out.println("Avg. number of global variables: " + ((totalNumberOfGlobalVariables * 10000) / reached.size()) / 10000.0);

    if (refiner != null) {
      if(precisionFile != null) {
        exportPrecision(reached);
      }
    }
  }

  /**
   * This method exports the precision to file.
   *
   * @param reached the set of reached states.
   */
  private void exportPrecision(ReachedSet reached) {
    Map<CFANode, Collection<String>> consolidatedPrecision = getConsolidatedPrecision(reached);
    try (Writer writer = Files.openOutputFile(precisionFile)) {
      writer.write(Joiner.on("\n").join(consolidatedPrecision.entrySet()));
    } catch (IOException e) {
      cpa.getLogger().logUserException(Level.WARNING, e, "Could not write explicit precision to file");
    }
  }

  /**
   * This method iterates of every state of the reached set and joins their respective precision into one map.
   *
   * @param reached the set of reached states
   * @return the join over precisions of states in the reached set
   */
  private Map<CFANode, Collection<String>> getConsolidatedPrecision(ReachedSet reached) {
    Map<CFANode, Collection<String>> consolidatedPrecision = new HashMap<>();
    for (Precision precision : reached.getPrecisions()) {
      if (precision instanceof WrapperPrecision) {
        ExplicitPrecision prec = ((WrapperPrecision)precision).retrieveWrappedPrecision(ExplicitPrecision.class);
        prec.getCegarPrecision().consolidate(consolidatedPrecision);
      }
    }
    return consolidatedPrecision;
  }

  private int getNumberOfGlobalVariables(ExplicitState state) {
    int numberOfGlobalVariables = 0;

    for(String variableName : state.getConstantsMap().keySet()) {
      if(variableName.contains("::")) {
        numberOfGlobalVariables++;
      }
    }

    return numberOfGlobalVariables;
  }
}
