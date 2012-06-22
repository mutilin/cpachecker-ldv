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
package org.sosy_lab.cpachecker.cpa.explicit.refiner.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;


/**
 * Helper class that collects all "relevant" variables in a given set of nodes, where "relevant" means,
 * that they either appear on the left hand side of an assignment or within an assume edge.
 */
public class AssignedVariablesCollector {
  Set<String> globalVariables = new HashSet<String>();

  public AssignedVariablesCollector() {
  }

  public Multimap<CFANode, String> collectVars(Collection<CFAEdge> edges) {
    Multimap<CFANode, String> collectedVariables = HashMultimap.create();

    for (CFAEdge edge : edges) {
      collectVariables(edge, collectedVariables);
    }

    return collectedVariables;
  }

  private void collectVariables(CFAEdge edge, Multimap<CFANode, String> collectedVariables) {
    String currentFunction = edge.getPredecessor().getFunctionName();

    switch (edge.getEdgeType()) {
    case BlankEdge:
    case CallToReturnEdge:
    case ReturnStatementEdge:
      //nothing to do
      break;

    case DeclarationEdge:
      CDeclaration declaration = ((CDeclarationEdge)edge).getDeclaration();
      if (declaration.getName() != null && declaration.isGlobal()) {
        globalVariables.add(declaration.getName());
        collectedVariables.put(edge.getSuccessor(), declaration.getName());
      }
      break;

    case AssumeEdge:
      CAssumeEdge assumeEdge = (CAssumeEdge)edge;
      collectVariables(assumeEdge, assumeEdge.getExpression(), collectedVariables);
      break;

    case StatementEdge:
      CStatementEdge statementEdge = (CStatementEdge)edge;
      if (statementEdge.getStatement() instanceof CAssignment) {
        CAssignment assignment = (CAssignment)statementEdge.getStatement();
        String assignedVariable = assignment.getLeftHandSide().toASTString();
        collectedVariables.put(edge.getSuccessor(), scoped(assignedVariable, currentFunction));
      }
      break;

    case FunctionCallEdge:
      CFunctionCallEdge functionCallEdge = (CFunctionCallEdge)edge;
      CFunctionCall functionCall     = functionCallEdge.getSummaryEdge().getExpression();

      if (functionCall instanceof CFunctionCallAssignmentStatement) {
        CFunctionCallAssignmentStatement funcAssign = (CFunctionCallAssignmentStatement)functionCall;
        String assignedVariable = scoped(funcAssign.getLeftHandSide().toASTString(), currentFunction);

        // track it at return (2nd statement below), not at call (next, commented statement)
        //collectedVariables.put(edge.getSuccessor(), assignedVariable);
        collectedVariables.put(functionCallEdge.getSummaryEdge().getSuccessor(), assignedVariable);


        collectedVariables.put(edge.getSuccessor(), assignedVariable);
        collectVariables(functionCallEdge, funcAssign.getRightHandSide(), collectedVariables);
      }
      break;
    }
  }

  /**
   * This method prefixes the name of a non-global variable with a given function name.
   *
   * @param variableName the variable name
   * @param functionName the function name
   * @return the prefixed variable name
   */
  private String scoped(String variableName, String functionName) {
    if (globalVariables.contains(variableName)) {
      return variableName;
    } else {
      return functionName + "::" + variableName;
    }
  }

  private void collectVariables(CFAEdge edge, CRightHandSide rightHandSide, Multimap<CFANode, String> collectedVariables) {
    rightHandSide.accept(new CollectVariablesVisitor(edge, collectedVariables));
  }

  private class CollectVariablesVisitor extends DefaultCExpressionVisitor<Void, RuntimeException>
                                               implements CRightHandSideVisitor<Void, RuntimeException> {

    private final CFAEdge currentEdge;
    private final Multimap<CFANode, String> collectedVariables;

    public CollectVariablesVisitor(CFAEdge edge, Multimap<CFANode, String> collectedVariables) {
      this.currentEdge          = edge;
      this.collectedVariables   = collectedVariables;
    }

    private void collectVariable(String var) {
      collectedVariables.put(currentEdge.getSuccessor(), scoped(var, currentEdge.getPredecessor().getFunctionName()));
    }

    @Override
    public Void visit(CIdExpression pE) {
      collectVariable(pE.getName());
      return null;
    }

    @Override
    public Void visit(CArraySubscriptExpression pE) {
      collectVariable(pE.toASTString());
      pE.getArrayExpression().accept(this);
      pE.getSubscriptExpression().accept(this);
      return null;
    }

    @Override
    public Void visit(CBinaryExpression pE) {
      pE.getOperand1().accept(this);
      pE.getOperand2().accept(this);
      return null;
    }

    @Override
    public Void visit(CCastExpression pE) {
      pE.getOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(CFieldReference pE) {
      collectVariable(pE.toASTString());
      pE.getFieldOwner().accept(this);
      return null;
    }

    @Override
    public Void visit(CFunctionCallExpression pE) {
      pE.getFunctionNameExpression().accept(this);
      for (CExpression param : pE.getParameterExpressions()) {
        param.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(CUnaryExpression pE) {
      UnaryOperator op = pE.getOperator();

      switch (op) {
      case AMPER:
      case STAR:
        collectVariable(pE.toASTString());
      default:
        pE.getOperand().accept(this);
      }

      return null;
    }

    @Override
    protected Void visitDefault(CExpression pExp) {
      return null;
    }
  }
}
