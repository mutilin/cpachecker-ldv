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



public abstract class Id {
  protected final String name;
  protected final int dereference;

  public Id(String n, int deref) {
    name = n;
    dereference = deref;
  }

  public int getDereference() {
    return dereference;
  }

  @Override
  public abstract Id clone();

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + dereference;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
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
    Id other = (Id) obj;
    if (dereference != other.dereference)
      return false;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    return true;
  }

  @Override
  public String toString() {
    /*StringBuilder sb = new StringBuilder();
    for (int i = 0; i < dereference; i++) {
      sb.append("*");
    }
    sb.append(name);
    return sb.toString();*/
    return name;
  }
}
