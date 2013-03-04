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

import static com.google.common.base.Preconditions.*;

import java.util.List;
import java.util.Objects;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public final class CCompositeType implements CComplexType {

  private final CComplexType.ComplexTypeKind    kind;
  private List<CCompositeTypeMemberDeclaration> members;
  private final String                name;
  private boolean   isConst;
  private boolean   isVolatile;

  public CCompositeType(final boolean pConst, final boolean pVolatile,
      final CComplexType.ComplexTypeKind pKind, final List<CCompositeTypeMemberDeclaration> pMembers, final String pName) {

    checkArgument(pKind == ComplexTypeKind.STRUCT || pKind == ComplexTypeKind.UNION);
    isConst= pConst;
    isVolatile=pVolatile;
    kind = pKind;
    members = ImmutableList.copyOf(pMembers);
    name = pName.intern();
  }

  @Override
  public CComplexType.ComplexTypeKind getKind() {
    return kind;
  }

  public List<CCompositeTypeMemberDeclaration> getMembers() {
    return members;
  }

  public void setMembers(List<CCompositeTypeMemberDeclaration> list) {
    members = ImmutableList.copyOf(list);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getQualifiedName() {
    return (kind.toASTString() + " " + name).trim();
  }

  @Override
  public String toString() {
    return getQualifiedName();
  }

  @Override
  public String toASTString(String pDeclarator) {
    StringBuilder lASTString = new StringBuilder();

    if (isConst()) {
      lASTString.append("const ");
    }
    if (isVolatile()) {
      lASTString.append("volatile ");
    }

    lASTString.append(kind.toASTString());
    lASTString.append(' ');
    lASTString.append(name);

    lASTString.append(" {\n");
    for (CCompositeTypeMemberDeclaration lMember : members) {
      lASTString.append("  ");
      lASTString.append(lMember.toASTString());
      lASTString.append("\n");
    }
    lASTString.append("} ");
    lASTString.append(pDeclarator);

    return lASTString.toString();
  }

  /**
   * This is the declaration of a member of a composite type.
   * It contains a type and an optional name.
   */
  public static final class CCompositeTypeMemberDeclaration {



    private final CType    type;
    private final String   name;

    public CCompositeTypeMemberDeclaration(CType pType,
                                           String pName) {

      type = checkNotNull(pType);
      name = pName;

    }



    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((name == null) ? 0 : name.hashCode());
      result = prime * result + ((type == null) ? 0 : type.hashCode());
      return result;
    }



    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      CCompositeTypeMemberDeclaration other = (CCompositeTypeMemberDeclaration) obj;
      return
          Objects.equals(name, other.name) &&
          CTypeUtils.equals(type, other.type);
    }



    public CType getType(){
      return type;
    }


    public String getName() {
      return name;
    }


    public String toASTString() {
      String name = Strings.nullToEmpty(getName());
      return getType().toASTString(name) + ";";
    }

  }

  @Override
  public boolean isConst() {
    return isConst;
  }

  @Override
  public boolean isVolatile() {
    return isVolatile;
  }


  @Override
  public <R, X extends Exception> R accept(CTypeVisitor<R, X> pVisitor) throws X {
    return pVisitor.visit(this);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException("Do not use hashCode of CType");
  }

  @Override
  public boolean equals(Object obj) {
    return CTypeUtils.equals(this, obj);
  }
}
