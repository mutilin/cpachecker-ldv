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
package org.sosy_lab.cpachecker.cpa.explicit;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.AParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.APointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.AUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.AVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.IADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.IAExpression;
import org.sosy_lab.cpachecker.cfa.ast.IAInitializer;
import org.sosy_lab.cpachecker.cfa.ast.IARightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.IAStatement;
import org.sosy_lab.cpachecker.cfa.ast.IAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.java.JBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JEnumConstantExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JFieldAccess;
import org.sosy_lab.cpachecker.cfa.ast.java.JFieldDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.java.JSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.java.JArrayType;
import org.sosy_lab.cpachecker.cfa.types.java.JClassOrInterfaceType;
import org.sosy_lab.cpachecker.cfa.types.java.JSimpleType;
import org.sosy_lab.cpachecker.cfa.types.java.JType;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.cpa.explicit.ExplicitState.MemoryLocation;
import org.sosy_lab.cpachecker.cpa.rtt.RTTState;
import org.sosy_lab.cpachecker.cpa.smg.SMGState;
import org.sosy_lab.cpachecker.cpa.smg.SMGTransferRelation.SMGAddressValue;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCCodeException;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

@Options(prefix="cpa.explicit")
public class ExplicitTransferRelation extends ForwardingTransferRelation<ExplicitState, ExplicitPrecision> {
  // set of functions that may not appear in the source code
  // the value of the map entry is the explanation for the user
  private static final Map<String, String> UNSUPPORTED_FUNCTIONS
      = ImmutableMap.of("pthread_create", "threads");

  @Option(description = "if there is an assumption like (x!=0), "
      + "this option sets unknown (uninitialized) variables to 1L, "
      + "when the true-branch is handled.")
  private boolean initAssumptionVars = false;

  private final Set<String> globalVariables = new HashSet<>();

  private final Set<String> javaNonStaticVariables = new HashSet<>();

  /**
   * name for the special variable used as container for return values of functions
   */
  public static final String FUNCTION_RETURN_VAR = "___cpa_temp_result_var_";

  private JRightHandSide missingInformationRightJExpression = null;
  private String missingInformationLeftJVariable = null;

  private boolean missingFieldVariableObject;
  private Pair<String, Long> fieldNameAndInitialValue;

  private boolean missingScopedFieldName;
  private JIdExpression notScopedField;
  private Long notScopedFieldValue;

  private boolean missingAssumeInformation;

  /**
   * This List is used to communicate the missing
   * Information needed from other cpas.
   * (at the moment specifically SMG)
   */
  private List<MissingInformation> missingInformationList;

  /**
   * Save the old State for strengthen.
   */
  private ExplicitState oldState;

  private final MachineModel machineModel;
  private final LogManager logger;
  private final Multimap<String, String> addressedVariables;

  public ExplicitTransferRelation(Configuration config, LogManager pLogger, CFA pCfa) throws InvalidConfigurationException {
    config.inject(this);
    machineModel = pCfa.getMachineModel();
    logger = pLogger;

    if (pCfa.getVarClassification().isPresent()) {
      addressedVariables = pCfa.getVarClassification().get().getAddressedVariables();
    } else {
      addressedVariables = ImmutableMultimap.of();
    }
  }

  @Override
  protected void postProcessing(ExplicitState successor) {
    if (successor != null){
      successor.addToDelta(state);
    }
  }


  @Override
  protected void setInfo(AbstractState pAbstractState,
      Precision pAbstractPrecision, CFAEdge pCfaEdge) {
    super.setInfo(pAbstractState, pAbstractPrecision, pCfaEdge);
    // More than 5 function parameters is sufficiently seldom.
    // For any other cfaEdge we need only a list of length 1.
    // In principle it is unnecessary to always create a new list
    // but I'm not sure of the behavior of calling strengthen, so
    // it is more secure.
    missingInformationList = new ArrayList<>(5);
    oldState = ((ExplicitState) pAbstractState).clone();
  }

  @Override
  protected ExplicitState handleMultiEdge(final MultiEdge cfaEdge) throws CPATransferException {
    // we need to keep the old state,
    // because the analysis uses a 'delta' for the now state
    final ExplicitState backup = state;
    for (CFAEdge edge : cfaEdge) {
      state = handleSimpleEdge(edge);
    }
    final ExplicitState successor = state;
    state = backup;
    return successor;
  }

  @Override
  protected ExplicitState handleFunctionCallEdge(FunctionCallEdge callEdge,
      List<? extends IAExpression> arguments, List<? extends AParameterDeclaration> parameters,
      String calledFunctionName) throws UnrecognizedCCodeException {
    ExplicitState newElement = state.clone();

    if (!callEdge.getSuccessor().getFunctionDefinition().getType().takesVarArgs()) {
      assert (parameters.size() == arguments.size());
    }

    // visitor for getting the values of the actual parameters in caller function context
    final ExplicitExpressionValueVisitor visitor = getVisitor();

    // get value of actual parameter in caller function context
    for (int i = 0; i < parameters.size(); i++) {
      Long value;
      IAExpression exp = arguments.get(i);

      if (exp instanceof JExpression) {
        value = ((JExpression) exp).accept(visitor);
      } else if (exp instanceof CExpression) {
        value = visitor.evaluate((CExpression) exp, (CType) parameters.get(i).getType());
      } else {
        throw new AssertionError("unknown expression: " + exp);
      }

      String paramName = parameters.get(i).getName();

      MemoryLocation formalParamName = MemoryLocation.valueOf(calledFunctionName, paramName, 0);

      if (value == null) {
        newElement.forget(formalParamName);

        if(isMissingCExpressionInformation(visitor, exp)) {
          addMissingInformation(formalParamName, exp);
        }
      } else {
        newElement.assignConstant(formalParamName, value);
      }

      visitor.reset();

    }

    return newElement;
  }

  @Override
  protected ExplicitState handleReturnStatementEdge(AReturnStatementEdge returnEdge, IAExpression expression)
          throws UnrecognizedCCodeException {

    if (expression == null) {
      expression = CNumericTypes.ZERO; // this is the default in C
    }


    MemoryLocation functionReturnVar = MemoryLocation.valueOf(functionName, FUNCTION_RETURN_VAR, 0);

    return handleAssignmentToVariable(functionReturnVar,
        returnEdge.getSuccessor().getEntryNode().getFunctionDefinition().getType().getReturnType(), // TODO easier way to get type?
        expression,
        getVisitor());
  }

  /**
   * Handles return from one function to another function.
   * @param functionReturnEdge return edge from a function to its call site
   * @return new abstract state
   */
  @Override
  protected ExplicitState handleFunctionReturnEdge(FunctionReturnEdge functionReturnEdge,
      FunctionSummaryEdge summaryEdge, AFunctionCall exprOnSummary, String callerFunctionName)
    throws UnrecognizedCodeException {

    ExplicitState newElement  = state.clone();

    // expression is an assignment operation, e.g. a = g(b);

    if (exprOnSummary instanceof AFunctionCallAssignmentStatement) {
      AFunctionCallAssignmentStatement assignExp = ((AFunctionCallAssignmentStatement)exprOnSummary);
      IAExpression op1 = assignExp.getLeftHandSide();

      // we expect left hand side of the expression to be a variable

      if (op1 instanceof CLeftHandSide) {
        MemoryLocation returnVarName = MemoryLocation.valueOf(functionName, FUNCTION_RETURN_VAR, 0);

        ExplicitExpressionValueVisitor v =
            new ExplicitExpressionValueVisitor(state, callerFunctionName,
                machineModel, logger, edge);

        MemoryLocation assignedVarName = v.evaluateMemoryLocation((CLeftHandSide) op1);

        boolean valueExists = state.contains(returnVarName);

        if (assignedVarName == null) {
          if (v.hasMissingPointer() && valueExists) {
            Long value = state.getValueFor(returnVarName);
            addMissingInformation((CLeftHandSide) op1, value);
          }
        } else if (!valueExists) {
          newElement.forget(assignedVarName);
        } else {
          Long value = state.getValueFor(returnVarName);
          newElement.assignConstant(assignedVarName, value);
        }

      } else if ((op1 instanceof AIdExpression)) {
        String returnVarName = getScopedVariableName(FUNCTION_RETURN_VAR, functionName);

        String assignedVarName = getScopedVariableName(op1, callerFunctionName);

        if (!state.contains(returnVarName)) {
          newElement.forget(assignedVarName);
        } else if (op1 instanceof JIdExpression && ((JIdExpression) op1).getDeclaration() instanceof JFieldDeclaration && !((JFieldDeclaration) ((JIdExpression) op1).getDeclaration()).isStatic()) {
          missingScopedFieldName = true;
          notScopedField = (JIdExpression) op1;
          notScopedFieldValue = state.getValueFor(returnVarName);
        } else {
          newElement.assignConstant(assignedVarName, state.getValueFor(returnVarName));
        }
      }

      // a* = b(); TODO: for now, nothing is done here, but cloning the current element
      else if (op1 instanceof APointerExpression) {
      }
      else {
        throw new UnrecognizedCodeException("on function return", summaryEdge, op1);
      }
    }

    newElement.dropFrame(functionName);
    return newElement;
  }

  @Override
  protected ExplicitState handleAssumption(AssumeEdge cfaEdge, IAExpression expression, boolean truthValue)
    throws UnrecognizedCCodeException {

    ExplicitExpressionValueVisitor evv = getVisitor();

    // get the value of the expression (either true[1L], false[0L], or unknown[null])
    Long value = getExpressionValue(expression, CNumericTypes.INT, evv);

    // value is null, try to derive further information
    if (value == null) {

      ExplicitState element = state.clone();
      AssigningValueVisitor avv = new AssigningValueVisitor(element, truthValue);

      if (expression instanceof JExpression && ! (expression instanceof CExpression)) {

        ((JExpression) expression).accept(avv);

        if (avv.hasMissingFieldAccessInformation() || avv.hasMissingEnumComparisonInformation()) {
          assert missingInformationRightJExpression != null;
          missingAssumeInformation = true;
        }

      } else {
        ((CExpression)expression).accept(avv);
      }

      if (isMissingCExpressionInformation(evv, expression)) {
        missingInformationList.add(new MissingInformation(truthValue, expression));
      }

      return element;

    } else if ((truthValue && value == 1L) || (!truthValue && value == 0L)) {
      // we do not know more than before, and the assumption is fulfilled,
      // so return the old state
      return state;

    } else {
      // assumption not fulfilled
      return null;
    }
  }


  @Override
  protected ExplicitState handleDeclarationEdge(ADeclarationEdge declarationEdge, IADeclaration declaration)
    throws UnrecognizedCCodeException {

    if (!(declaration instanceof AVariableDeclaration)
        || (declaration.getType() instanceof JType && !(declaration.getType() instanceof JSimpleType))) {
      // nothing interesting to see here, please move along
      return state;
    }

    ExplicitState newElement = state.clone();
    AVariableDeclaration decl = (AVariableDeclaration)declaration;

    // get the variable name in the declarator
    String varName = decl.getName();

    Long initialValue = null;

    // get initial value
    IAInitializer init = decl.getInitializer();

    // handle global variables
    if (decl.isGlobal()) {
      // if this is a global variable, add to the list of global variables
      globalVariables.add(varName);

      if (decl instanceof JFieldDeclaration && !((JFieldDeclaration)decl).isStatic()) {
        missingFieldVariableObject = true;
        javaNonStaticVariables.add(varName);
      }

      // global variables without initializer are set to 0 in C
      if (init == null) {
        initialValue = 0L;
      }
    }

    MemoryLocation memoryLocation;

    // assign initial value if necessary
    if(decl.isGlobal()) {
      memoryLocation = MemoryLocation.valueOf(varName,0);
    }else {
      memoryLocation = MemoryLocation.valueOf(functionName, varName, 0);
    }

    if (addressedVariables.containsEntry(decl.isGlobal() ? null : functionName,
                                         varName)
        && decl.getType() instanceof CType
        && ((CType)decl.getType()).getCanonicalType() instanceof CPointerType) {
      ExplicitState.addToBlacklist(memoryLocation);
    }

    if (init instanceof AInitializerExpression) {

      ExplicitExpressionValueVisitor evv = getVisitor();
      IAExpression exp = ((AInitializerExpression) init).getExpression();
      initialValue = getExpressionValue(exp, decl.getType(), evv);

      if (isMissingCExpressionInformation(evv, exp)) {
        addMissingInformation(memoryLocation, exp);
      }
    }

    boolean complexType = decl.getType() instanceof JClassOrInterfaceType || decl.getType() instanceof JArrayType;


    if (!complexType  && (missingInformationRightJExpression != null || initialValue != null)) {
      if (missingFieldVariableObject) {
        fieldNameAndInitialValue = Pair.of(varName, initialValue);
      } else if (missingInformationRightJExpression == null) {
        newElement.assignConstant(memoryLocation, initialValue);
      } else {
        missingInformationLeftJVariable = memoryLocation.getAsSimpleString();
      }
    } else {

      // If variable not tracked, its Object is irrelevant
      missingFieldVariableObject = false;
      newElement.forget(memoryLocation);
    }

    return newElement;
  }

  private boolean isMissingCExpressionInformation(ExplicitExpressionValueVisitor pEvv,
      IARightHandSide pExp) {

    return pExp instanceof CExpression && (pEvv.hasMissingPointer());
  }

  @Override
  protected ExplicitState handleStatementEdge(AStatementEdge cfaEdge, IAStatement expression)
    throws UnrecognizedCodeException {

    if (expression instanceof CFunctionCall) {
      CExpression fn = ((CFunctionCall)expression).getFunctionCallExpression().getFunctionNameExpression();
      if (fn instanceof CIdExpression) {
        String func = ((CIdExpression)fn).getName();
        if (UNSUPPORTED_FUNCTIONS.containsKey(func)) {
          throw new UnsupportedCCodeException(UNSUPPORTED_FUNCTIONS.get(func), cfaEdge, fn);
        } else if(func.equals("free")) {
          // Needed for erasing values
          missingInformationList.add(new MissingInformation(((CFunctionCall)expression).getFunctionCallExpression()));
        }
      }
    }

    // expression is a binary operation, e.g. a = b;

    if (expression instanceof IAssignment) {
      return handleAssignment((IAssignment)expression, cfaEdge);

    // external function call - do nothing
    } else if (expression instanceof AFunctionCallStatement) {

    // there is such a case
    } else if (expression instanceof AExpressionStatement) {

    } else {
      throw new UnrecognizedCodeException("Unknown statement", cfaEdge, expression);
    }

    return state;
  }


  private ExplicitState handleAssignment(IAssignment assignExpression, CFAEdge cfaEdge)
    throws UnrecognizedCodeException {
    IAExpression op1    = assignExpression.getLeftHandSide();
    IARightHandSide op2 = assignExpression.getRightHandSide();


    if (op1 instanceof AIdExpression) {
      // a = ...

        if (op1 instanceof JIdExpression && ((JIdExpression) op1).getDeclaration() instanceof JFieldDeclaration && !((JFieldDeclaration) ((JIdExpression) op1).getDeclaration()).isStatic()) {
          missingScopedFieldName = true;
          notScopedField = (JIdExpression) op1;
        }

        String varName = ((AIdExpression) op1).getName();

        MemoryLocation memloc;

        if(isGlobal(op1)) {
          memloc = MemoryLocation.valueOf(varName, 0);
        } else {
          memloc = MemoryLocation.valueOf(functionName, varName, 0);
        }

        return handleAssignmentToVariable(memloc, op1.getExpressionType(), op2, getVisitor());
    } else if (op1 instanceof APointerExpression) {
      // *a = ...

      if (isRelevant(op1, op2)) {
        missingInformationList.add(new MissingInformation(op1, op2));
      }

      op1 = ((APointerExpression)op1).getOperand();

      // Cil produces code like
      // *((int*)__cil_tmp5) = 1;
      // so remove cast
      if (op1 instanceof CCastExpression) {
        op1 = ((CCastExpression)op1).getOperand();
      }
    }

    else if (op1 instanceof CFieldReference) {

      ExplicitExpressionValueVisitor v = getVisitor();

      MemoryLocation memLoc = v.evaluateMemoryLocation((CFieldReference) op1);

      if (v.hasMissingPointer() && isRelevant(op1, op2)) {
        missingInformationList.add(new MissingInformation(op1, op2));
      }

      if (memLoc != null) {
        return handleAssignmentToVariable(memLoc, op1.getExpressionType(), op2, v);
      }
    }

    else if (op1 instanceof CArraySubscriptExpression || op1 instanceof AArraySubscriptExpression) {
      // array cell
      if (op1 instanceof CArraySubscriptExpression) {

        ExplicitExpressionValueVisitor v = getVisitor();

        MemoryLocation memLoc = v.evaluateMemoryLocation((CLeftHandSide) op1);

        if (v.hasMissingPointer() && isRelevant(op1, op2)) {
          missingInformationList.add(new MissingInformation(op1, op2));
        }

        if (memLoc != null) {
          return handleAssignmentToVariable(memLoc, op1.getExpressionType(), op2, v);
        }
      }
    } else {
      throw new UnrecognizedCodeException("left operand of assignment has to be a variable", cfaEdge, op1);
    }

    return state; // the default return-value is the old state
  }


  private boolean isRelevant(IAExpression pOp1, IARightHandSide pOp2) {
    return pOp1 instanceof CExpression && pOp2 instanceof CExpression;
  }

  /** This method analyses the expression with the visitor and assigns the value to lParam.
   * The method returns a new state, that contains (a copy of) the old state and the new assignment. */
  private ExplicitState handleAssignmentToVariable(
      MemoryLocation assignedVar, final Type lType, IARightHandSide exp, ExplicitExpressionValueVisitor visitor)
      throws UnrecognizedCCodeException {

    Long value;
    if (exp instanceof JRightHandSide) {
       value = ((JRightHandSide) exp).accept(visitor);
    } else if (exp instanceof CRightHandSide) {
       value = visitor.evaluate((CRightHandSide) exp, (CType) lType);
    } else {
      throw new AssertionError("unknown righthandside-expression: " + exp);
    }

    if (visitor.hasMissingPointer()) {
      assert value == null;
    }

    if (isMissingCExpressionInformation(visitor, exp)) {
      // Evaluation
      addMissingInformation(assignedVar, exp);
    }

    // here we clone the state, because we get new information or must forget it.
    ExplicitState newElement = state.clone();

    if (visitor.hasMissingFieldAccessInformation() || visitor.hasMissingEnumComparisonInformation()) {
      // This may happen if an object of class is created which could not be parsed,
      // In  such a case, forget about it
      if (value != null) {
        newElement.forget(assignedVar);
        return newElement;
      } else {
        missingInformationRightJExpression = (JRightHandSide) exp;
        if (!missingScopedFieldName) {
          missingInformationLeftJVariable = assignedVar.getAsSimpleString();
        }
      }
    }

    if (missingScopedFieldName) {
      notScopedFieldValue = value;
    } else {
      // some heuristics to clear wrong information
      // when a struct or a pointer to one is assigned
      // TODO not implemented in SMG version of ExplicitCPA
//      newElement.forgetAllWithPrefix(assignedVar + ".");
//      newElement.forgetAllWithPrefix(assignedVar + "->");

      if (value == null) {
        // Don't erase it when there if it has yet to be evaluated
        if (missingInformationRightJExpression == null) {
          // TODO HasToBeErased Later
         newElement.forget(assignedVar);
        }
      } else {
        newElement.assignConstant(assignedVar, value);
      }

    }
    return newElement;
  }

  private void addMissingInformation(MemoryLocation pMemLoc, IARightHandSide pExp) {
    if (pExp instanceof CExpression) {

      missingInformationList.add(new MissingInformation(pMemLoc,
          (CExpression) pExp));
    }
  }

  private void addMissingInformation(CLeftHandSide pOp1, Long pValue) {
    missingInformationList.add(new MissingInformation(pOp1, pValue));

  }

  /**
   * Visitor that derives further information from an assume edge
   */
  private class AssigningValueVisitor extends ExplicitExpressionValueVisitor {

    private ExplicitState assignableState;
    protected boolean truthValue = false;

    public AssigningValueVisitor(ExplicitState assignableState, boolean truthValue) {
      super(state, functionName, machineModel, logger, edge);
      this.assignableState = assignableState;
      this.truthValue = truthValue;
    }

    private IAExpression unwrap(IAExpression expression) {
      // is this correct for e.g. [!a != !(void*)(int)(!b)] !?!?!

      if (expression instanceof AUnaryExpression) {
        AUnaryExpression exp = (AUnaryExpression)expression;
        if (exp.getOperator() == UnaryOperator.NOT) { // TODO why only C-UnaryOperator?
          expression = exp.getOperand();
          truthValue = !truthValue;

          expression = unwrap(expression);
        }
      }

      if (expression instanceof CCastExpression) {
        CCastExpression exp = (CCastExpression)expression;
        expression = exp.getOperand();

        expression = unwrap(expression);
      }

      return expression;
    }

    @Override
    public Long visit(CBinaryExpression pE) throws UnrecognizedCCodeException {
      BinaryOperator binaryOperator   = pE.getOperator();

      CExpression lVarInBinaryExp  = pE.getOperand1();

      lVarInBinaryExp = (CExpression) unwrap(lVarInBinaryExp);

      CExpression rVarInBinaryExp  = pE.getOperand2();

      Long leftValue                  = lVarInBinaryExp.accept(this);
      Long rightValue                 = rVarInBinaryExp.accept(this);

      if ((binaryOperator == BinaryOperator.EQUALS && truthValue) || (binaryOperator == BinaryOperator.NOT_EQUALS && !truthValue)) {
        if (leftValue == null &&  rightValue != null && isAssignable(lVarInBinaryExp)) {
          MemoryLocation leftVariableLocation = getMemoryLocation(lVarInBinaryExp);
          assignableState.assignConstant(leftVariableLocation, rightValue);
        }

        else if (rightValue == null && leftValue != null && isAssignable(rVarInBinaryExp)) {
          MemoryLocation rightVariableName = getMemoryLocation(rVarInBinaryExp);
          assignableState.assignConstant(rightVariableName, leftValue);
        }
      }

      if (initAssumptionVars) {
        // x is unknown, a binaryOperation (x!=0), true-branch: set x=1L
        // x is unknown, a binaryOperation (x==0), false-branch: set x=1L
        if ((binaryOperator == BinaryOperator.NOT_EQUALS && truthValue)
            || (binaryOperator == BinaryOperator.EQUALS && !truthValue)) {
          if (leftValue == null && rightValue == 0L && isAssignable(lVarInBinaryExp)) {
            MemoryLocation leftVariableName = getMemoryLocation(lVarInBinaryExp);
            assignableState.assignConstant(leftVariableName, 1L);
          }

          else if (rightValue == null && leftValue == 0L && isAssignable(rVarInBinaryExp)) {
            MemoryLocation rightVariableName = getMemoryLocation(rVarInBinaryExp);
            assignableState.assignConstant(rightVariableName, 1L);
          }
        }
      }
      return super.visit(pE);
    }

    @Override
    public Long visit(JBinaryExpression pE) {
      JBinaryExpression.BinaryOperator binaryOperator   = pE.getOperator();

      JExpression lVarInBinaryExp  = pE.getOperand1();

      lVarInBinaryExp = (JExpression) unwrap(lVarInBinaryExp);

      JExpression rVarInBinaryExp  = pE.getOperand2();

      Long leftValue                  = lVarInBinaryExp.accept(this);
      Long rightValue                 = rVarInBinaryExp.accept(this);

      if ((binaryOperator == JBinaryExpression.BinaryOperator.EQUALS && truthValue) || (binaryOperator == JBinaryExpression.BinaryOperator.NOT_EQUALS && !truthValue)) {
        if (leftValue == null &&  rightValue != null && isAssignable(lVarInBinaryExp)) {

          String leftVariableName = getScopedVariableName(lVarInBinaryExp, functionName);
          assignableState.assignConstant(leftVariableName, rightValue);
        } else if (rightValue == null && leftValue != null && isAssignable(rVarInBinaryExp)) {
          String rightVariableName = getScopedVariableName(rVarInBinaryExp, functionName);
          assignableState.assignConstant(rightVariableName, leftValue);

        }
      }

      if (initAssumptionVars) {
        // x is unknown, a binaryOperation (x!=0), true-branch: set x=1L
        // x is unknown, a binaryOperation (x==0), false-branch: set x=1L
        if ((binaryOperator == JBinaryExpression.BinaryOperator.NOT_EQUALS && truthValue)
            || (binaryOperator == JBinaryExpression.BinaryOperator.EQUALS && !truthValue)) {
          if (leftValue == null && rightValue == 0L && isAssignable(lVarInBinaryExp)) {
            String leftVariableName = getScopedVariableName(lVarInBinaryExp, functionName);
            assignableState.assignConstant(leftVariableName, 1L);

          }

          else if (rightValue == null && leftValue == 0L && isAssignable(rVarInBinaryExp)) {
            String rightVariableName = getScopedVariableName(rVarInBinaryExp, functionName);
            assignableState.assignConstant(rightVariableName, 1L);
          }
        }
      }
      return super.visit(pE);
    }

    protected MemoryLocation getMemoryLocation(CExpression pLValue) throws UnrecognizedCCodeException {
      ExplicitExpressionValueVisitor v = getVisitor();
      assert pLValue instanceof CLeftHandSide;
      return checkNotNull(v.evaluateMemoryLocation(pLValue));
    }

    protected boolean isAssignable(JExpression expression) {

      boolean result = false;

      if (expression instanceof JIdExpression) {

        JSimpleDeclaration decl = ((JIdExpression) expression).getDeclaration();

        if (decl == null) {
          result = false;
        } else if (decl instanceof JFieldDeclaration) {
          result = ((JFieldDeclaration) decl).isStatic();
        } else {
          result = true;
        }
      }

      return result;
    }



    protected boolean isAssignable(CExpression expression) throws UnrecognizedCCodeException  {

      if (expression instanceof CIdExpression) {
        return true;
      }

      if (expression instanceof CFieldReference || expression instanceof CArraySubscriptExpression) {
        ExplicitExpressionValueVisitor evv = getVisitor();
        return evv.canBeEvaluated(expression);
      }

      return false;
    }
  }


  private class SMGAssigningValueVisitor extends AssigningValueVisitor {

    private final SMGExplicitCommunicator expressionEvaluator;
    @SuppressWarnings("unused")
    private final SMGState smgState;

    public SMGAssigningValueVisitor(
        ExplicitState pAssignableState,
        boolean pTruthValue,
        SMGState pSmgState) {

      super(pAssignableState, pTruthValue);
      checkNotNull(pSmgState);
      expressionEvaluator = new SMGExplicitCommunicator(pAssignableState, functionName,
          pSmgState, machineModel, logger, edge);
      smgState = pSmgState;
    }

    @Override
    protected boolean isAssignable(CExpression pExpression) throws UnrecognizedCCodeException {

      //TODO Ugly, Refactor
      if (pExpression instanceof CLeftHandSide) {
        MemoryLocation memLoc =
            expressionEvaluator.evaluateLeftHandSide(pExpression);

        return memLoc != null;
      }

      return false;
    }

    @Override
    protected MemoryLocation getMemoryLocation(CExpression pLValue) throws UnrecognizedCCodeException {
      return expressionEvaluator.evaluateLeftHandSide(pLValue);
    }
  }

  private class  FieldAccessExpressionValueVisitor extends ExplicitExpressionValueVisitor {
    private final RTTState jortState;

    public FieldAccessExpressionValueVisitor(RTTState pJortState) {
      super(state, functionName, machineModel, logger, edge);
      jortState = pJortState;
    }

    @Override
    public Long visit(JBinaryExpression binaryExpression) {

      if ((binaryExpression.getOperator() == JBinaryExpression.BinaryOperator.EQUALS
          || binaryExpression.getOperator() == JBinaryExpression.BinaryOperator.NOT_EQUALS)
          && (binaryExpression.getOperand1() instanceof JEnumConstantExpression
              ||  binaryExpression.getOperand2() instanceof JEnumConstantExpression)) {
        return handleEnumComparison(
            binaryExpression.getOperand1(),
            binaryExpression.getOperand2(), binaryExpression.getOperator());
      }

      return super.visit(binaryExpression);
    }

    private Long handleEnumComparison(JExpression operand1, JExpression operand2,
        JBinaryExpression.BinaryOperator operator) {

      String value1;
      String value2;

      if (operand1 instanceof JEnumConstantExpression) {
        value1 = ((JEnumConstantExpression) operand1).getConstantName();
      } else if (operand1 instanceof JIdExpression) {
        String scopedVarName = handleIdExpression((JIdExpression) operand1);

        if (jortState.contains(scopedVarName)) {
          String uniqueObject = jortState.getUniqueObjectFor(scopedVarName);

          if (jortState.getConstantsMap().containsValue(uniqueObject)) {
            value1 = jortState.getRunTimeClassOfUniqueObject(uniqueObject);
          } else {
            return null;
          }
        } else {
          return null;
        }
      } else {
        return null;
      }


      if (operand2 instanceof JEnumConstantExpression) {
        value2 = ((JEnumConstantExpression) operand2).getConstantName();
      } else if (operand1 instanceof JIdExpression) {
        String scopedVarName = handleIdExpression((JIdExpression) operand2);

        if (jortState.contains(scopedVarName)) {
          String uniqueObject = jortState.getUniqueObjectFor(scopedVarName);

          if (jortState.getConstantsMap().containsValue(uniqueObject)) {
            value2 = jortState.getRunTimeClassOfUniqueObject(uniqueObject);
          } else {
            return null;
          }
        } else {
          return null;
        }
      } else {
        return null;
      }

      boolean result = value1.equals(value2);

      switch (operator) {
      case EQUALS:   break;
      case NOT_EQUALS: result = !result;
      }

      return  result ? 1L : 0L;
    }

    private String handleIdExpression(JIdExpression expr) {

      JSimpleDeclaration decl = expr.getDeclaration();

      if (decl == null) {
        return null;
      }

      String objectScope = getObjectScope(jortState, functionName, expr);

      return getRTTScopedVariableName(decl, functionName, objectScope);

    }

    @Override
    public Long visit(JIdExpression idExp) {

      String varName = handleIdExpression(idExp);

      if (state.contains(varName)) {
        return state.getValueFor(varName);
      } else {
        return null;
      }
    }
  }

  private Long getExpressionValue(IAExpression expression, final Type type, ExplicitExpressionValueVisitor evv)
      throws UnrecognizedCCodeException {

    if (expression instanceof JRightHandSide) {

      final Long value = ((JRightHandSide) expression).accept(evv);

      if (evv.hasMissingFieldAccessInformation() || evv.hasMissingEnumComparisonInformation()) {
        missingInformationRightJExpression = (JRightHandSide) expression;
        return null;
      } else {
        return value;
      }
    } else if (expression instanceof CRightHandSide) {
      return evv.evaluate((CRightHandSide) expression, (CType) type);
    } else {
      throw new AssertionError("unhandled righthandside-expression: " + expression);
    }
  }

  public String getScopedVariableName(String variableName, String functionName) {

    if (globalVariables.contains(variableName)) {
      return variableName;
    }

    return functionName + "::" + variableName;
  }

  public String getScopedVariableName(IAExpression variableName, String functionName) {

    if (isGlobal(variableName)) {
      return variableName.toASTString();
    }

    return functionName + "::" + variableName.toASTString();
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements, CFAEdge cfaEdge, Precision precision)
    throws CPATransferException {
    assert element instanceof ExplicitState;

    super.setInfo(element, precision, cfaEdge);

    Collection<? extends AbstractState> retVal = null;

    for (AbstractState ae : elements) {
      if (ae instanceof RTTState) {
        retVal =  strengthen((RTTState)ae);
        break;
      } else if(ae instanceof SMGState) {
        retVal = strengthen((SMGState) ae);
      } else if(ae instanceof AutomatonState) {
        retVal = strengthen((AutomatonState) ae, cfaEdge);
      }
    }

    super.resetInfo();
    oldState = null;

    return retVal;
  }

  private Collection<? extends AbstractState> strengthen(AutomatonState pAutomatonState, CFAEdge pCfaEdge) throws CPATransferException {

    CIdExpression retVarName = new CIdExpression(null, new CSimpleType(false, false, CBasicType.INT, false, false, false, false, false, false, false), "___cpa_temp_result_var_", null);

    List<CAssumeEdge> assumeEdges = pAutomatonState.getAsAssumeEdges(retVarName, pCfaEdge.getPredecessor().getFunctionName());

    ExplicitState state = this.state;


    for(AssumeEdge assumeEdge : assumeEdges) {
      state = this.handleAssumption(assumeEdge, assumeEdge.getExpression(), assumeEdge.getTruthAssumption());

      if(state == null) {
        break;
      } else {
        setInfo(state, precision, pCfaEdge);
      }
    }

    if (state == null) {
      return Collections.emptyList();
    } else {
      return Collections.singleton(state);
    }
  }

  private Collection<? extends AbstractState> strengthen(SMGState smgState) throws UnrecognizedCCodeException {

    ExplicitState newElement = state.clone();

    //TODO Refactor

    for (MissingInformation missingInformation : missingInformationList) {
      if (missingInformation.isMissingAssumption()) {
        newElement = resolvingAssumption(newElement, smgState, missingInformation);
      } else if (missingInformation.isMissingAssignment()) {
        if (isRelevant(missingInformation)) {
          newElement = resolvingAssignment(newElement, smgState, missingInformation);
        } else {
          // We have to forget Nonrelevant Information to not contradict SMGState.
          newElement = forgetMemLoc(newElement, missingInformation, smgState);
        }
      } else if(missingInformation.isFreeInvocation()) {
        newElement = resolveFree(newElement, smgState, missingInformation);
      }
    }

    //TODO More common handling of missing information (erase missing Information if other cpas solved it).
    missingInformationList.clear();

    if(newElement == null) {
      return new HashSet<>();
    }

    return state.equals(newElement) ? null : Collections.singleton(newElement);
  }

  private ExplicitState resolveFree(ExplicitState pNewElement, SMGState pSmgState,
      MissingInformation pMissingInformation) throws UnrecognizedCCodeException {

    CFunctionCallExpression functionCall = pMissingInformation.getMissingFreeInvocation();

    CExpression pointerExp;

    try {
      pointerExp = functionCall.getParameterExpressions().get(0);
    } catch (IndexOutOfBoundsException e) {
      logger.logDebugException(e);
      throw new UnrecognizedCCodeException("Bulit in function free has no parameter", edge, functionCall);
    }

    SMGExplicitCommunicator cc = new SMGExplicitCommunicator(pNewElement, functionName, pSmgState,
        machineModel, logger, edge);

    SMGAddressValue address;
    try {
      address = cc.evaluateSMGAddressExpression(pointerExp);
    } catch (CPATransferException e) {
      logger.logDebugException(e);
      throw new UnrecognizedCCodeException("Error while evaluating free pointer exception.", edge, functionCall);
    }

    if (address.isUnknown()) {
      //TODO if sound Option is implemented, here every heap value has to be erased.
      return pNewElement;
    }

    pNewElement.forgetValuesWithIdentifier(address.getObject().getLabel());

    return pNewElement;
  }

  private ExplicitState forgetMemLoc(ExplicitState pNewElement, MissingInformation pMissingInformation,
      SMGState pSmgState) throws UnrecognizedCCodeException {

    MemoryLocation memoryLocation = null;

    if (pMissingInformation.hasKnownMemoryLocation()) {
      memoryLocation = pMissingInformation.getcLeftMemoryLocation();
    } else if (pMissingInformation.hasUnknownMemoryLocation()) {
      memoryLocation = resolveMemoryLocation(pSmgState,
          pMissingInformation.getMissingCLeftMemoryLocation());
    }

    if (memoryLocation == null) {
      // Always return the new Element
      // if you want to interrupt the calculation
      // in case it was changed before
      return pNewElement;
    } else {
      pNewElement.forget(memoryLocation);
      return pNewElement;
    }
  }

  private boolean isRelevant(MissingInformation missingInformation) {

    CRightHandSide value;

    if (missingInformation.hasUnknownMemoryLocation()) {
      value = missingInformation.getMissingCLeftMemoryLocation();
    } else if (missingInformation.hasUnknownValue()) {
      value = missingInformation.getMissingCExpressionInformation();
    } else {
      return false;
    }

    CType type = value.getExpressionType().getCanonicalType();

    return !(type instanceof CPointerType);
  }

  //TODO Better Name, these are not just Assignments, but also calls, etc
  private ExplicitState resolvingAssignment(ExplicitState pNewElement,
      SMGState pSmgState, MissingInformation pMissingInformation) throws UnrecognizedCCodeException {

    MemoryLocation memoryLocation = null;

    if (pMissingInformation.hasKnownMemoryLocation()) {
      memoryLocation = pMissingInformation.getcLeftMemoryLocation();
    } else if (pMissingInformation.hasUnknownMemoryLocation()) {
      memoryLocation = resolveMemoryLocation(pSmgState,
          pMissingInformation.getMissingCLeftMemoryLocation());
    }

    if (memoryLocation == null) {
      // Always return the new Element
      // if you want to interrupt the calculation
      // in case it was changed before
      return pNewElement;
    }

    Long explicitValue = null;

    if (pMissingInformation.hasKnownValue()) {
      explicitValue = pMissingInformation.getcExpressionValue();
    } else if (pMissingInformation.hasUnknownValue()) {
      explicitValue = resolveValue(pSmgState, pMissingInformation.getMissingCExpressionInformation());
    }

    if (explicitValue == null) {
      // Always return the new Element
      // if you want to interrupt the calculation
      // in case it was changed before
      if (pNewElement.contains(memoryLocation)) {
        pNewElement.forget(memoryLocation);
      }
      return pNewElement;
    }

    pNewElement.assignConstant(memoryLocation, explicitValue);

    return pNewElement;
  }

  private Long resolveValue(SMGState pSmgState, CExpression rValue)
      throws UnrecognizedCCodeException {

    SMGExplicitCommunicator cc = new SMGExplicitCommunicator(oldState, functionName,
        pSmgState, machineModel, logger, edge);

    return cc.evaluateExpression(rValue);
  }

  private MemoryLocation resolveMemoryLocation(SMGState pSmgState, CExpression lValue)
      throws UnrecognizedCCodeException {

    SMGExplicitCommunicator cc =
        new SMGExplicitCommunicator(oldState, functionName, pSmgState, machineModel, logger, edge);

    return cc.evaluateLeftHandSide(lValue);
  }

  private ExplicitState resolvingAssumption(ExplicitState pNewElement,
      SMGState pSmgState, MissingInformation pMissingInformation) throws UnrecognizedCCodeException {

    Boolean bTruthValue = pMissingInformation.getTruthAssumption();

    long truthValue = bTruthValue ? 1 : 0;

    Long value = resolveValue(pSmgState, pMissingInformation.getMissingCExpressionInformation());

    if(value != null && value != truthValue) {
      return null;
    } else {

      if(value == null) {

        // Try deriving further Information
        ExplicitState element = pNewElement.clone();
        SMGAssigningValueVisitor avv = new SMGAssigningValueVisitor(element, bTruthValue, pSmgState);
        pMissingInformation.getMissingCExpressionInformation().accept(avv);

        return element;
      }

      return pNewElement;
    }
  }

  private Collection<? extends AbstractState> strengthen(RTTState rttState)
      throws UnrecognizedCCodeException {

    ExplicitState newElement = state.clone();

    if (missingFieldVariableObject) {
      newElement.assignConstant(getRTTScopedVariableName(
          fieldNameAndInitialValue.getFirst(),
          rttState.getKeywordThisUniqueObject()),
          fieldNameAndInitialValue.getSecond());

      missingFieldVariableObject = false;
      fieldNameAndInitialValue = null;
      return Collections.singleton(newElement);

    } else if (missingScopedFieldName) {

      newElement = handleNotScopedVariable(rttState, newElement);
      missingScopedFieldName = false;
      notScopedField = null;
      notScopedFieldValue = null;
      missingInformationRightJExpression = null;

      if (newElement != null) {
      return Collections.singleton(newElement);
      } else {
        return null;
      }
    } else if (missingAssumeInformation && missingInformationRightJExpression != null) {
      Long value = handleMissingInformationRightJExpression(rttState);

      missingAssumeInformation = false;
      missingInformationRightJExpression = null;

      if (value == null) {
        return null;
      } else if ((((AssumeEdge) edge).getTruthAssumption() && value == 1L)
          || (!((AssumeEdge) edge).getTruthAssumption() && value == 0L)) {
        return Collections.singleton(newElement);
      } else {
        return new HashSet<>();
      }
    } else if (missingInformationRightJExpression != null) {

      Long value = handleMissingInformationRightJExpression(rttState);

      if (value != null) {
        newElement.assignConstant(missingInformationLeftJVariable, value);
        missingInformationRightJExpression = null;
        missingInformationLeftJVariable = null;
        return Collections.singleton(newElement);
      } else {
        missingInformationRightJExpression = null;
        missingInformationLeftJVariable = null;
        if (missingInformationLeftJVariable != null) { // TODO why check this???
          newElement.forget(missingInformationLeftJVariable);
        }
        return Collections.singleton(newElement);
      }
    }
    return null;
  }

  private String getRTTScopedVariableName(String fieldName, String uniqueObject) {
    return  uniqueObject + "::"+ fieldName;
  }

  private Long handleMissingInformationRightJExpression(RTTState pJortState)
      throws UnrecognizedCCodeException {
    return missingInformationRightJExpression.accept(
        new FieldAccessExpressionValueVisitor(pJortState));
  }

  private ExplicitState handleNotScopedVariable(RTTState rttState, ExplicitState newElement) throws UnrecognizedCCodeException {

   String objectScope = getObjectScope(rttState, functionName, notScopedField);

   if (objectScope != null) {

     String scopedFieldName = getRTTScopedVariableName(notScopedField.getName(), objectScope);

     Long value = notScopedFieldValue;
     if (missingInformationRightJExpression != null) {
       value = handleMissingInformationRightJExpression(rttState);
     }

     if (value != null) {
       newElement.assignConstant(scopedFieldName, value);
       return newElement;
     } else {
       newElement.forget(scopedFieldName);
       return newElement;
     }
   } else {
     return null;
   }


  }

  private String getObjectScope(RTTState rttState, String methodName,
      JIdExpression notScopedField) {

    // Could not resolve var
    if (notScopedField.getDeclaration() == null) {
      return null;
    }

    if (notScopedField instanceof JFieldAccess) {

      JIdExpression qualifier = ((JFieldAccess) notScopedField).getReferencedVariable();

      String qualifierScope = getObjectScope(rttState, methodName, qualifier);

      String scopedFieldName =
          getRTTScopedVariableName(qualifier.getDeclaration(), methodName, qualifierScope);

      if (rttState.contains(scopedFieldName)) {
        return rttState.getUniqueObjectFor(scopedFieldName);
      } else {
        return null;
      }
    } else {
      if (rttState.contains(RTTState.KEYWORD_THIS)) {
        return rttState.getUniqueObjectFor(RTTState.KEYWORD_THIS);
      } else {
        return null;
      }
    }
  }

  private String getRTTScopedVariableName(
      JSimpleDeclaration decl,
      String methodName, String uniqueObject) {

    if (decl == null) { return ""; }

    if (decl instanceof JFieldDeclaration && ((JFieldDeclaration) decl).isStatic()) {
      return decl.getName();
    } else if (decl instanceof JFieldDeclaration) {
      return uniqueObject + "::" + decl.getName();
    } else {
      return methodName + "::" + decl.getName();
    }
  }

  private static class MissingInformation {

    /**
     * This field stores the Expression of the Memory Location that
     * could not be evaluated.
     */
    private final CExpression missingCLeftMemoryLocation;

    /**
     *  This expression stores the Memory Location
     *  to be assigned.
     */
    private final MemoryLocation cLeftMemoryLocation;

    /**
     * Expression could not be evaluated due to missing information. (e.g.
     * missing pointer alias).
     */
    private final CExpression missingCExpressionInformation;

    /**
     * Expression could not be evaluated due to missing information. (e.g.
     * missing pointer alias).
     */
    private final Long cExpressionValue;

    /**
     * The truth Assumption made in this assume edge.
     */
    private final Boolean truthAssumption;

    private CFunctionCallExpression missingFreeInvocation = null;

    @SuppressWarnings("unused")
    public MissingInformation(CExpression pMissingCLeftMemoryLocation,
        CExpression pMissingCExpressionInformation) {
      missingCExpressionInformation = pMissingCExpressionInformation;
      missingCLeftMemoryLocation = pMissingCLeftMemoryLocation;
      cExpressionValue = null;
      cLeftMemoryLocation = null;
      truthAssumption = null;
    }

    //TODO Better checks...don't be lazy, just because class
    // will likely change.

    public boolean hasUnknownValue() {
      return missingCExpressionInformation != null;
    }

    public boolean hasKnownValue() {
      return cExpressionValue != null;
    }

    public boolean hasUnknownMemoryLocation() {
      return missingCLeftMemoryLocation != null;
    }

    public boolean hasKnownMemoryLocation() {
      return cLeftMemoryLocation != null;
    }

    public boolean isMissingAssignment() {
      // TODO Better Name for this method.
      // Checks if a variable needs to be assigned a value,
      // but to evaluate the MemoryLocation, or the value,
      // we lack information.

      return (missingCExpressionInformation != null
              || missingCLeftMemoryLocation != null)
          && truthAssumption == null;
    }

    public boolean isMissingAssumption() {
      return truthAssumption != null && missingCExpressionInformation != null;
    }

    public MissingInformation(CExpression pMissingCLeftMemoryLocation,
        Long pCExpressionValue) {
      missingCExpressionInformation = null;
      missingCLeftMemoryLocation = pMissingCLeftMemoryLocation;
      cExpressionValue = pCExpressionValue;
      cLeftMemoryLocation = null;
      truthAssumption = null;
    }

    public MissingInformation(MemoryLocation pCLeftMemoryLocation,
        CExpression pMissingCExpressionInformation) {
      missingCExpressionInformation = pMissingCExpressionInformation;
      missingCLeftMemoryLocation = null;
      cExpressionValue = null;
      cLeftMemoryLocation = pCLeftMemoryLocation;
      truthAssumption = null;
    }

    public MissingInformation(IAExpression pMissingCLeftMemoryLocation,
        IARightHandSide pMissingCExpressionInformation) {
      // This constructor casts to CExpression, just to have as few
      // as possible pieces of code for communication cluttering
      // up the transfer relation.
      // Especially, since this class will later be used to
      // communicate missing Information independent of language

      missingCExpressionInformation = (CExpression) pMissingCExpressionInformation;
      missingCLeftMemoryLocation = (CExpression) pMissingCLeftMemoryLocation;
      cExpressionValue = null;
      cLeftMemoryLocation = null;
      truthAssumption = null;
    }

    public MissingInformation(Boolean pTruthAssumption,
        IARightHandSide pMissingCExpressionInformation) {
      // This constructor casts to CExpression, just to have as few
      // as possible pieces of code for communication cluttering
      // up the transfer relation.
      // Especially, since this class will later be used to
      // communicate missing Information independent of language

      missingCExpressionInformation = (CExpression) pMissingCExpressionInformation;
      missingCLeftMemoryLocation = null;
      cExpressionValue = null;
      cLeftMemoryLocation = null;
      truthAssumption = pTruthAssumption;
    }

    public MissingInformation(CFunctionCallExpression pFunctionCallExpression) {
      missingFreeInvocation = pFunctionCallExpression;
      missingCExpressionInformation = null;
      missingCLeftMemoryLocation = null;
      cExpressionValue = null;
      cLeftMemoryLocation = null;
      truthAssumption = null;

    }

    public boolean isFreeInvocation() {
      return missingFreeInvocation != null;
    }

    public Long getcExpressionValue() {
      checkNotNull(cExpressionValue);
      return cExpressionValue;
    }

    public MemoryLocation getcLeftMemoryLocation() {
      checkNotNull(cLeftMemoryLocation);
      return cLeftMemoryLocation;
    }

    @SuppressWarnings("unused")
    public CExpression getMissingCExpressionInformation() {
      checkNotNull(missingCExpressionInformation);
      return missingCExpressionInformation;
    }

    @SuppressWarnings("unused")
    public CExpression getMissingCLeftMemoryLocation() {
      checkNotNull(missingCLeftMemoryLocation);
      return missingCLeftMemoryLocation;
    }

    @SuppressWarnings("unused")
    public Boolean getTruthAssumption() {
      checkNotNull(truthAssumption);
      return truthAssumption;
    }

    public CFunctionCallExpression getMissingFreeInvocation() {
      return missingFreeInvocation;
    }
  }

  /** returns an initialized, empty visitor */
  private ExplicitExpressionValueVisitor getVisitor() {
    return new ExplicitExpressionValueVisitor(state, functionName, machineModel, logger, edge);
  }
}