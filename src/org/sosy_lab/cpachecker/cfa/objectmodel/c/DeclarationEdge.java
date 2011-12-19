/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.objectmodel.c;

import org.sosy_lab.cpachecker.cfa.ast.IASTDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.IASTInitializer;
import org.sosy_lab.cpachecker.cfa.ast.IType;
import org.sosy_lab.cpachecker.cfa.ast.StorageClass;
import org.sosy_lab.cpachecker.cfa.objectmodel.AbstractCFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;

public class DeclarationEdge extends AbstractCFAEdge {

  private final IASTDeclaration declaration;

  public DeclarationEdge(final IASTDeclaration pDeclaration, final int pLineNumber,
      final CFANode pPredecessor,final CFANode pSuccessor) {

    super(pDeclaration.toASTString(), pLineNumber, pPredecessor, pSuccessor);
    declaration = pDeclaration;
  }

  @Override
  public CFAEdgeType getEdgeType() {
    return CFAEdgeType.DeclarationEdge;
  }

  public StorageClass getStorageClass() {
    return declaration.getStorageClass();
  }

  public IType getDeclSpecifier() {
    return declaration.getDeclSpecifier();
  }

  public String getName() {
    return declaration.getName();
  }

  public IASTInitializer getInitializer() {
    return declaration.getInitializer();
  }

  @Override
  public IASTDeclaration getRawAST() {
    return declaration;
  }

  public boolean isGlobal() {
    return false;
  }
}
