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
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FloatingPointFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FloatingPointFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FunctionFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.RationalFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.QuantifiedFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.UnsafeFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.replacing.ReplacingFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;

import com.google.common.collect.Lists;

/**
 * This class is the central entry point for all formula creation
 * and manipulation operations for client code.
 * It delegates to the actual solver package
 * and provides additional utilities.
 *
 *  This class and some of its related class have supporting operations
 *  for creating and manipulation formulas with SSA indices:
 *  - {@link #makeVariable(FormulaType, String, int)} creates a variable with an SSA index
 *  - {@link #instantiate(Formula, SSAMap)} adds SSA indices to variables in a formula
 *  - {@link #uninstantiate(Formula)} removes all SSA indices from a formula
 *
 *  The method {@link #parseName(String)} is also related to this, but should not be used!
 */
@Options(prefix="cpa.predicate")
public class FormulaManagerView {

  public interface LoadManagers {
    public BooleanFormulaManagerView wrapManager(BooleanFormulaManager manager, UnsafeFormulaManager unsafe);
    public NumeralFormulaManagerView<IntegerFormula, IntegerFormula> wrapIntegerManager(NumeralFormulaManager<IntegerFormula, IntegerFormula> manager);
    public NumeralFormulaManagerView<NumeralFormula, RationalFormula> wrapRationalManager(NumeralFormulaManager<NumeralFormula, RationalFormula> manager);
    public BitvectorFormulaManagerView wrapManager(BitvectorFormulaManager manager);
    public FloatingPointFormulaManagerView wrapManager(FloatingPointFormulaManager manager);
    public QuantifiedFormulaManagerView wrapManager(QuantifiedFormulaManager manager);
    public FunctionFormulaManagerView wrapManager(FunctionFormulaManager pManager);
  }

  private static LoadManagers DEFAULTMANAGERS =
      new LoadManagers() {
        @Override
        public BitvectorFormulaManagerView wrapManager(BitvectorFormulaManager pManager) {
          return new BitvectorFormulaManagerView(pManager);
        }

        @Override
        public FloatingPointFormulaManagerView wrapManager(FloatingPointFormulaManager pManager) {
          return new FloatingPointFormulaManagerView(pManager);
        }

        @Override
        public NumeralFormulaManagerView<IntegerFormula, IntegerFormula> wrapIntegerManager(NumeralFormulaManager<IntegerFormula, IntegerFormula> pManager) {
          return new NumeralFormulaManagerView<>(pManager);
        }

        @Override
        public NumeralFormulaManagerView<NumeralFormula, RationalFormula> wrapRationalManager(NumeralFormulaManager<NumeralFormula, RationalFormula> pManager) {
          return new NumeralFormulaManagerView<>(pManager);
        }

        @Override
        public BooleanFormulaManagerView wrapManager(BooleanFormulaManager pManager, UnsafeFormulaManager pUnsafe) {
          return new BooleanFormulaManagerView(pManager, pUnsafe);
        }

        @Override
        public FunctionFormulaManagerView wrapManager(FunctionFormulaManager pManager) {
          return new FunctionFormulaManagerView(pManager);
        }

        @Override
        public QuantifiedFormulaManagerView wrapManager(QuantifiedFormulaManager pManager) {
          return new QuantifiedFormulaManagerView(pManager);
        }
      };

  public static enum Theory {
    INTEGER,
    RATIONAL,
    BITVECTOR,
    ;
  }

  private final LogManager logger;

  private final FormulaManager manager;
  private final LoadManagers loadManagers;

  private final BooleanFormulaManagerView booleanFormulaManager;
  private final BitvectorFormulaManagerView bitvectorFormulaManager;
  private final FloatingPointFormulaManagerView floatingPointFormulaManager;
  private final NumeralFormulaManagerView<IntegerFormula, IntegerFormula> integerFormulaManager;
  private NumeralFormulaManagerView<NumeralFormula, RationalFormula> rationalFormulaManager;
  private final FunctionFormulaManagerView functionFormulaManager;
  private final QuantifiedFormulaManagerView quantifiedFormulaManager;

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
  private Theory encodeBitvectorAs = Theory.RATIONAL;

  @Option(secure=true, description="Allows to ignore Concat and Extract Calls when Bitvector theory was replaced with Integer or Rational.")
  private boolean ignoreExtractConcat = true;

  protected FormulaManagerView(LoadManagers pLoadManagers, FormulaManager pBaseManager, Configuration config, LogManager pLogger) throws InvalidConfigurationException {
    config.inject(this, FormulaManagerView.class);
    loadManagers = pLoadManagers;
    if (encodeBitvectorAs != Theory.BITVECTOR) {
      manager = new ReplacingFormulaManager(pBaseManager, encodeBitvectorAs, ignoreExtractConcat);
    } else {
      manager = pBaseManager;
    }

    try {
      bitvectorFormulaManager = loadManagers.wrapManager(manager.getBitvectorFormulaManager());
      bitvectorFormulaManager.couple(this);
    } catch (UnsupportedOperationException e) {
      throw new InvalidConfigurationException("The chosen SMT solver does not support the theory of bitvectors, "
          + "please choose another SMT solver "
          + "or use the option cpa.predicate.encodeBitvectorAs "
          + "to approximate bitvectors with another theory.",
          e);
    }
    FloatingPointFormulaManagerView fpfmgr = null;
    try {
      fpfmgr = loadManagers.wrapManager(manager.getFloatingPointFormulaManager());
      fpfmgr.couple(this);
    } catch (UnsupportedOperationException e) {
      // optional theory
    }
    floatingPointFormulaManager = fpfmgr;
    integerFormulaManager = loadManagers.wrapIntegerManager(manager.getIntegerFormulaManager());
    integerFormulaManager.couple(this);
    booleanFormulaManager = loadManagers.wrapManager(manager.getBooleanFormulaManager(), manager.getUnsafeFormulaManager());
    booleanFormulaManager.couple(this);
    functionFormulaManager = loadManagers.wrapManager(manager.getFunctionFormulaManager());
    functionFormulaManager.couple(this);
    quantifiedFormulaManager = loadManagers.wrapManager(manager.getQuantifiedFormulaManager());
    quantifiedFormulaManager.couple(this);

    logger = pLogger;
  }

  public FormulaManagerView(FormulaManager wrapped, Configuration config, LogManager pLogger) throws InvalidConfigurationException {
    this(DEFAULTMANAGERS, wrapped, config, pLogger);
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
   * Make a variable of the given type.
   * @param formulaType
   * @param value
   * @return
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

  /**
   * Make a variable of the given type.
   * @param formulaType
   * @param value
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T extends Formula> T makeNumber(FormulaType<T> formulaType, String value) {
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
      rationalFormulaManager = checkNotNull(loadManagers.wrapRationalManager(manager.getRationalFormulaManager()));
      rationalFormulaManager.couple(this);
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

  public <T extends Formula> FormulaType<T> getFormulaType(T pFormula) {
    checkNotNull(pFormula);
    return manager.getFormulaType(pFormula);
  }

  @SuppressWarnings("unchecked")
  <T extends Formula> T wrapInView(T formula) {
    if (formula instanceof BooleanFormula) {
      return (T) booleanFormulaManager.wrapInView((BooleanFormula) formula);
    }
    if (formula instanceof IntegerFormula) {
      return (T) integerFormulaManager.wrapInView((IntegerFormula) formula);
    }
    if (formula instanceof RationalFormula) {
      return (T) getRationalFormulaManager().wrapInView((RationalFormula) formula);
    }
    if (formula instanceof BitvectorFormula) {
      return (T) bitvectorFormulaManager.wrapInView((BitvectorFormula) formula);
    }
    if (formula instanceof FloatingPointFormula) {
      return (T) floatingPointFormulaManager.wrapInView((FloatingPointFormula) formula);
    }
    throw new IllegalArgumentException("Unknown formula type " + formula);
  }

  public <T extends Formula> BooleanFormula assignment(T left, T right) {
    left = extractFromView(left);
    right = extractFromView(right);
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

  @SuppressWarnings("unchecked")
  <T extends Formula> T extractFromView(T formula) {
    if (formula instanceof BooleanFormula) {
      return (T) booleanFormulaManager.extractFromView((BooleanFormula) formula);
    }
    if (formula instanceof IntegerFormula) {
      return (T) integerFormulaManager.extractFromView((IntegerFormula) formula);
    }
    if (formula instanceof RationalFormula) {
      return (T) getRationalFormulaManager().extractFromView((RationalFormula) formula);
    }
    if (formula instanceof BitvectorFormula) {
      return (T) bitvectorFormulaManager.extractFromView((BitvectorFormula) formula);
    }
    if (formula instanceof FloatingPointFormula) {
      return (T) floatingPointFormulaManager.extractFromView((FloatingPointFormula) formula);
    }

    throw new IllegalArgumentException("Invalid class");
  }

  public BooleanFormula parse(String pS) throws IllegalArgumentException {
    return wrapInView(manager.parse(pS));
  }

  public <T extends Formula> T  instantiate(T fView, SSAMap ssa) {
    T f = extractFromView(fView);
    T endResult = myInstantiate(ssa, f);
    return wrapInView(endResult);
  }

  // the character for separating name and index of a value
  private static final String INDEX_SEPARATOR = "@";

  static String makeName(String name, int idx) {
    if (idx < 0) {
      return name;
    }
    return name + INDEX_SEPARATOR + idx;
  }

  private <T extends Formula> T myInstantiate(SSAMap ssa, T f) {
    UnsafeFormulaManager unsafeManager = manager.getUnsafeFormulaManager();
    Deque<Formula> toProcess = new ArrayDeque<>();
    Map<Formula, Formula> cache = new HashMap<>();

    toProcess.push(f);
    while (!toProcess.isEmpty()) {
      final Formula tt = toProcess.peek();
      if (cache.containsKey(tt)) {
        toProcess.pop();
        continue;
      }

      if (unsafeManager.isVariable(tt)) {
        toProcess.pop();
        String name = unsafeManager.getName(tt);
        int idx = ssa.getIndex(name);
        if (idx > 0) {
          // ok, the variable has an instance in the SSA, replace it
          Formula newt = unsafeManager.replaceName(tt, makeName(name, idx));
          cache.put(tt, newt);
        } else {
          // the variable is not used in the SSA, keep it as is
          cache.put(tt, tt);
        }

      } else {
        boolean childrenDone = true;
        int arity = unsafeManager.getArity(tt);
        List<Formula> newargs = Lists.newArrayListWithExpectedSize(arity);
        for (int i = 0; i < arity; ++i) {
          Formula c = unsafeManager.getArg(tt, i);
          Formula newC = cache.get(c);
          if (newC != null) {
            newargs.add(newC);
          } else {
            toProcess.push(c);
            childrenDone = false;
          }
        }

        if (childrenDone) {
          toProcess.pop();
          Formula newt;

          if (unsafeManager.isUF(tt)) {
            String name = unsafeManager.getName(tt);
            assert name != null;

            if (ufCanBeLvalue(name)) {
              final int idx = ssa.getIndex(name);
              if (idx > 0) {
                // ok, the variable has an instance in the SSA, replace it
                newt = unsafeManager.replaceArgsAndName(tt, makeName(name, idx), newargs);
              } else {
                newt = unsafeManager.replaceArgs(tt, newargs);
              }
            } else {
              newt = unsafeManager.replaceArgs(tt, newargs);
            }
          } else {
            newt = unsafeManager.replaceArgs(tt, newargs);
          }

          cache.put(tt, newt);
        }
      }
    }

    @SuppressWarnings("unchecked")
    T result = (T)cache.get(f);
    assert result != null;
    assert manager.getFormulaType(f).equals(manager.getFormulaType(result));
    return result;
  }

  private boolean ufCanBeLvalue(String name) {
    return name.startsWith("*");
  }

  public <T extends Formula> T uninstantiate(T pF) {
    return wrapInView(myUninstantiate(extractFromView(pF)));
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

    UnsafeFormulaManager unsafeManager = manager.getUnsafeFormulaManager();
    Map<Formula, Formula> cache = uninstantiateCache;
    Deque<Formula> toProcess = new ArrayDeque<>();

    toProcess.push(f);
    while (!toProcess.isEmpty()) {
      final Formula tt = toProcess.peek();
      if (cache.containsKey(tt)) {
        toProcess.pop();
        continue;
      }

      if (unsafeManager.isVariable(tt)) {
        String name = parseName(unsafeManager.getName(tt)).getFirst();

        Formula newt = unsafeManager.replaceName(tt, name);
        cache.put(tt, newt);

      } else {
        boolean childrenDone = true;
        int arity = unsafeManager.getArity(tt);
        List<Formula> newargs = Lists.newArrayListWithExpectedSize(arity);
        for (int i = 0; i < arity; ++i) {
          Formula c = unsafeManager.getArg(tt, i);
          Formula newC = cache.get(c);
          if (newC != null) {
            newargs.add(newC);
          } else {
            toProcess.push(c);
            childrenDone = false;
          }
        }

        if (childrenDone) {
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

          cache.put(tt, newt);
        }
      }
    }

    @SuppressWarnings("unchecked")
    T result = (T)cache.get(f);
    assert result != null;
    assert manager.getFormulaType(f).equals(manager.getFormulaType(result));
    return result;
  }

  public Collection<BooleanFormula> extractAtoms(BooleanFormula f, boolean splitArithEqualities, boolean conjunctionsOnly) {
    Collection<BooleanFormula> unwrapped = myExtractAtoms(extractFromView(f), splitArithEqualities, conjunctionsOnly);

    List<BooleanFormula> atoms = new ArrayList<>(unwrapped.size());
    for (BooleanFormula booleanFormula : unwrapped) {
      atoms.add(wrapInView(booleanFormula));
    }

    return atoms;
  }

  private Collection<BooleanFormula> myExtractAtoms(BooleanFormula f, boolean splitArithEqualities,
      boolean conjunctionsOnly) {
    BooleanFormulaManager rawBooleanManager = manager.getBooleanFormulaManager();
    BitvectorFormulaManager rawBitpreciseManager = manager.getBitvectorFormulaManager();
    UnsafeFormulaManager unsafeManager = manager.getUnsafeFormulaManager();

    Set<BooleanFormula> handled = new HashSet<>();
    List<BooleanFormula> atoms = new ArrayList<>();

    Deque<BooleanFormula> toProcess = new ArrayDeque<>();
    toProcess.push(f);
    handled.add(f);

    while (!toProcess.isEmpty()) {
      BooleanFormula tt = toProcess.pop();
      assert handled.contains(tt);

      if (rawBooleanManager.isTrue(tt) || rawBooleanManager.isFalse(tt)) {
        continue;
      }

      if (unsafeManager.isAtom(tt)) {
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
          } else if (rawBitpreciseManager.isEqual(tt)) {
            // NOTE: the type doesn't matter in the current implementations under this situation,
            // however if it does in the future we will have to add an (unsafe) api to read the bitlength (at least)
            FormulaType<BitvectorFormula> type = FormulaType.getBitvectorTypeWithSize(32);
            BitvectorFormula a0 = unsafeManager.typeFormula(type, unsafeManager.getArg(tt, 0));
            BitvectorFormula a1 = unsafeManager.typeFormula(type, unsafeManager.getArg(tt, 1));
            tt1 = rawBitpreciseManager.lessOrEquals(a0, a1, true);
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
          && !(rawBooleanManager.isNot(tt) || rawBooleanManager.isAnd(tt))) {
        // conjunctions only, but formula is neither "not" nor "and"
        // treat this as atomic
        atoms.add(myUninstantiate(tt));

      } else {
        // ok, go into this formula
        for (int i = 0; i < unsafeManager.getArity(tt); ++i) {
          Formula c = unsafeManager.getArg(tt, i);
          assert manager.getFormulaType(c).isBooleanType();
          if (handled.add((BooleanFormula)c)) {
            toProcess.push((BooleanFormula)c);
          }
        }
      }
    }

    return atoms;
  }

  public boolean isPurelyArithmetic(Formula f) {
    return myIsPurelyArithmetic(extractFromView(f));
  }

  // returns true if the given term is a pure arithmetic term
  private boolean myIsPurelyArithmetic(Formula f) {
    Boolean result = arithCache.get(f);
    if (result != null) { return result; }

    UnsafeFormulaManager unsafeManager = manager.getUnsafeFormulaManager();

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
    return myExtractVariables(extractFromView(f));
  }

  private Set<String> myExtractVariables(Formula f) {
    UnsafeFormulaManager unsafeManager = manager.getUnsafeFormulaManager();
    Set<Formula> seen = new HashSet<>();
    Set<String> vars = new HashSet<>();

    Deque<Formula> toProcess = new ArrayDeque<>();
    toProcess.push(f);

    while (!toProcess.isEmpty()) {
      Formula t = toProcess.pop();

//      if ( msat_term_is_true(msatEnv, t) || msat_term_is_false(msatEnv, t)) {
//        continue;
//      }

      if (unsafeManager.isVariable(t)) {
        vars.add(unsafeManager.getName(t));
      } else {
        // ok, go into this formula
        for (int i = 0; i < unsafeManager.getArity(t); ++i) {
          Formula c = unsafeManager.getArg(t, i);

          if (seen.add(c)) {
            toProcess.push(c);
          }
        }
      }
    }

    return vars;
  }

  public Appender dumpFormula(Formula pT) {
    return manager.dumpFormula(extractFromView(pT));
  }

  public boolean isPurelyConjunctive(BooleanFormula t) {
    return myIsPurelyConjunctive(extractFromView(t));
  }

  private boolean myIsPurelyConjunctive(BooleanFormula t) {
    UnsafeFormulaManager unsafeManager = manager.getUnsafeFormulaManager();

    BooleanFormulaManager rawBooleanManager = manager.getBooleanFormulaManager();

    if (unsafeManager.isAtom(t) || unsafeManager.isUF(t)) {
      // term is atom
      return true;

    } else if (rawBooleanManager.isNot(t)) {
      t = (BooleanFormula)unsafeManager.getArg(t, 0);
      return (unsafeManager.isUF(t) || unsafeManager.isAtom(t));

    } else if (rawBooleanManager.isAnd(t)) {
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
    UnsafeFormulaManager unsafeManager = manager.getUnsafeFormulaManager();
    BooleanFormulaManager rawBooleanManager = manager.getBooleanFormulaManager();

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

    BooleanFormula result = rawBooleanManager.makeBoolean(true);
    BitvectorFormulaManager bitMgr = manager.getBitvectorFormulaManager();
    if (andFound) {
      // Note: We can assume that we have no real bitvectors here, so size should be not important
      // If it ever should be we can just add an method to the unsafe-manager to read the size.
      BitvectorFormula z = bitMgr.makeBitvector(1, 0);
      FormulaType<BitvectorFormula> type = FormulaType.getBitvectorTypeWithSize(1);
      //Term z = env.numeral("0");
      for (Formula nn : allLiterals) {
        BitvectorFormula n = unsafeManager.typeFormula(type, nn);
        BitvectorFormula u1 = bitMgr.and(z, n);
        BitvectorFormula u2 = bitMgr.and(n, z);
        //Term u1 = env.term(bitwiseAndUfDecl, n, z);
        //Term u2 = env.term(bitwiseAndUfDecl, z, n);
        //Term e1;
        //e1 = env.term("=", u1, z);
        BooleanFormula e1 = bitMgr.equal(u1, z);
        //Term e2 = env.term("=", u2, z);
        BooleanFormula e2 = bitMgr.equal(u2, z);
        BooleanFormula a = booleanFormulaManager.and(e1, e2);
        //Term a = env.term("and", e1, e2);

        result = booleanFormulaManager.and(result, a); //env.term("and", result, a);
      }
    }
    return result;
  }

    // returns a formula with some "static learning" about some bitwise
    public BooleanFormula getBitwiseAxioms(Formula f) {
      return wrapInView(myGetBitwiseAxioms(extractFromView(f)));
    }


  public String getVersion() {
    return manager.getVersion();
  }


  public boolean useBitwiseAxioms() {
    return useBitwiseAxioms;
  }

  private BooleanFormula myCreatePredicateVariable(String pName) {
    return manager.getBooleanFormulaManager().makeVariable(pName);
//    UnsafeFormulaManager unsafeManager = manager.getUnsafeFormulaManager();
//    BooleanFormulaManager rawBooleanManager = manager.getBooleanFormulaManager();
//
//    //long t = getTerm(atom);
//
//    String repr = unsafeManager.isAtom(atom)
//        ? unsafeManager.getTermRepr(atom)  : ("#" + unsafeManager.getTermId( atom));
//    return rawBooleanManager.makeVariable("\"PRED" + repr + "\"");
  }

  public BooleanFormula createPredicateVariable(String pName) {
    return wrapInView(myCreatePredicateVariable(pName));
  }

  public BooleanFormula simplify(BooleanFormula input) {
    UnsafeFormulaManager unsafeManager = manager.getUnsafeFormulaManager();
    return unsafeManager.simplify(input);
  }
  }
