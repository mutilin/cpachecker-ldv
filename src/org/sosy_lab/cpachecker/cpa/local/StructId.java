/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.local;




public class StructId extends Id {
  //need to devide A.x и B.x
  private final Id owner;

  public StructId(Id own, String field, int deref) {
    super(field, deref);
    owner = own;
    //fields = new LinkedList<StructId>();
  }

  /*private StructId(String own, String field, int deref, List<StructId> f) {
    owner = own;
    name = field;
    dereference = deref;
    //fields = f;
  }*/

  @Override
  public StructId clone() {
    return new StructId(this.owner, this.name, this.dereference/*, this.fields*/);
  }

  public Id getOwner() {
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
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    StructId other = (StructId) obj;
    if (owner == null) {
      if (other.owner != null)
        return false;
    } else if (!owner.equals(other.owner))
      return false;
    return true;
  }

  @Override
  public String toString() {
    /*if (owner == null)
      return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < dereference; i++) {
      sb.append("*");
    }
    sb.append("(" + owner.toString() + "." + name + ")");
    return sb.toString();*/
    return name;
  }
}
