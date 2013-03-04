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
package org.sosy_lab.cpachecker.cfa.ast.c;

import java.util.List;

import org.sosy_lab.cpachecker.cfa.ast.AInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;


public class CInitializerList extends AInitializerList implements CInitializer, CAstNode {

  public CInitializerList(FileLocation pFileLocation, List<CInitializer> pInitializerList) {
    super(pFileLocation, pInitializerList);
  }

  @Override
  public <R, X extends Exception> R accept(CInitializerVisitor<R, X> v) throws X {
    return v.visit(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<CInitializer> getInitializers() {
    return (List<CInitializer>) super.getInitializers();
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) { return true; }
    if (obj == null) { return false; }
    if (!(obj instanceof CInitializerList)) { return false; }
    return super.equals(obj);
  }
}
