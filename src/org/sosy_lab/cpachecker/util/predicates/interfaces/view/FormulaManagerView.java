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
package org.sosy_lab.cpachecker.util.predicates.interfaces.view;


import static com.google.common.base.Preconditions.*;

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
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Appender;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Triple;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.util.predicates.FormulaManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FloatingPointFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FloatingPointFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType.ArrayFormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType.BitvectorType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType.FloatingPointType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.RationalFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.UnsafeFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.rationals.Rational;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * This class is the central entry point for all formula creation
 * and manipulation operations for client code.
 * It delegates to the actual solver package
 * and provides additional utilities.
 * The preferred way of instantiating this class is via
 * {@link Solver#create(Configuration, LogManager, org.sosy_lab.cpachecker.core.ShutdownNotifier)}.
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

  public static enum FormulaStructure {
    ATOM,
    LITERAL,
    DISJUNCTIVE_CLAUSE,
    CONJUNCTIVE_CLAUSE,
    FORMULA
  }

  public static enum Theory {
    INTEGER,
    RATIONAL,
    BITVECTOR,
    FLOAT,
    ;
  }

  private final LogManager logger;

  private final FormulaManager manager;
  private final UnsafeFormulaManager unsafeManager;

  private final BooleanFormulaManagerView booleanFormulaManager;
  private final BitvectorFormulaManagerView bitvectorFormulaManager;
  private final FloatingPointFormulaManagerView floatingPointFormulaManager;
  private final NumeralFormulaManagerView<IntegerFormula, IntegerFormula> integerFormulaManager;
  private NumeralFormulaManagerView<NumeralFormula, RationalFormula> rationalFormulaManager;
  private final FunctionFormulaManagerView functionFormulaManager;
  private final QuantifiedFormulaManagerView quantifiedFormulaManager;
  private final ArrayFormulaManagerView arrayFormulaManager;

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

  @Option(secure=true, description="Allows to ignore Concat and Extract Calls when Bitvector theory was replaced with Integer or Rational.")
  private boolean ignoreExtractConcat = true;

  public FormulaManagerView(FormulaManagerFactory solverFactory, Configuration config, LogManager pLogger) throws InvalidConfigurationException {
    config.inject(this, FormulaManagerView.class);
    logger = pLogger;
    manager = checkNotNull(solverFactory.getFormulaManager());
    unsafeManager = manager.getUnsafeFormulaManager();

    BitvectorFormulaManager rawBitvectorFormulaManager;
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
        rawBitvectorFormulaManager = new ReplaceBitvectorWithNumeralAndFunctionTheory<>(this,
            manager.getIntegerFormulaManager(), manager.getFunctionFormulaManager(),
            ignoreExtractConcat);
        break;
      case RATIONAL:
        rawBitvectorFormulaManager = new ReplaceBitvectorWithNumeralAndFunctionTheory<>(this,
            manager.getRationalFormulaManager(), manager.getFunctionFormulaManager(),
            ignoreExtractConcat);
      break;
      case FLOAT:
        throw new InvalidConfigurationException("Value FLOAT is not valid for option cpa.predicate.encodeBitvectorAs");
      default:
        throw new AssertionError();
    }
    bitvectorFormulaManager = new BitvectorFormulaManagerView(this, rawBitvectorFormulaManager);

    integerFormulaManager = new NumeralFormulaManagerView<>(this, manager.getIntegerFormulaManager());
    booleanFormulaManager = new BooleanFormulaManagerView(this, manager.getBooleanFormulaManager(), manager.getUnsafeFormulaManager());
    functionFormulaManager = new FunctionFormulaManagerView(this, manager.getFunctionFormulaManager());
    quantifiedFormulaManager = new QuantifiedFormulaManagerView(this, manager.getQuantifiedFormulaManager());
    arrayFormulaManager = new ArrayFormulaManagerView(this, manager.getArrayFormulaManager());

    FloatingPointFormulaManager rawFloatingPointFormulaManager;
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
          this, getIntegerFormulaManager());
      break;
    case RATIONAL:
      rawFloatingPointFormulaManager = new ReplaceFloatingPointWithNumeralAndFunctionTheory<>(
          this, getRationalFormulaManager());
    break;
    case BITVECTOR:
      throw new InvalidConfigurationException("Value BITVECTOR is not valid for option cpa.predicate.encodeFloatAs");
    default:
      throw new AssertionError();
    }
    floatingPointFormulaManager = new FloatingPointFormulaManagerView(this, rawFloatingPointFormulaManager);
  }


  @SuppressWarnings("unchecked")
  <T1 extends Formula, T2 extends Formula> T1 wrap(FormulaType<T1> targetType, T2 toWrap) {
    assert !(toWrap instanceof WrappingFormula<?, ?>);

    if (targetType.isBitvectorType() && (encodeBitvectorAs != Theory.BITVECTOR)) {
      return (T1) new WrappingBitvectorFormula<>((BitvectorType)targetType, toWrap);

    } else if (targetType.isFloatingPointType() && (encodeFloatAs != Theory.FLOAT)) {
      return (T1) new WrappingFloatingPointFormula<>((FloatingPointType)targetType, toWrap);

    } else if (targetType.isArrayType()) {
      final ArrayFormulaType<?, ?> targetArrayType = (ArrayFormulaType<?, ?>) targetType;
//      final FormulaType<? extends Formula> targetIndexType = targetArrayType.getIndexType();
//      final FormulaType<? extends Formula> targetElementType = targetArrayType.getElementType();
      return (T1) new WrappingArrayFormula<>(targetArrayType, toWrap);

    } else if (targetType.equals(manager.getFormulaType(toWrap))) {
      return (T1) toWrap;

    } else {
      throw new IllegalArgumentException("invalid wrap call");
    }
  }

  Formula unwrap(Formula f) {
    if (f instanceof WrappingFormula<?, ?>) {
      return ((WrappingFormula<?, ?>)f).getWrapped();
    } else {
      return f;
    }
  }

  FormulaType<?> unwrapType(FormulaType<?> type) {
    if (type.isArrayType()) {
      ArrayFormulaType<?, ?> arrayType = (ArrayFormulaType<?, ?>) type;
      return FormulaType.getArrayType(
          unwrapType(arrayType.getIndexType()),
          unwrapType(arrayType.getElementType()));
    }

    if (type.isBitvectorType()) {
      switch (encodeBitvectorAs) {
      case BITVECTOR:
        return type;
      case INTEGER:
        return FormulaType.IntegerType;
      case RATIONAL:
        return FormulaType.RationalType;
      }
    }

    if (type.isFloatingPointType()) {
      switch (encodeFloatAs) {
      case FLOAT:
        return type;
      case INTEGER:
        return FormulaType.IntegerType;
      case RATIONAL:
        return FormulaType.RationalType;
      }
    }

    return type;
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
   * @param formulaType
   * @param value
   * @return
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
    FormulaType<T> formulaType = getFormulaType(formula);
    if (formulaType.isIntegerType() && value.isIntegral()) {
      t = integerFormulaManager.makeNumber(value.toString());
    } else if (formulaType.isRationalType()) {
      t = getRationalFormulaManager().makeNumber(value.toString());
    } else if (value.isIntegral() && formulaType.isBitvectorType()) {
      t = bitvectorFormulaManager.makeBitvector((FormulaType<BitvectorFormula>)formulaType,
          new BigInteger(value.toString()));
    } else {
      throw new IllegalArgumentException("Not supported interface");
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
      t = getRationalFormulaManager().add((NumeralFormula)pF1, (NumeralFormula)pF2);
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
   * @param pFormula
   * @param pMsb
   * @param pLsb
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeExtract(T pFormula, int pMsb, int pLsb) {
    checkArgument(pLsb >= 0);
    checkArgument(pMsb >= pLsb);
    Formula t;
    if (pFormula instanceof BitvectorFormula) {
      t = bitvectorFormulaManager.extract((BitvectorFormula)pFormula, pMsb, pLsb);
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
      throw new IllegalArgumentException("Not supported interface");
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

  public NumeralFormulaManagerView<IntegerFormula, IntegerFormula> getIntegerFormulaManager() {
    return integerFormulaManager;
  }

  public NumeralFormulaManagerView<NumeralFormula, RationalFormula> getRationalFormulaManager() {
    // lazy initialisation, because not all SMT-solvers support Rationals and maybe we only want to use Integers.
    if (rationalFormulaManager == null) {
      rationalFormulaManager = new NumeralFormulaManagerView<>(this, manager.getRationalFormulaManager());
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
    return quantifiedFormulaManager;
  }

  public ArrayFormulaManagerView getArrayFormulaManager() {
    return arrayFormulaManager;
  }

  @SuppressWarnings("unchecked")
  public <T extends Formula> FormulaType<T> getFormulaType(T pFormula) {
    checkNotNull(pFormula);

    if (pFormula instanceof WrappingFormula<?, ?>) {
      WrappingFormula<?, ?> castFormula = (WrappingFormula<?, ?>)pFormula;
      return (FormulaType<T>)castFormula.getType();
    } else {
      return getRawFormulaType(pFormula);
    }
  }

  private <T extends Formula> FormulaType<T> getRawFormulaType(T pFormula) {
    return manager.getFormulaType(pFormula);
  }


  public <T extends Formula> BooleanFormula assignment(T left, T right) {
    FormulaType<T> lformulaType = this.getFormulaType(left);
    FormulaType<T> rformulaType = this.getFormulaType(right);
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

  public BooleanFormula instantiate(BooleanFormula f, SSAMap ssa) {
    return myInstantiate(ssa, f);
  }

  // the character for separating name and index of a value
  private static final String INDEX_SEPARATOR = "@";

  static String makeName(String name, int idx) {
    if (idx < 0) {
      return name;
    }
    return name + INDEX_SEPARATOR + idx;
  }

  private <T extends Formula> T myInstantiate(final SSAMap pSsa, T pF) {

    return myFreeVariableNodeTransformer(
        pF,
        new HashMap<Formula, Formula>(),
        new Function<Formula, Formula>() {

      @Override
      public Formula apply(Formula pArg0) {
        String name = unsafeManager.getName(pArg0);
        int idx = pSsa.getIndex(name);
        if (idx > 0) {
          // OK, the variable has an instance in the SSA, replace it
          return unsafeManager.replaceName(pArg0, makeName(name, idx));
        } else {
          // the variable is not used in the SSA, keep it as is
          return pArg0;
        }
      }
    });
  }

  private boolean ufCanBeLvalue(String name) {
    return name.startsWith("*");
  }

  public BooleanFormula uninstantiate(BooleanFormula pF) {
    return myUninstantiate(pF);
  }

  // various caches for speeding up expensive tasks
  //
  // cache for splitting arithmetic equalities in extractAtoms
  private final Map<Formula, Boolean> arithCache = new HashMap<>();

  // cache for uninstantiating terms (see uninstantiate() below)
  private final Map<Formula, Formula> uninstantiateCache = new HashMap<>();

  /**
   * Only use inside this package and for solver-specific classes
   * when creating a {@link Model}.
   * Do not use in client code!
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

  private <T extends Formula> T myUninstantiate(T f) {
    return myFreeVariableNodeTransformer(f, uninstantiateCache, new Function<Formula, Formula>() {
      @Override
      public Formula apply(Formula pArg0) {
        // Un-instantiated the variable (by renaming it)
        String name = parseName(unsafeManager.getName(pArg0)).getFirst();
        return unsafeManager.replaceName(pArg0, name);
      }
    });
  }

  private <T extends Formula> T myFreeVariableNodeTransformer(
      final T pFormula,
      final Map<Formula, Formula> pCache,
      final Function<Formula, Formula> pRenameFunction) {

    Preconditions.checkNotNull(pCache);
    Preconditions.checkNotNull(pFormula);
    Preconditions.checkNotNull(pRenameFunction);

    Deque<Formula> toProcess = new ArrayDeque<>();

    // Add the formula to the work queue
    toProcess.push(pFormula);

    // Process the work queue
    while (!toProcess.isEmpty()) {
      final Formula tt = toProcess.peek();

      if (pCache.containsKey(tt)) {
        toProcess.pop();
        continue;
      }

      if (unsafeManager.isFreeVariable(tt)) {

        Formula renamed = pRenameFunction.apply(tt);
        pCache.put(tt, renamed);

      } else if (unsafeManager.isBoundVariable(tt)) {

        // There is no need for un-instantiating bound variables.
        pCache.put(tt, tt);

      } else if (unsafeManager.isQuantification(tt)) {

        // Quantifications are no function applications,
        //  i.e., they do not have an arity!

        BooleanFormula q = (BooleanFormula) tt;
        BooleanFormula ttBody = unsafeManager.getQuantifiedBody(tt);
        BooleanFormula transformedBody = (BooleanFormula) pCache.get(ttBody);

        if (transformedBody != null) {
          // make a new quantified formula
          BooleanFormula newTt = unsafeManager.replaceQuantifiedBody(
              (BooleanFormula) tt, transformedBody);
          pCache.put(tt, newTt);

        } else {
          toProcess.push(ttBody);
        }

      } else {

        boolean allArgumentsTransformed = true;

        // Construct a new argument list for the function application.
        // ATTENTION: also boolean operators, like AND, OR, ...
        //             are function applications!
        int arity = unsafeManager.getArity(tt);
        List<Formula> newargs = Lists.newArrayListWithExpectedSize(arity);

        for (int i = 0; i < arity; ++i) {
          Formula c = unsafeManager.getArg(tt, i);
          Formula newC = pCache.get(c);

          if (newC != null) {
            newargs.add(newC);

          } else {
            toProcess.push(c);
            allArgumentsTransformed = false;
          }
        }

        // The Flag childrenDone indicates whether all arguments
        // of the function were already un-instantiated, i.e., the
        // un-instantiated formula of all arguments is in the cache.

        if (allArgumentsTransformed) {
          // Create an un-instantiated version of the
          // function application.

          toProcess.pop();
          Formula newt;

          if (unsafeManager.isUF(tt)) {
            String name = unsafeManager.getName(tt);
            assert name != null;

            if (ufCanBeLvalue(name)) {
              name = parseName(name).getFirst();
              newt = unsafeManager.replaceArgsAndName(tt, name, newargs);

            } else {
              newt = unsafeManager.replaceArgs(tt, newargs);
            }
          } else {
            newt = unsafeManager.replaceArgs(tt, newargs);
          }

          pCache.put(tt, newt);
        }
      }
    }

    @SuppressWarnings("unchecked")
    T result = (T)pCache.get(pFormula);
    assert result != null;
    assert getRawFormulaType(pFormula).equals(getRawFormulaType(result));
    return result;
  }

  public Collection<BooleanFormula> extractAtoms(BooleanFormula f, boolean splitArithEqualities, boolean conjunctionsOnly) {
    return unwrapFormulasOfList(
        myExtractAtoms(f, splitArithEqualities, conjunctionsOnly, FormulaStructure.ATOM));
  }

  public Collection<BooleanFormula> extractLiterals(BooleanFormula f, boolean splitArithEqualities, boolean conjunctionsOnly) {
    return unwrapFormulasOfList(
        myExtractAtoms(f, splitArithEqualities, conjunctionsOnly, FormulaStructure.LITERAL));
  }

  private List<BooleanFormula> unwrapFormulasOfList(Collection<BooleanFormula> unwrapped) {
    List<BooleanFormula> atoms = new ArrayList<>(unwrapped.size());
    for (BooleanFormula booleanFormula : unwrapped) {
      atoms.add(booleanFormula);
    }
    return atoms;
  }

  private FormulaStructure getFormulaStructure(Formula f) {
    if (unsafeManager.isAtom(f)) {
      return FormulaStructure.ATOM;
    } else if (unsafeManager.isLiteral(f)) {
      return FormulaStructure.LITERAL;
    } else {
      return FormulaStructure.FORMULA;
    }
  }

  private Collection<BooleanFormula> myExtractAtoms(BooleanFormula f, boolean splitArithEqualities,
      boolean conjunctionsOnly, FormulaStructure breakdownTo) {
    Set<BooleanFormula> handled = new HashSet<>();
    List<BooleanFormula> atoms = new ArrayList<>();

    if (breakdownTo != FormulaStructure.ATOM
        && breakdownTo != FormulaStructure.LITERAL) {
      throw new UnsupportedOperationException("Formulas cannot be splitted onto the requested level!");
    }

    Deque<BooleanFormula> toProcess = new ArrayDeque<>();
    toProcess.push(f);
    handled.add(f);

    while (!toProcess.isEmpty()) {
      BooleanFormula tt = toProcess.pop();
      assert handled.contains(tt);

      if (booleanFormulaManager.isTrue(tt) || booleanFormulaManager.isFalse(tt)) {
        continue;
      }

      final FormulaStructure ttStructure = getFormulaStructure(tt);
      final boolean isSmallesConsidered = (ttStructure == breakdownTo)
          || ((breakdownTo == FormulaStructure.LITERAL)
              && (ttStructure == FormulaStructure.ATOM));

      if (isSmallesConsidered) {
        tt = myUninstantiate(tt);

        if (splitArithEqualities
            && myIsPurelyArithmetic(tt)) {
          BooleanFormula tt1 = null;
          if (rationalFormulaManager == null && getIntegerFormulaManager().isEqual(tt)) {
            // If solver does not support Rationals, rationalFormulaManager is Null.
            // Otherwise we assume, that rationalFormulaManager was used before (for creating formulas) and is initialized (!= Null).
            IntegerFormula a0 = unsafeManager.typeFormula(FormulaType.IntegerType, unsafeManager.getArg(tt, 0));
            IntegerFormula a1 = unsafeManager.typeFormula(FormulaType.IntegerType, unsafeManager.getArg(tt, 1));
            tt1 = getIntegerFormulaManager().lessOrEquals(a0, a1);
          } else if (rationalFormulaManager != null && rationalFormulaManager.isEqual(tt)) {
            RationalFormula a0 = unsafeManager.typeFormula(FormulaType.RationalType, unsafeManager.getArg(tt, 0));
            RationalFormula a1 = unsafeManager.typeFormula(FormulaType.RationalType, unsafeManager.getArg(tt, 1));
            tt1 = rationalFormulaManager.lessOrEquals(a0, a1);
          } else if (bitvectorFormulaManager.isEqual(tt)) {
            // NOTE: the type doesn't matter in the current implementations under this situation,
            // however if it does in the future we will have to add an (unsafe) api to read the bitlength (at least)
            FormulaType<BitvectorFormula> type = FormulaType.getBitvectorTypeWithSize(32);
            BitvectorFormula a0 = unsafeManager.typeFormula(type, unsafeManager.getArg(tt, 0));
            BitvectorFormula a1 = unsafeManager.typeFormula(type, unsafeManager.getArg(tt, 1));
            tt1 = bitvectorFormulaManager.lessOrEquals(a0, a1, true);
          }
          if (tt1 != null) {
            //SymbolicFormula tt2 = encapsulate(t2);
            handled.add(tt1);
            //cache.add(tt2);
            atoms.add(tt1);
            //atoms.add(tt2);
            atoms.add(tt);
          }
        } else {
          atoms.add(tt);
        }

      } else if (conjunctionsOnly
          && !(booleanFormulaManager.isNot(tt) || booleanFormulaManager.isAnd(tt))) {
        // conjunctions only, but formula is neither "not" nor "and"
        // treat this as atomic
        atoms.add(myUninstantiate(tt));

      } else {
        // ok, go into this formula
        for (int i = 0; i < unsafeManager.getArity(tt); ++i) {
          Formula c = unsafeManager.getArg(tt, i);
          assert getRawFormulaType(c).isBooleanType();
          if (handled.add((BooleanFormula)c)) {
            toProcess.push((BooleanFormula)c);
          }
        }
      }
    }

    return atoms;
  }

  public boolean isPurelyArithmetic(Formula f) {
    return myIsPurelyArithmetic(unwrap(f));
  }

  // returns true if the given term is a pure arithmetic term
  private boolean myIsPurelyArithmetic(Formula f) {
    Boolean result = arithCache.get(f);
    if (result != null) { return result; }

    boolean res = true;
    if (unsafeManager.isUF(f)) {
      res = false;

    } else {
      int arity = unsafeManager.getArity(f);
      for (int i = 0; i < arity; ++i) {
        res |= myIsPurelyArithmetic(unsafeManager.getArg(f, i));
        if (!res) {
          break;
        }
      }
    }
    arithCache.put(f, res);
    return res;
  }

  public Set<String> extractVariableNames(Formula f) {
    Set<String> result = Sets.newHashSet();

    for (Formula v: myExtractFreeVariables(unwrap(f))) {
      result.add(unsafeManager.getName(v));
    }

    return result;
  }

  public Set<Triple<Formula, String, Integer>> extractFreeVariables(Formula f) {
    Set<Triple<Formula, String, Integer>> result = Sets.newHashSet();

    for (Formula varFormula: myExtractFreeVariables(unwrap(f))) {
      Pair<String, Integer> var = parseName(unsafeManager.getName(varFormula));
      result.add(Triple.of(varFormula, var.getFirst(), var.getSecond()));
    }

    return result;
  }

  public Set<Formula> extractFreeVariableFormulas(Formula f) {
    return myExtractFreeVariables(unwrap(f));
  }

  private Set<Formula> myExtractFreeVariables(Formula pExtractFrom) {
    // TODO The FormulaType of returned formulas may not be correct,
    // because we cannot determine if for example a Rational formula
    // is really rational, or should be wrapped as a Bitvector formula
    Set<Formula> seen = new HashSet<>();
    Set<Formula> varFormulas = new HashSet<>();

    Deque<Formula> toProcess = new ArrayDeque<>();
    toProcess.push(pExtractFrom);

    while (!toProcess.isEmpty()) {
      Formula t = toProcess.pop();

      if (unsafeManager.isFreeVariable(t)) {
        varFormulas.add(t);

      } else if (unsafeManager.isBoundVariable(t)) {
        // Do nothing for variables that are bound by a quantifier!

      } else if (unsafeManager.isQuantification(t)) {
        Formula body = unsafeManager.getQuantifiedBody(t);
        if (seen.add(body)) {
          toProcess.push(body);
        }

      } else {
        // Go into this formula.
        for (int i = 0; i < unsafeManager.getArity(t); ++i) {
          Formula c = unsafeManager.getArg(t, i);

          if (seen.add(c)) {
            toProcess.push(c);
          }
        }
      }
    }

    return varFormulas;
  }

  public Appender dumpFormula(Formula pT) {
    return manager.dumpFormula(unwrap(pT));
  }

  public boolean isPurelyConjunctive(BooleanFormula t) {
    return myIsPurelyConjunctive(t);
  }

  private boolean myIsPurelyConjunctive(BooleanFormula t) {
    if (unsafeManager.isAtom(t) || unsafeManager.isUF(t)) {
      // term is atom
      return true;

    } else if (booleanFormulaManager.isNot(t)) {
      t = (BooleanFormula)unsafeManager.getArg(t, 0);
      return (unsafeManager.isUF(t) || unsafeManager.isAtom(t));

    } else if (booleanFormulaManager.isAnd(t)) {
      for (int i = 0; i < unsafeManager.getArity(t); ++i) {
        if (!myIsPurelyConjunctive((BooleanFormula)unsafeManager.getArg(t, i))) {
          return false;
        }
      }
      return true;

    } else {
      return false;
    }
  }

  public static final String BitwiseAndUfName = "_&_";
  public static final String BitwiseOrUfName ="_!!_"; // SMTInterpol does not allow "|" to be used
  public static final String BitwiseXorUfName ="_^_";
  public static final String BitwiseNotUfName ="_~_";

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
//        FunctionSymbol funcSym = ((ApplicationTerm) t).getFunction();
//        andFound = bitwiseAndUfDecl.equals(funcSym.getName());
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
        BitvectorFormula n = unsafeManager.typeFormula(type, nn);
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


  public String getVersion() {
    return manager.getVersion();
  }


  public boolean useBitwiseAxioms() {
    return useBitwiseAxioms;
  }

  public BooleanFormula createPredicateVariable(String pName) {
    return booleanFormulaManager.makeVariable(pName);
//    UnsafeFormulaManager unsafeManager = manager.getUnsafeFormulaManager();
//    BooleanFormulaManager rawBooleanManager = manager.getBooleanFormulaManager();
//
//    //long t = getTerm(atom);
//
//    String repr = unsafeManager.isAtom(atom)
//        ? unsafeManager.getTermRepr(atom)  : ("#" + unsafeManager.getTermId( atom));
//    return rawBooleanManager.makeVariable("\"PRED" + repr + "\"");
  }

  public BooleanFormula simplify(BooleanFormula input) {
    return unsafeManager.simplify(input);
  }

  /**
   * Adds prefix to all variables present in the formula.
   * TODO: refactor, combine with the previous substitution API.
   */
  public Formula addPrefixToAllVariables(Formula input, String prefix) {
    Formula formula = unwrap(input);
    Set<Triple<Formula, String, Integer>> allVars =
        extractFreeVariables(formula);
    FormulaType<Formula> t = getFormulaType(formula);

    List<Formula> from = new ArrayList<>(allVars.size());
    List<Formula> to = new ArrayList<>(allVars.size());
    for (Triple<Formula, String, Integer> e : allVars) {
      Formula token = e.getFirst();

      String oldName = unsafeManager.getName(token);
      from.add(token);
      to.add(makeVariable(t, prefix + oldName));
    }
    return unsafeManager.substitute(formula, from, to);

  }
}
