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
package org.sosy_lab.cpachecker.cfa.types.java;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;




public class JSimpleType implements JType {

  private final JBasicType type;
  private final boolean isPrimitive;


  public JSimpleType(JBasicType pType) {
    type = pType;

    switch (type) {
    case BOOLEAN:
      //$FALL-THROUGH$
    case BYTE:
      //$FALL-THROUGH$
    case INT:
      //$FALL-THROUGH$
    case SHORT:
      //$FALL-THROUGH$
    case FLOAT:
      //$FALL-THROUGH$
    case DOUBLE:
      isPrimitive = true;
      break;
    default:
      isPrimitive = false;
    }

  }


  public JBasicType getType() {
    return type;
  }

  @Override
  public String toASTString(String pDeclarator) {
    List<String> parts = new ArrayList<String>();

    parts.add(Strings.emptyToNull(type.toASTString()));
    parts.add(Strings.emptyToNull(pDeclarator));

    return Joiner.on(' ').skipNulls().join(parts);
  }


  public boolean isPrimitive() {
    return isPrimitive;
  }

}