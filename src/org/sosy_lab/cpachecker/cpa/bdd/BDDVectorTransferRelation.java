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
package org.sosy_lab.cpachecker.cpa.bdd;

import static org.sosy_lab.cpachecker.cpa.bdd.VarCollector.*;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CDefaults;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.predicates.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;

/** This Transfer Relation tracks variables and handles them as boolean,
 * so only the cases ==0 and !=0 are tracked. */
@Options(prefix = "cpa.bdd")
public class BDDVectorTransferRelation extends BDDTransferRelation {

  private final BitvectorManager bvmgr;

  @Option(description = "initialize all variables to 0 when they are declared")
  private boolean initAllVars = false;

  public BDDVectorTransferRelation(NamedRegionManager manager, Configuration config)
      throws InvalidConfigurationException {
    super(manager, config);
    this.bvmgr = new BitvectorManager(manager, config);
  }

  @Override
  public Collection<BDDState> getAbstractSuccessors(
      AbstractState abstractState, Precision prec, CFAEdge cfaEdge)
      throws CPATransferException {
    BDDState state = (BDDState) abstractState;
    BDDPrecision precision = (BDDPrecision) prec;

    if (precision.isDisabled()) {
      // this means that no variables should be tracked
      return Collections.singleton(state);
    }

    if (state.getRegion().isFalse()) { return Collections.emptyList(); }

    BDDState successor = null;

    switch (cfaEdge.getEdgeType()) {

    case AssumeEdge:
      successor = handleAssumption(state, (CAssumeEdge) cfaEdge, precision);
      break;

    case StatementEdge:
      successor = handleStatementEdge(state, (CStatementEdge) cfaEdge, precision);
      break;

    case DeclarationEdge:
      successor = handleDeclarationEdge(state, (CDeclarationEdge) cfaEdge, precision);
      break;

    case MultiEdge:
      successor = state;
      Collection<BDDState> c = null;
      for (CFAEdge innerEdge : (MultiEdge) cfaEdge) {
        c = getAbstractSuccessors(successor, precision, innerEdge);
        if (c.isEmpty()) {
          successor = state; //TODO really correct??
        } else if (c.size() == 1) {
          successor = c.toArray(new BDDState[1])[0];
        } else {
          throw new AssertionError("only size 0 or 1 allowed");
        }
      }
      break;

    case FunctionCallEdge:
      successor = handleFunctionCallEdge(state, (CFunctionCallEdge) cfaEdge, precision);
      break;

    case FunctionReturnEdge:
      successor = handleFunctionReturnEdge(state, (CFunctionReturnEdge) cfaEdge, precision);
      break;

    case ReturnStatementEdge:
      successor = handleReturnStatementEdge(state, (CReturnStatementEdge) cfaEdge, precision);
      break;

    case BlankEdge:
    case CallToReturnEdge:
    default:
      successor = state;
    }

    if (successor == null) {
      return Collections.emptySet();
    } else {
      assert !successor.getRegion().isFalse();
      return Collections.singleton(successor);
    }
  }

  /** handles statements like "a = 0;" and "b = !a;" */
  private BDDState handleStatementEdge(BDDState state, CStatementEdge cfaEdge, BDDPrecision precision)
      throws UnrecognizedCCodeException {
    CStatement statement = cfaEdge.getStatement();
    if (!(statement instanceof CAssignment)) { return state; }
    CAssignment assignment = (CAssignment) statement;

    CExpression lhs = assignment.getLeftHandSide();
    BDDState result = state;
    if (lhs instanceof CIdExpression || lhs instanceof CFieldReference
        || lhs instanceof CArraySubscriptExpression) {

      String varName = buildVarName(lhs.toASTString(), isGlobal(lhs), state.getFunctionName());
      if (precision.isTracking(varName)) {

        // make tmp for assignment
        // this is done to handle assignments like "a = !a;" as "tmp = !a; a = tmp;"
        Region[] tmp = createPredicate(buildVarName(
            TMP_VARIABLE, false, state.getFunctionName()));
        Region newRegion = state.getRegion();

        CRightHandSide rhs = assignment.getRightHandSide();
        if (rhs instanceof CExpression) {

          // make region for RIGHT SIDE and build equality of var and region
          BDDCExpressionVisitor ev = new BDDCExpressionVisitor(state, precision);
          Region[] regRHS = ((CExpression) rhs).accept(ev);
          newRegion = addEquality(tmp, regRHS, newRegion);

        } else if (rhs instanceof CFunctionCallExpression) {
          // call of external function: we know nothing, so we do nothing

          // TODO can we assume, that malloc returns something !=0?
          // are there some "save functions"?

        } else {
          throw new UnrecognizedCCodeException(cfaEdge, rhs);
        }

        // delete var, make tmp equal to (new) var, then delete tmp
        Region[] var = createPredicate(varName);
        newRegion = removePredicate(newRegion, var);
        newRegion = addEquality(var, tmp, newRegion);
        newRegion = removePredicate(newRegion, tmp);

        result = new BDDState(rmgr, state.getFunctionCallState(), newRegion,
            state.getVars(), cfaEdge.getPredecessor().getFunctionName());
      }
    }

    assert !result.getRegion().isFalse();
    return result;
  }

  /** handles declarations like "int a = 0;" and "int b = !a;" */
  private BDDState handleDeclarationEdge(BDDState state, CDeclarationEdge cfaEdge,
      BDDPrecision precision) throws UnrecognizedCCodeException {

    CDeclaration decl = cfaEdge.getDeclaration();

    if (decl instanceof CVariableDeclaration) {
      CVariableDeclaration vdecl = (CVariableDeclaration) decl;
      CInitializer initializer = vdecl.getInitializer();

      CExpression init = null;
      if (initializer == null && initAllVars) { // auto-initialize variables to zero
        init = CDefaults.forType(decl.getType(), decl.getFileLocation());
      } else if (initializer instanceof CInitializerExpression) {
        init = ((CInitializerExpression) initializer).getExpression();
      }

      // make variable (predicate) for LEFT SIDE of declaration,
      // delete variable, if it was initialized before i.e. in another block, with an existential operator
      String varName = buildVarName(vdecl.getName(), vdecl.isGlobal(), state.getFunctionName());
      if (precision.isTracking(varName)) {
        Region[] var = createPredicate(varName);
        Region newRegion = removePredicate(state.getRegion(), var);

        // track vars, so we can delete them after returning from a function,
        // see handleFunctionReturnEdge(...) for detail.
        if (!vdecl.isGlobal()) {
          state.getVars().add(varName);
        }

        // initializer on RIGHT SIDE available, make region for it
        if (init != null) {
          BDDCExpressionVisitor ev = new BDDCExpressionVisitor(state, precision);
          Region[] regRHS = init.accept(ev);
          newRegion = addEquality(var, regRHS, newRegion);
          return new BDDState(rmgr, state.getFunctionCallState(), newRegion,
              state.getVars(), cfaEdge.getPredecessor().getFunctionName());
        }
      }
    }

    return state; // if we know nothing, we return the old state
  }

  private BDDState handleFunctionCallEdge(BDDState state, CFunctionCallEdge cfaEdge,
      BDDPrecision precision) throws UnrecognizedCCodeException {

    Region newRegion = state.getRegion();
    Set<String> newVars = new LinkedHashSet<String>();

    // overtake arguments from last functioncall into function,
    // get args from functioncall and make them equal with params from functionstart
    List<CExpression> args = cfaEdge.getArguments();
    List<CParameterDeclaration> params = cfaEdge.getSuccessor().getFunctionParameters();
    String innerFunctionName = cfaEdge.getSuccessor().getFunctionName();
    assert args.size() == params.size();

    for (int i = 0; i < args.size(); i++) {

      // make variable (predicate) for param, this variable is not global (->false)
      String varName = buildVarName(params.get(i).getName(), false, innerFunctionName);
      assert !newVars.contains(varName) : "variable used twice as param";

      // make region for arg and build equality of var and arg
      if (precision.isTracking(varName)) {
        newVars.add(varName);
        Region[] var = createPredicate(varName);
        BDDCExpressionVisitor ev = new BDDCExpressionVisitor(state, precision);
        Region[] arg = args.get(i).accept(ev);
        newRegion = addEquality(var, arg, newRegion);
      }
    }

    return new BDDState(rmgr, state, newRegion, newVars, innerFunctionName);
  }

  private BDDState handleFunctionReturnEdge(BDDState state, CFunctionReturnEdge cfaEdge,
      BDDPrecision precision) {
    Region newRegion = state.getRegion();

    // delete variables from returning function,
    // this results in a smaller BDD and allows to call a function twice.
    for (String varName : state.getVars()) {
      newRegion = removePredicate(newRegion, createPredicate(varName));
    }

    // set result of function equal to variable on left side
    CFunctionSummaryEdge fnkCall = cfaEdge.getSummaryEdge();
    CStatement call = fnkCall.getExpression().asStatement();

    // make region (predicate) for RIGHT SIDE
    Region[] retVar = createPredicate(buildVarName(FUNCTION_RETURN_VARIABLE, false, state.getFunctionName()));

    // handle assignments like "y = f(x);"
    if (call instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement cAssignment = (CFunctionCallAssignmentStatement) call;
      CExpression lhs = cAssignment.getLeftHandSide();

      // make variable (predicate) for LEFT SIDE of assignment,
      // delete variable, if it was used before, this is done with an existential operator
      BDDState functionCall = state.getFunctionCallState();
      String varName = buildVarName(lhs.toASTString(), isGlobal(lhs), functionCall.getFunctionName());
      if (precision.isTracking(varName)) {
        Region[] var = createPredicate(varName);
        newRegion = removePredicate(newRegion, var);
        newRegion = addEquality(var, retVar, newRegion);
      }
    }

    // LAST ACTION: delete varname of right side
    newRegion = removePredicate(newRegion, retVar);

    return new BDDState(rmgr, state.getFunctionCallState().getFunctionCallState(),
        newRegion, state.getFunctionCallState().getVars(),
        cfaEdge.getSuccessor().getFunctionName());
  }

  private BDDState handleReturnStatementEdge(BDDState state, CReturnStatementEdge cfaEdge,
      BDDPrecision precision) throws UnrecognizedCCodeException {

    // make variable (predicate) for returnStatement,
    // delete variable, if it was used before, this is done with an existential operator
    Region[] retvar = createPredicate(buildVarName(FUNCTION_RETURN_VARIABLE, false, state.getFunctionName()));

    assert state.getRegion().equals(removePredicate(state.getRegion(), retvar)) : FUNCTION_RETURN_VARIABLE
        + " was used twice in one trace??";

    // make region for RIGHT SIDE, this is the 'x' from 'return (x);
    CRightHandSide rhs = cfaEdge.getExpression();
    if (rhs instanceof CExpression) {
      BDDCExpressionVisitor ev = new BDDCExpressionVisitor(state, precision);
      Region[] regRHS = ((CExpression) rhs).accept(ev);
      Region newRegion = addEquality(retvar, regRHS, state.getRegion());
      return new BDDState(rmgr, state.getFunctionCallState(), newRegion,
          state.getVars(), cfaEdge.getPredecessor().getFunctionName());
    }
    return state;
  }

  private BDDState handleAssumption(BDDState state, CAssumeEdge cfaEdge,
      BDDPrecision precision) throws UnrecognizedCCodeException {

    CExpression expression = cfaEdge.getExpression();
    BDDCExpressionVisitor ev = new BDDCExpressionVisitor(state, precision);
    Region[] operand = expression.accept(ev);

    if (operand == null) { // assumption cannot be evaluated
      return state;

    } else {

      Region evaluated = bvmgr.makeOr(operand);

      if (!cfaEdge.getTruthAssumption()) { // if false-branch
        evaluated = rmgr.makeNot(evaluated);
      }

      // get information from region into evaluated region
      Region newRegion = rmgr.makeAnd(state.getRegion(), evaluated);
      if (newRegion.isFalse()) { // assumption is not fulfilled / not possible
        return null;
      } else {
        return new BDDState(rmgr, state.getFunctionCallState(), newRegion,
            state.getVars(), cfaEdge.getPredecessor().getFunctionName());
      }
    }
  }

  /** This function builds the equality of left and right side and adds it to the environment.
   * If left or right side is null, the environment is returned unchanged. */
  private Region addEquality(Region[] leftSide, Region[] rightSide, Region environment) {
    if (leftSide == null || rightSide == null) {
      return environment;
    } else {
      final Region[] assignRegions = bvmgr.makeBinaryEqual(leftSide, rightSide);
      for (int i = 0; i < assignRegions.length; i++) {
        environment = rmgr.makeAnd(environment, assignRegions[i]);
      }
      return environment;
    }
  }

  /** This function returns regions containing bits of a variable. */
  private Region[] createPredicate(String varName) {
    createdPredicates++;
    return bvmgr.createPredicate(varName);
  }

  /** This Visitor evaluates the visited expression and creates a region for it. */
  private class BDDCExpressionVisitor
      implements CExpressionVisitor<Region[], UnrecognizedCCodeException> {

    private String functionName;
    private BDDPrecision precision;

    BDDCExpressionVisitor(BDDState state, BDDPrecision prec) {
      this.functionName = state.getFunctionName();
      this.precision = prec;
    }

    /** This function returns regions containing bits of a variable.
     * The name of the variable is build from functionName and varName.
     * If the precision does not allow to track this variable, NULL is returned. */
    private Region[] makePredicate(CExpression exp, String functionName, BDDPrecision precision) {
      String scopedVarName = buildVarName(exp.toASTString(), isGlobal(exp), functionName);
      if (precision.isTracking(scopedVarName)) {
        return createPredicate(scopedVarName);
      } else {
        return null;
      }
    }

    @Override
    public Region[] visit(CArraySubscriptExpression exp) throws UnrecognizedCCodeException {
      Region[] var = makePredicate(exp, functionName, precision);
      return var;
    }

    @Override
    public Region[] visit(CBinaryExpression exp) throws UnrecognizedCCodeException {
      Region[] operand1 = exp.getOperand1().accept(this);
      Region[] operand2 = exp.getOperand2().accept(this);

      if (operand1 == null || operand2 == null) { return null; }

      Region[] returnValue = null;
      switch (exp.getOperator()) {

      case BINARY_AND:
        returnValue = bvmgr.makeBinaryAnd(operand1, operand2);
        break;

      case LOGICAL_AND:
        returnValue = bvmgr.makeLogicalAnd(operand1, operand2);
        break;

      case BINARY_OR:
        returnValue = bvmgr.makeBinaryOr(operand1, operand2);
        break;

      case LOGICAL_OR:
        returnValue = bvmgr.makeLogicalOr(operand1, operand2);
        break;

      case EQUALS:
        returnValue = bvmgr.makeLogicalEqual(operand1, operand2);
        break;

      case NOT_EQUALS:
        returnValue = bvmgr.makeNot(bvmgr.makeLogicalEqual(operand1, operand2));
        break;

      case BINARY_XOR:
        returnValue = bvmgr.makeXor(operand1, operand2);
        break;

      case PLUS:
        returnValue = bvmgr.makeAdd(operand1, operand2);
        break;

      case MINUS:
        returnValue = bvmgr.makeSub(operand1, operand2);
        break;

      case LESS_THAN:
        returnValue = bvmgr.makeLess(operand1, operand2);
        break;

      case LESS_EQUAL: // A<=B <--> !(B<A)
        returnValue = bvmgr.makeNot(bvmgr.makeLess(operand2, operand1));
        break;

      case GREATER_THAN: // A>B <--> B<A
        returnValue = bvmgr.makeLess(operand2, operand1);
        break;

      case GREATER_EQUAL:// A>=B <--> !(A<B)
        returnValue = bvmgr.makeNot(bvmgr.makeLess(operand1, operand2));
        break;

      case MULTIPLY:
      case DIVIDE:
      case MODULO:
      case SHIFT_LEFT:
      case SHIFT_RIGHT:
        // a*b, a<<b, etc --> don't know anything
      }
      return returnValue;
    }

    @Override
    public Region[] visit(CCastExpression exp) throws UnrecognizedCCodeException {
      // we ignore casts, because Zero is Zero.
      return exp.getOperand().accept(this);
    }

    @Override
    public Region[] visit(CFieldReference exp) throws UnrecognizedCCodeException {
      return makePredicate(exp, functionName, precision);
    }

    @Override
    public Region[] visit(CIdExpression exp) throws UnrecognizedCCodeException {
      return makePredicate(exp, functionName, precision);
    }

    @Override
    public Region[] visit(CCharLiteralExpression exp) throws UnrecognizedCCodeException {
      return null;
    }

    @Override
    public Region[] visit(CFloatLiteralExpression exp) throws UnrecognizedCCodeException {
      return null;
    }

    @Override
    public Region[] visit(CIntegerLiteralExpression exp) throws UnrecognizedCCodeException {
      return bvmgr.makeNumber(exp.getValue());
    }

    @Override
    public Region[] visit(CStringLiteralExpression exp) throws UnrecognizedCCodeException {
      return null;
    }

    @Override
    public Region[] visit(CTypeIdExpression exp) throws UnrecognizedCCodeException {
      return null;
    }

    @Override
    public Region[] visit(CUnaryExpression exp) throws UnrecognizedCCodeException {
      Region[] operand = exp.getOperand().accept(this);

      if (operand == null) { return null; }

      Region[] returnValue = null;
      switch (exp.getOperator()) {
      case NOT:
        returnValue = bvmgr.makeNot(operand);
        break;

      case PLUS: // +X == X
        returnValue = operand;
        break;

      case MINUS: // -X == (0-X)
        returnValue = bvmgr.makeSub(bvmgr.makeFalse(), operand);
        break;

      default:
        // *exp --> don't know anything
      }
      return returnValue;
    }
  }
}
