/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.local;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CTypedefType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.ConstantIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralLocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.IdentifierCreator;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.ReturnIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;

@Options(prefix="cpa.local")
public class LocalTransferRelation implements TransferRelation {

  @Option(name="allocatefunctions", description = "functions, which allocate new free memory")
  private Set<String> allocate;

  @Option(name="allocateFunctionPattern", description = "functions, which allocate new free memory")
  private Set<String> allocatePattern = Sets.newHashSet("alloc");

  @Option(name="conservativefunctions", description = "functions, which allocate new free memory")
  private Set<String> conservationOfSharedness;

  private Map<String, Integer> allocateInfo;

  private final IdentifierCreator idCreator;

  public LocalTransferRelation(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
    parseAllocatedFunctions(config);
    idCreator = new IdentifierCreator();
  }
  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision,
      CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {

    LocalState LocalElement = (LocalState) pState;
    LocalState successor;
    switch(pCfaEdge.getEdgeType()) {

      case FunctionCallEdge: {
        successor = createNewScopeInState(LocalElement, (CFunctionCallEdge)pCfaEdge);
        break;
      }

      case FunctionReturnEdge: {
        successor = handleReturnEdge(LocalElement, (CFunctionReturnEdge)pCfaEdge);
        break;
      }

      case MultiEdge: {
        successor = LocalElement;
        for (CFAEdge simpleEdge : ((MultiEdge)pCfaEdge).getEdges()) {
          successor = handleSimpleEdge(successor, simpleEdge);
        }
        break;
      }

      default:
        successor = handleSimpleEdge(LocalElement, pCfaEdge);
    }

    if (successor == null) {
      return Collections.emptySet();
    } else {
      return Collections.singleton(successor);
    }
  }

  private LocalState handleSimpleEdge(LocalState pState, CFAEdge pCfaEdge) throws HandleCodeException, UnrecognizedCFAEdgeException {
    LocalState newState = pState.clone();
    switch(pCfaEdge.getEdgeType()) {
      case DeclarationEdge: {
        CDeclarationEdge declEdge = (CDeclarationEdge) pCfaEdge;
        handleDeclaration(newState, declEdge);
        break;
      }

      // if edge is a statement edge, e.g. a = b + c
      case StatementEdge: {
        CStatementEdge statementEdge = (CStatementEdge) pCfaEdge;
        handleStatement(newState, statementEdge.getStatement());
        break;
      }

      case ReturnStatementEdge: {
        handleReturnStatementEdge(newState, (CReturnStatementEdge)pCfaEdge);
        break;
      }

      case AssumeEdge:
      case BlankEdge:
      case CallToReturnEdge: {
        break;
      }

      default:
        throw new UnrecognizedCFAEdgeException(pCfaEdge);
    }
    return newState;
  }

  private void parseAllocatedFunctions(Configuration config) {
    String num;
    allocateInfo = new HashMap<>();
    if (allocate != null) {
      for (String funcName : allocate) {
        num = config.getProperty(funcName + ".parameter");
        if (num == null) {
          allocateInfo.put(funcName, 0);
        } else {
          allocateInfo.put(funcName, Integer.parseInt(num));
        }
      }
    }
  }

  private void handleReturnStatementEdge(LocalState pSuccessor, CReturnStatementEdge pCfaEdge) throws HandleCodeException {
    Optional<CExpression> returnExpression = pCfaEdge.getExpression();
    if (returnExpression.isPresent()) {
      int dereference = findDereference(returnExpression.get().getExpressionType());
      if (dereference > 0) {
        AbstractIdentifier returnId = createId(returnExpression.get(), dereference);
        DataType type = pSuccessor.getType(returnId);
        pSuccessor.set(ReturnIdentifier.getInstance(), type);
      }
    }
  }

  private LocalState handleReturnEdge(LocalState pSuccessor, CFunctionReturnEdge pCfaEdge) throws HandleCodeException {
    CFunctionCall exprOnSummary     = pCfaEdge.getSummaryEdge().getExpression();
    DataType returnType             = pSuccessor.getType(ReturnIdentifier.getInstance());
    LocalState newElement           = pSuccessor.getPreviousState().clone();

    if (exprOnSummary instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement assignExp = ((CFunctionCallAssignmentStatement)exprOnSummary);
      String funcName = assignExp.getRightHandSide().getFunctionNameExpression().toASTString();
      boolean isAllocatedFunction = (allocate == null ? false : allocate.contains(funcName));
      if (!isAllocatedFunction) {
        for (String pattern : allocatePattern) {
          if (funcName.contains(pattern)) {
            isAllocatedFunction = true;
            break;
          }
        }
      }
      boolean isConservativeFunction = (conservationOfSharedness == null ? false : conservationOfSharedness.contains(funcName));

      CExpression op1 = assignExp.getLeftHandSide();
      CType type = op1.getExpressionType();
      //find type in old state...
      int dereference = findDereference(type);
      AbstractIdentifier returnId = createId(op1, dereference);

      if (isAllocatedFunction) {
        Integer num = allocateInfo.get(funcName);
        if (num == null) {
          //Means that we use pattern
          num = 0;
        }
        if (num == 0) {
          //local data are returned from function
          if (!returnId.isGlobal()) {
            newElement.forceSetLocal(returnId);
          }
        } else if (num > 0) {
          handleAllocatedFunction(newElement, assignExp.getRightHandSide());
        }
      } else if (isConservativeFunction){

        List<CExpression> parameters = assignExp.getRightHandSide().getParameterExpressions();
        // Usually it looks like 'priv = netdev_priv(dev)'
        // Other cases will be handled if they appear
        CExpression targetParam = parameters.get(0);
        AbstractIdentifier paramId = createId(targetParam, dereference);
        newElement.set(returnId, pSuccessor.getType(paramId));
      } else {
        newElement.set(returnId, returnType);
      }
    }
    //Update the outer parameters:
    CFunctionSummaryEdge sEdge = pCfaEdge.getSummaryEdge();
    CFunctionEntryNode entry = sEdge.getFunctionEntry();
    String funcName = entry.getFunctionName();
    boolean isAllocatedFunction = (allocate == null ? false : allocate.contains(funcName));
    if (!isAllocatedFunction) {
      List<String> paramNames = entry.getFunctionParameterNames();
      List<CExpression> arguments = sEdge.getExpression().getFunctionCallExpression().getParameterExpressions();
      List<CParameterDeclaration> parameterTypes = entry.getFunctionDefinition().getParameters();

      List<Pair<AbstractIdentifier, LocalVariableIdentifier>> toProcess =
          extractIdentifiers(arguments, paramNames, parameterTypes);

      for (Pair<AbstractIdentifier, LocalVariableIdentifier> pairId : toProcess) {
        DataType newType = pSuccessor.getType(pairId.getSecond());
        newElement.set(pairId.getFirst(), newType);
      }
    }
    return newElement;
  }

  private void handleAllocatedFunction(LocalState pSuccessor, CFunctionCallExpression right) throws HandleCodeException {
    String funcName = right.getFunctionNameExpression().toASTString();
    int num = allocateInfo.get(funcName);
    if (num > 0) {
      //local data are transmitted, as function parameters. F.e., allocate(&pointer);
      CExpression parameter = right.getParameterExpressions().get(num - 1);
      int dereference = findDereference(parameter.getExpressionType());
      AbstractIdentifier rightId = createId(parameter, dereference);
      if (!rightId.isGlobal()) {
        pSuccessor.forceSetLocal(rightId);
      }
    }
  }

  private LocalState createNewScopeInState(LocalState pSuccessor, CFunctionCallEdge callEdge) throws HandleCodeException {
    LocalState newState = new LocalState(pSuccessor);

    CFunctionEntryNode functionEntryNode = callEdge.getSuccessor();
    List<String> paramNames = functionEntryNode.getFunctionParameterNames();
    List<CExpression> arguments = callEdge.getArguments();
    List<CParameterDeclaration> parameterTypes = functionEntryNode.getFunctionDefinition().getParameters();


    List<Pair<AbstractIdentifier, LocalVariableIdentifier>> toProcess =
        extractIdentifiers(arguments, paramNames, parameterTypes);

    for (Pair<AbstractIdentifier, LocalVariableIdentifier> pairId : toProcess) {
      DataType type = pSuccessor.getType(pairId.getFirst());
      newState.set(pairId.getSecond(), type);
    }
    // else, something like 'f(..)'. Now we can't do anything
    //TODO Do something!
    return newState;
  }

  private List<Pair<AbstractIdentifier, LocalVariableIdentifier>> extractIdentifiers(
      List<CExpression> arguments,
      List<String> paramNames, List<CParameterDeclaration> parameterTypes) throws HandleCodeException {

    List<Pair<AbstractIdentifier, LocalVariableIdentifier>> result = new LinkedList<>();
    CExpression currentArgument;
    int dereference;
    for (int i = 0; i < arguments.size(); i++) {
      if (i >= paramNames.size()) {
        //function with unknown parameter size: printf(char* a, ...);
        break;
      }
      currentArgument = arguments.get(i);
      dereference = findDereference(parameterTypes.get(i).getType());
      AbstractIdentifier previousId;

      for (int j = 1, previousDeref = 1; j <= dereference; j++, previousDeref++) {
        LocalVariableIdentifier id = new GeneralLocalVariableIdentifier(paramNames.get(i), j);
        previousId = createId(currentArgument, previousDeref);
        result.add(Pair.of(previousId, id));
      }
    }
    return result;
  }

  private void handleStatement(LocalState pSuccessor, CStatement pStatement) throws HandleCodeException {
    if (pStatement instanceof CAssignment) {
      // assignment like "a = b" or "a = foo()"
      CAssignment assignment = (CAssignment)pStatement;
      CExpression left = assignment.getLeftHandSide();
      assign(pSuccessor, left, assignment.getRightHandSide());

    }
  }

  public static int findDereference(CType type) {
    if (type instanceof CPointerType) {
      CPointerType pointerType = (CPointerType) type;
      return (findDereference(pointerType.getType()) + 1);
    } else if (type instanceof CArrayType) {
      CArrayType arrayType = (CArrayType) type;
      return (findDereference(arrayType.getType()) + 1);
    } else if (type instanceof CTypedefType) {
      return findDereference(((CTypedefType)type).getRealType());
    } else {
      return 0;
    }
  }

  private AbstractIdentifier createId(CExpression expression, int dereference) throws HandleCodeException {
    idCreator.setDereference(dereference);
    AbstractIdentifier id = expression.accept(idCreator);
    if (id instanceof GlobalVariableIdentifier || id instanceof LocalVariableIdentifier) {
      id = ((SingleIdentifier)id).getGeneralId();
    }
    return id;
  }

  private void assign(LocalState pSuccessor, CExpression left, CRightHandSide right) throws HandleCodeException {

    int leftDereference = findDereference(left.getExpressionType());

    /* If we assign a = b, we should set *a <-> *b and **a <-> **b
     */
    while (leftDereference > 0) {
      AbstractIdentifier leftId = createId(left, leftDereference);

      if (right instanceof CExpression && !(leftId instanceof ConstantIdentifier)) {
        /* Difference in leftDereference and right one appears in very specific cases, like
         * 'int* t = 0' and 'int* t[]; void* b; b = malloc(..); t = b;'
         * Therefore, we use left dereference as main one
         */
        AbstractIdentifier rightId = createId((CExpression)right, leftDereference);
        if (leftId.isGlobal() && !(leftId instanceof SingleIdentifier && LocalCPA.localVariables.contains(((SingleIdentifier)leftId).getName()))) {
          if (!(rightId instanceof ConstantIdentifier)) {
            //Variable is global, not memory location!
            //So, we should set the type of 'right' to global
            pSuccessor.set(rightId, DataType.GLOBAL);
          }
        } else {
          DataType type = pSuccessor.getType(rightId);
          pSuccessor.set(leftId, type);
        }
      } else if (right instanceof CFunctionCallExpression) {
	    	String funcName = ((CFunctionCallExpression)right).getFunctionNameExpression().toASTString();
	    	boolean isAllocatedFunction = (allocate == null ? false : allocate.contains(funcName));
	      if (!isAllocatedFunction) {
	        for (String pattern : allocatePattern) {
	          if (funcName.contains(pattern)) {
	            isAllocatedFunction = true;
	            break;
	          }
	        }
	      }
	    	boolean isConservativeFunction = (conservationOfSharedness == null ? false : conservationOfSharedness.contains(funcName));

	    	if (isAllocatedFunction) {
	    		Integer num = allocateInfo.get(funcName);
	        if (num == null) {
	          //Means that we use pattern
	          num = 0;
	        }
	        if (num == 0) {
	          //local data are returned from function
	          if (!leftId.isGlobal()) {
	          	pSuccessor.forceSetLocal(leftId);
	          }
	        } else if (num > 0) {
	        	handleAllocatedFunction(pSuccessor, (CFunctionCallExpression) right);
	        }
	    	} else if (isConservativeFunction) {
	    	  List<CExpression> parameters = ((CFunctionCallExpression)right).getParameterExpressions();
	    	  // Usually it looks like 'priv = netdev_priv(dev)'
	    	  // Other cases will be handled if they appear
	    	  CExpression targetParam = parameters.get(0);
	    	  AbstractIdentifier paramId = createId(targetParam, leftDereference);
	    	  pSuccessor.set(leftId, pSuccessor.getType(paramId));
	    	} else {
	    	  /* unknown function
	    	   * It is important to reset the value
	    	   */
	    	  pSuccessor.set(leftId, null);
	    	}
      }
      leftDereference--;
    }
  }

  private void handleDeclaration(LocalState pSuccessor, CDeclarationEdge declEdge) throws HandleCodeException {
    if (declEdge.getDeclaration().getClass() != CVariableDeclaration.class) {
      return;
    }

    CDeclaration decl = declEdge.getDeclaration();
    if (decl instanceof CVariableDeclaration) {

      CInitializer init = ((CVariableDeclaration)decl).getInitializer();
      if (init != null && init instanceof CInitializerExpression) {
        assign(pSuccessor, new CIdExpression(((CVariableDeclaration)decl).getFileLocation(), decl),
            ((CInitializerExpression)init).getExpression());
      } else {
        if (findDereference(decl.getType()) > 0 && !decl.isGlobal()
            && (declEdge.getSuccessor().getFunctionName().equals("ldv_main") || decl.getType() instanceof CArrayType)) {
          //we don't save global variables
          pSuccessor.set(new GeneralLocalVariableIdentifier(decl.getName(), findDereference(decl.getType())), DataType.LOCAL);
        }
      }
    }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState pState, List<AbstractState> pOtherStates,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException, InterruptedException {
    return null;
  }
  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(AbstractState pState, Precision pPrecision)
      throws CPATransferException, InterruptedException {
    throw new UnsupportedOperationException(
        "The " + this.getClass().getSimpleName()
        + " expects to be called with a CFA edge supplied"
        + " and does not support configuration where it needs to"
        + " return abstract states for any CFA edge.");
  }

}
