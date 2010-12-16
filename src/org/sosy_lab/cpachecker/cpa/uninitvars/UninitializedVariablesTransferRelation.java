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
package org.sosy_lab.cpachecker.cpa.uninitvars;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.eclipse.cdt.core.dom.ast.IASTArrayDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTArraySubscriptExpression;
import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTCastExpression;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpressionList;
import org.eclipse.cdt.core.dom.ast.IASTFieldReference;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTInitializer;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IASTTypeIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.CallToReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.GlobalDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.ReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;

import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.types.Type;
import org.sosy_lab.cpachecker.cpa.types.TypesElement;
import org.sosy_lab.cpachecker.cpa.types.Type.TypeClass;
import org.sosy_lab.cpachecker.cpa.uninitvars.UninitializedVariablesElement.ElementProperty;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

/**
 * @author Philipp Wendler, Gregor Endler
 *
 * Needs typesCPA to properly deal with field references.
 * If run without typesCPA, uninitialized field references may not be detected.
 */
public class UninitializedVariablesTransferRelation implements TransferRelation {

  private Set<String> globalVars; // set of all global variable names

  private boolean printWarnings;

  private LogManager logger;

  //needed for strengthen()
  private String lastAdded = null;
  //used to check if a warning message in strengthen() has been displayed if typesCPA is not present
  private boolean typesWarningAlreadyDisplayed = false;

  public UninitializedVariablesTransferRelation(String printWarnings, LogManager logger) {
    globalVars = new HashSet<String>();
    this.printWarnings = Boolean.parseBoolean(printWarnings);
    this.logger = logger;
  }

  private AbstractElement getAbstractSuccessor(AbstractElement element,
                                              CFAEdge cfaEdge,
                                              Precision precision)
                                              throws CPATransferException {

    UninitializedVariablesElement successor = ((UninitializedVariablesElement)element).clone();
    successor.clearProperties();

    switch (cfaEdge.getEdgeType()) {

    case DeclarationEdge:
      handleDeclaration(successor, (DeclarationEdge)cfaEdge);
      break;

    case StatementEdge:
      handleStatement(successor, ((StatementEdge)cfaEdge).getExpression(), cfaEdge);
      break;

    case ReturnEdge:
      //handle statement like a = func(x) in the CallToReturnEdge
      ReturnEdge returnEdge = (ReturnEdge)cfaEdge;
      CallToReturnEdge ctrEdge = returnEdge.getSuccessor().getEnteringSummaryEdge();
      handleStatement(successor, ctrEdge.getExpression(), ctrEdge);
      break;

    case AssumeEdge:
      // just check if there are uninitialized variable usages
      if (printWarnings) {
        isExpressionUninitialized(successor, ((AssumeEdge)cfaEdge).getExpression(), cfaEdge);
      }
      break;

    case FunctionCallEdge:
      //on calling a function, check initialization status of the parameters
      handleFunctionCall(successor, (FunctionCallEdge)cfaEdge);
      break;

    case BlankEdge:
      break;

    default:
      throw new UnrecognizedCFAEdgeException(cfaEdge);
    }

    return successor;
  }

  private void addWarning(CFAEdge edge, String variable, IASTExpression expression,
                                                      UninitializedVariablesElement element) {

    if (printWarnings) {

      int lineNumber = edge.getLineNumber();
      String message;

      if (edge instanceof CallToReturnEdge && expression instanceof IASTFunctionCallExpression) {
        message = "uninitialized return value of function call " + variable + " in line "
        + lineNumber + ": " + edge.getRawStatement();
        element.addProperty(ElementProperty.UNINITIALIZED_RETURN_VALUE);
      } else {
        message = "uninitialized variable " + variable + " used in line "
        + lineNumber + ": " + edge.getRawStatement();
        element.addProperty(ElementProperty.UNINITIALIZED_VARIABLE_USED);
      }

      element.addWarning(lineNumber, variable, message);
    }
  }

  private void setUninitialized(UninitializedVariablesElement element, String varName) {
    if (globalVars.contains(varName)) {
      element.addGlobalVariable(varName);
    } else {
      element.addLocalVariable(varName);
    }
  }

  private void setInitialized(UninitializedVariablesElement element, String varName) {
    if (globalVars.contains(varName)) {
      element.removeGlobalVariable(varName);
    } else {
      element.removeLocalVariable(varName);
    }
  }

  private void handleDeclaration(UninitializedVariablesElement element,
      DeclarationEdge declaration) {

    //typedefs do not concern this CPA
    if (declaration.getDeclSpecifier().getStorageClass() != IASTDeclSpecifier.sc_typedef) {

      for (IASTDeclarator declarator : declaration.getDeclarators()) {
        if (declarator != null) {

          String varName;
          //in case of a nested declarator, get the variable name from the inner declarator
          if (declarator.getNestedDeclarator() != null) {
            varName = declarator.getNestedDeclarator().getName().toString();
          } else {
            //otherwise there is only one declarator
            varName = declarator.getName().toString();
          }
          if (declaration instanceof GlobalDeclarationEdge) {
            globalVars.add(varName);
          }

          lastAdded = varName;

          IASTInitializer initializer = declarator.getInitializer();
          // initializers in CIL are always constant, so no need to check if
          // initializer expression contains uninitialized variables
          if (initializer == null &&
              !(declaration.getDeclSpecifier().getStorageClass() == IASTDeclSpecifier.sc_extern) &&
              !(declarator instanceof IASTArrayDeclarator) &&
              !(declarator instanceof IASTFunctionDeclarator)) {
            setUninitialized(element, varName);
          } else {
            setInitialized(element, varName);
          }
        }
      }
    }
  }

  private void handleFunctionCall(UninitializedVariablesElement element, FunctionCallEdge callEdge)
                                                                  throws UnrecognizedCCodeException {
    //find functions's parameters and arguments
    FunctionDefinitionNode functionEntryNode = (FunctionDefinitionNode)callEdge.getSuccessor();
    List<String> paramNames = functionEntryNode.getFunctionParameterNames();
    List<IASTExpression> arguments = callEdge.getArguments();

    if (!arguments.isEmpty()) {

      int numOfParams = paramNames.size();

      //if the following  is the case, this is a varargs function and thus can take any number of arguments
      if (numOfParams < arguments.size()) {
        //then, for unnamed parameters, only check for use of uninitialized variables
        for (int j = numOfParams; j < arguments.size(); j++) {
          isExpressionUninitialized(element, arguments.get(j), callEdge);
        }
      }

      LinkedList<String> uninitParameters = new LinkedList<String>();
      LinkedList<String> initParameters = new LinkedList<String>();

      //collect initialization status of the called function's parameters from the context of the calling function
      for (int i = 0; i < numOfParams; i++) {
        if(isExpressionUninitialized(element, arguments.get(i), callEdge)) {
          uninitParameters.add(paramNames.get(i));
        } else {
          initParameters.add(paramNames.get(i));
        }
      }

      //create local context of the called function
      element.callFunction(functionEntryNode.getFunctionName());

      //set initialization status of the function's parameters according to the arguments
      for (String param : uninitParameters) {
        setUninitialized(element, param);
      }
      for (String param : initParameters) {
        setInitialized(element, param);
      }

    } else {
      //if there are no parameters, just create the local context
      element.callFunction(functionEntryNode.getFunctionName());
    }
  }

  private void handleStatement(UninitializedVariablesElement element,
                               IASTExpression expression, CFAEdge cfaEdge)
                               throws UnrecognizedCCodeException {

    if ((cfaEdge instanceof StatementEdge) && ((StatementEdge)cfaEdge).isJumpEdge()) {
      //this is the return-statement of a function
      //set a local variable tracking the return statement's initialization status
      if (isExpressionUninitialized(element, expression, cfaEdge)) {
        setUninitialized(element, "CPAChecker_UninitVars_FunctionReturn");
      } else {
        setInitialized(element, "CPAChecker_UninitVars_FunctionReturn");
      }

    } else if (expression instanceof IASTFunctionCallExpression) {
      //in case of a return edge, remove the local context of the function from which we returned
      if (cfaEdge instanceof CallToReturnEdge) {
        element.returnFromFunction();
      }
      //a mere function call (func(a)) does not change the initialization status of variables
      // just check if there are uninitialized variable usages
      if (printWarnings) {
        IASTExpression params = ((IASTFunctionCallExpression)expression).getParameterExpression();
        isExpressionUninitialized(element, params, cfaEdge);
      }

    } else if (expression instanceof IASTUnaryExpression) {
      // a unary operation (a++) does not change the initialization status of variables

      // just check if there are uninitialized variable usages
      if (printWarnings) {
        isExpressionUninitialized(element, expression, cfaEdge);
      }

    } else if (expression instanceof IASTBinaryExpression) {
      // expression is a binary operation, e.g. a = b or a += b;

      IASTBinaryExpression binExpression = (IASTBinaryExpression)expression;

      int typeOfOperator = binExpression.getOperator();
      if (typeOfOperator == IASTBinaryExpression.op_assign) {
        // a = b
        handleAssign(element, binExpression, cfaEdge);

      } else if (
             typeOfOperator == IASTBinaryExpression.op_binaryAndAssign
          || typeOfOperator == IASTBinaryExpression.op_binaryOrAssign
          || typeOfOperator == IASTBinaryExpression.op_binaryXorAssign
          || typeOfOperator == IASTBinaryExpression.op_divideAssign
          || typeOfOperator == IASTBinaryExpression.op_minusAssign
          || typeOfOperator == IASTBinaryExpression.op_moduloAssign
          || typeOfOperator == IASTBinaryExpression.op_multiplyAssign
          || typeOfOperator == IASTBinaryExpression.op_plusAssign
          || typeOfOperator == IASTBinaryExpression.op_shiftLeftAssign
          || typeOfOperator == IASTBinaryExpression.op_shiftRightAssign
          ) {
        // a += b etc.

        String leftName = binExpression.getOperand1().getRawSignature();
        if (element.isUninitialized(leftName)) {
          // a +=5 where a is uninitialized -> everything stays the same
          if (printWarnings) {
            addWarning(cfaEdge, leftName, expression, element);
            // check wether there are further uninitialized variables on right side
            isExpressionUninitialized(element, binExpression.getOperand2(), cfaEdge);
          }

        } else {
          handleAssign(element, binExpression, cfaEdge);
        }

      } else {
        // a + b etc.
        throw new UnrecognizedCCodeException("unknown binary operator", cfaEdge, binExpression);
      }

    } else {
      throw new UnrecognizedCCodeException(cfaEdge, expression);
    }
  }

  private void handleAssign(UninitializedVariablesElement element,
                            IASTBinaryExpression expression, CFAEdge cfaEdge)
                            throws UnrecognizedCCodeException {

    IASTExpression op1 = expression.getOperand1();
    IASTExpression op2 = expression.getOperand2();

    if (op1 instanceof IASTIdExpression) {
      // assignment to simple variable

      String leftName = op1.getRawSignature();

      if (isExpressionUninitialized(element, op2, cfaEdge)) {
        setUninitialized(element, leftName);
      } else {
        setInitialized(element, leftName);
      }


    } else if (op1 instanceof IASTFieldReference) {
      //for field references, don't change the initialization status in case of a pointer dereference
      if (((IASTFieldReference) op1).isPointerDereference()) {
        if (printWarnings) {
          isExpressionUninitialized(element, op1, cfaEdge);
          isExpressionUninitialized(element, op2, cfaEdge);
        }
      } else {
        String leftName = op1.getRawSignature();
        if (isExpressionUninitialized(element, op2, cfaEdge)) {
          setUninitialized(element, leftName);
        } else {
          setInitialized(element, leftName);
        }
      }

    } else if (

        ((op1 instanceof IASTUnaryExpression)
            && (((IASTUnaryExpression)op1).getOperator() == IASTUnaryExpression.op_star))
            || (op1 instanceof IASTArraySubscriptExpression)) {
      // assignment to the target of a pointer or an array element,
      // this does not change the initialization status of the variable

      if (printWarnings) {
        isExpressionUninitialized(element, op1, cfaEdge);
        isExpressionUninitialized(element, op2, cfaEdge);
      }

    } else {
      throw new UnrecognizedCCodeException("unknown left hand side of an assignment", cfaEdge, op1);
    }
  }

  private boolean isExpressionUninitialized(UninitializedVariablesElement element,
                                            IASTExpression expression,
                                            CFAEdge cfaEdge) throws UnrecognizedCCodeException {
    if (expression == null) {
      // e.g. empty parameter list
      return false;

    } else if (expression instanceof IASTIdExpression) {
      String variable = expression.getRawSignature();
      if (element.isUninitialized(variable)) {
        addWarning(cfaEdge, variable, expression, element);
        return true;
      } else {
        return false;
      }

    } else if (expression instanceof IASTTypeIdExpression) {
      // e.g. sizeof
      return false;

    } else if (expression instanceof IASTFieldReference) {
      IASTFieldReference e = (IASTFieldReference) expression;
      if (e.isPointerDereference()) {
        return isExpressionUninitialized(element, e.getFieldOwner(), cfaEdge);
      } else {
        String variable = expression.getRawSignature();
        if (element.isUninitialized(variable)) {
          addWarning(cfaEdge, variable, expression, element);
          return true;
        } else {
          return false;
        }
      }

    } else if (expression instanceof IASTArraySubscriptExpression) {
      IASTArraySubscriptExpression arrayExpression = (IASTArraySubscriptExpression)expression;
      return isExpressionUninitialized(element, arrayExpression.getArrayExpression(), cfaEdge)
           | isExpressionUninitialized(element, arrayExpression.getSubscriptExpression(), cfaEdge);

    } else if (expression instanceof IASTUnaryExpression) {
      IASTUnaryExpression unaryExpression = (IASTUnaryExpression)expression;

      int typeOfOperator = unaryExpression.getOperator();
      if (   (typeOfOperator == IASTUnaryExpression.op_amper)
          || (typeOfOperator == IASTUnaryExpression.op_sizeof)) {
        return false;

      } else {
        return isExpressionUninitialized(element, unaryExpression.getOperand(), cfaEdge);
      }

    } else if (expression instanceof IASTBinaryExpression) {
      IASTBinaryExpression binExpression = (IASTBinaryExpression) expression;
      return isExpressionUninitialized(element, binExpression.getOperand1(), cfaEdge)
           | isExpressionUninitialized(element, binExpression.getOperand2(), cfaEdge);

    } else if (expression instanceof IASTCastExpression) {
      return isExpressionUninitialized(element, ((IASTCastExpression)expression).getOperand(), cfaEdge);

    } else if (expression instanceof IASTFunctionCallExpression) {
      IASTFunctionCallExpression funcExpression = (IASTFunctionCallExpression)expression;
      //if the FunctionCallExpression is associated with a statement edge, then this is
      //an external function call, and call to return edges for external calls are disabled.
      //since we can not know its return value's initialization status, only check the parameters
      if (cfaEdge instanceof StatementEdge) {
        IASTExpression params = funcExpression.getParameterExpression();
        if (printWarnings) {
          isExpressionUninitialized(element, params, cfaEdge);
        }
        return false;

      } else {
        //for an internal function call, we can check the return value - for an external call
        //(with enabled call to return edges), the return value is always assumed to be initialized
        boolean returnUninit = element.isUninitialized("CPAChecker_UninitVars_FunctionReturn");
        if (printWarnings && returnUninit) {
          addWarning(cfaEdge, funcExpression.getRawSignature(), expression, element);
        }
        //get rid of the local context, as it is no longer needed and may be different on the next call.
        //only do this in case of an internal call.
        if (cfaEdge instanceof CallToReturnEdge &&
            !((CallToReturnEdge)cfaEdge).getRawStatement().equals("External Call")) {
          element.returnFromFunction();
        }
        return returnUninit;
      }

    } else if (expression instanceof IASTExpressionList) {
      IASTExpressionList expressionList = (IASTExpressionList)expression;
      boolean result = false;
      for (IASTExpression exp : expressionList.getExpressions()) {
        if (isExpressionUninitialized(element, exp, cfaEdge)) {
          result = true;
        }
      }
      return result;

    } else if (expression instanceof IASTLiteralExpression) {
      return false;

    } else {
      throw new UnrecognizedCCodeException(cfaEdge, expression);
    }
  }

  @Override
  public Collection<AbstractElement> getAbstractSuccessors(
                                           AbstractElement element,
                                           Precision precision, CFAEdge cfaEdge)
                       throws CPATransferException {
    return Collections.singleton(getAbstractSuccessor(element, cfaEdge, precision));
  }

  @Override
  /**
   * strengthen() is only necessary when declaring field variables, so the underlying struct type
   * is properly associated. This can only be done here because information about types is needed, which can
   * only be provided by typesCPA.
   */
  public Collection<? extends AbstractElement> strengthen(AbstractElement element,
                          List<AbstractElement> otherElements, CFAEdge cfaEdge,
                          Precision precision) {
    
    //only call for declarations. check for lastAdded prevents unnecessary repeated executions for the same statement
    boolean typesCPAPresent = false;

    if (cfaEdge.getEdgeType() == CFAEdgeType.DeclarationEdge && lastAdded != null) {

      for (AbstractElement other : otherElements) {

        //only interested in the types here
        if (other instanceof TypesElement) {
          typesCPAPresent = true;

          //find type of the item last added to the list of variables
          TypesElement typeElem = (TypesElement) other;
          Type t = findType(typeElem, cfaEdge, lastAdded);

          if (t != null) {
            //only need to do this for non-external structs: add a variable for each field of the struct
            //and set it uninitialized (since it is only declared at this point); do this recursively for all
            //fields that are structs themselves
            if (t.getTypeClass() == TypeClass.STRUCT &&
                !(((DeclarationEdge)cfaEdge).getDeclSpecifier().getStorageClass() == IASTDeclSpecifier.sc_extern)) {

              handleStructDeclaration((UninitializedVariablesElement)element, typeElem,
                                      (Type.CompositeType)t, lastAdded, lastAdded, 
                                      cfaEdge instanceof GlobalDeclarationEdge);
            }
          }
        }
      }

      if (!typesWarningAlreadyDisplayed && !typesCPAPresent && lastAdded != null) {
        //set typesWarningAlreadyDisplayed so this message only comes up once
        typesWarningAlreadyDisplayed = true;
        logger.log(Level.INFO,
        "TypesCPA not present - information about field references may be unreliable");
      }

      //set lastAdded to null to prevent unnecessary repeats
      lastAdded = null;
    }

    //the following deals with structs being assigned to other structs
    if (cfaEdge.getEdgeType() == CFAEdgeType.StatementEdge) {

      IASTExpression exp = ((StatementEdge)cfaEdge).getExpression();

      if (exp instanceof IASTBinaryExpression) {

        if (((IASTBinaryExpression)exp).getOperator() == IASTBinaryExpression.op_assign) {

          IASTExpression op1 = ((IASTBinaryExpression) exp).getOperand1();
          IASTExpression op2 = ((IASTBinaryExpression) exp).getOperand2();

          String leftName = op1.getRawSignature();
          String rightName = op2.getRawSignature();

          for (AbstractElement other : otherElements) {
            //only interested in the types here
            if (other instanceof TypesElement) {
              typesCPAPresent = true;

              TypesElement typeElem = (TypesElement) other;

              Type t1 = checkForFieldReferenceType(op1, typeElem, cfaEdge);
              Type t2 = checkForFieldReferenceType(op2, typeElem, cfaEdge);

              if (t1 != null && t2 != null) {

                //only interested in structs being assigned to structs here
                if (t1.getTypeClass() == TypeClass.STRUCT
                    && t2.getTypeClass() == TypeClass.STRUCT) {

                  //only structs of the same type can be assigned to each other
                  assert t1.equals(t2);

                  //check all fields of the structures' type and set their status
                  initializeFields((UninitializedVariablesElement)element, cfaEdge, exp, typeElem,
                                   (Type.CompositeType)t1, leftName, rightName, leftName, rightName);
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * recursively checks the initialization status of all fields of a struct being assigned to
   * another struct of the same type, setting the status of the assignee's fields accordingly
   */
  private void initializeFields(UninitializedVariablesElement element,
                                CFAEdge cfaEdge, IASTExpression exp,
                                TypesElement typeElem, Type.CompositeType structType,
                                String leftName, String rightName,
                                String recursiveLeftName, String recursiveRightName) {

    Set<String> members = structType.getMembers();

    //check all members
    for (String member : members) {
      Type t = structType.getMemberType(member);
      //for a field that is itself a struct, repeat the whole process
      if (t != null && t.getTypeClass() == TypeClass.STRUCT) {
        initializeFields(element, cfaEdge, exp, typeElem, (Type.CompositeType)t, member, member,
                         recursiveLeftName + "." + member, recursiveRightName + "." + member);
      //else, check the initialization status of the assigned variable
      //and set the status of the assignee accordingly
      } else {
        if (element.isUninitialized(recursiveRightName + "." + member)) {
          if (printWarnings) {
            addWarning(cfaEdge, recursiveRightName + "." + member, exp, element);
          }
          setUninitialized(element, recursiveLeftName + "." + member);
        }
      }
    }
  }

  /**
   * recursively sets all fields of a struct uninitialized, except if the field is itself a struct
   */
  private void handleStructDeclaration(UninitializedVariablesElement element,
                                       TypesElement typeElem,
                                       Type.CompositeType structType,
                                       String varName,
                                       String recursiveVarName,
                                       boolean isGlobalDeclaration) {
    
    //structs themselves are always considered initialized
    setInitialized(element, recursiveVarName);

    Set<String> members = structType.getMembers();

    for (String member : members) {
      Type t = structType.getMemberType(member);
      //for a field that is itself a struct, repeat the whole process
      if (t != null && t.getTypeClass() == TypeClass.STRUCT) {
        handleStructDeclaration(element, typeElem, (Type.CompositeType)t, member,
                                recursiveVarName + "." + member, isGlobalDeclaration);
      } else {
        //set non structure fields uninitialized, since they have only just been declared
        if (isGlobalDeclaration) {
          globalVars.add(recursiveVarName + "." + member);
        }
        setUninitialized(element, recursiveVarName + "." + member);
      }
    }
  }

  /**
   * checks wether a given expression is a field reference;
   * if yes, find the type of the referenced field, if no, try to determine the type of the variable
   */
  private Type checkForFieldReferenceType(IASTExpression exp, TypesElement typeElem, CFAEdge cfaEdge) {

    String name = exp.getRawSignature();
    Type t = null;

    if (exp instanceof IASTFieldReference) {
      String[] s = name.split("[.]");
      t = findType(typeElem, cfaEdge, s[0]);
      int i = 1;

      //follow the field reference to its end
      while (t != null && t.getTypeClass() == TypeClass.STRUCT && i < s.length) {
        t = ((Type.CompositeType)t).getMemberType(s[i]);
        i++;
      }

    //if exp is not a field reference, simply try to find the type of the associated variable name
    } else {
      t = findType(typeElem, cfaEdge, name);
    }
    return t;
  }

  /**
   * checks all possible locations for type information of a given name
   */
  private Type findType(TypesElement typeElem, CFAEdge cfaEdge, String varName) {
    Type t = null;
    //check type definitions
    t = typeElem.getTypedef(varName);
    //if this fails, check functions
    if (t == null) {
      t = typeElem.getFunction(varName);
    }
    //if this also fails, check variables for the global context
    if (t == null) {
      t = typeElem.getVariableType(null, varName);
    }
    try {
      //if again there was no result, check local variables and function parameters
      if (t == null) {
        t = typeElem.getVariableType(cfaEdge.getSuccessor().getFunctionName(), varName);
      }
    } catch (IllegalArgumentException e) {
      //if nothing at all can be found, just return null
    }
    return t;
  }

}