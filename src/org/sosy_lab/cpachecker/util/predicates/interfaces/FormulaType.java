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
package org.sosy_lab.cpachecker.util.predicates.interfaces;

import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.RationalFormula;

/**
 * Represents a type of a formula.
 * @param <T> the static type of the FormulaType.
 */
public abstract class FormulaType<T extends Formula> {

  private FormulaType() {}

  public boolean isBitvectorType() {
    return false;
  }

  public boolean isBooleanType() {
    return false;
  }

  public boolean isFloatingPointType() {
    return false;
  }

  public boolean isNumeralType() {
    return false;
  }

  public boolean isRationalType() {
    return false;
  }

  public boolean isIntegerType() {
    return false;
  }

  @Override
  public abstract String toString();

  public abstract static class NumeralType<T extends NumeralFormula> extends FormulaType<T> {

    @Override
    public final boolean isNumeralType() {
      return true;
    }
  }

  public static final FormulaType<RationalFormula> RationalType = new NumeralType<RationalFormula>() {

    @Override
    public boolean isRationalType() {
      return true;
    }

    @Override
    public String toString() {
      return "Rational";
    }
  };

  public static final FormulaType<IntegerFormula> IntegerType = new NumeralType<IntegerFormula>() {

    @Override
    public boolean isIntegerType() {
      return true;
    }

    @Override
    public String toString() {
      return "Integer";
    }
  };

  public static final FormulaType<BooleanFormula> BooleanType = new FormulaType<BooleanFormula>() {

    @Override
    public boolean isBooleanType() {
      return true;
    }

    @Override
    public String toString() {
      return "Boolean";
    }
  };

  public static BitvectorType getBitvectorTypeWithSize(int size) {
    return BitvectorType.getBitvectorType(size);
  }

  public static final class BitvectorType extends FormulaType<BitvectorFormula> {
    private final int size;

    private BitvectorType(int size) {
      this.size = (size);
    }
    private static Map<Integer, BitvectorType> table = new HashMap<>();
    /**
     * Gets the Raw Bitvector-Type with the given size.
     * @param size
     * @return
     */
    private static BitvectorType getBitvectorType(int size) {
      int hashValue = size;
      BitvectorType value = table.get(hashValue);
      if (value == null) {
        value = new BitvectorType(size);
        table.put(hashValue, value);
      }
      return value;
    }

    @Override
    public boolean isBitvectorType() {
      return true;
    }

    public int getSize() {
      return size;
    }

    public BitvectorType withSize(int size) {
      return getBitvectorType(size);
    }

    @Override
    public String toString() {
      return "Bitvector<" + getSize() + ">";
    }
  }

  public static FloatingPointType getFloatingPointType(int exponentSize, int mantissaSize) {
    return new FloatingPointType(exponentSize, mantissaSize);
  }

  private static final FloatingPointType SINGLE_PRECISION_FP_TYPE = new FloatingPointType(8, 23);
  private static final FloatingPointType DOUBLE_PRECISION_FP_TYPE = new FloatingPointType(11, 52);

  public static FloatingPointType getSinglePrecisionFloatingPointType() {
    return SINGLE_PRECISION_FP_TYPE;
  }

  public static FloatingPointType getDoublePrecisionFloatingPointType() {
    return DOUBLE_PRECISION_FP_TYPE;
  }

  public static final class FloatingPointType extends FormulaType<FloatingPointFormula> {

    private final int exponentSize;
    private final int mantissaSize;

    private FloatingPointType(int pExponentSize, int pMantissaSize) {
      exponentSize = pExponentSize;
      mantissaSize = pMantissaSize;
    }

    @Override
    public boolean isFloatingPointType() {
      return true;
    }

    public int getExponentSize() {
      return exponentSize;
    }

    public int getMantissaSize() {
      return mantissaSize;
    }

    @Override
    public int hashCode() {
      return (31 + exponentSize) * 31 + mantissaSize;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof FloatingPointType)) {
        return false;
      }
      FloatingPointType other = (FloatingPointType) obj;
      return this.exponentSize == other.exponentSize
          && this.mantissaSize == other.mantissaSize;
    }

    @Override
    public String toString() {
      return "FloatingPoint<exp=" + exponentSize + ",mant=" + mantissaSize + ">";
    }
  }
}
