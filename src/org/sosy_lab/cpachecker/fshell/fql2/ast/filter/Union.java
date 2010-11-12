/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.fshell.fql2.ast.filter;

public class Union implements Filter {

  private Filter mFilter1;
  private Filter mFilter2;

  public Union(Filter pFilter1, Filter pFilter2) {
    assert(pFilter1 != null);
    assert(pFilter2 != null);

    mFilter1 = pFilter1;
    mFilter2 = pFilter2;
  }

  public Filter getFirstFilter() {
    return mFilter1;
  }

  public Filter getSecondFilter() {
    return mFilter2;
  }

  @Override
  public String toString() {
    return "UNION(" + mFilter1.toString() + ", " + mFilter2.toString() + ")";
  }

  @Override
  public int hashCode() {
    return 527 + mFilter1.hashCode() + mFilter2.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Union other = (Union) obj;

    return (mFilter1.equals(other.mFilter1) && mFilter2.equals(other.mFilter2));
  }

  @Override
  public <T> T accept(FilterVisitor<T> pVisitor) {
    assert(pVisitor != null);

    return pVisitor.visit(this);
  }

}
