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
package org.sosy_lab.solver.z3;

import static org.sosy_lab.solver.z3.Z3NativeApi.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nullable;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.PathCounterTemplate;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.FileWriteMode;

class Z3SmtLogger {

  @Options(deprecatedPrefix="cpa.predicate.solver.z3.logger",
           prefix="solver.z3.logger")
  private static class Z3Settings {

    private final @Nullable PathCounterTemplate basicLogfile;

    @Option(secure=true, description = "Export solver queries in Smtlib2 format, " +
            "there are small differences for different solvers, " +
            "choose target-solver.",
            values = { Z3, MATHSAT5 }, toUppercase = true)
    private String target = Z3;

    private Z3Settings(Configuration config, PathCounterTemplate logfile) throws InvalidConfigurationException {
      config.inject(this);
      basicLogfile = logfile;
    }
  }

  private static final String MATHSAT5 = "MATHSAT5";
  private static final String Z3 = "Z3";
  // TODO support for smtinterpol?

  private final Path logfile;
  private final Z3Settings settings;
  private final long z3context;

  private final HashSet<Long> declarations = Sets.newHashSet();

  private int itpIndex = 0; // each interpolation gets its own index
  private final HashMap<Long, String> interpolationFormulas = Maps.newHashMap(); // for mathsat-compatibility

  Z3SmtLogger(long z3context, Configuration config, PathCounterTemplate logfile) throws InvalidConfigurationException {
    this(z3context, new Z3Settings(config, logfile));
  }

  private Z3SmtLogger(long pZ3context, Z3Settings pSettings) {
    z3context = pZ3context;
    settings = pSettings;

    if (settings.basicLogfile != null) {
      this.logfile = settings.basicLogfile.getFreshPath();
      log("", false); // create or clean the file
    } else {
      this.logfile = null;
    }
  }

  /** returns a new instance with a new logfile. */
  public Z3SmtLogger cloneWithNewLogfile() {
    return new Z3SmtLogger(z3context, settings);
  }

  public void logOption(String option, String value) {
    if (logfile == null) { return; }
    logBracket("set-option :" + option + " " + value);
  }

  public void logVarDeclaration(long name, long type) {
    if (logfile == null) { return; }
    if (declarations.add(name)) {
      logBracket("declare-fun " + ast_to_string(z3context, name) +
          " () " + sort_to_string(z3context, type));
    }
  }

  public void logFunctionDeclaration(long symbol, long[] inputTypes, long returnType) {
    if (logfile == null) { return; }
    if (declarations.add(symbol)) {
      StringBuilder s = new StringBuilder();
      s.append("declare-fun ").append(get_symbol_string(z3context, symbol)).append(" (");
      for (long it : inputTypes) {
        s.append(sort_to_string(z3context, it)).append(" ");
      }
      s.append(") ").append(sort_to_string(z3context, returnType));

      logBracket(s.toString());
    }
  }
  public void logPush(int n) {
    if (logfile == null) { return; }
    logBracket("push " + n);
  }

  public void logPop(int n) {
    if (logfile == null) { return; }
    logBracket("pop " + n);
  }

  public void logAssert(long expr) {
    //    if (smtlogfile == null) { return; }
    //    logBracket("assert " + ast_to_string(z3context, expr));
    logInterpolationAssert(expr); // for mathsat, to use "one" declarations-file
  }

  public void logInterpolationAssert(long expr) {
    if (logfile == null) { return; }
    itpIndex++;
    String formula = ast_to_string(z3context, expr);

    switch (settings.target) {
    case Z3:
      logBracket("assert " + formula);
      break;

    case MATHSAT5:
      String name = "itpId" + itpIndex;
      logBracket("assert (! " + formula + " :interpolation-group " + name + ")");
      interpolationFormulas.put(expr, name);
      break;
    default:
      throw new AssertionError();
    }
  }

  public void logCheck() {
    if (logfile == null) { return; }
    logBracket("check-sat");
  }

  public void logGetModel() {
    if (logfile == null) { return; }
    logBracket("get-model");
  }


  public void logInterpolation(
      List<Long> formulasOfA, List<Long> formulasOfB,
      long conjunctionA, long conjunctionB) {
    if (logfile == null) { return; }

    StringBuilder itpQuery = new StringBuilder();
    switch (settings.target) {
    case Z3:
      itpQuery.append("get-interpolant ")
              .append(ast_to_string(z3context, conjunctionA))
              .append(" ")
              .append(ast_to_string(z3context, conjunctionB));
      break;

    case MATHSAT5:
      itpQuery.append("get-interpolant (");

      for (long f : formulasOfA) {
        Preconditions.checkArgument(interpolationFormulas.containsKey(f));
        itpQuery.append(interpolationFormulas.get(f)).append(" ");
      }

      itpQuery.append(")");
      break;
    default:
      throw new AssertionError();
    }

    logCheck(); // TODO remove check?
    logBracket(itpQuery.toString());
  }

  public void logSeqInterpolation(long[] interpolationFormulas) {
    if (logfile == null) { return; }

    StringBuilder itpQuery = new StringBuilder();
    switch (settings.target) {
    case Z3:
      itpQuery.append("get-interpolants ");
      for (long f : interpolationFormulas) {
        itpQuery.append(ast_to_string(z3context, f));
      }
      break;

    case MATHSAT5:
      throw new AssertionError("not supported via SMT-input/output??");

    default:
      throw new AssertionError();
    }

    logCheck(); // TODO remove check?
    logBracket(itpQuery.toString());
  }

  public void logBracket(String s) {
    if (logfile == null) { return; }
    log("(" + s + ")\n", true);
  }

  private synchronized void log(String s, boolean append) {
    try {
      if (append) {
        logfile.asCharSink(Charset.defaultCharset(), FileWriteMode.APPEND).write(s);
      } else {
        logfile.asCharSink(Charset.defaultCharset()).write(s);
      }
    } catch (IOException e) {
      throw new AssertionError("IO-Error in smtlogfile", e);
    }
  }

}
