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
package org.sosy_lab.cpachecker.cpa.octagon;

import java.math.BigInteger;
import java.util.Arrays;

import org.sosy_lab.common.Pair;

import com.google.common.base.Preconditions;


public class OctIntervalCoefficients extends AOctCoefficients {

  /**
   * Create new Coefficients for #size variables.
   *
   * @param size The amount of variables for which coefficients should be stored
   */
  public OctIntervalCoefficients(int size) {
    super(size);
    coefficients = new BigInteger[(size+1)*2];
    isInfite = new boolean[(size+1)*2];
    Arrays.fill(coefficients, BigInteger.ZERO);
    Arrays.fill(isInfite, false);
  }

  /**
   * Create new Coefficients for #size variables.
   *
   * @param size The amount of variables for which coefficients should be stored
   * @param index The index of the variable which should be set by default to a given value
   * @param value The value to which the variable should be set
   */
  public OctIntervalCoefficients(int size, int index, long lowerBound, long upperBound) {
    super(size);
    Preconditions.checkArgument(index < size, "Index too big");
    coefficients = new BigInteger[(size+1)*2];
    isInfite = new boolean[(size+1)*2];
    Arrays.fill(coefficients, BigInteger.ZERO);
    Arrays.fill(isInfite, false);
    coefficients[index] = BigInteger.valueOf(lowerBound);
    coefficients[index+1] = BigInteger.valueOf(upperBound);
  }


  /**
   * Create new Coefficients for #size variables. With a constant value.
   *
   * @param size The amount of variables for which coefficients should be stored
   * @param index The index of the variable which should be set by default to a given value
   * @param value The value to which the variable should be set
   */
  public OctIntervalCoefficients(int size, long lowerBound, long upperBound) {
    super(size);
    coefficients = new BigInteger[(size+1)*2];
    isInfite = new boolean[(size+1)*2];
    Arrays.fill(coefficients, BigInteger.ZERO);
    Arrays.fill(isInfite, false);
    coefficients[size*2] = BigInteger.valueOf(lowerBound);
    coefficients[size*2 +1] = BigInteger.valueOf(upperBound);
  }

  @Override
  public OctIntervalCoefficients fitToSize(int size) {
    Preconditions.checkArgument(hasOnlyConstantValue(), "changing size is only possible when there are no values besides constant ones");
    OctIntervalCoefficients oct = new OctIntervalCoefficients(size);
    oct.coefficients[oct.coefficients.length-1] = coefficients[coefficients.length-1];
    oct.coefficients[oct.coefficients.length-2] = coefficients[coefficients.length-2];
    oct.isInfite[oct.coefficients.length-1] = isInfite[coefficients.length-1];
    oct.isInfite[oct.coefficients.length-2] = isInfite[coefficients.length-2];
    return oct;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IOctCoefficients add(IOctCoefficients other) {
    Preconditions.checkArgument(other.size() == size, "Different size of coefficients.");
    if (other instanceof OctSimpleCoefficients) {
      return add((OctSimpleCoefficients)other);
    } else if (other instanceof OctIntervalCoefficients) {
      return add((OctIntervalCoefficients)other);
    }
    throw new IllegalArgumentException("Unkown subtype of OctCoefficients");
  }

  private IOctCoefficients add(OctSimpleCoefficients other) {
    OctIntervalCoefficients ret = new OctIntervalCoefficients(size);
    for (int i = 0; i < other.size; i++) {
      ret.coefficients[i*2] = coefficients[i*2].add(other.coefficients[i]);
      ret.coefficients[(i*2)+2] = coefficients[(i*2)+1].add(other.coefficients[i]);
    }
    return ret;
  }

  private IOctCoefficients add(OctIntervalCoefficients other) {
    OctIntervalCoefficients ret = new OctIntervalCoefficients(size);
    for (int i = 0; i < coefficients.length; i++) {
      ret.coefficients[i] = coefficients[i].add(other.coefficients[i]);
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IOctCoefficients sub(IOctCoefficients other) {
    Preconditions.checkArgument(other.size() == size, "Different size of coefficients.");
    if (other instanceof OctSimpleCoefficients) {
      return sub((OctSimpleCoefficients)other);
    } else if (other instanceof OctIntervalCoefficients) {
      return null;
    }
    throw new IllegalArgumentException("Unkown subtype of OctCoefficients");
  }

  private IOctCoefficients sub(OctSimpleCoefficients other) {
    OctIntervalCoefficients ret = new OctIntervalCoefficients(size);
    for (int i = 0; i < other.size; i++) {
      ret.coefficients[i*2] = coefficients[i*2].subtract(other.coefficients[i]);
      ret.coefficients[(i*2)+2] = coefficients[(i*2)+1].subtract(other.coefficients[i]);
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IOctCoefficients mult(long coeff) {
    OctIntervalCoefficients ret = new OctIntervalCoefficients(size);
    for (int i = 0; i < coefficients.length; i++) {
      ret.coefficients[i] = coefficients[i].multiply(BigInteger.valueOf(coeff));
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IOctCoefficients div(long coeff) {
    if (coeff == 0) {
      return null;
    }

    OctIntervalCoefficients ret = new OctIntervalCoefficients(size);
    for (int i = 0; i < coefficients.length; i++) {
      ret.coefficients[i] = coefficients[i].divide(BigInteger.valueOf(coeff));
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IOctCoefficients mult(IOctCoefficients oct) {
    Preconditions.checkArgument(oct.size() == size, "Different size of coefficients.");
    if (oct instanceof OctSimpleCoefficients && oct.hasOnlyConstantValue()) {
      OctSimpleCoefficients tmp = (OctSimpleCoefficients) oct;
      return mult(tmp.coefficients[tmp.coefficients.length-1].longValue());
    }
    // TODO more possible?
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IOctCoefficients div(IOctCoefficients oct) {
    Preconditions.checkArgument(oct.size() == size, "Different size of coefficients.");
    if (oct instanceof OctSimpleCoefficients && oct.hasOnlyConstantValue() && hasOnlyConstantValue()) {
      OctSimpleCoefficients tmp = (OctSimpleCoefficients) oct;
      return div(tmp.coefficients[tmp.coefficients.length-1].longValue());
    }
    return null;
  }

  @Override
  public IOctCoefficients binAnd(IOctCoefficients oct) {
    return null;
  }

  @Override
  public IOctCoefficients binOr(IOctCoefficients oct) {
    return null;
  }

  @Override
  public IOctCoefficients binXOr(IOctCoefficients oct) {
    return null;
  }

  @Override
  public IOctCoefficients shiftLeft(IOctCoefficients oct) {
    return null;
  }

  @Override
  public IOctCoefficients shiftRight(IOctCoefficients oct) {
    return null;
  }

  @Override
  public IOctCoefficients modulo(IOctCoefficients oct) {
    return null;
  }

  @Override
  public OctSimpleCoefficients not() {
    Preconditions.checkArgument(hasOnlyConstantValue());
    if (coefficients[coefficients.length-2].compareTo(BigInteger.ZERO) < 0
        || coefficients[coefficients.length-1].compareTo(BigInteger.ZERO) > 0) {
      return new OctSimpleCoefficients(size);
    } else if (coefficients[coefficients.length-2].compareTo(BigInteger.ZERO) == 0
        && coefficients[coefficients.length-1].compareTo(BigInteger.ZERO) == 0
        && !isInfite[coefficients.length-2] && !isInfite[coefficients.length-1]){
      return new OctSimpleCoefficients(size, 1);
    } else {
      return null;
    }
  }

  @Override
  public OctSimpleCoefficients greaterEq(IOctCoefficients oct) {
    Preconditions.checkArgument(oct.size() == size, "Different size of coefficients.");
    if (!(hasOnlyConstantValue() && oct.hasOnlyConstantValue())) {
      return null;
    }
    if (oct instanceof OctSimpleCoefficients) {
      BigInteger leftVal = coefficients[coefficients.length-1];
      BigInteger rightVal = ((OctSimpleCoefficients)oct).coefficients[oct.size()];

      if (!isInfite[coefficients.length-1] && leftVal.compareTo(rightVal) >= 0) {
        return new OctSimpleCoefficients(size, 1);
      } else {
        return new OctSimpleCoefficients(size);
      }
    } else if (oct instanceof OctIntervalCoefficients) {
      BigInteger leftVal = coefficients[coefficients.length-1];
      BigInteger rightVal = ((OctIntervalCoefficients)oct).coefficients[coefficients.length-2];

      if (!((OctIntervalCoefficients)oct).isInfite[coefficients.length-2] && !isInfite[coefficients.length -1] && leftVal.compareTo(rightVal) >= 0) {
        return new OctSimpleCoefficients(size, 1);
      } else {
        return new OctSimpleCoefficients(size);
      }
    }
    throw new IllegalArgumentException("Unkown subtype of OctCoefficient.");
  }

  @Override
  public OctSimpleCoefficients greater(IOctCoefficients oct) {
    Preconditions.checkArgument(oct.size() == size, "Different size of coefficients.");
    if (!(hasOnlyConstantValue() && oct.hasOnlyConstantValue())) {
      return null;
    }
    if (oct instanceof OctSimpleCoefficients) {
      BigInteger leftVal = coefficients[coefficients.length-1];
      BigInteger rightVal = ((OctSimpleCoefficients)oct).coefficients[oct.size()];

      if (!isInfite[coefficients.length-1] && leftVal.compareTo(rightVal) > 0) {
        return new OctSimpleCoefficients(size, 1);
      } else {
        return new OctSimpleCoefficients(size);
      }
    } else if (oct instanceof OctIntervalCoefficients) {
      BigInteger leftVal = coefficients[coefficients.length-1];
      BigInteger rightVal = ((OctIntervalCoefficients)oct).coefficients[coefficients.length-2];

      if (!((OctIntervalCoefficients)oct).isInfite[coefficients.length-2] && !isInfite[coefficients.length -1] && leftVal.compareTo(rightVal) > 0) {
        return new OctSimpleCoefficients(size, 1);
      } else {
        return new OctSimpleCoefficients(size);
      }
    }
    throw new IllegalArgumentException("Unkown subtype of OctCoefficient.");
  }

  @Override
  public OctSimpleCoefficients smallerEq(IOctCoefficients oct) {
    Preconditions.checkArgument(oct.size() == size, "Different size of coefficients.");
    if (!(hasOnlyConstantValue() && oct.hasOnlyConstantValue())) {
      return null;
    }
    if (oct instanceof OctSimpleCoefficients) {
      BigInteger leftVal = coefficients[coefficients.length-2];
      BigInteger rightVal = ((OctSimpleCoefficients)oct).coefficients[oct.size()];

      if (!isInfite[coefficients.length-2] && leftVal.compareTo(rightVal) <= 0) {
        return new OctSimpleCoefficients(size, 1);
      } else {
        return new OctSimpleCoefficients(size);
      }
    } else if (oct instanceof OctIntervalCoefficients) {
      BigInteger leftVal = coefficients[coefficients.length-2];
      BigInteger rightVal = ((OctIntervalCoefficients)oct).coefficients[coefficients.length-1];

      if (!((OctIntervalCoefficients)oct).isInfite[coefficients.length-1] && !isInfite[coefficients.length -2] && leftVal.compareTo(rightVal) <= 0) {
        return new OctSimpleCoefficients(size, 1);
      } else {
        return new OctSimpleCoefficients(size);
      }
    }
    throw new IllegalArgumentException("Unkown subtype of OctCoefficient.");
  }

  @Override
  public OctSimpleCoefficients smaller(IOctCoefficients oct) {
    Preconditions.checkArgument(oct.size() == size, "Different size of coefficients.");
    if (!(hasOnlyConstantValue() && oct.hasOnlyConstantValue())) {
      return null;
    }
    if (oct instanceof OctSimpleCoefficients) {
      BigInteger leftVal = coefficients[coefficients.length-2];
      BigInteger rightVal = ((OctSimpleCoefficients)oct).coefficients[oct.size()];

      if (!isInfite[coefficients.length-2] && leftVal.compareTo(rightVal) < 0) {
        return new OctSimpleCoefficients(size, 1);
      } else {
        return new OctSimpleCoefficients(size);
      }
    } else if (oct instanceof OctIntervalCoefficients) {
      BigInteger leftVal = coefficients[coefficients.length-1];
      BigInteger rightVal = ((OctIntervalCoefficients)oct).coefficients[coefficients.length-2];

      if (!((OctIntervalCoefficients)oct).isInfite[coefficients.length-1] && !isInfite[coefficients.length -2] && leftVal.compareTo(rightVal) < 0) {
        return new OctSimpleCoefficients(size, 1);
      } else {
        return new OctSimpleCoefficients(size);
      }
    }
    throw new IllegalArgumentException("Unkown subtype of OctCoefficient.");
  }

  @Override
  public OctSimpleCoefficients eq(IOctCoefficients oct) {
    Preconditions.checkArgument(oct.size() == size, "Different size of coefficients.");
    if (!(hasOnlyConstantValue() && oct.hasOnlyConstantValue())) {
      return null;
    }
    if (oct instanceof OctSimpleCoefficients) {
      int val = 1;
      for (int i = 0; i <= size && val == 1 ; i++) {
        BigInteger val1 = coefficients[(size*2)];
        BigInteger val2 = coefficients[(size*2)+1];
        BigInteger val3 = ((OctSimpleCoefficients)oct).coefficients[size];
        val = val1.compareTo(val3) == 0 && val2.compareTo(val3) == 0 ? 1 : 0;
        if (val == 1) {
          val = ((isInfite[size*2] && isInfite[(size*2)+1]) == ((OctSimpleCoefficients)oct).isInfite[size]) ? 1 : 0;
        }
      }
      return new OctSimpleCoefficients(size, val);

    } else if (oct instanceof OctIntervalCoefficients) {
      int val = 1;
      for (int i = 0; i < coefficients.length && val == 1; i++) {
        val = coefficients[i].compareTo(((OctIntervalCoefficients)oct).coefficients[i]);
        if (val == 1) {
          val = isInfite[i] && ((OctIntervalCoefficients)oct).isInfite[i] ? 1 : 0;
        }
      }
      return new OctSimpleCoefficients(size, val);
    }
    throw new IllegalArgumentException("Unkown subtype of OctCoefficient.");
  }

  @Override
  public OctSimpleCoefficients ineq(IOctCoefficients oct) {
    Preconditions.checkArgument(oct.size() == size, "Different size of coefficients.");
    if (!(hasOnlyConstantValue() && oct.hasOnlyConstantValue())) {
      return null;
    }
    if (oct instanceof OctSimpleCoefficients) {
      BigInteger lowerBound = coefficients[coefficients.length-1];
      BigInteger upperBound = coefficients[coefficients.length-2];
      BigInteger val3 = ((OctSimpleCoefficients)oct).coefficients[size];

      if ((!isInfite[coefficients.length-1] && (lowerBound.compareTo(val3)) > 0) || (!isInfite[coefficients.length-2] && upperBound.compareTo(val3) < 0)) {
        return new OctSimpleCoefficients(size, 1);
      } else {
        return new OctSimpleCoefficients(size);
      }
    } else if (oct instanceof OctIntervalCoefficients) {
      BigInteger lowerBound1 = coefficients[coefficients.length-1];
      BigInteger upperBound1 = coefficients[coefficients.length-2];
      BigInteger lowerBound2 = ((OctIntervalCoefficients)oct).coefficients[coefficients.length-1];
      BigInteger upperBound2 = ((OctIntervalCoefficients)oct).coefficients[coefficients.length-2];

      if ((lowerBound1.compareTo(upperBound2) > 0 && !isInfite[coefficients.length-1] && !((OctIntervalCoefficients)oct).isInfite[coefficients.length-2])
          || (upperBound1.compareTo(lowerBound2) < 0 && !isInfite[coefficients.length-2] && !((OctIntervalCoefficients)oct).isInfite[coefficients.length-1])) {
        return new OctSimpleCoefficients(size, 1);
      } else {
        return new OctSimpleCoefficients(size);
      }
    }
    throw new IllegalArgumentException("Unkown subtype of OctCoefficient.");
  }

  public OctIntervalCoefficients greaterPart(IOctCoefficients oct) {
    Preconditions.checkArgument(oct.size() == size, "Different size of coefficients.");
    if (!(hasOnlyConstantValue() && oct.hasOnlyConstantValue())) {
      return null;
    }

    OctSimpleCoefficients test = greater(oct);
    if (test != null && test.getConstantValue().equals(BigInteger.ONE)) {
      return this;
    }

    if (oct instanceof OctSimpleCoefficients) {
      BigInteger otherConstVal = ((OctSimpleCoefficients) oct).getConstantValue();
      if (isInfite[coefficients.length -1] || coefficients[coefficients.length-1].compareTo(otherConstVal) <= 0) {
        OctIntervalCoefficients ret = new OctIntervalCoefficients(size);
        ret.coefficients[coefficients.length-1] = coefficients[coefficients.length-1];
        ret.isInfite[coefficients.length-1] = isInfite[coefficients.length-1];
        ret.coefficients[coefficients.length-2] = otherConstVal.add(BigInteger.ONE);
        return ret;
      }
    } else if (oct instanceof OctIntervalCoefficients) {
      // TODO
    }
    return null;
  }

  public OctIntervalCoefficients smallerPart(IOctCoefficients oct) {
    Preconditions.checkArgument(oct.size() == size, "Different size of coefficients.");
    if (!(hasOnlyConstantValue() && oct.hasOnlyConstantValue())) {
      return null;
    }

    OctSimpleCoefficients test = smaller(oct);
    if (test != null && test.getConstantValue().equals(BigInteger.ONE)) {
      return this;
    }
    test = greaterEq(oct);
    if (test != null && test.getConstantValue().equals(BigInteger.ONE)) {
      return null;
    }

    if (oct instanceof OctSimpleCoefficients) {
      BigInteger otherConstVal = ((OctSimpleCoefficients) oct).getConstantValue();
      if (coefficients[coefficients.length-2].compareTo(otherConstVal) >= 0) {
        OctIntervalCoefficients ret = new OctIntervalCoefficients(size);
        ret.coefficients[coefficients.length-1] = coefficients[coefficients.length-1];
        ret.isInfite[coefficients.length-1] = isInfite[coefficients.length-1];
        ret.coefficients[coefficients.length-2] = otherConstVal.subtract(BigInteger.ONE);
        return ret;
      }
    } else if (oct instanceof OctIntervalCoefficients) {
      // TODO
    }
    return null;
  }

  /**
   * Returns the coefficients (lowerBount, upperBound) at the given index.
   */
  public Pair<Pair<BigInteger, Boolean>, Pair<BigInteger, Boolean>> get(int index) {
    Preconditions.checkArgument(index < size, "Index too big");
    return Pair.of(Pair.of(coefficients[index+1], isInfite[index+1]), Pair.of(coefficients[index], isInfite[index]));
  }

  public Pair<Pair<BigInteger, Boolean>, Pair<BigInteger, Boolean>> getConstantValue() {
    return Pair.of(Pair.of(coefficients[coefficients.length-1], isInfite[coefficients.length-1]), Pair.of(coefficients[coefficients.length-2], isInfite[coefficients.length-2]));
  }

  /**
   * Sets the coefficient to a given value at a certain index.
   */
  public void set(int index, long lowerBound, long upperBound) {
    coefficients[index] = BigInteger.valueOf(upperBound);
    coefficients[index + 1] = BigInteger.valueOf(lowerBound);
    isInfite[index] = false;
    isInfite[index + 1] = false;
  }

  public void setConstantValue(long lowerBound, long upperBound) {
    coefficients[coefficients.length-2] = BigInteger.valueOf(upperBound);
    isInfite[isInfite.length-2] = false;
    coefficients[coefficients.length-1] = BigInteger.valueOf(lowerBound);
    isInfite[isInfite.length-1] = false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasOnlyConstantValue() {
    for (int i = 0; i < coefficients.length - 2; i++) {
      if (!coefficients[i].equals(BigInteger.ZERO)) { return false; }
    }
    return true;
  }

  public static IOctCoefficients getUIntCoeffs(int size) {
    OctIntervalCoefficients result = new OctIntervalCoefficients(size);
    result.coefficients[result.coefficients.length-2] = BigInteger.ZERO;
    result.isInfite[result.coefficients.length-2] = true;
    result.coefficients[result.coefficients.length-1] = BigInteger.ZERO;
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < coefficients.length - 1; i += 2) {
      String a, b;
      if (isInfite[i+1]) {
        a = "INFINITY";
      } else {
        a = coefficients[i+1].toString();
      }
      if (isInfite[i]) {
        b = "INFINITY";
      } else {
        b = coefficients[i].toString();
      }
      builder.append((i+1)/2 + " -> [" + a + ", " + b + "]\n");
    }
    return builder.toString();
  }
}