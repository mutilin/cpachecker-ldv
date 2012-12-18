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
package org.sosy_lab.cpachecker.cfa.types.c;


public final class CPointerType implements CType {

  private final CType type;
  private boolean   isConst;
  private boolean   isVolatile;

  public CPointerType(final boolean pConst, final boolean pVolatile,
      final CType pType) {
    isConst = pConst;
    isVolatile = pVolatile;
    type = pType;
  }

  @Override
  public boolean isConst() {
    return isConst;
  }

  @Override
  public boolean isVolatile() {
    return isVolatile;
  }

  public CType getType() {
    return type;
  }

  @Override
  public String toASTString(String pDeclarator) {
    // ugly hack but it works:
    // We need to insert the "*" between the type and the name (e.g. "int *var").
    String decl;

    if (type instanceof CArrayType) {
      decl = type.toASTString("(*" + pDeclarator + ")");
    } else {
      decl = type.toASTString("*" + pDeclarator);
    }

    return (isConst() ? "const " : "")
        + (isVolatile() ? "volatile " : "")
        + decl;
  }
}