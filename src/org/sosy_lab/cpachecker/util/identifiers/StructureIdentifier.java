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

import java.util.Collection;

import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.cpachecker.cfa.types.c.CProblemType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cpa.local.LocalTransferRelation;



public class StructureIdentifier extends SingleIdentifier{
  protected AbstractIdentifier owner;

  public StructureIdentifier(String pNm, CType pTp, int dereference, AbstractIdentifier own) {
    super(pNm, pTp, dereference);
    this.owner = own;
  }

  @Override
  public String toString() {
    String info = "";
    if (dereference > 0) {
      for (int i = 0; i < dereference; i++) {
        info += "*";
      }
    } else if (dereference == -1) {
      info += "&";
    } else if (dereference < -1){
      info = "Error in string representation, dereference < -1";
      return info;
    }
    info += "(" + owner.toString() + ").";
    info += name;
    return info;
  }

  @Override
  public StructureIdentifier clone() {
    return new StructureIdentifier(name, type, dereference, owner.clone());
  }

  public AbstractIdentifier getOwner() {
    return owner;
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
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    StructureIdentifier other = (StructureIdentifier) obj;
    if (owner == null) {
      if (other.owner != null) {
        return false;
      }
    } else if (!owner.equals(other.owner)) {
      return false;
    }
    return true;
  }

  @Override
  public SingleIdentifier clearDereference() {
    return new StructureIdentifier(name, type, 0, owner);
  }

  @Override
  public boolean isGlobal() {
    return owner.isGlobal();
  }

  @Override
  public boolean isPointer() {
    if (LocalTransferRelation.findDereference(type) > 0) {
      return true;
    } else if (dereference > 0) {
      return true;
    } else {
      return owner.isPointer();
    }
  }

  @Override
  public String toLog() {
    return "s;" + name + ";" + dereference;
  }

  private CType getStructureType() {
    if (owner instanceof SingleIdentifier) {
      return ((SingleIdentifier)owner).type;
    } else if (owner instanceof ConstantIdentifier) {
      return new CSimpleType(false, false, CBasicType.INT, false, false, false, false, false, false, false);
    } else if (owner instanceof BinaryIdentifier) {
      return new CProblemType("Complex_type");
    } else {
      System.err.println("Can't create structureFieldId for " + this.toString());
      return null;
    }
  }

  @Override
  public GeneralIdentifier getGeneralId() {
    return new GeneralStructureFieldIdentifier(name, type.toASTString(""), getStructureType(), dereference);
  }

  public StructureFieldIdentifier toStructureFieldIdentifier() {
    return new StructureFieldIdentifier(name, type.toASTString(""), getStructureType(), dereference);
  }

  /**
   * This method recursively checks owners of this structure, if any is contained in given collection
   * @param set - some collection of identifiers
   * @return first abstract identifier, which is found or null if no owners are found in collection
   */
  @Override
  public AbstractIdentifier containsIn(Collection<? extends AbstractIdentifier> set) {
    if (set.contains(this)) {
      return this;
    } else {
      AbstractIdentifier ownerContainer = owner.containsIn(set);
      if (ownerContainer == null) {
        return null;
      } else {
        return ownerContainer;
      }
    }
  }
}
