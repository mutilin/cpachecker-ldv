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
package org.sosy_lab.cpachecker.cfa.model.java;

import java.util.List;

import org.sosy_lab.cpachecker.cfa.ast.java.JMethodDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;

public class JMethodEntryNode extends FunctionEntryNode {

  public JMethodEntryNode(final int pLineNumber,
      final JMethodDeclaration pMethodDefinition,
      final FunctionExitNode pExitNode,
      final List<String> pParameterNames) {

    super(pLineNumber, pMethodDefinition.getName(), pExitNode, pMethodDefinition,pParameterNames);
  }

  @Override
  public JMethodDeclaration getFunctionDefinition() {
    return  (JMethodDeclaration)functionDefinition;
  }

  @Override
  public List<JParameterDeclaration> getFunctionParameters() {
    return getFunctionDefinition().getParameters();
  }
}
