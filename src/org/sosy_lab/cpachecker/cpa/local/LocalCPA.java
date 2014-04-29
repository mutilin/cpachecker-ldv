/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.local;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeJoinOperator;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.defaults.StaticPrecisionAdjustment;
import org.sosy_lab.cpachecker.core.defaults.StopJoinOperator;
import org.sosy_lab.cpachecker.core.defaults.StopNeverOperator;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;


@Options(prefix="cpa.local")
public class LocalCPA implements ConfigurableProgramAnalysisWithBAM {
    private LocalDomain abstractDomain;
    private MergeOperator mergeOperator;
    private StopOperator stopOperator;
    private TransferRelation transferRelation;
    private final Reducer reducer;

    public static Set<String> localVariables;

    public static CPAFactory factory() {
      return AutomaticCPAFactory.forType(LocalCPA.class);
    }

    @Option(name="merge", toUppercase=true, values={"SEP", "JOIN"},
        description="which merge operator to use for LocalCPA")
    private String mergeType = "JOIN";

    @Option(name="stop", toUppercase=true, values={"SEP", "JOIN", "NEVER"},
        description="which stop operator to use for LocalCPA")
    private String stopType = "SEP";

    private LocalCPA(LogManager pLogger, Configuration pConfig) throws InvalidConfigurationException {
      pConfig.inject(this);
      this.abstractDomain = new LocalDomain();
      this.mergeOperator = initializeMergeOperator();
      this.stopOperator = initializeStopOperator();
      reducer = new LocalReducer();
      this.transferRelation = new LocalTransferRelation(pConfig);
      // @Option is not allowed on static members
      localVariables = new HashSet<>(Arrays.asList(pConfig.getProperty("cpa.local.localvariables").split(", ")));
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
      return StaticPrecisionAdjustment.getInstance();
    }

    @Override
    public AbstractState getInitialState(CFANode pNode) {
      return new LocalState(null);
    }

    @Override
    public Precision getInitialPrecision(CFANode pNode) {
      return SingletonPrecision.getInstance();
    }

    @Override
    public Reducer getReducer() {
      return reducer;
    }
}
