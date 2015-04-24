package org.sosy_lab.cpachecker.cfa.postprocessing.function;

/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatementVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType.CCompositeTypeMemberDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CElaboratedType;
import org.sosy_lab.cpachecker.cfa.types.c.CFunctionType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CTypedefType;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.IdentifierCreator;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;


/**
 * Helper class that collects all functions referenced by some CFAEdges,
 * not counting those that are called directly.
 * (Only functions that have their address taken (implicitly) are returned.)
 */
class CReferencedFunctionsCollector {

  private final Set<String> collectedFunctions = new HashSet<>();
  private final CollectFunctionsVisitor collector = new CollectFunctionsVisitor();

  public Set<String> getCollectedFunctions() {
    return collectedFunctions;
  }

  public Map<String, Set<String>> getFieldAssignement() {
    return collector.funcToField;
  }

  public Map<String, Set<String>> getGlobalAssignement() {
    return collector.funcToGlobal;
  }

  public void visitEdge(CFAEdge edge) {
    switch (edge.getEdgeType()) {
    case AssumeEdge:
      CAssumeEdge assumeEdge = (CAssumeEdge)edge;
      assumeEdge.getExpression().accept(collector);
      break;
    case BlankEdge:
      //nothing to do
      break;
    case CallToReturnEdge:
      //nothing to do
      assert false;
      break;
    case DeclarationEdge:
      CDeclaration declaration = ((CDeclarationEdge)edge).getDeclaration();
      if (declaration instanceof CVariableDeclaration) {
        CInitializer init = ((CVariableDeclaration)declaration).getInitializer();
        if (init != null) {
          int num = collector.collectedFunctions.size();
          init.accept(collector);
          if (num < collector.collectedFunctions.size()) {
            saveDeclaration(declaration.getType(), init);
          }
        }

      }
      break;
    case ReturnStatementEdge:
      CReturnStatementEdge returnEdge = (CReturnStatementEdge)edge;
      if (returnEdge.getExpression().isPresent()) {
        returnEdge.getExpression().get().accept(collector);
      }
      break;
    case StatementEdge:
      CStatementEdge statementEdge = (CStatementEdge)edge;
      statementEdge.getStatement().accept(collector);
      break;
    case MultiEdge:
      //TODO
      assert false;
      break;
    default:
      assert false;
      break;
    }
    collectedFunctions.addAll(collector.collectedFunctions);
    collector.collectedFunctions.clear();
  }

  public void visitDeclaration(CVariableDeclaration decl) {
    if (decl.getInitializer() != null) {
      int num = collector.collectedFunctions.size();
      decl.getInitializer().accept(collector);
      if (num < collector.collectedFunctions.size()) {
        AbstractIdentifier id;
        try {
          id = IdentifierCreator.createIdentifier(decl, "", 0);
          CInitializer init = decl.getInitializer();
          if (id instanceof GlobalVariableIdentifier && init instanceof CInitializerExpression) {
            AbstractIdentifier initId = ((CInitializerExpression)init).getExpression().accept(collector.idCreator);
            if (initId instanceof SingleIdentifier) {
              saveInfoIntoMap(collector.funcToGlobal, ((SingleIdentifier) initId).getName(), ((GlobalVariableIdentifier)id).getName());
            }
          } else {
            saveDeclaration(decl.getType(), decl.getInitializer());
          }
        } catch (HandleCodeException e) {
          e.printStackTrace();
        }
      }
    }
    collectedFunctions.addAll(collector.collectedFunctions);
    collector.collectedFunctions.clear();
  }

  private void saveDeclaration(CType type, CInitializer init) {
    if (init instanceof CInitializerList) {
      //Only structures
      if (type instanceof CArrayType) {
        List<CInitializer> initList = ((CInitializerList)init).getInitializers();
        for (CInitializer cInit : initList) {
          saveDeclaration(((CArrayType)type).getType(), cInit);
        }
      } else if (type instanceof CElaboratedType) {
        saveDeclaration(type.getCanonicalType(), init);
      } else if (type instanceof CCompositeType) {
        List<CCompositeTypeMemberDeclaration> list = ((CCompositeType) type).getMembers();
        List<CInitializer> initList = ((CInitializerList)init).getInitializers();
        for (int i = 0; i < list.size(); i++) {
          CCompositeTypeMemberDeclaration decl = list.get(i);
          CInitializer cInit = initList.get(i);
          if (!(cInit instanceof CInitializerExpression)) {
            continue;
          }
          saveInitializerExpression((CInitializerExpression)cInit, decl.getName());
        }
      } else if (type instanceof CTypedefType) {
        saveDeclaration(((CTypedefType) type).getRealType(), init);
      }
    }
  }

  private void saveInitializerExpression(CInitializerExpression init, String fieldName) {
    IdentifierCreator creator = new IdentifierCreator();
    try {
      AbstractIdentifier id = init.getExpression().accept(creator);
      CExpression initExpression = init.getExpression();
      if (initExpression instanceof CCastExpression) {
        // (void*) (&f)
        initExpression = ((CCastExpression)initExpression).getOperand();
      }
      CType type = initExpression.getExpressionType().getCanonicalType();
      if (type instanceof CPointerType) {
        type = ((CPointerType) type).getType();
        if (type instanceof CFunctionType) {
          if (id instanceof SingleIdentifier) {
            String lastFunction = ((SingleIdentifier) id).getName();
            saveInfoIntoMap(collector.funcToField, lastFunction, fieldName);
          }
        }
      }
    } catch (HandleCodeException e) {
      e.printStackTrace();
    }
  }

  private static void saveInfoIntoMap(Map<String, Set<String>> map, String funcName, String info) {
    Set<String> result;
    if (map.containsKey(funcName)) {
      result = map.get(funcName);
    } else {
      result = new HashSet<>();
      map.put(funcName, result);
    }
    result.add(info);
  }

  private static class CollectFunctionsVisitor extends DefaultCExpressionVisitor<Void, RuntimeException>
                                               implements CRightHandSideVisitor<Void, RuntimeException>,
                                                          CStatementVisitor<Void, RuntimeException>,
                                                          CInitializerVisitor<Void, RuntimeException> {

    private final Set<String> collectedFunctions = new HashSet<>();
    public final Map<String, Set<String>> funcToField = new HashMap<>();
    public final Map<String, Set<String>> funcToGlobal = new HashMap<>();
    private String lastFunction;

    private IdentifierCreator idCreator = new IdentifierCreator();

    public CollectFunctionsVisitor() {
    }

    @Override
    public Void visit(CIdExpression pE) {
      if (pE.getExpressionType() instanceof CFunctionType) {
        collectedFunctions.add(pE.getName());
        lastFunction = pE.getName();
      }
      return null;
    }

    @Override
    public Void visit(CArraySubscriptExpression pE) {
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
    public Void visit(CComplexCastExpression pE) {
      pE.getOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(CFieldReference pE) {
      pE.getFieldOwner().accept(this);
      return null;
    }

    @Override
    public Void visit(CFunctionCallExpression pE) {
      if (pE.getDeclaration() == null) {
        pE.getFunctionNameExpression().accept(this);
      } else {
        // skip regular function calls
      }

      for (CExpression param : pE.getParameterExpressions()) {
        param.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(CUnaryExpression pE) {
      pE.getOperand().accept(this);
      return null;
    }

    @Override
    public Void visit(CPointerExpression pE) {
      pE.getOperand().accept(this);
      return null;
    }

    @Override
    protected Void visitDefault(CExpression pExp) {
      return null;
    }

    @Override
    public Void visit(CInitializerExpression pInitializerExpression) {
      pInitializerExpression.getExpression().accept(this);
      return null;
    }

    @Override
    public Void visit(CInitializerList pInitializerList) {
      for (CInitializer init : pInitializerList.getInitializers()) {
        init.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(CDesignatedInitializer pCStructInitializerPart) {
      pCStructInitializerPart.getRightHandSide().accept(this);
      return null;
    }

    @Override
    public Void visit(CExpressionStatement pIastExpressionStatement) {
      pIastExpressionStatement.getExpression().accept(this);
      return null;
    }

    @Override
    public Void visit(CExpressionAssignmentStatement pIastExpressionAssignmentStatement) {
      int num = collectedFunctions.size();
      pIastExpressionAssignmentStatement.getLeftHandSide().accept(this);
      pIastExpressionAssignmentStatement.getRightHandSide().accept(this);
      if (num < collectedFunctions.size()) {
        saveAssignement(pIastExpressionAssignmentStatement.getLeftHandSide());
      }
      return null;
    }

    @Override
    public Void visit(CFunctionCallAssignmentStatement pIastFunctionCallAssignmentStatement) {
      pIastFunctionCallAssignmentStatement.getLeftHandSide().accept(this);
      pIastFunctionCallAssignmentStatement.getRightHandSide().accept(this);
      return null;
    }

    @Override
    public Void visit(CFunctionCallStatement pIastFunctionCallStatement) {
      pIastFunctionCallStatement.getFunctionCallExpression().accept(this);
      return null;
    }

    private void saveAssignement(CLeftHandSide left) {
      try {
        AbstractIdentifier id = left.accept(idCreator);
        Map<String, Set<String>> targetMap;
        if (id instanceof StructureIdentifier) {
          targetMap = funcToField;
        } else if (id instanceof GlobalVariableIdentifier) {
          targetMap = funcToGlobal;
        } else {
          return;
        }

        saveInfoIntoMap(targetMap, lastFunction, ((SingleIdentifier) id).getName());
      } catch (HandleCodeException e) {
        e.printStackTrace();
      }
    }
  }
}
