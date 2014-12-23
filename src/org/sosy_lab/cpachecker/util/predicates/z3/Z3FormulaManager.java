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
package org.sosy_lab.cpachecker.util.predicates.z3;

import static org.sosy_lab.cpachecker.util.predicates.z3.Z3NativeApi.*;

import java.io.IOException;

import javax.annotation.Nullable;

import org.sosy_lab.common.Appender;
import org.sosy_lab.common.Appenders;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.PathCounterTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.util.NativeLibraries;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.InterpolatingProverEnvironment;
import org.sosy_lab.cpachecker.util.predicates.interfaces.OptEnvironment;
import org.sosy_lab.cpachecker.util.predicates.interfaces.ProverEnvironment;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.matching.SmtAstMatcher;
import org.sosy_lab.cpachecker.util.predicates.z3.Z3NativeApi.PointerToInt;

@Options(prefix = "cpa.predicate.solver.z3")
public class Z3FormulaManager extends AbstractFormulaManager<Long, Long, Long> {

  @Option(secure=true, description = "simplify formulas when they are asserted in a solver.")
  boolean simplifyFormulas = false;

  /** Optimization settings */
  @Option(secure=true, description = "Engine to use for the optimization",
    values = {"basic", "farkas", "symba"})
  String optimizationEngine = "basic";

  @Option(secure=true, description = "Ordering for objectives in the optimization" +
      " context", values = {"lex", "pareto", "box"})
  String objectivePrioritizationMode = "box";

  private final Z3SmtLogger z3smtLogger;
  private Z3AstMatcher z3astMatcher;

  private static final String OPT_ENGINE_CONFIG_KEY = "optsmt_engine";
  private static final String OPT_PRIORITY_CONFIG_KEY = "priority";

  private Z3FormulaManager(
      Z3FormulaCreator pFormulaCreator,
      Z3UnsafeFormulaManager pUnsafeManager,
      Z3FunctionFormulaManager pFunctionManager,
      Z3BooleanFormulaManager pBooleanManager,
      Z3IntegerFormulaManager pIntegerManager,
      Z3RationalFormulaManager pRationalManager,
      Z3BitvectorFormulaManager pBitpreciseManager,
      Z3QuantifiedFormulaManager pQuantifiedManager,
      Z3ArrayFormulaManager pArrayManager,
      Z3SmtLogger smtLogger, Configuration config) throws InvalidConfigurationException {

    super(pFormulaCreator, pUnsafeManager, pFunctionManager, pBooleanManager,
        pIntegerManager, pRationalManager, pBitpreciseManager, null, pQuantifiedManager, pArrayManager);

    config.inject(this);
    this.z3smtLogger = smtLogger;
    this.z3astMatcher = new Z3AstMatcher(this);
  }

  public static synchronized Z3FormulaManager create(LogManager logger,
      Configuration config, @Nullable PathCounterTemplate solverLogfile)
      throws InvalidConfigurationException {

    NativeLibraries.loadLibrary("z3j");

    long cfg = mk_config();
    set_param_value(cfg, "MODEL", "true"); // this option is needed also without interpolation
    set_param_value(cfg, "PROOF", "true");

    // TODO add some other params, memory-limit?
    final long context = mk_context_rc(cfg);
    del_config(cfg);

    long boolSort = mk_bool_sort(context);
    inc_ref(context, sort_to_ast(context, boolSort));

    long integerSort = mk_int_sort(context);
    inc_ref(context, sort_to_ast(context, integerSort));
    long realSort = mk_real_sort(context);
    inc_ref(context, sort_to_ast(context, realSort));

    // The string representations of Z3s formulas should be in SMTLib2!
    set_ast_print_mode(context, Z3NativeApiConstants.Z3_PRINT_SMTLIB2_COMPLIANT);

    // create logger for variables and set initial options in this logger,
    // note: logger for the solvers are created later,
    // they will not contain variable-declaration!
    Z3SmtLogger smtLogger = new Z3SmtLogger(context, config, solverLogfile);

    // this options should match the option set above!
    smtLogger.logOption("model", "true");
    smtLogger.logOption("proof", "true");

    Z3FormulaCreator creator = new Z3FormulaCreator(context, boolSort, integerSort, realSort, smtLogger);

    // Create managers
    Z3UnsafeFormulaManager unsafeManager = new Z3UnsafeFormulaManager(creator);
    Z3FunctionFormulaManager functionTheory = new Z3FunctionFormulaManager(creator, unsafeManager, smtLogger);
    Z3BooleanFormulaManager booleanTheory = new Z3BooleanFormulaManager(creator);
    Z3IntegerFormulaManager integerTheory = new Z3IntegerFormulaManager(creator, functionTheory);
    Z3RationalFormulaManager rationalTheory = new Z3RationalFormulaManager(creator, functionTheory);
    Z3BitvectorFormulaManager bitvectorTheory = new Z3BitvectorFormulaManager(creator);
    Z3QuantifiedFormulaManager quantifierManager = new Z3QuantifiedFormulaManager(creator);
    Z3ArrayFormulaManager arrayManager = new Z3ArrayFormulaManager(creator);

    // Set the custom error handling
    // which will throw java Exception
    // instead of exit(1).
    setInternalErrorHandler(context);
    return new Z3FormulaManager(
        creator,
        unsafeManager, functionTheory, booleanTheory,
        integerTheory, rationalTheory, bitvectorTheory, quantifierManager, arrayManager,
        smtLogger, config);
  }

  @Override
  public ProverEnvironment newProverEnvironment(boolean pGenerateModels, boolean pGenerateUnsatCore) {
    return new Z3TheoremProver(this, pGenerateUnsatCore);
  }

  @Override
  public InterpolatingProverEnvironment<?> newProverEnvironmentWithInterpolation(boolean pShared) {
    return new Z3InterpolatingProver(this);
  }

  @Override
  public SmtAstMatcher getSmtAstMatcher() {
    return z3astMatcher;
  }

  @Override
  public OptEnvironment newOptEnvironment() {
    Z3OptProver out = new Z3OptProver(this);
    out.setParam(OPT_ENGINE_CONFIG_KEY, this.optimizationEngine);
    out.setParam(OPT_PRIORITY_CONFIG_KEY, this.objectivePrioritizationMode);
    return out;
  }

  @Override
  public BooleanFormula parse(String str) throws IllegalArgumentException {

    // TODO do we need sorts or decls?
    // the context should know them already,
    // TODO check this
    long[] sort_symbols = new long[0];
    long[] sorts = new long[0];
    long[] decl_symbols = new long[0];
    long[] decls = new long[0];

    long e = parse_smtlib2_string(getEnvironment(), str, sort_symbols, sorts, decl_symbols, decls);

    return encapsulateBooleanFormula(e);
  }

  static long getZ3Expr(Formula pT) {
    if (pT instanceof Z3Formula) {
      return ((Z3Formula)pT).getFormulaInfo();
    }
    throw new IllegalArgumentException("Cannot get the formula info of type " + pT.getClass().getSimpleName() + " in the Solver!");
  }

  @Override
  public String getVersion() {
    PointerToInt major = new PointerToInt();
    PointerToInt minor = new PointerToInt();
    PointerToInt build = new PointerToInt();
    PointerToInt revision = new PointerToInt();
    get_version(major, minor, build, revision);
    return "Z3 " +
        major.value + "." + minor.value + "." +
        build.value + "." + revision.value;
  }

  @Override
  public Appender dumpFormula(final Long expr) {
    return new Appenders.AbstractAppender() {

      @Override
      public void appendTo(Appendable out) throws IOException {
        StringBuilder modified = new StringBuilder();
        String txt = Z3NativeApi.benchmark_to_smtlib_string(getEnvironment(), "dumped-formula", "", "unknown", "", 0, new long[]{}, expr);
        String[] lines = txt.split("\n");

        for (String line: lines) {
          if (!(line.startsWith("(set-info")
              || line.startsWith(";")
              || line.startsWith("(check"))) {
            modified.append(line);
            modified.append(" ");
          }
        }

        out.append(modified.toString()
          .replace("(assert", "\n(assert")
          .replace("(dec", "\n(dec")
          .trim());
      }
    };
  }

  protected BooleanFormula encapsulateBooleanFormula(long t) {
    return getFormulaCreator().encapsulateBoolean(t);
  }

  /** returns a new logger with a new logfile. */
  Z3SmtLogger getSmtLogger() {
    return z3smtLogger.cloneWithNewLogfile();
  }

}