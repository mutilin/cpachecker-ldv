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

public class LocalVariableIdentifier extends VariableIdentifier {
  protected String function;//function, where this variable was declarated

  public LocalVariableIdentifier(String nm, CType t, String func, int dereference) {
    super(nm, t, dereference);
    function = func;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((function == null) ? 0 : function.hashCode());
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
    LocalVariableIdentifier other = (LocalVariableIdentifier) obj;
    if (function == null) {
      if (other.function != null)
        return false;
    } else if (!function.equals(other.function))
      return false;
    return true;
  }

  @Override
  public LocalVariableIdentifier clone() {
    return new LocalVariableIdentifier(name, type, function, dereference);
  }

  public String getFunction() {
    return function;
  }

  //it needs to set function after creation in lockStatistics.
  //In shared analysis function isn't used because of reducing
  public void setFunction(String func) {
    function = func;
  }

  @Override
  public SingleIdentifier clearDereference() {
    return new LocalVariableIdentifier(name, type, function, 0);
  }

  @Override
  public boolean isGlobal() {
    return false;
  }

  @Override
  public String toLog() {
    return "l;" + name + ";" + dereference;
  }

  @Override
  public GeneralIdentifier getGeneralId() {
    return new GeneralLocalVariableIdentifier(name, type, function, dereference);
  }
}
