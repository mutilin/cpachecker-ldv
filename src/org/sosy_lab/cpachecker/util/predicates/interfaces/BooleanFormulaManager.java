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
package org.sosy_lab.cpachecker.util.predicates.interfaces;



/**
 * This interface represents the Boolean-Theory
 */
public interface BooleanFormulaManager {
  /**
   * Checks if the given formular is a boolean
   */
  public boolean isBoolean(Formula pF);

  public FormulaType<BooleanFormula> getFormulaType();

  /**
   * @return a Formula representing the given value
   */
  public BooleanFormula makeBoolean(boolean value);

  public BooleanFormula makeVariable(String pVar);

  /**
   * Creates a formula representing an equivalence of the two arguments.
   * @param f1 a Formula
   * @param f2 a Formula
   * @return (f1 <-> f2)
   */
  public BooleanFormula equivalence(BooleanFormula formula1, BooleanFormula formula2);

  public boolean isEquivalence(BooleanFormula formula);

  public boolean isImplication(BooleanFormula formula);

  public boolean isTrue(BooleanFormula formula);

  public boolean isFalse(BooleanFormula formula);


  /**
   * Creates a formula representing "IF cond THEN f1 ELSE f2"
   * @param cond a Formula
   * @param f1 a Formula
   * @param f2 a Formula
   * @return (IF cond THEN f1 ELSE f2)
   */
  public <T extends Formula> T ifThenElse(BooleanFormula cond, T f1, T f2);


  public <T extends Formula> boolean isIfThenElse(T f);

  /**
   * Creates a formula representing a negation of the argument.
   * @param f a Formula
   * @return (!f1)
   */
  public BooleanFormula not(BooleanFormula bits);

  /**
   * Creates a formula representing an AND of the two arguments.
   * @param f1 a Formula
   * @param f2 a Formula
   * @return (f1 & f2)
   */
  public BooleanFormula and(BooleanFormula bits1, BooleanFormula bits2);

  /**
   * Creates a formula representing an OR of the two arguments.
   * @param f1 a Formula
   * @param f2 a Formula
   * @return (f1 | f2)
   */
  public BooleanFormula or(BooleanFormula bits1, BooleanFormula bits2);

  public BooleanFormula xor(BooleanFormula bits1, BooleanFormula bits2);


  public boolean isNot(BooleanFormula bits);

  public boolean isAnd(BooleanFormula bits);

  public boolean isOr(BooleanFormula bits);

  public boolean isXor(BooleanFormula bits);

}
