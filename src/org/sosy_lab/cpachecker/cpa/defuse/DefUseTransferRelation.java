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
package org.sosy_lab.cpachecker.cpa.defuse;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.ast.IASTAssignmentExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTInitializer;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.ReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

public class DefUseTransferRelation implements TransferRelation
{
  private DefUseElement handleExpression (DefUseElement defUseElement, IASTExpression expression, CFAEdge cfaEdge)
  {
    if (expression instanceof IASTAssignmentExpression)
    {
      IASTAssignmentExpression assignExpression = (IASTAssignmentExpression) expression;

      String lParam = assignExpression.getLeftHandSide().getRawSignature ();
      // String lParam2 = binaryExpression.getOperand2 ().getRawSignature ();

      DefUseDefinition definition = new DefUseDefinition (lParam, cfaEdge);
      defUseElement = new DefUseElement(defUseElement, definition);
    }
    return defUseElement;
  }

  private DefUseElement handleDeclaration (DefUseElement defUseElement, DeclarationEdge cfaEdge)
  {
    if (cfaEdge.getName() != null) {
      IASTInitializer initializer = cfaEdge.getInitializer ();
      if (initializer != null)
      {
        String varName = cfaEdge.getName().getRawSignature ();
        DefUseDefinition definition = new DefUseDefinition (varName, cfaEdge);

        defUseElement = new DefUseElement(defUseElement, definition);
      }
    }
    return defUseElement;
  }

  @Override
  public Collection<? extends AbstractElement> getAbstractSuccessors(AbstractElement element, Precision prec, CFAEdge cfaEdge) throws CPATransferException {
    DefUseElement defUseElement = (DefUseElement) element;
    
    switch (cfaEdge.getEdgeType ())
    {
    case StatementEdge:
    {
      StatementEdge statementEdge = (StatementEdge) cfaEdge;
      IASTExpression expression = statementEdge.getExpression ();
      defUseElement = handleExpression (defUseElement, expression, cfaEdge);
      break;
    }
    case ReturnStatementEdge:
    {
      ReturnStatementEdge returnEdge = (ReturnStatementEdge) cfaEdge;
      IASTExpression expression = returnEdge.getExpression ();
      defUseElement = handleExpression (defUseElement, expression, cfaEdge);
      break;
    }
    case DeclarationEdge:
    {
      DeclarationEdge declarationEdge = (DeclarationEdge) cfaEdge;
      defUseElement = handleDeclaration (defUseElement, declarationEdge);
      break;
    }
    }

    return Collections.singleton(defUseElement);
  }

  @Override
  public Collection<? extends AbstractElement> strengthen(AbstractElement element,
                         List<AbstractElement> otherElements, CFAEdge cfaEdge,
                         Precision precision) {
    return null;
  }
}
