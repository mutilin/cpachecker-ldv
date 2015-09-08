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
package org.sosy_lab.solver.mathsat5;

import static org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sosy_lab.solver.AssignableTerm.Variable;
import org.sosy_lab.solver.AssignableTerm;
import org.sosy_lab.solver.AssignableTerm.Function;
import org.sosy_lab.solver.Model;
import org.sosy_lab.solver.TermType;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.mathsat5.Mathsat5NativeApi.ModelIterator;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

class Mathsat5Model {

  // A model can contain arbitrary real numbers, such as 1/3.
  // Java does not provide a representation of such numbers with arbitrary precision,
  // thus we use BigDecimal (which can handle at least all rational numbers)
  // and round real numbers.
  private static final MathContext ROUNDING_PRECISION = MathContext.DECIMAL128;

  private static TermType toMathsatType(long e, long mType) {


    if (msat_is_bool_type(e, mType)) {
      return TermType.Boolean;
    } else if (msat_is_integer_type(e, mType)) {
      return TermType.Integer;
    } else if (msat_is_rational_type(e, mType)) {
      return TermType.Real;
    } else if (msat_is_bv_type(e, mType)) {
      return TermType.Bitvector; // all other values are bitvectors of different sizes
    } else if (msat_is_fp_type(e, mType)) {
      return TermType.FloatingPoint;
    } else if (msat_is_array_type(e, mType)) {
      return TermType.Array;
    } else {
      throw new IllegalArgumentException("Given parameter is not a mathsat type!");
    }
  }

  private static AssignableTerm toConstant(final long env, final long variableId) {
    if (!msat_term_is_constant(env, variableId)) {
      throw new IllegalArgumentException("Given mathsat id doesn't correspond to a constant! (" +
                                         msat_term_repr(variableId) + ")");
    }

    final long declarationId = msat_term_get_decl(variableId);
    final String name = msat_decl_get_name(declarationId);
    final TermType type = toMathsatType(env, msat_decl_get_return_type(declarationId));
    return new Variable(name, type);
  }


  private static Function toFunction(long env, long pFunctionId) {
    if (msat_term_is_constant(env, pFunctionId)) {
      throw new IllegalArgumentException("Given mathsat id is a variable! (" + msat_term_repr(pFunctionId) + ")");
    }

    long lDeclarationId = msat_term_get_decl(pFunctionId);
    String lName = msat_decl_get_name(lDeclarationId);
    TermType lType = toMathsatType(env, msat_decl_get_return_type(lDeclarationId));

    int lArity = msat_decl_get_arity(lDeclarationId);

    // TODO we assume only constants (reals) as parameters for now
    Object[] lArguments = new Object[lArity];

    for (int lArgumentIndex = 0; lArgumentIndex < lArity; lArgumentIndex++) {
      long lArgument = msat_term_get_arg(pFunctionId, lArgumentIndex);
      String lTermRepresentation = msat_term_repr(lArgument);

      Object lValue;
      long msatType = msat_term_get_type(lArgument);
      if (msat_is_integer_type(env, msatType)) {
        lValue = new BigInteger(lTermRepresentation);
      } else if (msat_is_rational_type(env, msatType)) {
        lValue = parseReal(lTermRepresentation);
      } else if (msat_is_bv_type(env, msatType)) {
        lValue = interpreteBitvector(lTermRepresentation);
      } else if (msat_is_fp_type(env, msatType)) {
        lValue = interpreteFloatingPoint(lTermRepresentation);
      } else {
        throw new NumberFormatException("Unknown number format: " + lTermRepresentation);
      }

      lArguments[lArgumentIndex] = lValue;
    }

    return new Function(lName, lType, lArguments);
  }


  private static AssignableTerm toAssignable(long env, long pTermId) {
    if (!msat_term_is_constant(env, pTermId)) {
      return toFunction(env, pTermId);
    } else {
      return toConstant(env, pTermId);
    }
  }

  static Model createMathsatModel(final long sourceEnvironment) throws SolverException {
    ImmutableMap.Builder<AssignableTerm, Object> model = ImmutableMap.builder();

    ModelIterator lModelIterator;
    try {
      lModelIterator = msat_create_ModelIterator(sourceEnvironment);
    } catch (IllegalArgumentException e) {
      // creating the iterator may fail,
      // for example if some theories were disabled in the solver but are needed
      throw new SolverException("Model iterator could not be created", e);
    }

    while (lModelIterator.hasNext()) {
      long[] lModelElement = lModelIterator.next();

      long lKeyTerm = lModelElement[0];
      long lValueTerm = lModelElement[1];

      String tmp = msat_term_repr(lValueTerm);
      if (msat_is_array_type(sourceEnvironment, lValueTerm)
          || msat_term_is_array_const(sourceEnvironment, lValueTerm)
          || msat_term_is_array_read(sourceEnvironment, lValueTerm)
          || msat_term_is_array_write(sourceEnvironment, lValueTerm)) {
        // TODO Implement the parsing for array terms
        continue;
      }

      AssignableTerm lAssignable = toAssignable(sourceEnvironment, lKeyTerm);

      // TODO maybe we have to convert to SMTLIB format and then read in values in a controlled way, e.g., size of bitvector
      // TODO we are assuming numbers as values
      if (!(msat_term_is_number(sourceEnvironment, lValueTerm)
            || msat_term_is_boolean_constant(sourceEnvironment, lValueTerm) || msat_term_is_false(sourceEnvironment, lValueTerm) || msat_term_is_true(sourceEnvironment, lValueTerm))) {
        throw new IllegalArgumentException("Mathsat term is not a number!");
      }

      String lTermRepresentation = msat_term_repr(lValueTerm);

      Object lValue;

      switch (lAssignable.getType()) {
      case Boolean:
        if (lTermRepresentation.equals("`true`")) {
          lValue = true;
        } else if (lTermRepresentation.equals("`false`")) {
          lValue = false;
        } else {
          throw new IllegalArgumentException("Mathsat unhandled boolean value " + lTermRepresentation);
        }
        break;
      case Real:
        lValue = parseReal(lTermRepresentation);
        break;

      case Integer:
        lValue = new BigInteger(lTermRepresentation);
        break;

      case Bitvector:
        lValue = interpreteBitvector(lTermRepresentation);
        break;

      case FloatingPoint:
        lValue = interpreteFloatingPoint(lTermRepresentation);
        break;

      default:
        throw new IllegalArgumentException("Mathsat term with unhandled type " + lAssignable.getType());
      }

      model.put(lAssignable, lValue);
    }

    lModelIterator.free();
    return new Model(model.build());
  }

  private static Pattern BITVECTOR_PATTERN = Pattern.compile("^(\\d+)_(\\d+)$");

  //TODO: change this to the latest version (if possible try to use a BitvectorFormula instance here)
  private static Object interpreteBitvector(String lTermRepresentation) {
    // the term is of the format "<VALUE>_<WIDTH>"
    Matcher matcher =  BITVECTOR_PATTERN.matcher(lTermRepresentation);
    if (!matcher.matches()) {
      throw new NumberFormatException("Unknown bitvector format: " + lTermRepresentation);
    }

    // TODO: calculate negative value?
    String term = matcher.group(1);
    String lengthValue = matcher.group(2);
    long length = Long.parseLong(lengthValue);
    Object value;
    if (length < 64) {
      value = Long.valueOf(term);
    } else {
      value = new BigInteger(term);
    }

    return value;
  }

  private static Pattern FLOATING_POINT_PATTERN = Pattern.compile("^(\\d+)_(\\d+)_(\\d+)$");

  private static Object interpreteFloatingPoint(String lTermRepresentation) {
    // the term is of the format "<VALUE>_<EXPWIDTH>_<MANTWIDTH>"
    Matcher matcher =  FLOATING_POINT_PATTERN.matcher(lTermRepresentation);
    if (!matcher.matches()) {
      throw new NumberFormatException("Unknown floating-point format: " + lTermRepresentation);
    }

    int expWidth = Integer.parseInt(matcher.group(2));
    int mantWidth = Integer.parseInt(matcher.group(3));

    if (expWidth == 11 && mantWidth == 52) {
      return Double.longBitsToDouble(UnsignedLong.valueOf(matcher.group(1)).longValue());
    } else if (expWidth == 8 && mantWidth == 23) {
      return Float.intBitsToFloat(UnsignedInteger.valueOf(matcher.group(1)).intValue());
    }

    // TODO to be fully correct, we would need to interpret this string
    return new BigInteger(matcher.group(1));
  }

  private static Object parseReal(String lTermRepresentation) {
    BigDecimal lValue;
    try {
      lValue = new BigDecimal(lTermRepresentation);
    }
    catch (NumberFormatException e) {
      // lets try special case for mathsat
      String[] lNumbers = lTermRepresentation.split("/");

      if (lNumbers.length != 2) {
        throw new NumberFormatException("Unknown number format: " + lTermRepresentation);
      }

      BigDecimal lNumerator = new BigDecimal(lNumbers[0]);
      BigDecimal lDenominator = new BigDecimal(lNumbers[1]);

      lValue = lNumerator.divide(lDenominator, ROUNDING_PRECISION);
    }
    return lValue;
  }

}
