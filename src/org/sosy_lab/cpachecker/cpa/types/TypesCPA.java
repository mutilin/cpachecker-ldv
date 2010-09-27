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
package org.sosy_lab.cpachecker.cpa.types;


import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionDefinitionNode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;

/**
 * @author Philipp Wendler
 */
public class TypesCPA extends AbstractCPA {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(TypesCPA.class);
  }

  private TypesCPA() {
    super("join", "sep", new TypesTransferRelation());
  }

  @Override
  public AbstractElement getInitialElement(CFAFunctionDefinitionNode pNode) {
    TypesElement element = new TypesElement();
    //remember the entry function definition node for later use
    ((TypesTransferRelation)getTransferRelation()).setEntryFunctionDefinitionNode((FunctionDefinitionNode)pNode);
    return element;
  }
}