/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.overflow;

import com.google.common.collect.ImmutableList;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpressionBuilder;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.FlatLatticeNoTopDomain;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.defaults.StaticPrecisionAdjustment;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.ArithmeticOverflowAssumptionBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CPA for detecting overflows in C programs.
 */
public class OverflowCPA
    extends SingleEdgeTransferRelation
    implements ConfigurableProgramAnalysis{

  private final CBinaryExpressionBuilder expressionBuilder;
  private final AbstractDomain domain;
  private final ArithmeticOverflowAssumptionBuilder noOverflowAssumptionBuilder;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(OverflowCPA.class);
  }

  private OverflowCPA(CFA pCfa, LogManager pLogger, Configuration pConfiguration)
      throws InvalidConfigurationException {
    expressionBuilder = new CBinaryExpressionBuilder(pCfa.getMachineModel(), pLogger);
    domain = new FlatLatticeNoTopDomain();
    noOverflowAssumptionBuilder =
        new ArithmeticOverflowAssumptionBuilder(pCfa, pLogger, pConfiguration);
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state,
      Precision precision,
      CFAEdge cfaEdge
  ) throws CPATransferException, InterruptedException {
    OverflowState prev = (OverflowState) state;

    if (prev.hasOverflow()) {

      // Once we have an overflow there is no need to continue.
      return Collections.emptyList();
    }


    List<CExpression> assumptions = noOverflowAssumptionBuilder.assumptionsForEdge(cfaEdge);
    if (assumptions.isEmpty()) {
      return ImmutableList.of(
          new OverflowState(
              ImmutableList.of(),
              ImmutableList.of(),
              false
          )
      );
    }

    // No overflows <=> all assumptions hold.
    List<CExpression> noOverflows;
    if (assumptions.isEmpty()) {
      noOverflows = Collections.emptyList();
    } else {
      noOverflows = assumptions;
    }

    ImmutableList.Builder<OverflowState> outStates = ImmutableList.builder();
    outStates.addAll(assumptions.stream().map(

        // Overflow <=> there exists a violating assumption.
        a -> mkState(Collections.singletonList(mkNot(a)), cfaEdge, true)
    ).iterator());
    outStates.add(mkState(noOverflows, cfaEdge, false));
    return outStates.build();
  }

  private OverflowState mkState(
      List<CExpression> expression,
      CFAEdge pEdge,
      boolean pHasOverflow
      ) {
    return new OverflowState(
        expression.stream().map(e -> mkStatement(e)).collect(Collectors.toList()),
        expression.stream().map(
            e -> mkAssumeEdge(
                pEdge.getPredecessor(),
                e,
                pEdge.getSuccessor()
            )
        ).collect(Collectors.toList()),
        pHasOverflow);
  }

  private CAssumeEdge mkAssumeEdge(
      CFANode predecessor,
      CExpression pExpression,
      CFANode successor) {
    return new CAssumeEdge(
        pExpression.toASTString(),
        FileLocation.DUMMY,
        predecessor,
        successor,
        pExpression,
        true
    );
  }

  private CExpressionStatement mkStatement(CExpression pExpression) {
    return new CExpressionStatement(FileLocation.DUMMY, pExpression);
  }

  private CExpression mkNot(CExpression arg) {
    return expressionBuilder.buildBinaryExpressionUnchecked(
        arg,
        CIntegerLiteralExpression.ZERO,
        BinaryOperator.EQUALS
    );
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return domain;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return this;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return MergeSepOperator.getInstance();
  }

  @Override
  public StopOperator getStopOperator() {
    return new StopSepOperator(domain);
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return StaticPrecisionAdjustment.getInstance();
  }

  @Override
  public AbstractState getInitialState(
      CFANode node, StateSpacePartition partition) throws InterruptedException {
    return new OverflowState(
        ImmutableList.of(),
        ImmutableList.of(),
        false);
  }

  @Override
  public Precision getInitialPrecision(
      CFANode node, StateSpacePartition partition) throws InterruptedException {
    return SingletonPrecision.getInstance();
  }
}
