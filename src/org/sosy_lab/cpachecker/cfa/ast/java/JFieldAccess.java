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
package org.sosy_lab.cpachecker.cfa.ast.java;

import java.util.Queue;

import org.sosy_lab.cpachecker.cfa.ast.CFileLocation;
import org.sosy_lab.cpachecker.cfa.types.java.JType;


public class JFieldAccess extends JIdExpression {


  private final Queue<JIdExpression> qualifier;

  public JFieldAccess(CFileLocation pFileLocation, JType pType, String pName, JFieldDeclaration pDeclaration, Queue<JIdExpression> pQualifier) {
    super(pFileLocation, pType, pName, pDeclaration);
    qualifier = pQualifier;
  }

  @Override
  public JFieldDeclaration getDeclaration() {
    return (JFieldDeclaration) super.getDeclaration();
  }

  public Queue<JIdExpression> getReferencedVariable() {
    return qualifier;
  }

}
