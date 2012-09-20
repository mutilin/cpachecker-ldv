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


public class StructureFieldIdentifier extends VariableIdentifier{
  private String fieldType;
  public StructureFieldIdentifier(String pNm, String pTp, String Ftype, boolean ref) {
    super(pNm, pTp, ref);
    fieldType = Ftype;
  }

  /*@Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((fieldType == null) ? 0 : fieldType.hashCode());
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
    StructureFieldIdentifier other = (StructureFieldIdentifier) obj;
    if (fieldType == null) {
      if (other.fieldType != null)
        return false;
    } else if (!fieldType.equals(other.fieldType))
      return false;
    return true;
  }*/

  @Override
  public String toString() {
    return (isDereference ? "*" : "") + name + "\n    |- Structure field\n    |- Structure type: " + type + "\n    |- Field type: "+ fieldType;
  }
}
