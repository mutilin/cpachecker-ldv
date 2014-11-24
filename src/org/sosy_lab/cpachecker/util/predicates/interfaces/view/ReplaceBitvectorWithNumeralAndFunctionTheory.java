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

import static org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType.getBitvectorTypeWithSize;
import static org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView.*;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType.BitvectorType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FunctionFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FunctionFormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormulaManager;

class ReplaceBitvectorWithNumeralAndFunctionTheory<T extends NumeralFormula>
  extends BaseManagerView implements BitvectorFormulaManager {

  private final NumeralFormulaManager<? super T, T> numericFormulaManager;
  private final FunctionFormulaManager functionManager;
  private final FunctionFormulaType<T> bitwiseAndUfDecl;
  private final FunctionFormulaType<T> bitwiseOrUfDecl;
  private final FunctionFormulaType<T> bitwiseXorUfDecl;
  private final FunctionFormulaType<T> bitwiseNotUfDecl;
  private final FunctionFormulaType<T> leftShiftUfDecl;
  private final FunctionFormulaType<T> rightShiftUfDecl;
  private final FormulaType<T> formulaType;
  private final boolean ignoreExtractConcat;

  public ReplaceBitvectorWithNumeralAndFunctionTheory(
      FormulaManagerView pViewManager,
      NumeralFormulaManager<? super T, T> rawNumericFormulaManager,
      FunctionFormulaManager rawFunctionManager,
      final boolean ignoreExtractConcat) {
    super(pViewManager);
    numericFormulaManager = rawNumericFormulaManager;
    this.ignoreExtractConcat = ignoreExtractConcat;
    this.functionManager = rawFunctionManager;

    formulaType = numericFormulaManager.getFormulaType();
    bitwiseAndUfDecl = createBinaryFunction(BitwiseAndUfName);
    bitwiseOrUfDecl = createBinaryFunction(BitwiseOrUfName);
    bitwiseXorUfDecl = createBinaryFunction(BitwiseXorUfName);
    bitwiseNotUfDecl = createUnaryFunction(BitwiseNotUfName);

    leftShiftUfDecl = createBinaryFunction("_<<_");
    rightShiftUfDecl = createBinaryFunction("_>>_");
  }

  @SuppressWarnings("unchecked")
  private T unwrap(BitvectorFormula pNumber) {
    return (T)super.unwrap(pNumber);
  }

  private FunctionFormulaType<T> createUnaryFunction(String name) {
    return functionManager.declareUninterpretedFunction(name, formulaType, formulaType);
  }

  private FunctionFormulaType<T> createBinaryFunction(String name) {
    return functionManager.declareUninterpretedFunction(name, formulaType, formulaType, formulaType);
  }

  private BitvectorFormula makeUf(FormulaType<BitvectorFormula> realreturn, FunctionFormulaType<T> decl, BitvectorFormula... t1) {
    List<Formula> args = unwrap(Arrays.<Formula>asList(t1));

    return wrap(realreturn, functionManager.callUninterpretedFunction(decl, args));
  }

  private final Map<Integer[], FunctionFormulaType<T>> extractMethods = new HashMap<>();

  private FunctionFormulaType<T> getExtractDecl(int pMsb, int pLsb) {
    Integer[] hasKey = new Integer[]{pMsb, pLsb};
    FunctionFormulaType<T> value = extractMethods.get(hasKey);
    if (value == null) {
      value = createUnaryFunction("_extract("+ pMsb + "," + pLsb + ")_");
      extractMethods.put(hasKey, value);
    }
    return value;
  }

  private Map<Integer[], FunctionFormulaType<T>> concatMethods = new HashMap<>();

  private FunctionFormulaType<T> getConcatDecl(int firstSize, int secoundSize) {
    Integer[] hasKey = new Integer[]{firstSize, secoundSize};
    FunctionFormulaType<T> value = concatMethods.get(hasKey);
    if (value == null) {
      value = createUnaryFunction("_concat("+ firstSize + "," + secoundSize + ")_");
      concatMethods.put(hasKey, value);
    }
    return value;
  }

  private Map<Integer, FunctionFormulaType<T>> extendSignedMethods = new HashMap<>();
  private Map<Integer, FunctionFormulaType<T>> extendUnsignedMethods = new HashMap<>();

  private FunctionFormulaType<T> getExtendDecl(int extensionBits, boolean pSigned) {
    Integer hasKey = Integer.valueOf(extensionBits);
    FunctionFormulaType<T> value;
    if (pSigned) {
      value = extendSignedMethods.get(hasKey);
      if (value == null) {
        value = createUnaryFunction("_extendSigned("+ extensionBits + ")_");
        extendSignedMethods.put(hasKey, value);
      }
    } else {
      value = extendUnsignedMethods.get(hasKey);
      if (value == null) {
        value = createUnaryFunction("_extendUnsigned("+ extensionBits + ")_");
        extendUnsignedMethods.put(hasKey, value);
      }
    }
    return value;
  }

  @Override
  public BitvectorFormula makeBitvector(int pLength, long pI) {
    T number = numericFormulaManager.makeNumber(pI);
    return wrap(getBitvectorTypeWithSize(pLength), number);
  }

  @Override
  public BitvectorFormula makeBitvector(int pLength, BigInteger pI) {
    T number = numericFormulaManager.makeNumber(pI);
    return wrap(getBitvectorTypeWithSize(pLength), number);
  }

  @Override
  public BitvectorFormula makeVariable(int pLength, String pVar) {
    return wrap(getBitvectorTypeWithSize(pLength), numericFormulaManager.makeVariable(pVar));
  }

  @Override
  public int getLength(BitvectorFormula pNumber) {
    return ((BitvectorType)getFormulaType(pNumber)).getSize();
  }

  @Override
  public BitvectorFormula negate(BitvectorFormula pNumber) {
    return wrap(getFormulaType(pNumber), numericFormulaManager.negate(unwrap(pNumber)));
  }

  @Override
  public BitvectorFormula add(BitvectorFormula pNumber1, BitvectorFormula pNumber2) {
    assert getLength(pNumber1) == getLength(pNumber2) : "Expect operators to have the same size";
    return wrap(getFormulaType(pNumber1), numericFormulaManager.add(unwrap(pNumber1), unwrap(pNumber2)));
  }

  @Override
  public BitvectorFormula subtract(BitvectorFormula pNumber1, BitvectorFormula pNumber2) {
    assert getLength(pNumber1) == getLength(pNumber2) : "Expect operators to have the same size";
    return wrap(getFormulaType(pNumber1), numericFormulaManager.subtract(unwrap(pNumber1), unwrap(pNumber2)));
  }

  @Override
  public BitvectorFormula divide(BitvectorFormula pNumber1, BitvectorFormula pNumber2, boolean pSigned) {
    assert getLength(pNumber1) == getLength(pNumber2) : "Expect operators to have the same size";
    return wrap(getFormulaType(pNumber1), numericFormulaManager.divide(unwrap(pNumber1), unwrap(pNumber2)));
  }

  @Override
  public BitvectorFormula modulo(BitvectorFormula pNumber1, BitvectorFormula pNumber2, boolean pSigned) {
    assert getLength(pNumber1) == getLength(pNumber2) : "Expect operators to have the same size";
    return wrap(getFormulaType(pNumber1), numericFormulaManager.modulo(unwrap(pNumber1), unwrap(pNumber2)));
  }

  @Override
  public BooleanFormula modularCongruence(BitvectorFormula pNumber1, BitvectorFormula pNumber2, long pModulo) {
    assert getLength(pNumber1) == getLength(pNumber2) : "Expect operators to have the same size";
    return numericFormulaManager.modularCongruence(unwrap(pNumber1), unwrap(pNumber2), pModulo);
  }

  @Override
  public BitvectorFormula multiply(BitvectorFormula pNumber1, BitvectorFormula pNumber2) {
    assert getLength(pNumber1) == getLength(pNumber2) : "Expect operators to have the same size";
    return wrap(getFormulaType(pNumber1), numericFormulaManager.multiply(unwrap(pNumber1), unwrap(pNumber2)));
  }

  @Override
  public BooleanFormula equal(BitvectorFormula pNumber1, BitvectorFormula pNumber2) {
    return numericFormulaManager.equal(unwrap(pNumber1), unwrap(pNumber2));
  }

  @Override
  public boolean isEqual(BooleanFormula pNumber) {
    return numericFormulaManager.isEqual(pNumber);
  }

  @Override
  public BooleanFormula greaterThan(BitvectorFormula pNumber1, BitvectorFormula pNumber2, boolean pSigned) {
    return numericFormulaManager.greaterThan(unwrap(pNumber1), unwrap(pNumber2));
  }

  @Override
  public BooleanFormula greaterOrEquals(BitvectorFormula pNumber1, BitvectorFormula pNumber2, boolean pSigned) {
    return numericFormulaManager.greaterOrEquals(unwrap(pNumber1), unwrap(pNumber2));
  }

  @Override
  public BooleanFormula lessThan(BitvectorFormula pNumber1, BitvectorFormula pNumber2, boolean pSigned) {
    return numericFormulaManager.lessThan(unwrap(pNumber1), unwrap(pNumber2));
  }

  @Override
  public BooleanFormula lessOrEquals(BitvectorFormula pNumber1, BitvectorFormula pNumber2, boolean pSigned) {
    return numericFormulaManager.lessOrEquals(unwrap(pNumber1), unwrap(pNumber2));
  }


  /**
   * Returns a term representing the (arithmetic if signed is true) right shift of number by toShift.
   */
  @Override
  public BitvectorFormula shiftRight(BitvectorFormula pNumber, BitvectorFormula pToShift, boolean signed) {
    assert getLength(pNumber) == getLength(pToShift) : "Expect operators to have the same size";
    return makeUf(getFormulaType(pNumber), rightShiftUfDecl, pNumber, pToShift);
  }

  @Override
  public BitvectorFormula shiftLeft(BitvectorFormula pNumber, BitvectorFormula pToShift) {
    assert getLength(pNumber) == getLength(pToShift) : "Expect operators to have the same size";
    return makeUf(getFormulaType(pNumber), leftShiftUfDecl, pNumber, pToShift);
  }

  @Override
  public BitvectorFormula concat(BitvectorFormula pFirst, BitvectorFormula pSecound) {
    int firstLength = getLength(pFirst);
    int secoundLength = getLength(pSecound);
    FormulaType<BitvectorFormula> returnType = getBitvectorTypeWithSize(firstLength + secoundLength);
    if (ignoreExtractConcat) {
      return wrap(returnType, unwrap(pSecound));
    }
    FunctionFormulaType<T> concatUfDecl = getConcatDecl(firstLength, secoundLength);
    return makeUf(returnType, concatUfDecl, pFirst, pSecound);
  }

  @Override
  public BitvectorFormula extract(BitvectorFormula pFirst, int pMsb, int pLsb) {
    FormulaType<BitvectorFormula> returnType = getBitvectorTypeWithSize(pMsb + 1 - pLsb);
    if (ignoreExtractConcat) {
      return wrap(returnType, unwrap(pFirst));
    }
    FunctionFormulaType<T> extractUfDecl = getExtractDecl(pMsb, pLsb);
    return makeUf(returnType, extractUfDecl, pFirst);
  }

  @Override
  public BitvectorFormula extend(BitvectorFormula pNumber, int pExtensionBits, boolean pSigned) {
    FormulaType<BitvectorFormula> returnType = getBitvectorTypeWithSize(getLength(pNumber) + pExtensionBits);
    if (ignoreExtractConcat) {
      return wrap(returnType, unwrap(pNumber));
    }
    FunctionFormulaType<T> extendUfDecl = getExtendDecl(pExtensionBits, pSigned);
    return makeUf(returnType, extendUfDecl, pNumber);
  }

  @Override
  public BitvectorFormula not(BitvectorFormula pBits) {
    return makeUf(getFormulaType(pBits), bitwiseNotUfDecl, pBits);
  }

  @Override
  public BitvectorFormula and(BitvectorFormula pBits1, BitvectorFormula pBits2) {
    assert getLength(pBits1) == getLength(pBits2) : "Expect operators to have the same size";
    return makeUf(getFormulaType(pBits1), bitwiseAndUfDecl, pBits1, pBits2);
  }

  @Override
  public BitvectorFormula or(BitvectorFormula pBits1, BitvectorFormula pBits2) {
    assert getLength(pBits1) == getLength(pBits2) : "Expect operators to have the same size";
    return makeUf(getFormulaType(pBits1), bitwiseOrUfDecl, pBits1, pBits2);
  }

  @Override
  public BitvectorFormula xor(BitvectorFormula pBits1, BitvectorFormula pBits2) {
    assert getLength(pBits1) == getLength(pBits2) : "Expect operators to have the same size";
    return makeUf(getFormulaType(pBits1), bitwiseXorUfDecl, pBits1, pBits2);
  }
}
