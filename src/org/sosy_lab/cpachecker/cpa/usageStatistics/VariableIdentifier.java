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
package org.sosy_lab.cpachecker.cpa.usageStatistics;

import org.sosy_lab.cpachecker.cfa.types.c.CType;


public abstract class VariableIdentifier implements Identifier {

  public static enum Ref {
    VARIABLE,
    ADRESS,
    REFERENCE;

    public String toASTString() {
      return " is " + name().toLowerCase();
    }
  }

  public VariableIdentifier(String nm, CType tp, int dereference) {
    name = nm;
    type = tp;

    if (dereference < 0)
      status = Ref.ADRESS;
    else if (dereference == 0)
      status = Ref.VARIABLE;
    else /*if (dereference > 0)*/
      status = Ref.REFERENCE;
  }

  public VariableIdentifier(String nm, CType tp, Ref dereference) {
    name = nm;
    type = tp;
    status = dereference;
  }

  protected String name;
  protected CType type;
  protected Ref status;

  public Ref getStatus() {
    return status;
  }

  public CType getType() {
    return type;
  }

  public VariableIdentifier makeAdress() {
    VariableIdentifier newId = this.clone();
    newId.status = Ref.ADRESS;
    return newId;
  }

  public VariableIdentifier makeReference() {
    VariableIdentifier newId = this.clone();
    newId.status = Ref.REFERENCE;
    return newId;
  }

  public VariableIdentifier makeVariable() {
    VariableIdentifier newId = this.clone();
    newId.status = Ref.VARIABLE;
    return newId;
  }

  public String getSimpleName() {
    return name;
  }

  public String getName() {
    if (status == Ref.ADRESS)
      return "(&" + name + ")";
    else if (status == Ref.VARIABLE)
      return name;
    else if (status == Ref.REFERENCE)
      return "(*" + name + ")";
    else
      return name;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((status == null) ? 0 : status.hashCode());
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
    VariableIdentifier other = (VariableIdentifier) obj;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (status != other.status)
      return false;
    if (type == null) {
      if (other.type != null)
        return false;
    } else if (!type.toASTString("").equals(other.type.toASTString("")))
      return false;
    return true;
  }

  @Override
  public abstract VariableIdentifier clone();

}
