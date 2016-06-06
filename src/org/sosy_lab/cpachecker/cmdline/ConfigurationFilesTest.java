/*
 *  CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cmdline;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.testing.TestLogHandler;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.converters.FileTypeConverter;
import org.sosy_lab.common.io.MoreFiles;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.ConsoleLogFormatter;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.CPAchecker;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * Test that the bundled configuration files are all valid.
 */
@RunWith(Parameterized.class)
public class ConfigurationFilesTest {

  private static final Pattern ALLOWED_WARNINGS =
      Pattern.compile(
          ".*File .* does not exist.*"
              + "|The following configuration options were specified but are not used:.*"
              + "|Handling of pointer aliasing is disabled, analysis is unsound if aliased pointers exist.",
          Pattern.DOTALL);

  private static final ImmutableList<String> UNUSED_OPTIONS =
      ImmutableList.of(
          // always set by this test
          "java.sourcepath",
          // handled by code outside of CPAchecker class
          "output.disable",
          "limits.time.cpu",
          "memorysafety.config",
          "overflow.config",
          "pcc.proofgen.doPCC",
          // only handled if specification automaton is additionally specified
          "cpa.automaton.breakOnTargetState",
          "WitnessAutomaton.cpa.automaton.treatErrorsAsTargets",
          // handled by component that is loaded lazily on demand
          "invariantGeneration.adjustConditions",
          "invariantGeneration.async",
          "invariantGeneration.config",
          "invariantGeneration.kInduction.async",
          "invariantGeneration.kInduction.guessCandidatesFromCFA",
          "invariantGeneration.kInduction.terminateOnCounterexample",
          // irrelevant if other solver is used
          "solver.z3.requireProofs",
          // present in many config files that explicitly disable counterexample checks
          "counterexample.checker",
          "counterexample.checker.config");

  private static final Path CONFIG_DIR = Paths.get("config");

  @Parameters(name = "{0}")
  public static Object[] getConfigFiles() throws IOException {
    try (Stream<Path> configFiles = Files.walk(CONFIG_DIR)) {
      return configFiles
          .filter(path -> path.getFileName().toString().endsWith(".properties"))
          .sorted()
          .toArray();
    }
  }

  @Parameter(0)
  public @Nullable Path configFile;

  @Test
  @SuppressWarnings("CheckReturnValue")
  public void parse() {
    try {
      Configuration.builder().loadFromFile(configFile).build();
    } catch (InvalidConfigurationException | IOException e) {
      assert_()
          .fail("Error during parsing of configuration file %s : %s", configFile, e.getMessage());
    }
  }

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @BeforeClass
  public static void createDummyInputFiles() throws IOException {
    // Create files that some analyses expect as input files.
    try (Reader r =
            Files.newBufferedReader(Paths.get("test/config/automata/AssumptionAutomaton.spc"));
        Writer w =
            MoreFiles.openOutputFile(
                Paths.get("output/AssumptionAutomaton.txt"), StandardCharsets.UTF_8)) {
      CharStreams.copy(r, w);
    }
  }

  @Test
  public void instantiate_and_run() throws IOException {
    // exclude files not meant to be instantiated
    assume().that((Iterable<Path>) configFile).doesNotContain(Paths.get("includes"));

    final Configuration config = createConfigurationForTestInstantiation();
    final boolean isJava = "Java".equalsIgnoreCase(config.getProperty("language"));

    final TestLogHandler logHandler = new TestLogHandler();
    logHandler.setLevel(Level.INFO);
    final LogManager logger = BasicLogManager.createWithHandler(logHandler);

    final CPAchecker cpachecker;
    try {
      cpachecker = new CPAchecker(config, logger, ShutdownManager.create());
    } catch (InvalidConfigurationException e) {
      assert_()
          .fail("Invalid configuration in configuration file %s : %s", configFile, e.getMessage());
      return;
    }

    try {
      cpachecker.run(createEmptyProgram(isJava));
    } catch (IllegalArgumentException e) {
      if (isJava) {
        assume().fail("Java frontend has a bug and cannot be run twice");
      }
      throw e;
    } catch (UnsatisfiedLinkError e) {
      assume().fail(e.getMessage());
      return;
    }

    Stream<String> severeMessages =
        logHandler
            .getStoredLogRecords()
            .stream()
            .filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .map(LogRecord::getMessage)
            .filter(s -> !ALLOWED_WARNINGS.matcher(s).matches());

    if (severeMessages.count() > 0) {
      assert_()
          .fail(
              "Not true that log for config %s does not contain messages with level SEVERE:\n%s",
              configFile,
              logHandler
                  .getStoredLogRecords()
                  .stream()
                  .map(ConsoleLogFormatter.withoutColors()::format)
                  .collect(Collectors.joining())
                  .trim());
    }

    if (!config.hasProperty("analysis.restartAfterUnknown")
        || !config.hasProperty("analysis.useParallelAnalyses")) {
      // TODO find a solution how to check for unused properties correctly even with RestartAlgorithm
      Set<String> unusedOptions = new TreeSet<>(config.getUnusedProperties());
      unusedOptions.removeAll(UNUSED_OPTIONS);
      assertThat(unusedOptions).named("unused options specified in " + configFile).isEmpty();
    }
  }

  private Configuration createConfigurationForTestInstantiation() {
    try {
      FileTypeConverter fileTypeConverter =
          FileTypeConverter.create(
              Configuration.builder()
                  .setOption("rootDirectory", tempFolder.getRoot().toString())
                  .build());
      Configuration.getDefaultConverters().put(FileOption.class, fileTypeConverter);

      return Configuration.builder()
          .loadFromFile(configFile)
          .addConverter(FileOption.class, fileTypeConverter)
          .setOption("java.sourcepath", tempFolder.getRoot().toString())
          .build();
    } catch (InvalidConfigurationException | IOException e) {
      assume().fail(e.getMessage());
      throw new AssertionError();
    }
  }

  private String createEmptyProgram(boolean isJava) throws IOException {
    String program;
    if (isJava) {
      MoreFiles.writeFile(
          tempFolder.newFile("Main.java").toPath(),
          StandardCharsets.US_ASCII,
          "public class Main { public static void main(String... args) {} }");
      program = "Main";
    } else {
      File cFile = tempFolder.newFile("program.i");
      MoreFiles.writeFile(cFile.toPath(), StandardCharsets.US_ASCII, "void main() {}");
      program = cFile.toString();
    }
    return program;
  }
}
