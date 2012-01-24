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
package org.sosy_lab.cpachecker.cfa.ast;

import java.math.BigDecimal;

public final class IASTFloatLiteralExpression extends IASTLiteralExpression {

  // use a BigDecimal here because C code might have floating point types with bigger precision than double
  private final BigDecimal value;

  public IASTFloatLiteralExpression(IASTFileLocation pFileLocation,
                                    IType pType,
                                    BigDecimal pValue) {
    super(pFileLocation, pType);
    value = pValue;
  }

  @Override
  public BigDecimal getValue() {
    return value;
  }

  @Override
  public <R, X extends Exception> R accept(ExpressionVisitor<R, X> v) throws X {
    return v.visit(this);
  }

  @Override
  public <R, X extends Exception> R accept(RightHandSideVisitor<R, X> v) throws X {
    return v.visit(this);
  }

  @Override
  public String toASTString() {
    return value.toString();
  }
}
