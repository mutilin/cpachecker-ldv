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
package org.sosy_lab.cpachecker.util.predicates.mathsat5;

import static org.sosy_lab.cpachecker.util.predicates.mathsat5.Mathsat5NativeApi.*;

import java.io.File;
import java.util.Map;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractBitvectorFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractBooleanFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFunctionFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractRationalFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractUnsafeFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.FormulaCreator;

import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.collect.ImmutableMap;

@Options(prefix="cpa.predicate.mathsat5")
public class Mathsat5FormulaManager extends AbstractFormulaManager<Long> {

  @Options(prefix="cpa.predicate.mathsat5")
  private static class Mathsat5Settings {

    @Option(description = "List of further options which will be passed to Mathsat in addition to the default options. "
        + "Format is 'key1=value1,key2=value2'")
    private String furtherOptions = "";

    @Option(description = "Export solver queries in Smtlib format into a file (for Mathsat5).")
    private boolean logAllQueries = false;

    @Option(description = "Export solver queries in Smtlib format into a file (for Mathsat5).")
    @FileOption(Type.OUTPUT_FILE)
    private File logfile = new File("mathsat5.%d.smt2");

    private final ImmutableMap<String,String> furtherOptionsMap ;

    private Mathsat5Settings(Configuration config) throws InvalidConfigurationException{
      config.inject(this);

      MapSplitter optionSplitter = Splitter.on(',').trimResults().omitEmptyStrings()
                      .withKeyValueSeparator(Splitter.on('=').limit(2).trimResults());

      try {
        furtherOptionsMap = ImmutableMap.copyOf(optionSplitter.split(furtherOptions));
      } catch (IllegalArgumentException e) {
        throw new InvalidConfigurationException("Invalid Mathsat option in \"" + furtherOptions + "\": " + e.getMessage(), e);
      }
    }
  }

  private final Mathsat5FormulaCreator formulaCreator;
  private final long mathsatEnv;
  private final Mathsat5Settings settings;
  private int logfileCounter = 0;

  private Mathsat5FormulaManager(
      AbstractUnsafeFormulaManager<Long> unsafeManager,
      AbstractFunctionFormulaManager<Long> pFunctionManager,
      AbstractBooleanFormulaManager<Long> pBooleanManager,
      AbstractRationalFormulaManager<Long> pNumericManager,
      AbstractBitvectorFormulaManager<Long> pBitpreciseManager, Mathsat5Settings pSettings) {

    super(unsafeManager, pFunctionManager, pBooleanManager, pNumericManager, pBitpreciseManager);
    FormulaCreator<Long> creator = getFormulaCreator();
    if (!(creator instanceof Mathsat5FormulaCreator)) {
      throw new IllegalArgumentException("the formel-creator has to be a Mathsat5FormulaCreator instance!");
    }
    formulaCreator = (Mathsat5FormulaCreator) getFormulaCreator();
    mathsatEnv = formulaCreator.getEnv();
    settings = pSettings;
  }


  static long getMsatTerm(Formula pT) {
    return ((Mathsat5Formula)pT).getTerm();
  }

  public static Mathsat5FormulaManager create(LogManager logger, Configuration config) throws InvalidConfigurationException{
    // Init Msat
    Mathsat5Settings settings = new Mathsat5Settings(config);

    long msatConf = msat_create_config();
    msat_set_option_checked(msatConf, "theory.la.split_rat_eq", "false");

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
    Mathsat5RationalFormulaManager rationalTheory = Mathsat5RationalFormulaManager.create(creator, functionTheory);
    Mathsat5BitvectorFormulaManager bitvectorTheory  = Mathsat5BitvectorFormulaManager.create(creator);

    return new Mathsat5FormulaManager(
        unsafeManager, functionTheory, booleanTheory,
        rationalTheory, bitvectorTheory, settings);
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> T encapsulateTerm(Class<T> pClazz, long t) {
    return formulaCreator.encapsulate(pClazz, t);
  }

  @Override
  public <T extends Formula> T parse(Class<T> pClazz, String pS) throws IllegalArgumentException {
    long f = msat_from_smtlib2(mathsatEnv, pS);
    return encapsulateTerm(pClazz, f);
  }

  @Override
  public String dumpFormula(Long f) {
    return msat_to_smtlib2(mathsatEnv, f);
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

    for (Map.Entry<String, String> option : settings.furtherOptionsMap.entrySet()) {
      msat_set_option_checked(cfg, option.getKey(), option.getValue());
    }

    if (settings.logAllQueries && settings.logfile != null) {
      String filename = String.format(settings.logfile.getAbsolutePath(), logfileCounter++);

      msat_set_option_checked(cfg, "debug.api_call_trace", "1");
      msat_set_option_checked(cfg, "debug.api_call_trace_filename", filename);
    }

    if (shared) {
      env = msat_create_shared_env(cfg, this.mathsatEnv);
    } else {
      env = msat_create_env(cfg);
    }

    return env;
  }

  long getMsatEnv(){
    return mathsatEnv;
  }

}
