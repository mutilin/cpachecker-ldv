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



public class StructureIdentifier extends Identifier{
  protected Identifier owner;

  public StructureIdentifier(String pNm, CType pTp, int dereference, Identifier own) {
    super(pNm, pTp, dereference);
    this.owner = own;
  }

  @Override
  public String toString() {
    String info = "";
    for (int i = 0; i < dereference; i++) {
      info += "*";
    }
    info = "(" + owner.toString() + ").";
    info += name;
    return info;
  }

  @Override
  public StructureIdentifier clone() {
    return new StructureIdentifier(name, type, dereference, owner.clone());
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((owner == null) ? 0 : owner.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    StructureIdentifier other = (StructureIdentifier) obj;
    if (owner == null) {
      if (other.owner != null)
        return false;
    } else if (!owner.equals(other.owner))
      return false;
    return true;
  }

  @Override
  public Identifier clearDereference() {
    return new StructureIdentifier(name, type, 0, owner);
  }
}
