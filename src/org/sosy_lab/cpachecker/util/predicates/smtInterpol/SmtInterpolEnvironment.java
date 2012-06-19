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
package org.sosy_lab.cpachecker.util.predicates.smtInterpol;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.sosy_lab.common.Triple;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;

import de.uni_freiburg.informatik.ultimate.logic.Annotation;
import de.uni_freiburg.informatik.ultimate.logic.Assignments;
import de.uni_freiburg.informatik.ultimate.logic.LoggingScript;
import de.uni_freiburg.informatik.ultimate.logic.Logics;
import de.uni_freiburg.informatik.ultimate.logic.Model;
import de.uni_freiburg.informatik.ultimate.logic.QuotedString;
import de.uni_freiburg.informatik.ultimate.logic.SMTLIBException;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Valuation;
import de.uni_freiburg.informatik.ultimate.smtinterpol.smtlib2.SMTInterpol;

/** This is a Wrapper around SmtInterpol.
 * It guarantees the stack-behavior of function-declarations towards the SmtSolver,
 * so functions remain declared, if levels are popped.
 * This Wrapper allows to set a logfile for all Smt-Queries (default "smtinterpol.smt2").
 */
@Options(prefix="cpa.predicate.smtinterpol")
public class SmtInterpolEnvironment implements Script {

  /**
   * Enum listing possible types for SmtInterpol.
   */
  public enum Type {
    BOOL("Bool"),
    INT("Int"),
    REAL("Real");
    // TODO more types?
    // TODO merge enum with ModelTypes?

    private final String name;

    private Type(String s) {
      name = s;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  @Option(name="logfile", description="export solverqueries in smtlib-format")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private File smtLogfile = new File("smtinterpol.smt2");

  /** this is a counter to get distinct logfiles for distinct environments. */
  private static int logfileCounter = 0;

  /** the wrapped Script */
  private Script script;
  private Logics logic = null;

  /** This Set stores declared functions.
   * It is used to guarantee, that functions are only declared once. */
  private Set<String> declaredFunctions = new HashSet<String>();

  /** The stack contains a List of Declarations for each levels on the assertion-stack.
   * It is used to declare functions again, if stacklevels are popped. */
  private List<Collection<Triple<String, Sort[], Sort>>> stack =
      new ArrayList<Collection<Triple<String, Sort[], Sort>>>();

  /** This Collection is the toplevel of the stack. */
  private Collection<Triple<String, Sort[], Sort>> currentDeclarations;

  /** The Constructor creates the wrapped Element, sets some options
   * and initializes the logger. */
  public SmtInterpolEnvironment(Configuration config) throws InvalidConfigurationException {
    config.inject(this);

    Logger logger = Logger.getRootLogger(); // TODO use SosyLab-Logger
    // levels: ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF:
    logger.setLevel(Level.OFF);

    SMTInterpol smtInterpol = new SMTInterpol(logger);
    if (smtLogfile == null) { // option -noout
      script = smtInterpol;

    } else {
      String filename = getFilename(smtLogfile.getAbsolutePath());
      try {
        // create a thin wrapper around Benchmark,
        // this allows to write most formulas of the solver to outputfile
        // TODO how much faster is SmtInterpol without this Wrapper?
        script = new LoggingScript(smtInterpol, filename, true);
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }
    }

    try {
      script.setOption(":produce-proofs", true);
      script.setOption(":produce-models", true);
      script.setOption(":verbosity", new BigInteger("2"));
    } catch (SMTLIBException e) {
      e.printStackTrace();
    }
  }

  /**  This function creates a filename with following scheme:
       first filename is unchanged, then a number is appended */
  private String getFilename(final String oldFilename) {
    String filename = oldFilename;
    if (logfileCounter != 0) {
      if (oldFilename.endsWith(".smt2")) {
        filename = oldFilename.substring(0, oldFilename.length() - 4)
            + "__" + logfileCounter + ".smt2";
      } else {
        filename += "__" + logfileCounter;
      }
    }
    logfileCounter++;
    return filename;
  }

  @Override
  public void setLogic(String logic) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setLogic(Logics logic) {
    assert !isLogicSet() : "Logic was set before, you cannot do this again.";
    try {
      script.setLogic(logic);
      this.logic = logic;
    } catch (SMTLIBException e) {
      e.printStackTrace();
    }
  }

  public boolean isLogicSet() {
    return logic != null;
  }

  @Override
  public void setOption(String opt, Object value) {
    try {
      script.setOption(opt, value);
    } catch (SMTLIBException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void setInfo(String info, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void declareSort(String sort, int arity) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void defineSort(String sort, Sort[] sortParams, Sort definition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void declareFun(String fun, Sort[] paramSorts, Sort resultSort) {
    declareFun(fun, paramSorts, resultSort, true);
  }

  /** This function declares a function.
   * It is possible to check, if the function was declared before.
   * If both ('check' and 'declared before') are true, nothing is done. */
  private void declareFun(String fun, Sort[] paramSorts, Sort resultSort, boolean check) {
    String funRepr = functionToString(fun, paramSorts, resultSort);
    if (check != declaredFunctions.contains(funRepr)) {
      try {
        script.declareFun(fun, paramSorts, resultSort);
        declaredFunctions.add(funRepr);
      } catch (SMTLIBException e) {
        e.printStackTrace();
      }
      if (stack.size() != 0) {
        currentDeclarations.add(Triple.of(fun, paramSorts, resultSort));
      }
    }
  }

  /** This function returns a String-representation of a function-declaration.
   * It can be used to get a hashable representation of a function.
   * example:   "bool f (int int )"   */
  private String functionToString(String fun, Sort[] paramSorts, Sort resultSort) {
    StringBuilder str = new StringBuilder(resultSort.getName())
        .append(" ").append(fun).append("(");
    for (Sort paramSort : paramSorts) {
      str.append(paramSort.getName()).append(" ");
    }
    str.append(")");
    return str.toString();
  }

  @Override
  public void defineFun(String fun, TermVariable[] params, Sort resultSort, Term definition) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void push(int levels) {
    try {
      script.push(levels);
    } catch (SMTLIBException e) {
      e.printStackTrace();
    }

    for (int i = 0; i < levels; i++) {
      currentDeclarations = new ArrayList<Triple<String, Sort[], Sort>>();
      stack.add(currentDeclarations);
    }
  }

  /** This function pops levels from the assertion-stack.
   * It also declares popped functions on the lower level. */
  @Override
  public void pop(int levels) {
    assert stack.size() >= levels : "not enough levels to remove";
    try {
     // for (int i=0;i<levels;i++) script.pop(1); // for old version of SmtInterpol
      script.pop(levels);
    } catch (SMTLIBException e) {
      e.printStackTrace();
    }

    if (stack.size() - levels > 0) {
      currentDeclarations = stack.get(stack.size() - levels - 1);
    } else {
      currentDeclarations = null;
    }

    for (int i = 0; i < levels; i++) {
      final Collection<Triple<String, Sort[], Sort>> topDecl = stack.remove(stack.size() - 1);

      for (Triple<String, Sort[], Sort> function : topDecl) {
        final String fun = function.getFirst();
        final Sort[] paramSorts = function.getSecond();
        final Sort resultSort = function.getThird();
        declareFun(fun, paramSorts, resultSort, false);
      }
    }
  }

  @Override
  public LBool assertTerm(Term term) {
    assert stack.size() > 0 : "assertions should be on higher levels";
    LBool result = null;
    try {
      result = script.assertTerm(term);
    } catch (SMTLIBException e) {
      e.printStackTrace();
    }
    return result;
  }

  @Override
  public LBool checkSat() {
    LBool result = LBool.UNKNOWN;
    try {
      result = script.checkSat();
    } catch (SMTLIBException e) {
      e.printStackTrace();
    }
    return result;
  }

  @Override
  public Term[] getAssertions() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getProof() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Term[] getUnsatCore() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Model getModel() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Valuation getValue(Term[] terms) {
    try {
      return script.getValue(terms);
    } catch (SMTLIBException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public Assignments getAssignment() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getOption(String opt) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getInfo(String info) {
    return script.getInfo(info);
  }

  @Override
  public void exit() {
    throw new UnsupportedOperationException();
  }

  /** This function returns the Sort for a Type. */
  public Sort sort(Type type) {
    return sort(type.toString());
  }

  @Override
  public Sort sort(String sortname, Sort... params) {
    try {
      return script.sort(sortname, params);
    } catch (SMTLIBException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public Sort sort(String pSortname, BigInteger[] pIndices, Sort... pParams) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Term term(String funcname, Term... params) {
    try {
      return script.term(funcname, params);
    } catch (SMTLIBException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public Term term(String funcname, BigInteger[] indices, Sort returnSort, Term... params) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TermVariable variable(String varname, Sort sort) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Term quantifier(int quantor, TermVariable[] vars, Term body, Term[]... patterns) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Term let(TermVariable[] pVars, Term[] pValues, Term pBody) {
    try {
      return script.let(pVars, pValues, pBody);
    } catch (SMTLIBException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public Term annotate(Term t, Annotation... annotations) {
    try {
      return script.annotate(t, annotations);
    } catch (SMTLIBException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public Term numeral(String num) {
    try {
      return script.numeral(num);
    } catch (SMTLIBException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public Term numeral(BigInteger num) throws SMTLIBException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Term decimal(String num) {
    try {
      return script.decimal(num);
    } catch (SMTLIBException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public Term decimal(BigDecimal decimal) throws SMTLIBException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Term string(String s) throws SMTLIBException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Term hexadecimal(String pHex) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Term binary(String pBin) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Term simplifyTerm(Term term) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reset() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Term[] getInterpolants(Term[] partition) {
    assert stack.size() > 0 : "interpolants should be on higher levels";
    try {
      return script.getInterpolants(partition);
    } catch (SMTLIBException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public Sort[] sortVariables(String... pNames) {
    throw new UnsupportedOperationException();
  }

  /** This function returns the version of SmtInterpol, for logging. */
  public String getVersion() {
    QuotedString program = (QuotedString)script.getInfo(":name");
    QuotedString version = (QuotedString)script.getInfo(":version");
    return program.getValue() + " " + version.getValue();
  }
}
