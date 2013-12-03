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
package org.sosy_lab.cpachecker.cpa.automaton;

import java.io.File;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.sosy_lab.common.Files;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.converters.FileTypeConverter;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.StringBuildingLogHandler;
import org.sosy_lab.cpachecker.core.CPAchecker;
import org.sosy_lab.cpachecker.core.CPAcheckerResult;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;

import com.google.common.collect.ImmutableMap;

public class AutomatonTest {
  private static final String CPAS_UNINITVARS = "cpa.location.LocationCPA, cpa.uninitvars.UninitializedVariablesCPA";
  private static final String OUTPUT_FILE = "output/AutomatonExport.dot";

  // Specification Tests
  @Test
  public void CyclicInclusionTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              CPAS_UNINITVARS,
        "specification",     "test/config/automata/tmpSpecification.spc",
        "log.consoleLevel",               "INFO",
        "analysis.stopAfterError",        "FALSE"
      );

      File tmpSpc = new File("test/config/automata/tmpSpecification.spc");
      String content = "#include UninitializedVariablesTestAutomaton.txt \n" +
      "#include tmpSpecification.spc \n";
      Files.writeFile(tmpSpc, content);
      TestResults results = run(prop, "test/programs/simple/UninitVarsErrors.c");
      Assert.assertTrue(results.isSafe());
      Assert.assertTrue(results.logContains("File \"test/config/automata/tmpSpecification.spc\" was referenced multiple times."));
      Assert.assertTrue("Could not delete temporary specification",tmpSpc.delete());
  }
  @Test
  public void IncludeSpecificationTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              CPAS_UNINITVARS,
        "specification",     "test/config/automata/defaultSpecificationForTesting.spc",
        "log.consoleLevel",               "INFO",
        "analysis.stopAfterError",        "FALSE"
      );

      TestResults results = run(prop, "test/programs/simple/UninitVarsErrors.c");
      Assert.assertTrue(results.logContains("Automaton: Uninitialized return value"));
      Assert.assertTrue(results.logContains("Automaton: Uninitialized variable used"));
/*
      results = run(prop, "test/programs/simple/PointerAnalysisErrors.c");
      Assert.assertTrue(results.logContains("Found a DOUBLE_FREE"));
      Assert.assertTrue(results.logContains("Found an INVALID_FREE"));
      Assert.assertTrue(results.logContains("Found a POTENTIALLY_UNSAFE_DEREFERENCE"));
      Assert.assertTrue(results.logContains("Found a Memory Leak"));
      Assert.assertTrue(results.logContains("Found an UNSAFE_DEREFERENCE"));
*/
  }
  @Test
  public void SpecificationAndNoCompositeTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "cpa", "cpa.location.LocationCPA",
        "log.consoleLevel", "INFO",
        "specification", "test/config/automata/LockingAutomatonAll.txt");

      TestResults results = run(prop, "test/programs/simple/modificationExample.c");
      Assert.assertTrue(results.logContains("Option specification gave specification automata, but no CompositeCPA was used"));
      Assert.assertEquals(CPAcheckerResult.Result.NOT_YET_STARTED, results.getCheckerResult().getResult());
  }
  @Test
  public void modificationTestWithSpecification() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA, cpa.explicit.ExplicitCPA",
        "specification",     "test/config/automata/modifyingAutomaton.txt",
        "log.consoleLevel",               "INFO",
        "cpa.explicit.threshold",       "10");

      TestResults results = run(prop, "test/programs/simple/modificationExample.c");
      Assert.assertTrue(results.logContains("MODIFIED"));
      Assert.assertTrue(results.logContains("Modification successful"));
      Assert.assertTrue(results.isSafe());
  }

  //Automaton Tests
  @Test
  public void MatchEndOfProgramTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA",
        "specification",     "test/config/automata/PrintLastStatementAutomaton.spc",
        "log.consoleLevel",               "INFO",
        "analysis.stopAfterError",        "TRUE"
      );

      TestResults results = run(prop, "test/programs/simple/loop1.c");
      Assert.assertTrue(results.logContains("Last statement is \"return (0);\""));
      Assert.assertTrue(results.logContains("Last statement is \"return (-1);\""));
      Assert.assertTrue(results.isSafe());
  }
  @Test
  public void failIfNoAutomatonGiven() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA, cpa.explicit.ExplicitCPA, cpa.automaton.ControlAutomatonCPA",
        "log.consoleLevel",               "INFO",
        "cpa.explicit.threshold",       "10");

      TestResults results = run(prop, "test/programs/simple/modificationExample.c");
      Assert.assertEquals(CPAcheckerResult.Result.NOT_YET_STARTED, results.getCheckerResult().getResult());
      Assert.assertTrue(results.logContains("Explicitly specified automaton CPA needs option cpa.automaton.inputFile!"));
  }

  @Test
  public void modificationTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA, cpa.explicit.ExplicitCPA, cpa.automaton.ControlAutomatonCPA",
        "cpa.automaton.inputFile",     "test/config/automata/modifyingAutomaton.txt",
        "log.consoleLevel",               "INFO",
        "cpa.explicit.threshold",       "10");

      TestResults results = run(prop, "test/programs/simple/modificationExample.c");
      Assert.assertTrue(results.logContains("MODIFIED"));
      Assert.assertTrue(results.logContains("Modification successful"));
      Assert.assertTrue(results.isSafe());
  }

  @Test
  public void modification_in_Observer_throws_Test() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA, cpa.explicit.ExplicitCPA, cpa.automaton.ObserverAutomatonCPA",
        "cpa.automaton.inputFile",     "test/config/automata/modifyingAutomaton.txt",
        "log.consoleLevel",               "SEVERE",
        "cpa.explicit.threshold",       "10"
      );

      TestResults results = run(prop, "test/programs/simple/modificationExample.c");
      // check for stack trace
      Assert.assertTrue(results.logContains("Error: Invalid configuration (The transition \"MATCH "));
  }

  @Test
  public void setuidTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA",
        "cpa.automaton.inputFile",     "test/config/automata/simple_setuid.txt",
        "log.consoleLevel",               "INFO",
        "analysis.stopAfterError",        "FALSE"
      );


      TestResults results = run(prop, "test/programs/simple/simple_setuid_test.c");
      Assert.assertTrue(results.logContains("Systemcall in line 10 with userid 2"));
      Assert.assertTrue(results.logContains("going to ErrorState on edge \"system(40);\""));
      Assert.assertTrue(results.isUnsafe());
  }
  @Test
  public void uninitVarsTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA, cpa.uninitvars.UninitializedVariablesCPA",
        "cpa.automaton.inputFile",     "test/config/automata/UninitializedVariablesTestAutomaton.txt",
        "log.consoleLevel",               "FINER",
        "cpa.automaton.dotExportFile", OUTPUT_FILE,
        "analysis.stopAfterError",        "FALSE"
      );

      TestResults results = run(prop, "test/programs/simple/UninitVarsErrors.c");
      Assert.assertTrue(results.logContains("Automaton: Uninitialized return value"));
      Assert.assertTrue(results.logContains("Automaton: Uninitialized variable used"));
  }

  @Test
  public void locking_correct() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA",
        "cpa.automaton.inputFile",     "test/config/automata/LockingAutomatonAll.txt",
        "log.consoleLevel",               "INFO",
        "cpa.automaton.dotExportFile", OUTPUT_FILE
      );

      TestResults results = run(prop, "test/programs/simple/locking_correct.c");
      Assert.assertTrue(results.isSafe());
  }

  @Test
  public void locking_incorrect() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA",
        "cpa.automaton.inputFile",     "test/config/automata/LockingAutomatonAll.txt",
        "log.consoleLevel",               "INFO"
      );

      TestResults results = run(prop, "test/programs/simple/locking_incorrect.c");
      Assert.assertTrue(results.isUnsafe());
  }

  @Test
  public void explicitAnalysis_observing() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA, cpa.explicit.ExplicitCPA",
        "cpa.automaton.inputFile",     "test/config/automata/ExplicitAnalysisObservingAutomaton.txt",
        "log.consoleLevel",               "INFO",
        "cpa.explicit.threshold" , "2000"
      );

      TestResults results = run(prop, "test/programs/simple/ex2.cil.c");
      Assert.assertTrue(results.logContains("st==3 after Edge st = 3;"));
      Assert.assertTrue(results.logContains("st==1 after Edge st = 1;"));
      Assert.assertTrue(results.logContains("st==2 after Edge st = 2;"));
      Assert.assertTrue(results.logContains("st==4 after Edge st = 4;"));
      Assert.assertTrue(results.isSafe());
  }

  @Test
  public void functionIdentifying() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",              "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA",
        "cpa.automaton.inputFile",     "test/config/automata/FunctionIdentifyingAutomaton.txt",
        "log.consoleLevel",               "FINER"
      );

      TestResults results = run(prop, "test/programs/simple/functionCall.c");
      Assert.assertTrue(results.logContains("i'm in Main after Edge int y;"));
      Assert.assertTrue(results.logContains("i'm in f after Edge y = f()"));
      Assert.assertTrue(results.logContains("i'm in f after Edge int x;"));
      Assert.assertTrue(results.logContains("i'm in Main after Edge return"));
      Assert.assertTrue(results.logContains("i'm in Main after Edge ERROR:"));
  }

  @Test
  public void interacting_Automata() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA automatonA, cpa.automaton.ObserverAutomatonCPA automatonB, cpa.explicit.ExplicitCPA",
        "automatonA.cpa.automaton.inputFile",     "test/config/automata/InteractionAutomatonA.txt",
        "automatonB.cpa.automaton.inputFile",     "test/config/automata/InteractionAutomatonB.txt",
        "log.consoleLevel", "INFO",
        "cpa.explicit.threshold" , "2000"
      );

      TestResults results = run(prop, "test/programs/simple/loop1.c");
      Assert.assertTrue(results.logContains("A: Matched i in line 13 x=2"));
      Assert.assertTrue(results.logContains("B: A increased to 2 And i followed "));
      Assert.assertTrue(results.isSafe());
  }

  private TestResults run(Map<String, String> pProperties, String pSourceCodeFilePath) throws Exception {
    Configuration config = Configuration.builder()
                                        .addConverter(FileOption.class, new FileTypeConverter(Configuration.defaultConfiguration()))
                                        .setOptions(pProperties)
                                        .build();
    StringBuildingLogHandler stringLogHandler = new StringBuildingLogHandler();
    LogManager logger = new BasicLogManager(config, stringLogHandler);
    ShutdownNotifier shutdownNotifier = ShutdownNotifier.create();
    CPAchecker cpaChecker = new CPAchecker(config, logger, shutdownNotifier);
    CPAcheckerResult results = cpaChecker.run(pSourceCodeFilePath);
    return new TestResults(stringLogHandler.getLog(), results);
  }

  private static class TestResults {
    private String log;
    private CPAcheckerResult checkerResult;
    public TestResults(String pLog, CPAcheckerResult pCheckerResult) {
      super();
      log = pLog;
      checkerResult = pCheckerResult;
    }
    @SuppressWarnings("unused")
    public String getLog() {
      return log;
    }
    public CPAcheckerResult getCheckerResult() {
      return checkerResult;
    }
    boolean logContains(String pattern) {
     return log.contains(pattern);
    }
    boolean isSafe() {
      return checkerResult.getResult().equals(CPAcheckerResult.Result.SAFE);
    }
    boolean isUnsafe() {
      return checkerResult.getResult().equals(CPAcheckerResult.Result.UNSAFE);
    }
    @Override
    public String toString() {
      return log;
    }
  }
}
