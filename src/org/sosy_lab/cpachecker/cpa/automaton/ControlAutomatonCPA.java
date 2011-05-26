/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.automaton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory.Optional;
import org.sosy_lab.cpachecker.core.defaults.FlatLatticeDomain;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.NoOpReducer;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.defaults.StaticPrecisionAdjustment;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithABM;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;

/**
 * This class implements an AutomatonAnalysis as described in the related Documentation.
 * @author rhein
 */
@Options(prefix="cpa.automaton")
public class ControlAutomatonCPA implements ConfigurableProgramAnalysis, StatisticsProvider, ConfigurableProgramAnalysisWithABM {

  @Option(name="dotExport")
  private boolean export = false;
  
  @Option(name="dotExportFile", type=Option.Type.OUTPUT_FILE)
  private File exportFile = new File("automaton.dot");

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ControlAutomatonCPA.class);
  }

  @Option(required=false, type=Option.Type.OPTIONAL_INPUT_FILE)
  private File inputFile = null;
  
  @Option
  private boolean breakOnTargetState = true;

  private final Automaton automaton;
  private final AutomatonState topState = new AutomatonState.TOP(this);
  private final AutomatonState bottomState = new AutomatonState.BOTTOM(this);

  private final AbstractDomain automatonDomain = new FlatLatticeDomain(topState);
  private final StopOperator stopOperator = new StopSepOperator(automatonDomain);
  private final AutomatonTransferRelation transferRelation;
  private final PrecisionAdjustment precisionAdjustment;
  private final Statistics stats = new AutomatonStatistics(this);

  protected ControlAutomatonCPA(@Optional Automaton pAutomaton, Configuration config, LogManager logger) throws InvalidConfigurationException {
    config.inject(this, ControlAutomatonCPA.class);
    
    transferRelation = new AutomatonTransferRelation(this, logger);
    precisionAdjustment = breakOnTargetState ? new AutomatonPrecisionAdjustment() : StaticPrecisionAdjustment.getInstance();
  
    if (pAutomaton != null) {
      this.automaton = pAutomaton;
    
    } else if (inputFile == null) {
      throw new InvalidConfigurationException("Explicitly specified automaton CPA needs option cpa.automaton.inputFile!");
    
    } else {
      List<Automaton> lst = AutomatonParser.parseAutomatonFile(inputFile, config, logger);
      if (lst.isEmpty()) {
        throw new InvalidConfigurationException("Could not find automata in the file " + inputFile.getAbsolutePath());
      } else if (lst.size() > 1) {
        throw new InvalidConfigurationException("Found " + lst.size() + " automata in the File " + inputFile.getAbsolutePath() + " The CPA can only handle ONE Automaton!");
      }
      
      this.automaton = lst.get(0);
    }
    logger.log(Level.FINEST, "Automaton", automaton.getName(), "loaded.");
    
    if (export && exportFile != null) {
      try {
        this.automaton.writeDotFile(new PrintStream(exportFile));
      } catch (FileNotFoundException e) {
        logger.log(Level.WARNING, "Could not create/write to the Automaton DOT file \"" + exportFile + "\"");
      }
    }
  }

  Automaton getAutomaton() {
    return this.automaton;
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return automatonDomain;
  }

  @Override
  public AbstractElement getInitialElement(CFANode pNode) {
    return AutomatonState.automatonStateFactory(automaton.getInitialVariables(), automaton.getInitialState(), this);
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode) {
    return SingletonPrecision.getInstance();
  }

  @Override
  public MergeOperator getMergeOperator() {
    return MergeSepOperator.getInstance();
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
  public AutomatonTransferRelation getTransferRelation() {
    return transferRelation ;
  }

  @Override
  public Reducer getReducer() {
    return NoOpReducer.getInstance();
  }
  
  public AutomatonState getBottomState() {
    return this.bottomState;
  }

  public AutomatonState getTopState() {
    return this.topState;
  }
  
  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
  }
}
