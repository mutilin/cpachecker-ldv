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
package org.sosy_lab.cpachecker.fllesh.util;

import org.sosy_lab.cpachecker.cfa.objectmodel.BlankEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.CallToReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.GlobalDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.ReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;

public class DefaultCFAEdgeVisitor<T> extends AbstractCFAEdgeVisitor<T> {

  @Override
  public T visit(BlankEdge pEdge) {
    throw new UnsupportedOperationException();
  }

  @Override
  public T visit(AssumeEdge pEdge) {
    throw new UnsupportedOperationException();
  }

  @Override
  public T visit(CallToReturnEdge pEdge) {
    throw new UnsupportedOperationException();
  }

  @Override
  public T visit(DeclarationEdge pEdge) {
    throw new UnsupportedOperationException();
  }

  @Override
  public T visit(FunctionCallEdge pEdge) {
    throw new UnsupportedOperationException();
  }

  @Override
  public T visit(GlobalDeclarationEdge pEdge) {
    throw new UnsupportedOperationException();
  }

  @Override
  public T visit(ReturnEdge pEdge) {
    throw new UnsupportedOperationException();
  }

  @Override
  public T visit(StatementEdge pEdge) {
    throw new UnsupportedOperationException();
  }

}
