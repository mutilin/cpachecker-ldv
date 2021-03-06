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
package org.sosy_lab.cpachecker.util.predicates.smt;


import static com.google.common.base.Preconditions.*;
import static com.google.common.collect.FluentIterable.from;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.sosy_lab.common.Appender;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.smt.ReplaceBitvectorWithNumeralAndFunctionTheory.ReplaceBitvectorEncodingOptions;
import org.sosy_lab.solver.Model;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.api.ArrayFormula;
import org.sosy_lab.solver.api.BitvectorFormula;
import org.sosy_lab.solver.api.BitvectorFormulaManager;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.FloatingPointFormula;
import org.sosy_lab.solver.api.FloatingPointFormulaManager;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaManager;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.FuncDecl;
import org.sosy_lab.solver.api.FuncDeclKind;
import org.sosy_lab.solver.api.NumeralFormula;
import org.sosy_lab.solver.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.solver.api.NumeralFormula.RationalFormula;
import org.sosy_lab.solver.api.NumeralFormulaManager;
import org.sosy_lab.solver.api.QuantifiedFormulaManager.Quantifier;
import org.sosy_lab.solver.api.UnsafeFormulaManager;
import org.sosy_lab.solver.basicimpl.tactics.Tactic;
import org.sosy_lab.solver.visitors.BooleanFormulaVisitor;
import org.sosy_lab.solver.visitors.DefaultBooleanFormulaVisitor;
import org.sosy_lab.solver.visitors.DefaultFormulaVisitor;
import org.sosy_lab.solver.visitors.FormulaVisitor;
import org.sosy_lab.solver.visitors.RecursiveFormulaVisitor;
import org.sosy_lab.solver.visitors.TraversalProcess;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * This class is the central entry point for all formula creation
 * and manipulation operations for client code.
 * It delegates to the actual solver package
 * and provides additional utilities.
 * The preferred way of instantiating this class is via
 * {@link Solver#create(Configuration, LogManager, ShutdownNotifier)}.
 *
 *
 * This class and some of its related classes have supporting operations
 * for creating and manipulation formulas with SSA indices:
 * - {@link #makeVariable(FormulaType, String, int)} creates a variable with an SSA index
 * - {@link #instantiate(Formula, SSAMap)} adds SSA indices to variables in a formula
 * - {@link #uninstantiate(Formula)} removes all SSA indices from a formula
 *
 * The method {@link #parseName(String)} is also related to this, but should not be used!
 */
@Options(prefix="cpa.predicate")
public class FormulaManagerView {

  enum Theory {
    INTEGER,
    RATIONAL,
    BITVECTOR,
    FLOAT,
    ;
  }

  private final LogManager logger;

  private final FormulaManager manager;
  private final UnsafeFormulaManager unsafeManager;

  private final FormulaWrappingHandler wrappingHandler;
  private final BooleanFormulaManagerView booleanFormulaManager;
  private final BitvectorFormulaManagerView bitvectorFormulaManager;
  private final FloatingPointFormulaManagerView floatingPointFormulaManager;
  private IntegerFormulaManagerView integerFormulaManager;
  private RationalFormulaManagerView rationalFormulaManager;
  private final FunctionFormulaManagerView functionFormulaManager;
  private QuantifiedFormulaManagerView quantifiedFormulaManager;
  private ArrayFormulaManagerView arrayFormulaManager;

  @Option(secure=true, name = "formulaDumpFilePattern", description = "where to dump interpolation and abstraction problems (format string)")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private PathTemplate formulaDumpFile = PathTemplate.ofFormatString("%s%04d-%s%03d.smt2");

  @Option(secure=true, description="try to add some useful static-learning-like axioms for "
    + "bitwise operations (which are encoded as UFs): essentially, "
    + "we simply collect all the numbers used in bitwise operations, "
    + "and add axioms like (0 & n = 0)")
  private boolean useBitwiseAxioms = false;

  @Option(secure=true, description="Theory to use as backend for bitvectors."
      + " If different from BITVECTOR, the specified theory is used to approximate bitvectors."
      + " This can be used for solvers that do not support bitvectors, or for increased performance.")
  private Theory encodeBitvectorAs = Theory.INTEGER;

  @Option(secure=true, description="Theory to use as backend for floats."
      + " If different from FLOAT, the specified theory is used to approximate floats."
      + " This can be used for solvers that do not support floating-point arithmetic, or for increased performance.")
  private Theory encodeFloatAs = Theory.RATIONAL;

  @Option(secure=true, description="Enable fallback to UFs if a solver does not "
      + "support non-linear arithmetics. This option only effects MULT, MOD and DIV.")
  private boolean useUFsForNonLinearArithmetic = true;

  @VisibleForTesting
  public FormulaManagerView(FormulaManager pFormulaManager, Configuration config, LogManager pLogger) throws InvalidConfigurationException {
    config.inject(this, FormulaManagerView.class);
    logger = pLogger;
    manager = checkNotNull(pFormulaManager);
    unsafeManager = manager.getUnsafeFormulaManager();
    wrappingHandler = new FormulaWrappingHandler(manager, encodeBitvectorAs, encodeFloatAs);
    booleanFormulaManager = new BooleanFormulaManagerView(wrappingHandler, manager.getBooleanFormulaManager(), manager.getUnsafeFormulaManager());
    functionFormulaManager = new FunctionFormulaManagerView(wrappingHandler, manager.getFunctionFormulaManager());

    final BitvectorFormulaManager rawBitvectorFormulaManager = getRawBitvectorFormulaManager(config);
    final FloatingPointFormulaManager rawFloatingPointFormulaManager = getRawFloatingPointFormulaManager();

    bitvectorFormulaManager = new BitvectorFormulaManagerView(wrappingHandler, rawBitvectorFormulaManager, manager.getBooleanFormulaManager());
    floatingPointFormulaManager = new FloatingPointFormulaManagerView(wrappingHandler, rawFloatingPointFormulaManager);
    integerFormulaManager = new IntegerFormulaManagerView(wrappingHandler, getIntegerFormulaManager0());
  }

  /** Returns the BitvectorFormulaManager or a Replacement based on the Option 'encodeBitvectorAs'. */
  private BitvectorFormulaManager getRawBitvectorFormulaManager(Configuration config) throws InvalidConfigurationException, AssertionError {
    final BitvectorFormulaManager rawBitvectorFormulaManager;
    switch (encodeBitvectorAs) {
    case BITVECTOR:
      try {
        rawBitvectorFormulaManager = manager.getBitvectorFormulaManager();
      } catch (UnsupportedOperationException e) {
        throw new InvalidConfigurationException("The chosen SMT solver does not support the theory of bitvectors, "
            + "please choose another SMT solver "
            + "or use the option cpa.predicate.encodeBitvectorAs "
            + "to approximate bitvectors with another theory.",
            e);
      }
      break;
    case INTEGER:
      rawBitvectorFormulaManager = new ReplaceBitvectorWithNumeralAndFunctionTheory<>(wrappingHandler,
          manager.getBooleanFormulaManager(),
          getIntegerFormulaManager0(),
          manager.getFunctionFormulaManager(),
          new ReplaceBitvectorEncodingOptions(config));
      break;
    case RATIONAL:
      NumeralFormulaManager<NumeralFormula, RationalFormula> rmgr;
      try {
        rmgr = getRationalFormulaManager0();
      } catch (UnsupportedOperationException e) {
        throw new InvalidConfigurationException("The chosen SMT solver does not support the theory of rationals, "
            + "please choose another SMT solver "
            + "or use the option cpa.predicate.encodeBitvectorAs "
            + "to approximate bitvectors with another theory.",
            e);
      }
      rawBitvectorFormulaManager = new ReplaceBitvectorWithNumeralAndFunctionTheory<>(wrappingHandler,
          manager.getBooleanFormulaManager(),
          rmgr,
          manager.getFunctionFormulaManager(),
          new ReplaceBitvectorEncodingOptions(config));
      break;
    case FLOAT:
      throw new InvalidConfigurationException("Value FLOAT is not valid for option cpa.predicate.encodeBitvectorAs");
    default:
      throw new AssertionError();
    }
    return rawBitvectorFormulaManager;
  }

  /** Returns the FloatingPointFormulaManager or a Replacement based on the Option 'encodeFloatAs'. */
  private FloatingPointFormulaManager getRawFloatingPointFormulaManager() throws InvalidConfigurationException,
      AssertionError {
    final FloatingPointFormulaManager rawFloatingPointFormulaManager;
    switch (encodeFloatAs) {
    case FLOAT:
      try {
        rawFloatingPointFormulaManager = manager.getFloatingPointFormulaManager();
      } catch (UnsupportedOperationException e) {
        throw new InvalidConfigurationException(
            "The chosen SMT solver does not support the theory of floats, "
            + "please choose another SMT solver "
            + "or use the option cpa.predicate.encodeFloatAs "
            + "to approximate floats with another theory.",
            e);
      }
      break;
    case INTEGER:
      rawFloatingPointFormulaManager = new ReplaceFloatingPointWithNumeralAndFunctionTheory<>(
          wrappingHandler, getIntegerFormulaManager0(), manager.getFunctionFormulaManager(),
          manager.getBooleanFormulaManager());
      break;
    case RATIONAL:
      NumeralFormulaManager<NumeralFormula, RationalFormula> rmgr;
      try {
        rmgr = getRationalFormulaManager0();
      } catch (UnsupportedOperationException e) {
        throw new InvalidConfigurationException("The chosen SMT solver does not support the theory of rationals, "
            + "please choose another SMT solver "
            + "or use the option cpa.predicate.encodeFloatAs "
            + "to approximate floats with another theory.",
            e);
      }
      rawFloatingPointFormulaManager = new ReplaceFloatingPointWithNumeralAndFunctionTheory<>(
          wrappingHandler, rmgr, manager.getFunctionFormulaManager(),
          manager.getBooleanFormulaManager());
      break;
    case BITVECTOR:
      throw new InvalidConfigurationException("Value BITVECTOR is not valid for option cpa.predicate.encodeFloatAs");
    default:
      throw new AssertionError();
    }
    return rawFloatingPointFormulaManager;
  }

  private NumeralFormulaManager<IntegerFormula, IntegerFormula> getIntegerFormulaManager0() {
    NumeralFormulaManager<IntegerFormula, IntegerFormula> ifmgr = manager.getIntegerFormulaManager();
    if (useUFsForNonLinearArithmetic) {
      ifmgr = new NonLinearUFNumeralFormulaManager<>(
          wrappingHandler, ifmgr, functionFormulaManager);
    }
    return ifmgr;
  }

  private NumeralFormulaManager<NumeralFormula, RationalFormula> getRationalFormulaManager0() {
    NumeralFormulaManager<NumeralFormula, RationalFormula> rfmgr = manager.getRationalFormulaManager();
    if (useUFsForNonLinearArithmetic) {
      rfmgr = new NonLinearUFNumeralFormulaManager<>(
          wrappingHandler, rfmgr, functionFormulaManager);
    }
    return rfmgr;
  }

  FormulaWrappingHandler getFormulaWrappingHandler() {
    return wrappingHandler;
  }

  // DO NOT MAKE THIS METHOD PUBLIC!
  FormulaManager getRawFormulaManager() {
    return manager;
  }

  private <T1 extends Formula, T2 extends Formula> T1 wrap(FormulaType<T1> targetType, T2 toWrap) {
    return wrappingHandler.wrap(targetType, toWrap);
  }

  private <T extends Formula> Formula unwrap(T f) {
    return wrappingHandler.unwrap(f);
  }

  public Path formatFormulaOutputFile(String function, int call, String formula, int index) {
    if (formulaDumpFile == null) {
      return null;
    }

    return formulaDumpFile.getPath(function, call, formula, index);
  }

  public void dumpFormulaToFile(BooleanFormula f, Path outputFile) {
    if (outputFile != null) {
      try {
        Files.writeFile(outputFile, this.dumpFormula(f));
      } catch (IOException e) {
        logger.logUserException(Level.WARNING, e, "Failed to save formula to file");
      }
    }
  }

  /**
   * Helper method for creating variables of the given type.
   * @param formulaType the type of the variable.
   * @param name the name of the variable.
   * @return the created variable.
   */
  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeVariable(FormulaType<T> formulaType, String name) {
    Formula t;
    if (formulaType.isBooleanType()) {
      t = booleanFormulaManager.makeVariable(name);
    } else if (formulaType.isIntegerType()) {
      t = integerFormulaManager.makeVariable(name);
    } else if (formulaType.isRationalType()) {
      t = getRationalFormulaManager().makeVariable(name);
    } else if (formulaType.isBitvectorType()) {
      FormulaType.BitvectorType impl = (FormulaType.BitvectorType) formulaType;
      t = bitvectorFormulaManager.makeVariable(impl.getSize(), name);
    } else if (formulaType.isFloatingPointType()) {
      t = floatingPointFormulaManager.makeVariable(name, (FormulaType.FloatingPointType)formulaType);
    } else if (formulaType.isArrayType()) {
      FormulaType.ArrayFormulaType<?,?> arrayType = (FormulaType.ArrayFormulaType<?,?>) formulaType;
      t = arrayFormulaManager.makeArray(name, arrayType.getIndexType(), arrayType.getElementType());
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  /**
   * Make a variable of the given type.
   */
  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeNumber(FormulaType<T> formulaType, long value) {
    Formula t;
    if (formulaType.isIntegerType()) {
      t = integerFormulaManager.makeNumber(value);
    } else if (formulaType.isRationalType()) {
      t = getRationalFormulaManager().makeNumber(value);
    } else if (formulaType.isBitvectorType()) {
      t = bitvectorFormulaManager.makeBitvector((FormulaType<BitvectorFormula>)formulaType, value);
    } else if (formulaType.isFloatingPointType()) {
      t = floatingPointFormulaManager.makeNumber(value, (FormulaType.FloatingPointType)formulaType);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  /**
   * Make a number which type corresponds to the existing formula type.
   * // TODO: refactor all the {@code makeNumber} methods.
   */
  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeNumber(T formula, Rational value) {
    Formula t;
    FormulaType<?> formulaType = getFormulaType(formula);
    if (formulaType.isIntegerType() && value.isIntegral()) {
      t = integerFormulaManager.makeNumber(value.toString());
    } else if (formulaType.isRationalType()) {
      t = getRationalFormulaManager().makeNumber(value.toString());
    } else if (value.isIntegral() && formulaType.isBitvectorType()) {
      t = bitvectorFormulaManager.makeBitvector((FormulaType<BitvectorFormula>)formulaType,
          new BigInteger(value.toString()));
    } else if (formulaType.isFloatingPointType()) {
      t = floatingPointFormulaManager.makeNumber(value, (FormulaType.FloatingPointType)formulaType);
    } else {
      throw new IllegalArgumentException("Not supported interface: " + formula);
    }

    return (T) t;
  }

  /**
   * Make a variable of the given type.
   */
  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeNumber(FormulaType<T> formulaType, BigInteger value) {
    Formula t;
    if (formulaType.isIntegerType()) {
      t = integerFormulaManager.makeNumber(value);
    } else if (formulaType.isRationalType()) {
      t = getRationalFormulaManager().makeNumber(value);
    } else if (formulaType.isBitvectorType()) {
      t = bitvectorFormulaManager.makeBitvector((FormulaType<BitvectorFormula>)formulaType, value);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  @SuppressWarnings("unchecked")
  public  <T extends Formula> T makeNegate(T pNum) {
    Formula t;
    if (pNum instanceof IntegerFormula) {
      t = integerFormulaManager.negate((IntegerFormula)pNum);
    } else if (pNum instanceof RationalFormula) {
      t = getRationalFormulaManager().negate((RationalFormula)pNum);
    } else if (pNum instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.negate((BitvectorFormula)pNum);
    } else if (pNum instanceof FloatingPointFormula) {
      t = floatingPointFormulaManager.negate((FloatingPointFormula)pNum);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  @SuppressWarnings("unchecked")
  public  <T extends Formula> T makePlus(T pF1, T pF2) {
    Formula t;
    if (pF1 instanceof IntegerFormula && pF2 instanceof IntegerFormula) {
      t = integerFormulaManager.add((IntegerFormula)pF1, (IntegerFormula)pF2);
    } else if (pF1 instanceof NumeralFormula && pF2 instanceof NumeralFormula) {
      t = rationalFormulaManager.add((NumeralFormula)pF1, (NumeralFormula)pF2);
    } else if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.add((BitvectorFormula)pF1, (BitvectorFormula)pF2);
    } else if (pF1 instanceof FloatingPointFormula && pF2 instanceof FloatingPointFormula) {
      t = floatingPointFormulaManager.add((FloatingPointFormula)pF1, (FloatingPointFormula)pF2);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeMinus(T pF1, T pF2) {
    Formula t;
    if (pF1 instanceof IntegerFormula && pF2 instanceof IntegerFormula) {
      t = integerFormulaManager.subtract((IntegerFormula) pF1, (IntegerFormula) pF2);
    } else if (pF1 instanceof NumeralFormula && pF2 instanceof NumeralFormula) {
      t = getRationalFormulaManager().subtract((NumeralFormula) pF1, (NumeralFormula) pF2);
    } else if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.subtract((BitvectorFormula) pF1, (BitvectorFormula) pF2);
    } else if (pF1 instanceof FloatingPointFormula && pF2 instanceof FloatingPointFormula) {
      t = floatingPointFormulaManager.subtract((FloatingPointFormula)pF1, (FloatingPointFormula)pF2);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }
  @SuppressWarnings("unchecked")
  public  <T extends Formula> T makeMultiply(T pF1, T pF2) {
    Formula t;
    if (pF1 instanceof IntegerFormula && pF2 instanceof IntegerFormula) {
      t = integerFormulaManager.multiply((IntegerFormula) pF1, (IntegerFormula) pF2);
    } else if (pF1 instanceof NumeralFormula && pF2 instanceof NumeralFormula) {
      t = getRationalFormulaManager().multiply((NumeralFormula) pF1, (NumeralFormula) pF2);
    } else if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.multiply((BitvectorFormula) pF1, (BitvectorFormula) pF2);
    } else if (pF1 instanceof FloatingPointFormula && pF2 instanceof FloatingPointFormula) {
      t = floatingPointFormulaManager.multiply((FloatingPointFormula)pF1, (FloatingPointFormula)pF2);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  /**
   * This method returns the formula for the DIVIDE-operator.
   * Depending on the used formulaManager,
   * the result can be conform to either C99- or the SMTlib2-standard.
   *
   * Example:
   * SMTlib2: 10%3==1, 10%(-3)==1, (-10)%3==2,    (-10)%(-3)==2
   * C99:     10%3==1, 10%(-3)==1, (-10)%3==(-1), (-10)%(-3)==(-1)
   */
  @SuppressWarnings("unchecked")
  public <T extends Formula> T  makeDivide(T pF1, T pF2, boolean pSigned) {
    Formula t;
    if (pF1 instanceof IntegerFormula && pF2 instanceof IntegerFormula) {
      t = integerFormulaManager.divide((IntegerFormula) pF1, (IntegerFormula) pF2);
    } else if (pF1 instanceof NumeralFormula && pF2 instanceof NumeralFormula) {
      t = getRationalFormulaManager().divide((NumeralFormula) pF1, (NumeralFormula) pF2);
    } else if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.divide((BitvectorFormula) pF1, (BitvectorFormula) pF2, pSigned);
    } else if (pF1 instanceof FloatingPointFormula && pF2 instanceof FloatingPointFormula) {
      t = floatingPointFormulaManager.divide((FloatingPointFormula)pF1, (FloatingPointFormula)pF2);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  /**
   * This method returns the formula for the MODULO-operator.
   * Depending on the used formulaManager,
   * the result can be conform to either C99- or the SMTlib2-standard.
   *
   * Example:
   * SMTlib2: 10%3==1, 10%(-3)==1, (-10)%3==2,    (-10)%(-3)==2
   * C99:     10%3==1, 10%(-3)==1, (-10)%3==(-1), (-10)%(-3)==(-1)
   */
  @SuppressWarnings("unchecked")
  public <T extends Formula> T  makeModulo(T pF1, T pF2, boolean pSigned) {
    Formula t;
    if (pF1 instanceof IntegerFormula && pF2 instanceof IntegerFormula) {
      t = integerFormulaManager.modulo((IntegerFormula) pF1, (IntegerFormula) pF2);
    } else if (pF1 instanceof NumeralFormula && pF2 instanceof NumeralFormula) {
      t = getRationalFormulaManager().modulo((NumeralFormula) pF1, (NumeralFormula) pF2);
    } else if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.modulo((BitvectorFormula) pF1, (BitvectorFormula) pF2, pSigned);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  public <T extends Formula> BooleanFormula makeModularCongruence(T pF1, T pF2, long pModulo) {
    BooleanFormula t;
    if (pF1 instanceof IntegerFormula && pF2 instanceof IntegerFormula) {
      t = integerFormulaManager.modularCongruence((IntegerFormula) pF1, (IntegerFormula) pF2, pModulo);
    } else if (pF1 instanceof NumeralFormula && pF2 instanceof NumeralFormula) {
      t = getRationalFormulaManager().modularCongruence((NumeralFormula) pF1, (NumeralFormula) pF2, pModulo);
    } else if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.modularCongruence((BitvectorFormula) pF1, (BitvectorFormula) pF2, pModulo);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return t;
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeNot(T pF1) {
    Formula t;
    if (pF1 instanceof BooleanFormula) {
      t = booleanFormulaManager.not((BooleanFormula)pF1);
    } else if (pF1 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.not((BitvectorFormula)pF1);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeAnd(T pF1, T pF2) {
    Formula t;
    if (pF1 instanceof BooleanFormula && pF2 instanceof BooleanFormula) {
      t = booleanFormulaManager.and((BooleanFormula)pF1, (BooleanFormula)pF2);
    } else if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.and((BitvectorFormula)pF1, (BitvectorFormula)pF2);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeOr(T pF1, T pF2) {
    Formula t;
    if (pF1 instanceof BooleanFormula && pF2 instanceof BooleanFormula) {
      t = booleanFormulaManager.or((BooleanFormula)pF1, (BooleanFormula)pF2);
    } else if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.or((BitvectorFormula)pF1, (BitvectorFormula)pF2);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }


  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeXor(T pF1, T pF2) {
    Formula t;
    if (pF1 instanceof BooleanFormula && pF2 instanceof BooleanFormula) {
      t = booleanFormulaManager.xor((BooleanFormula)pF1, (BooleanFormula)pF2);
    } else if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.xor((BitvectorFormula)pF1, (BitvectorFormula)pF2);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeShiftLeft(T pF1, T pF2) {
    Formula t;
    if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.shiftLeft((BitvectorFormula)pF1, (BitvectorFormula)pF2);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeShiftRight(T pF1, T pF2, boolean signed) {
    Formula t;
    if (pF1 instanceof BitvectorFormula && pF2 instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.shiftRight((BitvectorFormula)pF1, (BitvectorFormula)pF2, signed);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  /**
   * Returns a term representing the selection of pFormula[pMsb:pLsb].
   */
  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeExtract(T pFormula, int pMsb, int pLsb, boolean signed) {
    checkArgument(pLsb >= 0);
    checkArgument(pMsb >= pLsb);
    Formula t;
    if (pFormula instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.extract((BitvectorFormula)pFormula, pMsb, pLsb, signed);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeConcat(T pFormula, T pAppendFormula) {
    Formula t;
    if (pFormula instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.concat((BitvectorFormula)pFormula, (BitvectorFormula)pAppendFormula);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  public <T extends Formula> T makeConcat(List<T> formulas) {
    checkArgument(!formulas.isEmpty());
    T conc = null;
    for (T t : formulas) {
      if (conc == null) {
        conc = t;
      } else {
        conc = makeConcat(conc, t);
      }
    }
    return conc;
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeExtend(T pFormula, int pExtensionBits, boolean pSigned) {
    checkArgument(pExtensionBits >= 0);
    Formula t;
    if (pFormula instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.extend((BitvectorFormula)pFormula, pExtensionBits, pSigned);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return (T) t;
  }

  @SuppressWarnings("unchecked")
  public  <T extends Formula> BooleanFormula makeEqual(T pLhs, T pRhs) {
    BooleanFormula t;
    if (pLhs instanceof BooleanFormula && pRhs instanceof BooleanFormula) {
      t = booleanFormulaManager.equivalence((BooleanFormula)pLhs, (BooleanFormula)pRhs);
    } else if (pLhs instanceof IntegerFormula && pRhs instanceof IntegerFormula) {
      t = integerFormulaManager.equal((IntegerFormula)pLhs, (IntegerFormula)pRhs);
    } else if (pLhs instanceof NumeralFormula && pRhs instanceof NumeralFormula) {
      t = getRationalFormulaManager().equal((NumeralFormula)pLhs, (NumeralFormula)pRhs);
    } else if (pLhs instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.equal((BitvectorFormula)pLhs, (BitvectorFormula)pRhs);
    } else if (pLhs instanceof FloatingPointFormula && pRhs instanceof FloatingPointFormula) {
      t = floatingPointFormulaManager.equalWithFPSemantics((FloatingPointFormula)pLhs, (FloatingPointFormula)pRhs);
    } else if (pLhs instanceof ArrayFormula<?, ?> && pRhs instanceof ArrayFormula<?, ?>) {
      @SuppressWarnings("rawtypes")
      ArrayFormula rhs = (ArrayFormula) pRhs;
      t = arrayFormulaManager.equivalence((ArrayFormula<?, ?>) pLhs, rhs);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return t;
  }

  public  <T extends Formula> BooleanFormula makeLessOrEqual(T pLhs, T pRhs, boolean signed) {
    BooleanFormula t;
    if (pLhs instanceof IntegerFormula && pRhs instanceof IntegerFormula) {
      t = integerFormulaManager.lessOrEquals((IntegerFormula)pLhs, (IntegerFormula)pRhs);
    } else if (pLhs instanceof NumeralFormula && pRhs instanceof NumeralFormula) {
      t = getRationalFormulaManager().lessOrEquals((NumeralFormula)pLhs, (NumeralFormula)pRhs);
    } else if (pLhs instanceof BitvectorFormula && pRhs instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.lessOrEquals((BitvectorFormula)pLhs, (BitvectorFormula)pRhs, signed);
    } else if (pLhs instanceof FloatingPointFormula && pRhs instanceof FloatingPointFormula) {
      t = floatingPointFormulaManager.lessOrEquals((FloatingPointFormula)pLhs, (FloatingPointFormula)pRhs);
    } else {
      throw new IllegalArgumentException("Not supported interface: " + pLhs + " " + pRhs);
    }

    return t;
  }
  public  <T extends Formula> BooleanFormula makeLessThan(T pLhs, T pRhs, boolean signed) {
    BooleanFormula t;
    if (pLhs instanceof IntegerFormula && pRhs instanceof IntegerFormula) {
      t = integerFormulaManager.lessThan((IntegerFormula) pLhs, (IntegerFormula) pRhs);
    } else if (pLhs instanceof NumeralFormula && pRhs instanceof NumeralFormula) {
      t = getRationalFormulaManager().lessThan((NumeralFormula) pLhs, (NumeralFormula) pRhs);
    } else if (pLhs instanceof BitvectorFormula && pRhs instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.lessThan((BitvectorFormula) pLhs, (BitvectorFormula) pRhs, signed);
    } else if (pLhs instanceof FloatingPointFormula && pRhs instanceof FloatingPointFormula) {
      t = floatingPointFormulaManager.lessThan((FloatingPointFormula)pLhs, (FloatingPointFormula)pRhs);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return t;
  }

  public  <T extends Formula> BooleanFormula makeGreaterThan(T pLhs, T pRhs, boolean signed) {
    BooleanFormula t;
    if (pLhs instanceof IntegerFormula && pRhs instanceof IntegerFormula) {
      t = integerFormulaManager.greaterThan((IntegerFormula) pLhs, (IntegerFormula) pRhs);
    } else if (pLhs instanceof NumeralFormula && pRhs instanceof NumeralFormula) {
      t = getRationalFormulaManager().greaterThan((NumeralFormula) pLhs, (NumeralFormula) pRhs);
    } else if (pLhs instanceof BitvectorFormula && pRhs instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.greaterThan((BitvectorFormula) pLhs, (BitvectorFormula) pRhs, signed);
    } else if (pLhs instanceof FloatingPointFormula && pRhs instanceof FloatingPointFormula) {
      t = floatingPointFormulaManager.greaterThan((FloatingPointFormula)pLhs, (FloatingPointFormula)pRhs);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return t;
  }

  public <T extends Formula> BooleanFormula makeGreaterOrEqual(T pLhs, T pRhs, boolean signed) {
    BooleanFormula t;
    if (pLhs instanceof IntegerFormula && pRhs instanceof IntegerFormula) {
      t = integerFormulaManager.greaterOrEquals((IntegerFormula) pLhs, (IntegerFormula) pRhs);
    } else if (pLhs instanceof NumeralFormula && pRhs instanceof NumeralFormula) {
      t = getRationalFormulaManager().greaterOrEquals((NumeralFormula) pLhs, (NumeralFormula) pRhs);
    } else if (pLhs instanceof BitvectorFormula && pRhs instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.greaterOrEquals((BitvectorFormula) pLhs, (BitvectorFormula) pRhs, signed);
    } else if (pLhs instanceof FloatingPointFormula && pRhs instanceof FloatingPointFormula) {
      t = floatingPointFormulaManager.greaterOrEquals((FloatingPointFormula)pLhs, (FloatingPointFormula)pRhs);
    } else {
      throw new IllegalArgumentException("Not supported interface");
    }

    return t;
  }

  public <T extends Formula> T makeVariable(FormulaType<T> formulaType, String name, int idx) {
    return makeVariable(formulaType, makeName(name, idx));
  }

  public IntegerFormulaManagerView getIntegerFormulaManager() {
    return integerFormulaManager;
  }

  public RationalFormulaManagerView getRationalFormulaManager() {
    if (rationalFormulaManager == null) {
      rationalFormulaManager = new RationalFormulaManagerView(wrappingHandler, getRationalFormulaManager0());
    }
    return rationalFormulaManager;
  }

  public BooleanFormulaManagerView getBooleanFormulaManager() {
    return booleanFormulaManager;
  }

  public BitvectorFormulaManagerView getBitvectorFormulaManager() {
    return bitvectorFormulaManager;
  }

  public FloatingPointFormulaManagerView getFloatingPointFormulaManager() {
    return floatingPointFormulaManager;
  }

  public FunctionFormulaManagerView getFunctionFormulaManager() {
    return functionFormulaManager;
  }

  public QuantifiedFormulaManagerView getQuantifiedFormulaManager() {
    if (quantifiedFormulaManager == null) {
      quantifiedFormulaManager = new QuantifiedFormulaManagerView(
          wrappingHandler,
          manager.getQuantifiedFormulaManager(),
          booleanFormulaManager,
          getIntegerFormulaManager()
      );
    }
    return quantifiedFormulaManager;
  }

  public ArrayFormulaManagerView getArrayFormulaManager() {
    if (arrayFormulaManager == null) {
      arrayFormulaManager = new ArrayFormulaManagerView(wrappingHandler, manager.getArrayFormulaManager());
    }
    return arrayFormulaManager;
  }

  public <T extends Formula> FormulaType<T> getFormulaType(T pFormula) {
    return wrappingHandler.getFormulaType(pFormula);
  }

  private <T extends Formula> FormulaType<T> getRawFormulaType(T pFormula) {
    return manager.getFormulaType(pFormula);
  }


  public <T extends Formula> BooleanFormula assignment(T left, T right) {
    FormulaType<?> lformulaType = getFormulaType(left);
    FormulaType<?> rformulaType = getFormulaType(right);
    if (!lformulaType.equals(rformulaType)) {
      throw new IllegalArgumentException("Can't assign different types! (" + lformulaType + " and " + rformulaType + ")");
    }

    if (lformulaType.isFloatingPointType()) {
      return getFloatingPointFormulaManager().assignment(
          (FloatingPointFormula)left, (FloatingPointFormula)right);
    }
    return makeEqual(left, right);
  }

  public BooleanFormula parse(String pS) throws IllegalArgumentException {
    return manager.parse(pS);
  }

  /**
   * Instantiate a list (!! guarantees to keep the ordering) of formulas.
   *  @see #instantiate(Formula, SSAMap)
   */
  public <F extends Formula> List<F> instantiate(List<F> pFormulas, final SSAMap pSsa) {
    return Lists.transform(pFormulas,
       new Function<F, F>() {
         @Override
         public F apply(F pF) {
           // Apply 'instantiate'!
           return instantiate(pF, pSsa);
         }
       });
  }

  public Set<String> instantiate(Iterable<String> pVariableNames, final SSAMap pSsa) {
    return from(pVariableNames).transform(new Function<String, String>() {
      @Override
      public String apply(String pArg0) {
        Pair<String, Integer> parsedVar = parseName(pArg0);
        return makeName(parsedVar.getFirst(), pSsa.getIndex(parsedVar.getFirst()));
      }
    }).toSet();
  }

  // the character for separating name and index of a value
  private static final String INDEX_SEPARATOR = "@";

  static String makeName(String name, int idx) {
    if (idx < 0) {
      return name;
    }
    return name + INDEX_SEPARATOR + idx;
  }


  /**
   * (Re-)instantiate the variables in pF with the SSA indices in pSsa.
   *
   * Existing instantiations are REPLACED by the
   * indices that are provided in the SSA map!
   */
  public <F extends Formula> F instantiate(F pF, final SSAMap pSsa) {
    return wrap(getFormulaType(pF),
        myFreeVariableNodeTransformer(unwrap(pF), new HashMap<Formula, Formula>(),
            new Function<String, String>() {

              @Override
              public String apply(String pFullSymbolName) {

                final Pair<String, Integer> indexedSymbol = parseName(pFullSymbolName);
                final int reInstantiateWithIndex = pSsa.getIndex(indexedSymbol.getFirst());

                if (reInstantiateWithIndex > 0) {
                  // OK, the variable has ALREADY an instance in the SSA, REPLACE it
                  return makeName(indexedSymbol.getFirst(), reInstantiateWithIndex);
                } else {
                  // the variable is not used in the SSA, keep it as is
                  return pFullSymbolName;
                }
              }
            })
        );
  }

  // various caches for speeding up expensive tasks
  //

  // cache for uninstantiating terms (see uninstantiate() below)
  private final Map<Formula, Formula> uninstantiateCache = new HashMap<>();

  /**
   * Only use inside this package and for solver-specific classes
   * when creating a {@link Model}. Do not use in client code!
   *
   * @throws IllegalArgumentException thrown if the given name is invalid
   */
  public static Pair<String, Integer> parseName(final String name) {
    String[] s = name.split(INDEX_SEPARATOR);
    if (s.length == 2) {
      return Pair.of(s[0], Integer.parseInt(s[1]));
    } else if (s.length == 1) {
      return Pair.of(s[0], null);
    } else {
      throw new IllegalArgumentException("Not an instantiated variable nor constant: " + name);
    }
  }

  /**
   * Uninstantiate a given formula.
   * (remove the SSA indices from its free variables and UFs)
   *
   * @param f  Input formula
   * @return    Uninstantiated formula
   */
  public <F extends Formula> F uninstantiate(F f) {
    return wrap(getFormulaType(f),
        myFreeVariableNodeTransformer(unwrap(f), uninstantiateCache,
            new Function<String, String>() {
              @Override
              public String apply(String pArg0) {
                // Un-instantiated variable name
                return parseName(pArg0).getFirst();
              }
            })
        );
  }

  /**
   * Apply an arbitrary renaming to all free variables and UFs in a formula.
   * @param pFormula The formula in which the renaming should occur.
   * @param pRenameFunction The renaming function (may not return null).
   * @return A formula of the same type and structure as the input.
   */
  public <F extends Formula> F renameFreeVariablesAndUFs(F pFormula,
      Function<String, String> pRenameFunction) {

    return wrap(getFormulaType(pFormula),
        myFreeVariableNodeTransformer(unwrap(pFormula),
            new HashMap<Formula, Formula>(),
            pRenameFunction));
  }

  private <T extends Formula> T myFreeVariableNodeTransformer(
      final T pFormula,
      final Map<Formula, Formula> pCache,
      final Function<String, String> pRenameFunction) {

    Preconditions.checkNotNull(pCache);
    Preconditions.checkNotNull(pFormula);
    Preconditions.checkNotNull(pRenameFunction);

    final Deque<Formula> toProcess = new ArrayDeque<>();

    // Add the formula to the work queue
    toProcess.push(pFormula);

    FormulaVisitor<Void> process = new FormulaVisitor<Void>() {

      @Override
      public Void visitFreeVariable(Formula f, String name) {
        String newName = pRenameFunction.apply(name);
        Formula renamed = unwrap(makeVariable(getFormulaType(f), newName));
        pCache.put(f, renamed);
        return null;
      }

      @Override
      public Void visitBoundVariable(Formula f, int deBruijnIdx) {

        // Bound variables have to stay as-is.
        pCache.put(f, f);
        return null;
      }

      @Override
      public Void visitConstant(Formula f, Object value) {
        pCache.put(f, f);
        return null;
      }


      @Override
      public Void visitFuncApp(Formula f, List<Formula> args,
          FuncDecl decl,
          Function<List<Formula>, Formula> newApplicationConstructor) {

        boolean allArgumentsTransformed = true;

        // Construct a new argument list for the function application.
        List<Formula> newArgs = new ArrayList<>(args.size());

        for (Formula c : args) {
          Formula newC = pCache.get(c);

          if (newC != null) {
            newArgs.add(newC);
          } else {
            toProcess.push(c);
            allArgumentsTransformed = false;
          }
        }

        // The Flag childrenDone indicates whether all arguments
        // of the function were already processed.
        if (allArgumentsTransformed) {

          // Create an processed version of the
          // function application.
          toProcess.pop();
          Formula out;
          if (decl.getKind() == FuncDeclKind.UF) {

            out = functionFormulaManager.declareAndCallUninterpretedFunction(
                pRenameFunction.apply(decl.getName()),
                getFormulaType(f),
                newArgs
            );

          } else {
            out = newApplicationConstructor.apply(newArgs);
          }
          pCache.put(f, out);
        }
        return null;
      }

      @Override
      public Void visitQuantifier(BooleanFormula f, Quantifier quantifier,
          List<Formula> args,
          BooleanFormula body) {
        BooleanFormula transformedBody = (BooleanFormula) pCache.get(body);

        if (transformedBody != null) {
          BooleanFormula newTt = quantifiedFormulaManager.mkQuantifier(
              quantifier, args, transformedBody
          );
          pCache.put(f, newTt);

        } else {
          toProcess.push(body);
        }
        return null;
      }
    };

    // Process the work queue
    while (!toProcess.isEmpty()) {
      Formula tt = toProcess.peek();

      if (pCache.containsKey(tt)) {
        toProcess.pop();
        continue;
      }

      //noinspection ResultOfMethodCallIgnored
      visit(process, tt);
    }

    @SuppressWarnings("unchecked")
    T result = (T)pCache.get(pFormula);
    assert result != null;
    assert getRawFormulaType(pFormula).equals(getRawFormulaType(result));
    return result;
  }

  /**
   * Extract all atoms of a given boolean formula.
   */
  public Collection<BooleanFormula> extractAtoms(BooleanFormula pFormula, final boolean splitArithEqualities) {
    final List<BooleanFormula> result = new ArrayList<>();

    final BooleanFormulaVisitor<Boolean> isLowestLevel =
        new DefaultBooleanFormulaVisitor<Boolean>() {
          @Override
          public Boolean visitDefault() {
            return false;
          }
          @Override
          public Boolean visitAtom(BooleanFormula atom, FuncDecl decl) {
            return true;
          }
        };

    new RecursiveFormulaVisitor(manager) {
      @Override
      public Void visitFuncApp(
          Formula f,
          List<Formula> args,
          FuncDecl decl, Function<List<Formula>, Formula> constructor) {

        if (getFormulaType(f).isBooleanType() &&
            booleanFormulaManager.visit(isLowestLevel, (BooleanFormula) f)) {

          if (splitArithEqualities && myIsPurelyArithmetic(f)) {
            List<BooleanFormula> split =
                unsafeManager.splitNumeralEqualityIfPossible((BooleanFormula)f);
            visit(split.get(0));
          }
          result.add((BooleanFormula) f);
        } else {
          super.visitFuncApp(f, args, decl, constructor);
        }
        return null;
      }
    }.visit(unwrap(pFormula));
    return result;
  }

  /**
   * Return the negated part of a formula, if the top-level operator is a negation.
   * I.e., for {@code not f} return {@code f}.
   *
   * For removing the outer-most negation of a formula if it is present
   * or otherwise keeping the original formula, use
   * {@code f = stripNegation(f).or(f);}.
   *
   * @param f The formula, possibly negated.
   * @return An optional formula.
   */
  public Optional<BooleanFormula> stripNegation(BooleanFormula f) {
    return booleanFormulaManager.visit(
        new DefaultBooleanFormulaVisitor<Optional<BooleanFormula>>() {
      @Override
      protected Optional<BooleanFormula> visitDefault() {
        return Optional.absent();
      }

      @Override
      public Optional<BooleanFormula> visitNot(BooleanFormula negated) {
        return Optional.of(negated);
      }
    }, f);
  }

  /**
   * @see UnsafeFormulaManager#splitNumeralEqualityIfPossible(Formula) for
   * documentation.
   */
  public List<BooleanFormula> splitNumeralEqualityIfPossible(BooleanFormula formula) {
    // only exported here for BooleanFormula because otherwise it is not type-safe
    return unsafeManager.splitNumeralEqualityIfPossible(formula);
  }

  /**
   * Cache for splitting arithmetic equalities in extractAtoms.
   */
  private final Map<Formula, Boolean> arithCache = new HashMap<>();

  /**
   * Returns true if the given term is a pure arithmetic term.
   */
  private boolean myIsPurelyArithmetic(Formula f) {
    Boolean result = arithCache.get(f);
    if (result != null) { return result; }

    // Stays at zero unless a UF is found.
    final AtomicInteger isPurelyAtomic = new AtomicInteger(0);
    new RecursiveFormulaVisitor(manager) {
      @Override
      public Void visitFuncApp(
          Formula f,
          List<Formula> args,
          FuncDecl decl, Function<List<Formula>, Formula> constructor) {
        if (decl.getKind() == FuncDeclKind.UF) {
          isPurelyAtomic.incrementAndGet();
        }
        return null;
      }
    }.visit(f);
    result = (isPurelyAtomic.get() == 0);
    arithCache.put(f, result);
    return result;
  }

  /**
   * Extract the names of all free variables in a formula.
   *
   * @param f   The input formula
   * @return    Set of variable names (might be instantiated)
   */
  public Set<String> extractVariableNames(Formula f) {
    return manager.extractFunctionNames(unwrap(f));
  }

  /**
   * Extract the names of all free variables + UFs in a formula.
   *
   * @param f   The input formula
   *
   * @return    Set of variable names (might be instantiated)
   */
  public Set<String> extractFunctionNames(Formula f) {
    return manager.extractFunctionNames(unwrap(f));
  }

  /**
   * Extract pairs of <variable name, variable formula>
   *  of all free variables in a formula.
   *
   * @deprecated The type of the returned Formula objects is incorrect.
   * Thus consider using {@link #extractVariableNames(Formula)} instead.
   * @param pF The input formula
   * @return Map from variable names to variable formulas.
   */
  @Deprecated
  public Map<String, Formula> extractFreeVariableMap(Formula pF) {
    return myExtractSubformulas(unwrap(pF), false);
  }

  /**
   * NOTE: FormulaType of traversed formulas does not include wrapping.
   */
  private Map<String, Formula> myExtractSubformulas(
      final Formula pFormula,
      final boolean extractUF) {

    // todo: code duplication with JavaSMT, pending extractFreeVariableMap
    // removal.
    final Map<String, Formula> found = new HashMap<>();
    manager.visitRecursively(new DefaultFormulaVisitor<TraversalProcess>() {

      @Override
      protected TraversalProcess visitDefault(Formula f) {
        return TraversalProcess.CONTINUE;
      }

      @Override
      public TraversalProcess visitFuncApp(
          Formula f,
          List<Formula> args,
          FuncDecl decl,
          Function<List<Formula>, Formula> constructor) {

        if (decl.getKind() == FuncDeclKind.UF && extractUF) {
          found.put(decl.getName(), f);
        }
        return TraversalProcess.CONTINUE;
      }

      @Override
      public TraversalProcess visitFreeVariable(Formula f, String name) {
        found.put(name, f);
        return TraversalProcess.CONTINUE;
      }
    }, pFormula);
    return found;
  }

  public Appender dumpFormula(BooleanFormula pT) {
    return manager.dumpFormula(pT);
  }

  public boolean isPurelyConjunctive(BooleanFormula t) {
    t = applyTactic(t, Tactic.NNF);
    return booleanFormulaManager.visit(new DefaultBooleanFormulaVisitor<Boolean>() {

      @Override public Boolean visitDefault() {
        return false;
      }
      @Override public Boolean visitTrue() {
        return true;
      }
      @Override public Boolean visitFalse() {
        return true;
      }
      @Override public Boolean visitAtom(BooleanFormula atom, FuncDecl decl) {
        return !containsIfThenElse(atom);
      }
      @Override public Boolean visitNot(BooleanFormula operand) {
        return booleanFormulaManager.visit(this, operand);
      }
      @Override public Boolean visitAnd(List<BooleanFormula> operands) {
        for (BooleanFormula operand : operands) {
          if (!booleanFormulaManager.visit(this, operand)) {
            return false;
          }
        }
        return true;
      }
    }, t);
  }

  private boolean containsIfThenElse(Formula f) {
    if (booleanFormulaManager.isIfThenElse(f)) {
      return true;
    }
    for (int i = 0; i < unsafeManager.getArity(f); ++i) {
      if (containsIfThenElse(unsafeManager.getArg(f, i))) {
        return true;
      }
    }
    return false;
  }

  static final String BitwiseAndUfName = "_&_";
  static final String BitwiseOrUfName ="_!!_"; // SMTInterpol does not allow "|" to be used
  static final String BitwiseXorUfName ="_^_";
  static final String BitwiseNotUfName ="_~_";

  // returns a formula with some "static learning" about some bitwise
  // operations, so that they are (a bit) "less uninterpreted"
  // Currently it add's the following formulas for each number literal n that
  // appears in the formula: "(n & 0 == 0) and (0 & n == 0)"
  // But only if an bitwise "and" occurs in the formula.
  private BooleanFormula myGetBitwiseAxioms(Formula f) {
    Deque<Formula> toProcess = new ArrayDeque<>();
    Set<Formula> seen = new HashSet<>();
    Set<Formula> allLiterals = new HashSet<>();

    boolean andFound = false;

    toProcess.add(f);
    while (!toProcess.isEmpty()) {
      final Formula tt = toProcess.pollLast();

      if (unsafeManager.isNumber(tt)) {
        allLiterals.add(tt);
      }
      if (unsafeManager.isUF(tt)) {
        if (unsafeManager.getName(tt).equals(BitwiseAndUfName) && !andFound) {
          andFound = true;
        }
      }
      int arity = unsafeManager.getArity(tt);
      for (int i = 0; i < arity; ++i) {
        Formula c = unsafeManager.getArg(tt, i);
        if (seen.add(c)) {
          // was not already contained in seen
          toProcess.add(c);
        }
      }
    }

    BooleanFormula result = booleanFormulaManager.makeBoolean(true);
    if (andFound) {
      // Note: We can assume that we have no real bitvectors here, so size should be not important
      // If it ever should be we can just add an method to the unsafe-manager to read the size.
      BitvectorFormula z = bitvectorFormulaManager.makeBitvector(1, 0);
      FormulaType<BitvectorFormula> type = FormulaType.getBitvectorTypeWithSize(1);
      //Term z = env.numeral("0");
      for (Formula nn : allLiterals) {
        BitvectorFormula n = bitvectorFormulaManager.wrap(type, nn);
        BitvectorFormula u1 = bitvectorFormulaManager.and(z, n);
        BitvectorFormula u2 = bitvectorFormulaManager.and(n, z);
        //Term u1 = env.term(bitwiseAndUfDecl, n, z);
        //Term u2 = env.term(bitwiseAndUfDecl, z, n);
        //Term e1;
        //e1 = env.term("=", u1, z);
        BooleanFormula e1 = bitvectorFormulaManager.equal(u1, z);
        //Term e2 = env.term("=", u2, z);
        BooleanFormula e2 = bitvectorFormulaManager.equal(u2, z);
        BooleanFormula a = booleanFormulaManager.and(e1, e2);
        //Term a = env.term("and", e1, e2);

        result = booleanFormulaManager.and(result, a); //env.term("and", result, a);
      }
    }
    return result;
  }

    // returns a formula with some "static learning" about some bitwise
    public BooleanFormula getBitwiseAxioms(BooleanFormula f) {
      return myGetBitwiseAxioms(f);
    }

  public boolean useBitwiseAxioms() {
    return useBitwiseAxioms;
  }

  public BooleanFormula createPredicateVariable(String pName) {
    return booleanFormulaManager.makeVariable(pName);
  }

  public <T extends Formula> T simplify(T input) {
    return manager.simplify(input);
  }

  public BooleanFormula substitute(
      BooleanFormula f, Map<? extends Formula, ? extends Formula> replacements) {
    Map<Formula, Formula> m = new HashMap<>();
    for (Entry<? extends Formula, ? extends Formula> e : replacements.entrySet()) {
      m.put(unwrap(e.getKey()), unwrap(e.getValue()));
    }
    return (BooleanFormula)unsafeManager.substitute(f, m);
  }

  /**
   * Use a SSA map to conclude what variables of an
   * [instantiated] formula can be considered 'dead'.
   *
   * A variable is considered 'dead' if its SSA index
   * is different from the index in the SSA map.
   */
  public Set<String> getDeadVariableNames(BooleanFormula pFormula, SSAMap pSsa) {
    return getDeadFunctionNames(pFormula, pSsa, false);
  }

  /**
   * Same as {@link #getDeadVariableNames}, but returns UF's as well.
   */
  public Set<String> getDeadFunctionNames(BooleanFormula pFormula, SSAMap pSsa) {
    return getDeadFunctionNames(pFormula, pSsa, true);
  }

  private Set<String> getDeadFunctionNames(BooleanFormula pFormula, SSAMap pSsa,
      boolean extractUFs) {
    Set<String> result = Sets.newHashSet();
    List<Formula> varFormulas = myGetDeadVariables(pFormula, pSsa, extractUFs);
    for (Formula f : varFormulas) {
      result.add(unsafeManager.getName(f));
    }
    return result;
  }

  /**
   * Helper method for {@link #getDeadVariableNames(BooleanFormula, SSAMap)}.
   * Do not make this method public, because the returned formulas have incorrect
   * types (they are not appropriately wrapped).
   */
  private List<Formula> myGetDeadVariables(BooleanFormula pFormula, SSAMap pSsa,
      boolean extractUF) {
    List<Formula> result = Lists.newArrayList();

    for (Entry<String, Formula> entry: myExtractSubformulas(unwrap(pFormula),
        extractUF).entrySet()) {

      String name = entry.getKey();
      Formula varFormula = entry.getValue();
      Pair<String, Integer> fullName = parseName(name);
      String varName = fullName.getFirst();
      Integer varSsaIndex = fullName.getSecond();

      if (varSsaIndex == null) {
        if (pSsa.containsVariable(varName)) {
          result.add(varFormula);
        }

      } else {

        if (varSsaIndex != pSsa.getIndex(varName)) {
          result.add(varFormula);
        }
      }
    }

    return result;
  }

  /**
   * Eliminate all propositions about 'dead' variables
   *  in a given formula.
   *
   * Quantifier elimination is used! This has to be supported by the solver!
   *    (solver-independent approaches would be possible)
   *
   * A variable is considered 'dead' if its SSA index
   *  is different from the index in the SSA map.
   */
  public BooleanFormula eliminateDeadVariables(
      final BooleanFormula pF,
      final SSAMap pSsa)
    throws SolverException, InterruptedException {

    Preconditions.checkNotNull(pF);
    Preconditions.checkNotNull(pSsa);

    List<Formula> irrelevantVariables = myGetDeadVariables(pF, pSsa, false);

    BooleanFormula eliminationResult = pF;

    if (!irrelevantVariables.isEmpty()) {
      QuantifiedFormulaManagerView qfmgr = getQuantifiedFormulaManager();
      BooleanFormula quantifiedFormula = qfmgr.exists(irrelevantVariables, pF);
      eliminationResult = qfmgr.eliminateQuantifiers(quantifiedFormula);
    }

    eliminationResult = simplify(eliminationResult); // TODO: Benchmark the effect!
    return eliminationResult;
  }

  /**
   * See {@link FormulaManager#applyTactic(BooleanFormula, Tactic)} for
   * documentation.
   */
  public BooleanFormula applyTactic(BooleanFormula input, Tactic tactic) {
    return manager.applyTactic(input, tactic);
  }

  /**
   * Visit the formula with a given visitor.
   */
  public <R> R visit(FormulaVisitor<R> rFormulaVisitor, Formula f) {
    return manager.visit(rFormulaVisitor, unwrap(f));
  }

  /**
   * Visit the formula recursively with a given {@link FormulaVisitor}.
   *
   * <p>This method guarantees that the traversal is done iteratively,
   * without using Java recursion, and thus is not prone to StackOverflowErrors.
   *
   * <p>Furthermore, this method also guarantees that every equal part of the formula
   * is visited only once. Thus it can be used to traverse DAG-like formulas efficiently.
   */
  public void visitRecursively(
      FormulaVisitor<TraversalProcess> rFormulaVisitor,
      Formula f) {
    manager.visitRecursively(rFormulaVisitor, unwrap(f));
  }
}
