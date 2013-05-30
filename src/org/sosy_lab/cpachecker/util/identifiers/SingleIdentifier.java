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
package org.sosy_lab.cpachecker.util.identifiers;

import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cpa.local.LocalTransferRelation;


public abstract class SingleIdentifier implements AbstractIdentifier{

  protected String name;
  protected CType type;
  protected int dereference;

  public SingleIdentifier(String nm, CType tp, int deref) {
    name = nm;
    type = tp;
    dereference = deref;
  }

  @Override
  public int getDereference() {
    return dereference;
  }

  public CType getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getPointers() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < dereference; i++) {
      sb.append("*");
    }
    return sb.toString();
  }

  @Override
  public boolean isPointer() {
    if (dereference > 0)
      return true;
    else if (LocalTransferRelation.findDereference(type) > 0)
      return true;
    else
      return false;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + dereference;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((type == null) ? 0 : type.toASTString("").hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SingleIdentifier other = (SingleIdentifier) obj;
    if (dereference != other.dereference)
      return false;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (type == null) {
      if (other.type != null)
        return false;
    } else if (!type.toASTString("").equals(other.type.toASTString("")))
      return false;
    return true;
  }

  @Override
  public abstract SingleIdentifier clone();

  @Override
  public abstract String toString();

  public abstract SingleIdentifier clearDereference();

  public abstract String toLog();

  public abstract GeneralIdentifier getGeneralId();

  @Override
  public void setDereference(int pD) {
    dereference = pD;
  }

}
