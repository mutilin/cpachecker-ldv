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
package org.sosy_lab.cpachecker.util;

import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.*;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpressionCollectorVisitor;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPABuilder;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * Abstract base class for static refinement approaches.
 */
@Options(prefix="staticRefiner")
abstract public class StaticRefiner {

  @Option(description="collect at most this number of assumes along a path, backwards from each target (= error) location")
  private int maxBackscanPathAssumes = 1;

  private final Configuration config;
  protected final CFA cfa;
  protected final LogManager logger;

  public StaticRefiner(
      Configuration pConfig,
      LogManager pLogger,
      CFA pCfa) throws InvalidConfigurationException {

    this.logger = pLogger;
    this.config = pConfig;
    this.cfa = pCfa;

    pConfig.inject(this, StaticRefiner.class);

    if (pConfig.getProperty("specification") == null) {
      throw new InvalidConfigurationException("No valid specificSportation is given!");
    }
  }

  protected Set<CIdExpression> getVariablesOfAssume(AssumeEdge pAssume) throws CPATransferException {
    if (pAssume.getExpression() instanceof CExpression) {
      CExpression ce = (CExpression) pAssume.getExpression();
      CIdExpressionCollectorVisitor referencedVariablesVisitor = new CIdExpressionCollectorVisitor();
      ce.accept(referencedVariablesVisitor);
      return referencedVariablesVisitor.getReferencedIdExpressions();
    } else {
      throw new RuntimeException("Only C programming language supported!");
    }
  }

  /**
   * This method finds in a backwards search, starting from the target locations in the
   * CFA, the list of n assume edges preceeding each target node, where n equals the
   * maxBackscanPathAssumes option.
   *
   * @return the mapping from target nodes to the corresponding preceeding assume edges
   */
  protected ListMultimap<CFANode, AssumeEdge> getTargetLocationAssumes(Collection<CFANode> targetNodes) {
    ListMultimap<CFANode, AssumeEdge> result  = ArrayListMultimap.create();
    if (targetNodes.isEmpty()) {
      return result;
    }

    // backwards search to determine all relevant edges
    for (CFANode targetNode : targetNodes) {
      Deque<Pair<CFANode, Integer>> queue = new ArrayDeque<>();
      queue.add(Pair.of(targetNode, 0));
      Set<CFANode> explored = new HashSet<>();

      while (!queue.isEmpty()) {
        // Take the next node that should be explored from the queue
        Pair<CFANode, Integer> v = queue.pop();

        // Each node that enters node v
        for (CFAEdge e: CFAUtils.enteringEdges(v.getFirst())) {
          CFANode u = e.getPredecessor();

          boolean isAssumeEdge = (e instanceof AssumeEdge);
          int depthIncrease = isAssumeEdge ? 1 : 0;

          if (isAssumeEdge) {
            AssumeEdge assume = (AssumeEdge) e;
            if (v.getSecond() < maxBackscanPathAssumes) {
              result.put(targetNode, assume);
            } else {
              continue;
            }
          }

          if (!explored.contains(u)) {
            queue.add(Pair.of(u, v.getSecond() + depthIncrease));
          }
        }

        explored.add(v.getFirst());
      }
    }

    return result;
  }

  /**
   * This method starts a simple CPA on the given CFA in order to find all target nodes
   * that are syntactical reachable in the CFA.
   *
   * @param cfa the CFA to operate on
   * @return the collection of target nodes
   */
  protected Collection<CFANode> getTargetNodesWithCPA() {
    try {
      ReachedSetFactory lReachedSetFactory = new ReachedSetFactory(Configuration.defaultConfiguration(), logger);

      // create new configuration based on the existing one, but with a few options reset
      Configuration lConfig = Configuration.builder().copyFrom(config)
        .setOption("output.disable", "true")
        .clearOption("cpa")
        .clearOption("cpas")
        .clearOption("CompositeCPA.cpas")
        .clearOption("cpa.composite.precAdjust")
        .build();

      CPABuilder lBuilder = new CPABuilder(lConfig, logger, lReachedSetFactory);
      ConfigurableProgramAnalysis lCpas = lBuilder.buildCPAs(cfa);
      Algorithm lAlgorithm = new CPAAlgorithm(lCpas, logger, lConfig);
      ReachedSet lReached = lReachedSetFactory.create();
      lReached.add(lCpas.getInitialState(cfa.getMainFunction()), lCpas.getInitialPrecision(cfa.getMainFunction()));

      lAlgorithm.run(lReached);

      return from(lReached)
               .filter(IS_TARGET_STATE)
               .transform(EXTRACT_LOCATION)
               .toSet();

    } catch (CPAException | InvalidConfigurationException e) {
      logger.log(Level.WARNING, "Cannot find target locations of the CFA.");
      logger.logDebugException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return Collections.emptyList();
  }
}
