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
package org.sosy_lab.cpachecker.fshell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.TimeAccumulator;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionDefinitionNode;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.algorithm.CEGARAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.Targetable;
import org.sosy_lab.cpachecker.core.reachedset.PartitionedReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist;
import org.sosy_lab.cpachecker.cpa.art.ARTCPA;
import org.sosy_lab.cpachecker.cpa.art.ARTStatistics;
import org.sosy_lab.cpachecker.cpa.assume.AssumeCPA;
import org.sosy_lab.cpachecker.cpa.cache.CacheCPA;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackCPA;
import org.sosy_lab.cpachecker.cpa.cfapath.CFAPathCPA;
import org.sosy_lab.cpachecker.cpa.cfapath.CFAPathStandardElement;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.composite.CompositeElement;
import org.sosy_lab.cpachecker.cpa.guardededgeautomaton.GuardedEdgeAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.guardededgeautomaton.productautomaton.ProductAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.guardededgeautomaton.productautomaton.ProductAutomatonElement;
import org.sosy_lab.cpachecker.cpa.interpreter.InterpreterCPA;
import org.sosy_lab.cpachecker.cpa.location.LocationCPA;
import org.sosy_lab.cpachecker.cpa.location.LocationElement;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateRefiner;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.fshell.cfa.Wrapper;
import org.sosy_lab.cpachecker.fshell.fql2.ast.FQLSpecification;
import org.sosy_lab.cpachecker.fshell.fql2.translators.ecp.CoverageSpecificationTranslator;
import org.sosy_lab.cpachecker.fshell.fql2.translators.ecp.IncrementalCoverageSpecificationTranslator;
import org.sosy_lab.cpachecker.fshell.interfaces.FQLTestGenerator;
import org.sosy_lab.cpachecker.fshell.testcases.ImpreciseExecutionException;
import org.sosy_lab.cpachecker.fshell.testcases.TestCase;
import org.sosy_lab.cpachecker.util.automaton.NondeterministicFiniteAutomaton;
import org.sosy_lab.cpachecker.util.ecp.ECPEdgeSet;
import org.sosy_lab.cpachecker.util.ecp.ElementaryCoveragePattern;
import org.sosy_lab.cpachecker.util.ecp.translators.GuardedEdgeLabel;
import org.sosy_lab.cpachecker.util.ecp.translators.InverseGuardedEdgeLabel;
import org.sosy_lab.cpachecker.util.ecp.translators.ToGuardedAutomatonTranslator;

/*
 * TODO AutomatonBuilder <- integrate State-Pool there to ensure correct time
 * measurements when invoking FlleSh several times in a unit test.
 *
 * TODO Incremental test goal automaton creation: extending automata (can we reuse
 * parts of the reached set?) This requires a change in the coverage check.
 * -> Handle enormous amounts of test goals.
 */

public class IncrementalFQLTestGenerator implements FQLTestGenerator {

  private final Configuration mConfiguration;
  private final LogManager mLogManager;
  private final Wrapper mWrapper;
  private final CoverageSpecificationTranslator mCoverageSpecificationTranslator;
  private final LocationCPA mLocationCPA;
  private final CallstackCPA mCallStackCPA;
  private final AssumeCPA mAssumeCPA;
  private final CFAPathCPA mCFAPathCPA;
  private final ConfigurableProgramAnalysis mPredicateCPA;

  private final TimeAccumulator mTimeInReach;
  private int mTimesInReach;
  private final GuardedEdgeLabel mAlphaLabel;
  private final GuardedEdgeLabel mOmegaLabel;
  private final GuardedEdgeLabel mInverseAlphaLabel;
  private final Map<TestCase, CFAEdge[]> mGeneratedTestCases;

  private final Map<NondeterministicFiniteAutomaton<GuardedEdgeLabel>, Collection<NondeterministicFiniteAutomaton.State>> mInfeasibleGoals;

  public IncrementalFQLTestGenerator(String pSourceFileName, String pEntryFunction) {
    Map<String, CFAFunctionDefinitionNode> lCFAMap;
    CFAFunctionDefinitionNode lMainFunction;

    try {
      mConfiguration = FShell3.createConfiguration(pSourceFileName, pEntryFunction);
      mLogManager = new LogManager(mConfiguration);

      lCFAMap = FShell3.getCFAMap(pSourceFileName, mConfiguration, mLogManager);
      lMainFunction = lCFAMap.get(pEntryFunction);
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    }

    /*
     * We have to instantiate mCoverageSpecificationTranslator before the wrapper
     * changes the underlying CFA. FQL specifications are evaluated against the
     * target graph generated during initialization of mCoverageSpecificationTranslator.
     */
    mCoverageSpecificationTranslator = new CoverageSpecificationTranslator(lMainFunction);

    mWrapper = new Wrapper((FunctionDefinitionNode)lMainFunction, lCFAMap, mLogManager);

    try {
      mWrapper.toDot("test/output/wrapper.dot");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    mAlphaLabel = new GuardedEdgeLabel(new ECPEdgeSet(mWrapper.getAlphaEdge()));
    mInverseAlphaLabel = new InverseGuardedEdgeLabel(mAlphaLabel);
    mOmegaLabel = new GuardedEdgeLabel(new ECPEdgeSet(mWrapper.getOmegaEdge()));


    /*
     * Initialize shared CPAs.
     */
    // location CPA
    try {
      mLocationCPA = (LocationCPA)LocationCPA.factory().createInstance();
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    } catch (CPAException e) {
      throw new RuntimeException(e);
    }

    // callstack CPA
    CPAFactory lCallStackCPAFactory = CallstackCPA.factory();
    try {
      mCallStackCPA = (CallstackCPA)lCallStackCPAFactory.createInstance();
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    } catch (CPAException e) {
      throw new RuntimeException(e);
    }

    // assume CPA
    mAssumeCPA = AssumeCPA.getCBMCAssume();

    // cfa path CPA
    mCFAPathCPA = CFAPathCPA.getInstance();

    // TODO make configurable
    // ... cache does not work well for big examples
    boolean lUseCache = false;

    // predicate abstraction CPA
    CPAFactory lPredicateCPAFactory = PredicateCPA.factory();
    lPredicateCPAFactory.setConfiguration(mConfiguration);
    lPredicateCPAFactory.setLogger(mLogManager);
    try {
      ConfigurableProgramAnalysis lPredicateCPA = lPredicateCPAFactory.createInstance();

      if (lUseCache) {
        mPredicateCPA = new CacheCPA(lPredicateCPA);
      }
      else {
        mPredicateCPA = lPredicateCPA;
      }
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    } catch (CPAException e) {
      throw new RuntimeException(e);
    }

    mTimeInReach = new TimeAccumulator();
    mTimesInReach = 0;

    // we can collect test cases accross several run invocations and use them for coverage analysis
    // TODO output test cases from an earlier run
    mGeneratedTestCases = new HashMap<TestCase, CFAEdge[]>();

    mInfeasibleGoals = new HashMap<NondeterministicFiniteAutomaton<GuardedEdgeLabel>, Collection<NondeterministicFiniteAutomaton.State>>();
  }

  @Override
  public FShell3Result run(String pFQLSpecification, boolean pApplySubsumptionCheck, boolean pApplyInfeasibilityPropagation, boolean pGenerateTestGoalAutomataInAdvance, boolean pCheckCorrectnessOfCoverageCheck, boolean pPedantic, boolean pAlternating) {
    return run(pFQLSpecification, pApplySubsumptionCheck, pApplyInfeasibilityPropagation, pCheckCorrectnessOfCoverageCheck, pPedantic);
  }

  private FShell3Result run(String pFQLSpecification, boolean pApplySubsumptionCheck, boolean pApplyInfeasibilityPropagation, boolean pCheckReachWhenCovered, boolean pPedantic) {
    // Parse FQL Specification
    FQLSpecification lFQLSpecification;
    try {
      lFQLSpecification = FQLSpecification.parse(pFQLSpecification);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    System.out.println("Cache hits (1): " + mCoverageSpecificationTranslator.getOverallCacheHits());
    System.out.println("Cache misses (1): " + mCoverageSpecificationTranslator.getOverallCacheMisses());

    ElementaryCoveragePattern lPassingClause = null;

    if (lFQLSpecification.hasPassingClause()) {
      lPassingClause = mCoverageSpecificationTranslator.mPathPatternTranslator.translate(lFQLSpecification.getPathPattern());
    }

    System.out.println("Cache hits (2): " + mCoverageSpecificationTranslator.getOverallCacheHits());
    System.out.println("Cache misses (2): " + mCoverageSpecificationTranslator.getOverallCacheMisses());

    FShell3Result.Factory lResultFactory = FShell3Result.factory();

    GuardedEdgeAutomatonCPA lPassingCPA = null;

    if (lPassingClause != null) {
      NondeterministicFiniteAutomaton<GuardedEdgeLabel> lAutomaton = ToGuardedAutomatonTranslator.toAutomaton(lPassingClause, mAlphaLabel, mInverseAlphaLabel, mOmegaLabel);
      lPassingCPA = new GuardedEdgeAutomatonCPA(lAutomaton);
    }

    // set up utility variables
    int lIndex = 0;

    int lFeasibleTestGoalsTimeSlot = 0;
    int lInfeasibleTestGoalsTimeSlot = 1;

    TimeAccumulator lTimeAccu = new TimeAccumulator(2);

    TimeAccumulator lTimeReach = new TimeAccumulator();
    TimeAccumulator lTimeCover = new TimeAccumulator();

    IncrementalCoverageSpecificationTranslator lTranslator = new IncrementalCoverageSpecificationTranslator(mCoverageSpecificationTranslator.mPathPatternTranslator);

    int lNumberOfTestGoals = lTranslator.getNumberOfTestGoals(lFQLSpecification.getCoverageSpecification());

    //int lNumberOfTestGoals = -1;

    System.out.println("Number of test goals: " + lNumberOfTestGoals);

    Iterator<ElementaryCoveragePattern> lGoalIterator = lTranslator.translate(lFQLSpecification.getCoverageSpecification());

    int lNumberOfCFAInfeasibleGoals = 0;

    while (lGoalIterator.hasNext()) {
      lIndex++;

      ElementaryCoveragePattern lGoalPattern = lGoalIterator.next();

      System.out.println("Processing test goal #" + lIndex + " of " + lNumberOfTestGoals + " test goals.");

      lTimeAccu.proceed();

      Goal lGoal = new Goal(lGoalPattern, mAlphaLabel, mInverseAlphaLabel, mOmegaLabel);

      NondeterministicFiniteAutomaton<GuardedEdgeLabel> lGoalAutomaton = lGoal.getAutomaton();
      NondeterministicFiniteAutomaton<GuardedEdgeLabel> lGoalAutomaton2 = ToGuardedAutomatonTranslator.removeInfeasibleTransitions(lGoalAutomaton);
      NondeterministicFiniteAutomaton<GuardedEdgeLabel> lGoalAutomaton4 = ToGuardedAutomatonTranslator.removeDeadEnds(lGoalAutomaton2);
      NondeterministicFiniteAutomaton<GuardedEdgeLabel> lGoalAutomaton3 = ToGuardedAutomatonTranslator.reduceEdgeSets(lGoalAutomaton4);

      boolean lIsCovered = false;

      if (pApplySubsumptionCheck) {
        for (Map.Entry<TestCase, CFAEdge[]> lGeneratedTestCase : mGeneratedTestCases.entrySet()) {
          TestCase lTestCase = lGeneratedTestCase.getKey();

          if (!lTestCase.isPrecise()) {
            throw new RuntimeException();
          }

          ThreeValuedAnswer lCoverageAnswer = FShell3.accepts(lGoalAutomaton3, lGeneratedTestCase.getValue());

          if (lCoverageAnswer.equals(ThreeValuedAnswer.ACCEPT)) {
            lIsCovered = true;

            lResultFactory.addFeasibleTestCase(lGoal.getPattern(), lTestCase);

            break;
          }
          else if (lCoverageAnswer.equals(ThreeValuedAnswer.UNKNOWN)) {
            GuardedEdgeAutomatonCPA lAutomatonCPA = new GuardedEdgeAutomatonCPA(lGoalAutomaton3);

            try {
              if (checkCoverage(lTestCase, mWrapper.getEntry(), lAutomatonCPA, lPassingCPA, mWrapper.getOmegaEdge().getSuccessor())) {
                lIsCovered = true;

                lResultFactory.addFeasibleTestCase(lGoal.getPattern(), lTestCase);

                break;
              }
            } catch (InvalidConfigurationException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
              throw new RuntimeException(e);
            } catch (CPAException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
              throw new RuntimeException(e);
            } catch (ImpreciseExecutionException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
              throw new RuntimeException(e);
            }
          }
        }
      }

      if (lIsCovered) {
        System.out.println("Goal #" + lIndex + " is covered by an existing test case!");

        if (!pCheckReachWhenCovered) {
          lTimeAccu.pause(lFeasibleTestGoalsTimeSlot);

          continue;
        }
      }

      GuardedEdgeAutomatonCPA lAutomatonCPA = new GuardedEdgeAutomatonCPA(lGoalAutomaton3);

      lTimeReach.proceed();

      boolean lReachableViaIntervalAnalysis = reach_intervalCPA(lAutomatonCPA, mWrapper.getEntry(), lPassingCPA);

      CounterexampleInfo lCounterexampleInfo = null;

      if (lReachableViaIntervalAnalysis) {
        lCounterexampleInfo = reach2(lAutomatonCPA, mWrapper.getEntry(), lPassingCPA);
      }
      else {
        lNumberOfCFAInfeasibleGoals++;
      }

      lTimeReach.pause();

      if (lCounterexampleInfo == null || lCounterexampleInfo.isSpurious()) {
        System.out.println("Goal #" + lIndex + " is infeasible!");

        if (lIsCovered) {
          throw new RuntimeException("Inconsistent result of coverage check and reachability analysis!");
        }

        //mInfeasibleGoals.put(lGoal.getAutomaton(), mReachedAutomatonStates);

        lResultFactory.addInfeasibleTestCase(lGoal.getPattern());

        lTimeAccu.pause(lInfeasibleTestGoalsTimeSlot);
      }
      else {
        lTimeCover.proceed();

        TestCase lTestCase = TestCase.fromCounterexample(lCounterexampleInfo, mLogManager);

        if (lTestCase.isPrecise()) {
          CFAEdge[] lCFAPath = null;

          boolean lIsPrecise = true;

          try {
            lCFAPath = reconstructPath(lTestCase, mWrapper.getEntry(), lAutomatonCPA, lPassingCPA, mWrapper.getOmegaEdge().getSuccessor());
          } catch (InvalidConfigurationException e) {
            throw new RuntimeException(e);
          } catch (CPAException e) {
            throw new RuntimeException(e);
          } catch (ImpreciseExecutionException e) {
            lIsPrecise = false;
            lTestCase = e.getTestCase();

            if (pPedantic) {
              throw new RuntimeException(e);
            }
          }

          if (lIsPrecise) {
            System.out.println("Goal #" + lIndex + " is feasible!");

            lResultFactory.addFeasibleTestCase(lGoal.getPattern(), lTestCase);

            // we only add precise test cases for coverage analysis
            mGeneratedTestCases.put(lTestCase, lCFAPath);
          }
          else {
            System.err.println("Goal #" + lIndex + " lead to an imprecise execution!");

            lResultFactory.addImpreciseTestCase(lTestCase);
          }
        }
        else {
          System.out.println("Goal #" + lIndex + " is imprecise!");

          lResultFactory.addImpreciseTestCase(lTestCase);
        }

        lTimeAccu.pause(lFeasibleTestGoalsTimeSlot);
        lTimeCover.pause();
      }
    }

    System.out.println("Number of CFA infeasible test goals: " + lNumberOfCFAInfeasibleGoals);

    System.out.println("Time in reach: " + mTimeInReach.getSeconds());
    System.out.println("Mean time of reach: " + (mTimeInReach.getSeconds()/mTimesInReach) + " s");

    // TODO remove ... look at statistics
    //System.out.println("#abstraction elements: " + mPredicateCPA.getAbstractionElementFactory().getNumberOfCreatedAbstractionElements());
    //System.out.println("#nonabstraction elements: " + NonabstractionElement.INSTANCES);

    FShell3Result lResult = lResultFactory.create(lTimeReach.getSeconds(), lTimeCover.getSeconds(), lTimeAccu.getSeconds(lFeasibleTestGoalsTimeSlot), lTimeAccu.getSeconds(lInfeasibleTestGoalsTimeSlot));

    /*if (lResult.getNumberOfTestGoals() != lNumberOfTestGoals) {
      throw new RuntimeException();
    }*/

    System.out.println("Generated Test Cases:");

    for (TestCase lTestCase : lResultFactory.getTestCases()) {
      System.out.println(lTestCase);
    }

    System.out.println("Size of infeasibility cache: " + mInfeasibleGoals.size());

    return lResult;
  }

  private CounterexampleInfo reach2(GuardedEdgeAutomatonCPA pAutomatonCPA, CFAFunctionDefinitionNode pEntryNode, GuardedEdgeAutomatonCPA pPassingCPA) {
    mTimeInReach.proceed();
    mTimesInReach++;

    /*
     * CPAs should be arranged in a way such that frequently failing CPAs, i.e.,
     * CPAs that are not able to produce successors, are treated first such that
     * the compound CPA stops applying further transfer relations early. Here, we
     * have to choose between the number of times a CPA produces no successors and
     * the computational effort necessary to determine that there are no successors.
     */

    LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<ConfigurableProgramAnalysis>();
    lComponentAnalyses.add(mLocationCPA);

    lComponentAnalyses.add(mCallStackCPA);

    List<ConfigurableProgramAnalysis> lAutomatonCPAs = new ArrayList<ConfigurableProgramAnalysis>(2);

    if (pPassingCPA != null) {
      lAutomatonCPAs.add(pPassingCPA);
    }

    lAutomatonCPAs.add(pAutomatonCPA);

    lComponentAnalyses.add(ProductAutomatonCPA.create(lAutomatonCPAs, false));
    lComponentAnalyses.add(mPredicateCPA);

    lComponentAnalyses.add(mAssumeCPA);

    ARTCPA lARTCPA;
    try {
      // create composite CPA
      CPAFactory lCPAFactory = CompositeCPA.factory();
      lCPAFactory.setChildren(lComponentAnalyses);
      lCPAFactory.setConfiguration(mConfiguration);
      lCPAFactory.setLogger(mLogManager);
      ConfigurableProgramAnalysis lCPA = lCPAFactory.createInstance();

      // create ART CPA
      CPAFactory lARTCPAFactory = ARTCPA.factory();
      lARTCPAFactory.setChild(lCPA);
      lARTCPAFactory.setConfiguration(mConfiguration);
      lARTCPAFactory.setLogger(mLogManager);

      lARTCPA = (ARTCPA)lARTCPAFactory.createInstance();
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    } catch (CPAException e) {
      throw new RuntimeException(e);
    }

    CPAAlgorithm lBasicAlgorithm = new CPAAlgorithm(lARTCPA, mLogManager);

    Refiner lRefiner;
    try {
      lRefiner = PredicateRefiner.create(lBasicAlgorithm.getCPA());
    } catch (CPAException e) {
      throw new RuntimeException(e);
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    }

    CEGARAlgorithm lAlgorithm;
    try {
      lAlgorithm = new CEGARAlgorithm(lBasicAlgorithm, lRefiner, mConfiguration, mLogManager);
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    } catch (CPAException e) {
      throw new RuntimeException(e);
    }

    Statistics lARTStatistics;
    try {
      lARTStatistics = new ARTStatistics(mConfiguration, lARTCPA);
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    }
    Set<Statistics> lStatistics = new HashSet<Statistics>();
    lStatistics.add(lARTStatistics);
    lAlgorithm.collectStatistics(lStatistics);

    AbstractElement lInitialElement = lARTCPA.getInitialElement(pEntryNode);
    Precision lInitialPrecision = lARTCPA.getInitialPrecision(pEntryNode);

    ReachedSet lReachedSet = new PartitionedReachedSet(Waitlist.TraversalMethod.TOPSORT);
    lReachedSet.add(lInitialElement, lInitialPrecision);

    try {
      lAlgorithm.run(lReachedSet);
    } catch (CPAException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      throw new RuntimeException(e);
    }

    mTimeInReach.pause();

    return lARTCPA.getLastCounterexample();
  }

  private boolean checkCoverage(TestCase pTestCase, CFAFunctionDefinitionNode pEntry, GuardedEdgeAutomatonCPA pCoverAutomatonCPA, GuardedEdgeAutomatonCPA pPassingAutomatonCPA, CFANode pEndNode) throws InvalidConfigurationException, CPAException, ImpreciseExecutionException {
    LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<ConfigurableProgramAnalysis>();
    lComponentAnalyses.add(mLocationCPA);

    List<ConfigurableProgramAnalysis> lAutomatonCPAs = new ArrayList<ConfigurableProgramAnalysis>(2);

    // test goal automata CPAs
    if (pPassingAutomatonCPA != null) {
      lAutomatonCPAs.add(pPassingAutomatonCPA);
    }

    lAutomatonCPAs.add(pCoverAutomatonCPA);

    int lProductAutomatonCPAIndex = lComponentAnalyses.size();
    lComponentAnalyses.add(ProductAutomatonCPA.create(lAutomatonCPAs, false));

    // call stack CPA
    lComponentAnalyses.add(mCallStackCPA);

    // explicit CPA
    InterpreterCPA lInterpreterCPA = new InterpreterCPA(pTestCase.getInputs());
    lComponentAnalyses.add(lInterpreterCPA);

    // assume CPA
    lComponentAnalyses.add(mAssumeCPA);


    CPAFactory lCPAFactory = CompositeCPA.factory();
    lCPAFactory.setChildren(lComponentAnalyses);
    lCPAFactory.setConfiguration(mConfiguration);
    lCPAFactory.setLogger(mLogManager);
    ConfigurableProgramAnalysis lCPA = lCPAFactory.createInstance();

    CPAAlgorithm lAlgorithm = new CPAAlgorithm(lCPA, mLogManager);

    AbstractElement lInitialElement = lCPA.getInitialElement(pEntry);
    Precision lInitialPrecision = lCPA.getInitialPrecision(pEntry);

    ReachedSet lReachedSet = new PartitionedReachedSet(Waitlist.TraversalMethod.TOPSORT);
    lReachedSet.add(lInitialElement, lInitialPrecision);

    try {
      lAlgorithm.run(lReachedSet);
    } catch (CPAException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      throw new RuntimeException(e);
    }

    // TODO sanity check by assertion
    CompositeElement lEndNode = (CompositeElement)lReachedSet.getLastElement();

    if (lEndNode == null) {
      return false;
    }
    else {
      if (((LocationElement)lEndNode.get(0)).getLocationNode().equals(pEndNode)) {
        // location of last element is at end node

        AbstractElement lProductAutomatonElement = lEndNode.get(lProductAutomatonCPAIndex);

        if (lProductAutomatonElement instanceof Targetable) {
          Targetable lTargetable = (Targetable)lProductAutomatonElement;

          return lTargetable.isTarget();
        }

        return false;
      }

      return false;
    }
  }

  private CFAEdge[] reconstructPath(TestCase pTestCase, CFAFunctionDefinitionNode pEntry, GuardedEdgeAutomatonCPA pCoverAutomatonCPA, GuardedEdgeAutomatonCPA pPassingAutomatonCPA, CFANode pEndNode) throws InvalidConfigurationException, CPAException, ImpreciseExecutionException {
    LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<ConfigurableProgramAnalysis>();
    lComponentAnalyses.add(mLocationCPA);

    List<ConfigurableProgramAnalysis> lAutomatonCPAs = new ArrayList<ConfigurableProgramAnalysis>(2);

    // test goal automata CPAs
    if (pPassingAutomatonCPA != null) {
      lAutomatonCPAs.add(pPassingAutomatonCPA);
    }

    lAutomatonCPAs.add(pCoverAutomatonCPA);

    int lProductAutomatonCPAIndex = lComponentAnalyses.size();
    lComponentAnalyses.add(ProductAutomatonCPA.create(lAutomatonCPAs, false));

    // call stack CPA
    lComponentAnalyses.add(mCallStackCPA);

    // explicit CPA
    InterpreterCPA lInterpreterCPA = new InterpreterCPA(pTestCase.getInputs());
    lComponentAnalyses.add(lInterpreterCPA);

    // CFA path CPA
    int lCFAPathCPAIndex = lComponentAnalyses.size();
    lComponentAnalyses.add(mCFAPathCPA);

    // assume CPA
    lComponentAnalyses.add(mAssumeCPA);


    CPAFactory lCPAFactory = CompositeCPA.factory();
    lCPAFactory.setChildren(lComponentAnalyses);
    lCPAFactory.setConfiguration(mConfiguration);
    lCPAFactory.setLogger(mLogManager);
    ConfigurableProgramAnalysis lCPA = lCPAFactory.createInstance();

    CPAAlgorithm lAlgorithm = new CPAAlgorithm(lCPA, mLogManager);

    AbstractElement lInitialElement = lCPA.getInitialElement(pEntry);
    Precision lInitialPrecision = lCPA.getInitialPrecision(pEntry);

    ReachedSet lReachedSet = new PartitionedReachedSet(Waitlist.TraversalMethod.TOPSORT);
    lReachedSet.add(lInitialElement, lInitialPrecision);

    try {
      lAlgorithm.run(lReachedSet);
    } catch (CPAException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      throw new RuntimeException(e);
    }

    // TODO sanity check by assertion
    CompositeElement lEndNode = (CompositeElement)lReachedSet.getLastElement();

    if (lEndNode == null) {
      throw new ImpreciseExecutionException(pTestCase, pCoverAutomatonCPA, pPassingAutomatonCPA);
    }

    if (!((LocationElement)lEndNode.get(0)).getLocationNode().equals(pEndNode)) {
      throw new ImpreciseExecutionException(pTestCase, pCoverAutomatonCPA, pPassingAutomatonCPA);
    }

    AbstractElement lProductAutomatonElement = lEndNode.get(lProductAutomatonCPAIndex);

    if (!(lProductAutomatonElement instanceof Targetable)) {
      throw new RuntimeException();
    }

    Targetable lTargetable = (Targetable)lProductAutomatonElement;

    if (!lTargetable.isTarget()) {
      throw new RuntimeException();
    }

    CFAPathStandardElement lPathElement = (CFAPathStandardElement)lEndNode.get(lCFAPathCPAIndex);

    return lPathElement.toArray();
  }

  private boolean reach_intervalCPA(GuardedEdgeAutomatonCPA pAutomatonCPA, CFAFunctionDefinitionNode pEntryNode, GuardedEdgeAutomatonCPA pPassingCPA) {
    mTimeInReach.proceed();
    mTimesInReach++;

    /*
     * CPAs should be arranged in a way such that frequently failing CPAs, i.e.,
     * CPAs that are not able to produce successors, are treated first such that
     * the compound CPA stops applying further transfer relations early. Here, we
     * have to choose between the number of times a CPA produces no successors and
     * the computational effort necessary to determine that there are no successors.
     */

    LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<ConfigurableProgramAnalysis>();
    lComponentAnalyses.add(mLocationCPA);

    lComponentAnalyses.add(mCallStackCPA);

    List<ConfigurableProgramAnalysis> lAutomatonCPAs = new ArrayList<ConfigurableProgramAnalysis>(2);

    if (pPassingCPA != null) {
      lAutomatonCPAs.add(pPassingCPA);
    }

    lAutomatonCPAs.add(pAutomatonCPA);

    int lProductAutomatonIndex = lComponentAnalyses.size();
    lComponentAnalyses.add(ProductAutomatonCPA.create(lAutomatonCPAs, false));

    /*try {
      CPAFactory lFactory = IntervalAnalysisCPA.factory();
      lFactory.setConfiguration(mConfiguration);
      lFactory.setLogger(mLogManager);
      ConfigurableProgramAnalysis lIntervalCPA = lFactory.createInstance();
      lComponentAnalyses.add(lIntervalCPA);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }*/

    lComponentAnalyses.add(mAssumeCPA);

    ConfigurableProgramAnalysis lCPA;
    try {
      // create composite CPA
      CPAFactory lCPAFactory = CompositeCPA.factory();
      lCPAFactory.setChildren(lComponentAnalyses);
      lCPAFactory.setConfiguration(mConfiguration);
      lCPAFactory.setLogger(mLogManager);
      lCPA = lCPAFactory.createInstance();
    } catch (InvalidConfigurationException e) {
      throw new RuntimeException(e);
    } catch (CPAException e) {
      throw new RuntimeException(e);
    }

    CPAAlgorithm lBasicAlgorithm = new CPAAlgorithm(lCPA, mLogManager);

    AbstractElement lInitialElement = lCPA.getInitialElement(pEntryNode);
    Precision lInitialPrecision = lCPA.getInitialPrecision(pEntryNode);

    //ReachedSet lReachedSet = new PartitionedReachedSet(Waitlist.TraversalMethod.TOPSORT);
    ReachedSet lReachedSet = new PartitionedReachedSet(Waitlist.TraversalMethod.DFS);
    lReachedSet.add(lInitialElement, lInitialPrecision);

    try {
      lBasicAlgorithm.run(lReachedSet);
    } catch (CPAException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      throw new RuntimeException(e);
    }

    CompositeElement lLastElement = (CompositeElement)lReachedSet.getLastElement();
    ProductAutomatonElement lProductAutomatonElement = (ProductAutomatonElement)lLastElement.get(lProductAutomatonIndex);

    mTimeInReach.pause();

    return lProductAutomatonElement.isFinalState();
  }
}

