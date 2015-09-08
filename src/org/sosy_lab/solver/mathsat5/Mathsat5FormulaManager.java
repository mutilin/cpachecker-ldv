/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.solver.mathsat5;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.*;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.Appender;
import org.sosy_lab.common.Appenders;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.PathCounterTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.util.NativeLibraries;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.InterpolatingProverEnvironment;
import org.sosy_lab.solver.api.OptEnvironment;
import org.sosy_lab.solver.api.ProverEnvironment;
import org.sosy_lab.solver.basicimpl.AbstractFormulaManager;
import org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.TerminationTest;

import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.collect.ImmutableMap;

public class Mathsat5FormulaManager extends AbstractFormulaManager<Long, Long, Long> implements AutoCloseable {

  @Options(deprecatedPrefix="cpa.predicate.solver.mathsat5",
           prefix="solver.mathsat5")
  private static class Mathsat5Settings {

    @Option(secure=true, description = "List of further options which will be passed to Mathsat in addition to the default options. "
        + "Format is 'key1=value1,key2=value2'")
    private String furtherOptions = "";

    private final @Nullable PathCounterTemplate logfile;

    private final ImmutableMap<String, String> furtherOptionsMap ;

    private Mathsat5Settings(Configuration config, PathCounterTemplate pLogfile) throws InvalidConfigurationException {
      config.inject(this);
      logfile = pLogfile;

      MapSplitter optionSplitter = Splitter.on(',').trimResults().omitEmptyStrings()
                      .withKeyValueSeparator(Splitter.on('=').limit(2).trimResults());

      try {
        furtherOptionsMap = ImmutableMap.copyOf(optionSplitter.split(furtherOptions));
      } catch (IllegalArgumentException e) {
        throw new InvalidConfigurationException("Invalid Mathsat option in \"" + furtherOptions + "\": " + e.getMessage(), e);
      }
    }
  }

  private final LogManager logger;
  private final long mathsatConfig;
  private final Mathsat5Settings settings;
  private final long randomSeed;

  private final ShutdownNotifier shutdownNotifier;
  private final TerminationTest terminationTest;

  @Options(deprecatedPrefix="cpa.predicate.solver.mathsat5",
    prefix="solver.mathsat5")
  private static class ExtraOptions {
    @Option(secure=true, description="Load less stable optimizing version of"
        + " mathsat5 solver.")
    boolean loadOptimathsat5 = false;
  }

  private Mathsat5FormulaManager(
      LogManager pLogger,
      long pMathsatConfig,
      Mathsat5FormulaCreator creator,
      Mathsat5UnsafeFormulaManager unsafeManager,
      Mathsat5FunctionFormulaManager pFunctionManager,
      Mathsat5BooleanFormulaManager pBooleanManager,
      Mathsat5IntegerFormulaManager pIntegerManager,
      Mathsat5RationalFormulaManager pRationalManager,
      Mathsat5BitvectorFormulaManager pBitpreciseManager,
      Mathsat5FloatingPointFormulaManager pFloatingPointmanager,
      Mathsat5ArrayFormulaManager pArrayManager,
      Mathsat5Settings pSettings,
      long pRandomSeed,
      final ShutdownNotifier pShutdownNotifier) {
    super(creator, unsafeManager, pFunctionManager, pBooleanManager,
        pIntegerManager, pRationalManager, pBitpreciseManager, pFloatingPointmanager, null, pArrayManager);

    mathsatConfig = pMathsatConfig;
    settings = pSettings;
    randomSeed = pRandomSeed;
    logger = checkNotNull(pLogger);

    shutdownNotifier = checkNotNull(pShutdownNotifier);
    terminationTest = new TerminationTest() {
        @Override
        public boolean shouldTerminate() throws InterruptedException {
          pShutdownNotifier.shutdownIfNecessary();
          return false;
        }
      };
  }

  ShutdownNotifier getShutdownNotifier() {
    return shutdownNotifier;
  }

  static long getMsatTerm(Formula pT) {
    return ((Mathsat5Formula)pT).getTerm();
  }

  public static Mathsat5FormulaManager create(LogManager logger,
      Configuration config, ShutdownNotifier pShutdownNotifier,
      @Nullable PathCounterTemplate solverLogFile, long randomSeed) throws InvalidConfigurationException {

    ExtraOptions extraOptions = new ExtraOptions();
    config.inject(extraOptions);
    if (extraOptions.loadOptimathsat5) {
      NativeLibraries.loadLibrary("optimathsat5j");
    } else {
      NativeLibraries.loadLibrary("mathsat5j");
    }

    // Init Msat
    Mathsat5Settings settings = new Mathsat5Settings(config, solverLogFile);

    long msatConf = msat_create_config();
    msat_set_option_checked(msatConf, "theory.la.split_rat_eq", "false");
    msat_set_option_checked(msatConf, "random_seed", Long.toString(randomSeed));

    for (Map.Entry<String, String> option : settings.furtherOptionsMap.entrySet()) {
      try {
        msat_set_option_checked(msatConf, option.getKey(), option.getValue());
      } catch (IllegalArgumentException e) {
        throw new InvalidConfigurationException(e.getMessage(), e);
      }
    }

    final long msatEnv = msat_create_env(msatConf);

    // Create Mathsat5FormulaCreator
    Mathsat5FormulaCreator creator = new Mathsat5FormulaCreator(msatEnv);

    // Create managers
    Mathsat5UnsafeFormulaManager unsafeManager = new Mathsat5UnsafeFormulaManager(creator);
    Mathsat5FunctionFormulaManager functionTheory = new Mathsat5FunctionFormulaManager(creator, unsafeManager);
    Mathsat5BooleanFormulaManager booleanTheory = Mathsat5BooleanFormulaManager.create(creator);
    Mathsat5IntegerFormulaManager integerTheory = new Mathsat5IntegerFormulaManager(creator, functionTheory);
    Mathsat5RationalFormulaManager rationalTheory = new Mathsat5RationalFormulaManager(creator, functionTheory);
    Mathsat5BitvectorFormulaManager bitvectorTheory  = Mathsat5BitvectorFormulaManager.create(creator);
    Mathsat5FloatingPointFormulaManager floatingPointTheory = new Mathsat5FloatingPointFormulaManager(creator, functionTheory);
    Mathsat5ArrayFormulaManager arrayTheory = new Mathsat5ArrayFormulaManager(creator);

    return new Mathsat5FormulaManager(logger, msatConf, creator,
        unsafeManager, functionTheory, booleanTheory,
        integerTheory, rationalTheory, bitvectorTheory, floatingPointTheory, arrayTheory,
        settings, randomSeed, pShutdownNotifier);
  }

  BooleanFormula encapsulateBooleanFormula(long t) {
    return getFormulaCreator().encapsulateBoolean(t);
  }

  @Override
  public ProverEnvironment newProverEnvironment(boolean pGenerateModels, boolean pGenerateUnsatCore) {
    return new Mathsat5TheoremProver(this, pGenerateModels, pGenerateUnsatCore);
  }

  @Override
  public InterpolatingProverEnvironment<?> newProverEnvironmentWithInterpolation(boolean pShared) {
    return new Mathsat5InterpolatingProver(this, pShared);
  }

  @Override
  public OptEnvironment newOptEnvironment() {
    return new Mathsat5OptProver(this);
  }

  @Override
  public BooleanFormula parse(String pS) throws IllegalArgumentException {
    long f = msat_from_smtlib2(getEnvironment(), pS);
    return encapsulateBooleanFormula(f);
  }

  @Override
  public Appender dumpFormula(final Long f) {
    assert getFormulaCreator().getFormulaType(f) == FormulaType.BooleanType : "Only BooleanFormulas may be dumped";

    // Lazy invocation of msat_to_smtlib2 wrapped in an Appender.
    return Appenders.fromToStringMethod(
        new Object() {
          @Override
          public String toString() {
            return msat_to_smtlib2(getEnvironment(), f);
          }
        });
  }


  @Override
  public String getVersion() {
    return msat_get_version();
  }

  long createEnvironment(long cfg, boolean shared, boolean ghostFilter) {
    long env;

    if (ghostFilter) {
      msat_set_option_checked(cfg, "dpll.ghost_filtering", "true");
    }

    msat_set_option_checked(cfg, "theory.la.split_rat_eq", "false");
    msat_set_option_checked(cfg, "random_seed", Long.toString(randomSeed));

    for (Map.Entry<String, String> option : settings.furtherOptionsMap.entrySet()) {
      msat_set_option_checked(cfg, option.getKey(), option.getValue());
    }

    if (settings.logfile != null) {
      Path filename = settings.logfile.getFreshPath();
      try {
        Files.createParentDirs(filename);
      } catch (IOException e) {
        logger.logException(Level.WARNING, e, "Cannot create directory for MathSAT logfile");
      }

      msat_set_option_checked(cfg, "debug.api_call_trace", "1");
      msat_set_option_checked(cfg, "debug.api_call_trace_filename", filename.toAbsolutePath().toString());
    }

    if (shared) {
      env = msat_create_shared_env(cfg, this.getEnvironment());
    } else {
      env = msat_create_env(cfg);
    }

    return env;
  }

  long addTerminationTest(long env) {
    return msat_set_termination_test(env, terminationTest);
  }

  @Override
  public void close() {
    logger.log(Level.FINER, "Freeing Mathsat environment");
    msat_destroy_env(getEnvironment());
    msat_destroy_config(mathsatConfig);
  }
}
