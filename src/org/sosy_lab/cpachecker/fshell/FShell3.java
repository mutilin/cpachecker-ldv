package org.sosy_lab.cpachecker.fshell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.CParser.Dialect;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.fshell.interfaces.FQLCoverageAnalyser;
import org.sosy_lab.cpachecker.fshell.interfaces.FQLTestGenerator;
import org.sosy_lab.cpachecker.fshell.testcases.ImpreciseExecutionException;
import org.sosy_lab.cpachecker.fshell.testcases.LoggingTestSuite;
import org.sosy_lab.cpachecker.fshell.testcases.TestCase;
import org.sosy_lab.cpachecker.fshell.testcases.TestSuite;
import org.sosy_lab.cpachecker.util.automaton.NondeterministicFiniteAutomaton;
import org.sosy_lab.cpachecker.util.ecp.translators.GuardedEdgeLabel;

import com.google.common.base.Joiner;

/*
 * TODO AutomatonBuilder <- integrate State-Pool there to ensure correct time
 * measurements when invoking FlleSh several times in a unit test.
 *
 * TODO Incremental test goal automaton creation: extending automata (can we reuse
 * parts of the reached set?) This requires a change in the coverage check.
 * -> Handle enormous amounts of test goals.
 *
 */

public class FShell3 implements FQLTestGenerator, FQLCoverageAnalyser {

  private final NonincrementalFQLTestGenerator mNonincrementalTestGenerator;
  private final IncrementalFQLTestGenerator mIncrementalTestGenerator;
  private final IncrementalAndAlternatingFQLTestGenerator mIncrementalAndAlternatingTestGenerator;
  private final StandardFQLCoverageAnalyser mCoverageAnalyser;

  private final IncrementalARTReusingFQLTestGenerator mIncrementalARTReusingTestGenerator;

  private String mFeasibilityInformationOutputFile = null;
  private String mFeasibilityInformationInputFile = null;
  private String mTestSuiteOutputFile = null;
  private int mMinIndex = 0;
  private int mMaxIndex = Integer.MAX_VALUE;
  private boolean mDoLogging = false;
  private boolean mDoAppendingLogging = false;
  private boolean mDoRestart = false;
  private long mRestartBound = 100000000; // 100 MB

  public FShell3(String pSourceFileName, String pEntryFunction) {
    mNonincrementalTestGenerator = new NonincrementalFQLTestGenerator(pSourceFileName, pEntryFunction);
    mIncrementalTestGenerator = new IncrementalFQLTestGenerator(pSourceFileName, pEntryFunction);
    mIncrementalAndAlternatingTestGenerator = new IncrementalAndAlternatingFQLTestGenerator(pSourceFileName, pEntryFunction);

    mCoverageAnalyser = new StandardFQLCoverageAnalyser(pSourceFileName, pEntryFunction);

    mIncrementalARTReusingTestGenerator = new IncrementalARTReusingFQLTestGenerator(pSourceFileName, pEntryFunction);
  }

  public void doRestart() {
    mDoRestart = true;
  }

  public void setRestartBound(long pRestartBound) {
    mRestartBound = pRestartBound;
  }

  public void setFeasibilityInformationOutputFile(String pFile) {
    mFeasibilityInformationOutputFile = pFile;
  }

  public void setFeasibilityInformationInputFile(String pFile) {
    mFeasibilityInformationInputFile = pFile;
  }

  public void setTestSuiteOutputFile(String pFile) {
    mTestSuiteOutputFile = pFile;
  }

  public void setGoalIndices(int pMinIndex, int pMaxIndex) {
    setMinIndex(pMinIndex);
    setMaxIndex(pMaxIndex);
  }

  public void setMinIndex(int pIndex) {
    mMinIndex = pIndex;
  }

  public void setMaxIndex(int pIndex) {
    mMaxIndex = pIndex;
  }

  public void doLogging() {
    mDoLogging = true;
  }

  public void doAppendingLogging() {
    mDoAppendingLogging = true;
  }

  public void seed(Collection<TestCase> pTestSuite) throws InvalidConfigurationException, CPAException, ImpreciseExecutionException {
    mIncrementalARTReusingTestGenerator.seed(pTestSuite);
  }

  public FShell3Result run(String pFQLSpecification) {
    return run(pFQLSpecification, true, false, false, false, true, false);
  }

  @Override
  public FShell3Result run(String pFQLSpecification, boolean pApplySubsumptionCheck, boolean pApplyInfeasibilityPropagation, boolean pGenerateTestGoalAutomataInAdvance, boolean pCheckCorrectnessOfCoverageCheck, boolean pPedantic, boolean pAlternating) {
    if (pGenerateTestGoalAutomataInAdvance) {
      return mNonincrementalTestGenerator.run(pFQLSpecification, pApplySubsumptionCheck, pApplyInfeasibilityPropagation, pGenerateTestGoalAutomataInAdvance, pCheckCorrectnessOfCoverageCheck, pPedantic, pAlternating);
    }
    else {
      if (pAlternating) {
        return mIncrementalAndAlternatingTestGenerator.run(pFQLSpecification, pApplySubsumptionCheck, pApplyInfeasibilityPropagation, pGenerateTestGoalAutomataInAdvance, pCheckCorrectnessOfCoverageCheck, pPedantic, pAlternating);
      }
      else {
        // TODO make configurable
        if (!pAlternating) {

          mIncrementalARTReusingTestGenerator.setGoalIndices(mMinIndex, mMaxIndex);

          FeasibilityInformation lFeasibilityInformation;
          TestSuite lTestSuite;

          if (mFeasibilityInformationInputFile != null) {
            try {
              lFeasibilityInformation = FeasibilityInformation.load(mFeasibilityInformationInputFile);

              if (!lFeasibilityInformation.hasTestsuiteFilename()) {
                throw new RuntimeException();
              }

              lTestSuite = TestSuite.load(lFeasibilityInformation.getTestsuiteFilename());

              mIncrementalARTReusingTestGenerator.setTestSuite(lTestSuite);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
          else {
            lFeasibilityInformation = new FeasibilityInformation();
            lTestSuite = new TestSuite();
          }

          if (mDoLogging) {
            if (mFeasibilityInformationOutputFile != null) {
              try {
                if (mTestSuiteOutputFile != null) {
                  lTestSuite = new LoggingTestSuite(lTestSuite, mTestSuiteOutputFile, mDoAppendingLogging);

                  lFeasibilityInformation.setTestsuiteFilename(mTestSuiteOutputFile);
                }
                else {
                  File lCWD = new java.io.File( "." );
                  File lTestSuiteFile = File.createTempFile("testsuite", ".tst", lCWD);
                  lTestSuite = new LoggingTestSuite(lTestSuite, lTestSuiteFile.getCanonicalPath(), mDoAppendingLogging);

                  lFeasibilityInformation.setTestsuiteFilename(lTestSuiteFile.getCanonicalPath());
                }

                lFeasibilityInformation = new LoggingFeasibilityInformation(lFeasibilityInformation, mFeasibilityInformationOutputFile, mDoAppendingLogging);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          }

          mIncrementalARTReusingTestGenerator.setFeasibilityInformation(lFeasibilityInformation);
          try {
            mIncrementalARTReusingTestGenerator.setTestSuite(lTestSuite);
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }

          if (mDoRestart) {
            mIncrementalARTReusingTestGenerator.doRestart();
            mIncrementalARTReusingTestGenerator.setRestartBound(mRestartBound);
          }

          FShell3Result lResult = mIncrementalARTReusingTestGenerator.run(pFQLSpecification, pApplySubsumptionCheck, pApplyInfeasibilityPropagation, pGenerateTestGoalAutomataInAdvance, pCheckCorrectnessOfCoverageCheck, pPedantic, pAlternating);

          if (mDoLogging) {
            ((LoggingTestSuite)lTestSuite).close();
            ((LoggingFeasibilityInformation)lFeasibilityInformation).close();
          }
          else {
            if (mFeasibilityInformationOutputFile != null) {
              try {
                if (mTestSuiteOutputFile != null) {
                  lTestSuite.write(mTestSuiteOutputFile);
                  lFeasibilityInformation.setTestsuiteFilename(mTestSuiteOutputFile);
                }
                else {
                  File lCWD = new java.io.File( "." );
                  File lTestSuiteFile = File.createTempFile("testsuite", ".tst", lCWD);
                  lTestSuite.write(lTestSuiteFile);
                  lFeasibilityInformation.setTestsuiteFilename(lTestSuiteFile.getCanonicalPath());
                }

                lFeasibilityInformation.write(mFeasibilityInformationOutputFile);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          }

          return lResult;
        }
        else {
          return mIncrementalTestGenerator.run(pFQLSpecification, pApplySubsumptionCheck, pApplyInfeasibilityPropagation, pGenerateTestGoalAutomataInAdvance, pCheckCorrectnessOfCoverageCheck, pPedantic, pAlternating);
        }
      }
    }
  }

  @Override
  public void checkCoverage(String pFQLSpecification, Collection<TestCase> pTestSuite, boolean pPedantic) {
    mCoverageAnalyser.checkCoverage(pFQLSpecification, pTestSuite, pPedantic);
  }

  public static Map<String, CFAFunctionDefinitionNode> getCFAMap(String pSourceFileName, Configuration pConfiguration, LogManager pLogManager) throws InvalidConfigurationException {
    CFACreator lCFACreator = new CFACreator(Dialect.GNUC, pConfiguration, pLogManager);

    // parse code file
    try {
      lCFACreator.parseFileAndCreateCFA(pSourceFileName);
    } catch (Exception e) {
      e.printStackTrace();

      throw new RuntimeException(e);
    }

    return lCFACreator.getFunctions();
  }

  public static ThreeValuedAnswer accepts(NondeterministicFiniteAutomaton<GuardedEdgeLabel> pAutomaton, CFAEdge[] pCFAPath) {
    Set<NondeterministicFiniteAutomaton.State> lCurrentStates = new HashSet<NondeterministicFiniteAutomaton.State>();
    Set<NondeterministicFiniteAutomaton.State> lNextStates = new HashSet<NondeterministicFiniteAutomaton.State>();

    lCurrentStates.add(pAutomaton.getInitialState());

    boolean lHasPredicates = false;

    for (CFAEdge lCFAEdge : pCFAPath) {
      for (NondeterministicFiniteAutomaton.State lCurrentState : lCurrentStates) {
        // Automaton accepts as soon as it sees a final state (implicit self-loop)
        if (pAutomaton.getFinalStates().contains(lCurrentState)) {
          return ThreeValuedAnswer.ACCEPT;
        }

        for (NondeterministicFiniteAutomaton<GuardedEdgeLabel>.Edge lOutgoingEdge : pAutomaton.getOutgoingEdges(lCurrentState)) {
          GuardedEdgeLabel lLabel = lOutgoingEdge.getLabel();

          if (lLabel.hasGuards()) {
            lHasPredicates = true;
          }
          else {
            if (lLabel.contains(lCFAEdge)) {
              lNextStates.add(lOutgoingEdge.getTarget());
            }
          }
        }
      }

      lCurrentStates.clear();

      Set<NondeterministicFiniteAutomaton.State> lTmp = lCurrentStates;
      lCurrentStates = lNextStates;
      lNextStates = lTmp;
    }

    for (NondeterministicFiniteAutomaton.State lCurrentState : lCurrentStates) {
      // Automaton accepts as soon as it sees a final state (implicit self-loop)
      if (pAutomaton.getFinalStates().contains(lCurrentState)) {
        return ThreeValuedAnswer.ACCEPT;
      }
    }

    if (lHasPredicates) {
      return ThreeValuedAnswer.UNKNOWN;
    }
    else {
      return ThreeValuedAnswer.REJECT;
    }
  }

  public static Configuration createConfiguration(String pSourceFile, String pEntryFunction) throws InvalidConfigurationException {
    File lPropertiesFile = FShell3.createPropertiesFile(pEntryFunction);
    return createConfiguration(Collections.singletonList(pSourceFile), lPropertiesFile.getAbsolutePath());
  }

  private static Configuration createConfiguration(List<String> pSourceFiles, String pPropertiesFile) throws InvalidConfigurationException {
    Map<String, String> lCommandLineOptions = new HashMap<String, String>();

    lCommandLineOptions.put("analysis.programNames", Joiner.on(", ").join(pSourceFiles));
    //lCommandLineOptions.put("output.path", "test/output");

    Configuration lConfiguration = null;
    try {
      lConfiguration = Configuration.builder()
                                    .loadFromFile(pPropertiesFile)
                                    .setOptions(lCommandLineOptions)
                                    .build();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return lConfiguration;
  }

  private static File createPropertiesFile(String pEntryFunction) {
    if (pEntryFunction == null) {
      throw new IllegalArgumentException("Parameter pEntryFunction is null!");
    }

    File lPropertiesFile = null;

    try {

      lPropertiesFile = File.createTempFile("fshell.", ".properties");
      lPropertiesFile.deleteOnExit();

      PrintWriter lWriter = new PrintWriter(new FileOutputStream(lPropertiesFile));
      // we do not use a fixed error location (error label) therefore
      // we do not want to remove parts of the CFA
      lWriter.println("cfa.removeIrrelevantForErrorLocations = false");

      //lWriter.println("log.consoleLevel = ALL");

      lWriter.println("analysis.traversal.order = topsort");
      lWriter.println("analysis.entryFunction = " + pEntryFunction);

      // we want to use CEGAR algorithm
      lWriter.println("analysis.useRefinement = true");
      lWriter.println("cegar.refiner = " + org.sosy_lab.cpachecker.cpa.predicate.PredicateRefiner.class.getCanonicalName());

      lWriter.println("cpa.predicate.addBranchingInformation = false");
      lWriter.println("cpa.predicate.useNondetFlags = true");
      lWriter.println("cpa.predicate.initAllVars = false");
      //lWriter.println("cpa.predicate.noAutoInitPrefix = __BLAST_NONDET");
      lWriter.println("cpa.predicate.blk.useCache = false");
      //lWriter.println("cpa.predicate.mathsat.lvalsAsUIFs = true");
      // we need theory combination for example for using uninterpreted functions used in conjunction with linear arithmetic (correctly)
      // TODO caution: using dtc changes the results ... WRONG RESULTS !!!
      //lWriter.println("cpa.predicate.mathsat.useDtc = true");

      lWriter.println("cpa.interval.merge = JOIN");

      lWriter.close();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return lPropertiesFile;
  }
}

