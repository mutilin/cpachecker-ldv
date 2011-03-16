/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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

public final class IASTTypeIdExpression extends IASTExpression {

  private final int        operator;
  private final IASTTypeId type;

  public IASTTypeIdExpression(final String pRawSignature,
      final IASTFileLocation pFileLocation, final IType pType,
      final int pOperator, final IASTTypeId pTypeId) {
    super(pRawSignature, pFileLocation, pType);
    operator = pOperator;
    type = pTypeId;
  }

  public int getOperator() {
    return operator;
  }

  public IASTTypeId getTypeId() {
    return type;
  }

  @Override
  public IASTNode[] getChildren(){
    return new IASTNode[] {type};
  }
 
  public static final int op_sizeof = 0;
  public static final int op_typeid = 1;
  public static final int op_alignof = 2;
  public static final int op_typeof = 3;
}
