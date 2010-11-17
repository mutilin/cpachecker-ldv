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
package org.sosy_lab.cpachecker.cpa.interval;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.cdt.core.dom.ast.IASTArraySubscriptExpression;
import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTCastExpression;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTFieldReference;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IASTTypeIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
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
import org.sosy_lab.cpachecker.cpa.pointeranalysis.PointerAnalysisElement;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

@Options(prefix="cpas.interval")
public class IntervalAnalysisTransferRelation implements TransferRelation
{
  private static final int OFFSET_ARITHMETIC_OPERATOR = 17;
  private static final int OFFSET_LOGIC_OPERATOR = 13;

  private final Set<String> globalVars = new HashSet<String>();

  @Option
  private int threshold = 0;

  public IntervalAnalysisTransferRelation(Configuration config) throws InvalidConfigurationException
  {
    config.inject(this);
  }

  @Override
  public Collection<AbstractElement> getAbstractSuccessors (AbstractElement element, Precision precision, CFAEdge cfaEdge) throws CPATransferException
  {
    AbstractElement successor;
    IntervalAnalysisElement intervalElement = (IntervalAnalysisElement)element;

    // check the type of the edge
    switch(cfaEdge.getEdgeType())
    {
      // if edge is a statement edge, e.g. a = b + c
      case StatementEdge:
        StatementEdge statementEdge = (StatementEdge)cfaEdge;

        // this statement is a function return, e.g. return (a);
        // note that this is different from return edge
        // this is a statement edge which leads the function to the
        // last node of its CFA, where return edge is from that last node
        // to the return site of the caller function
        if (statementEdge.isJumpEdge())
          successor = handleExitFromFunction(intervalElement, statementEdge.getExpression(), statementEdge);
        // this is a regular statement
        else
          successor = handleStatement(intervalElement, statementEdge.getExpression(), cfaEdge);
        break;

      // edge is a declaration edge, e.g. int a;
      case DeclarationEdge:
        successor = handleDeclaration(intervalElement, (DeclarationEdge)cfaEdge);
        break;

      // this is an assumption, e.g. if(a == b)
      case AssumeEdge:
        AssumeEdge assumeEdge = (AssumeEdge)cfaEdge;
        successor = handleAssumption(intervalElement, assumeEdge.getExpression(), cfaEdge, assumeEdge.getTruthAssumption());
        break;

      case BlankEdge:
        successor = intervalElement.clone();
        break;

      case FunctionCallEdge:
        FunctionCallEdge functionCallEdge = (FunctionCallEdge)cfaEdge;

        // TODO external function call
        if(functionCallEdge.isExternalCall())
          throw new UnrecognizedCCodeException("external function calls not yet supported", functionCallEdge);

        else
          successor = handleFunctionCall(intervalElement, functionCallEdge);
        break;

      // this is a return edge from function, this is different from return statement
      // of the function. See case in statement edge for details
      case ReturnEdge:
        successor = handleFunctionReturn(intervalElement, (ReturnEdge)cfaEdge);
        break;

      default:
        throw new UnrecognizedCFAEdgeException(cfaEdge);
    }

    if(successor == null)
      return Collections.emptySet();

    else
      return Collections.singleton(successor);
  }

  /**
   * Handles return from one function to another function.
   * @param element previous abstract element.
   * @param functionReturnEdge return edge from a function to its call site.
   * @return new abstract element.
   */
  private IntervalAnalysisElement handleFunctionReturn(IntervalAnalysisElement element, ReturnEdge functionReturnEdge)
    throws UnrecognizedCCodeException
  {
    CallToReturnEdge summaryEdge = functionReturnEdge.getSuccessor().getEnteringSummaryEdge();

    IASTExpression expression = summaryEdge.getExpression();

    IntervalAnalysisElement previousElem = element.getPreviousElement();
    IntervalAnalysisElement newElement = previousElem.clone();

    String callerFunctionName = functionReturnEdge.getSuccessor().getFunctionName();
    String calledFunctionName = functionReturnEdge.getPredecessor().getFunctionName();

    // expression is a binary operation, e.g. a = g(b);
    if(expression instanceof IASTBinaryExpression)
    {
      IASTBinaryExpression binExp = (IASTBinaryExpression)expression;

      assert(binExp.getOperator() == IASTBinaryExpression.op_assign);

      IASTExpression operand1 = binExp.getOperand1();

      // we expect left hand side of the expression to be a variable
      if(operand1 instanceof IASTIdExpression)
      {
        String variableName = operand1.getRawSignature();

        String returnVariableName = calledFunctionName + "::" + "___cpa_temp_result_var_";

        for(String globalVar : globalVars)
        {
          if(globalVar.equals(variableName))
          {
            if(element.getNoOfReferences().containsKey(globalVar) && element.getNoOfReferences().get(globalVar).intValue() >= this.threshold)
            {
              newElement.removeInterval(globalVar);
              newElement.getNoOfReferences().put(globalVar, element.getNoOfReferences().get(globalVar));
            }

            else
            {
              if(element.contains(returnVariableName))
                newElement.addInterval(variableName, element.getInterval(returnVariableName), this.threshold);

              else
                newElement.removeInterval(variableName);
            }
          }

          else
          {
            if(element.getNoOfReferences().containsKey(globalVar) && element.getNoOfReferences().get(globalVar).intValue() >= this.threshold)
            {
              newElement.removeInterval(globalVar);
              newElement.getNoOfReferences().put(globalVar, element.getNoOfReferences().get(globalVar));
            }

            else
            {
              if(element.contains(globalVar))
              {
                newElement.addInterval(globalVar, element.getInterval(globalVar), this.threshold);
                newElement.getNoOfReferences().put(globalVar, element.getNoOfReferences().get(globalVar));
              }

              else
                newElement.removeInterval(variableName);
            }
          }
        }

        if(!globalVars.contains(variableName))
        {
          String assignedVarName = constructVariableName(variableName, callerFunctionName);

          if(element.contains(returnVariableName))
            newElement.addInterval(assignedVarName, element.getInterval(returnVariableName), this.threshold);

          else
            newElement.removeInterval(assignedVarName);
        }
      }

      else
        throw new UnrecognizedCCodeException("on function return", summaryEdge, operand1);
    }

    // TODO is g(b); relevant?
    else if(expression instanceof IASTUnaryExpression)
      ;

    // TODO is g(b); relevant?
    else if(expression instanceof IASTFunctionCallExpression)
      ;

    else
      throw new UnrecognizedCCodeException("on function return", summaryEdge, expression);

    return newElement;
  }

  private IntervalAnalysisElement handleFunctionCall(IntervalAnalysisElement element, FunctionCallEdge callEdge)
    throws UnrecognizedCCodeException
  {
    FunctionDefinitionNode functionEntryNode = (FunctionDefinitionNode)callEdge.getSuccessor();

    String calledFunctionName = functionEntryNode.getFunctionName();
    String callerFunctionName = callEdge.getPredecessor().getFunctionName();

    List<String> paramNames = functionEntryNode.getFunctionParameterNames();
    IASTExpression[] arguments = callEdge.getArguments();

    if(arguments == null)
      arguments = new IASTExpression[0];

    assert(paramNames.size() == arguments.length);

    IntervalAnalysisElement newElement = new IntervalAnalysisElement(element);

    for(String globalVar : globalVars)
    {
      if(element.contains(globalVar))
      {
        newElement.getIntervals().put(globalVar, element.getInterval(globalVar));
        newElement.getNoOfReferences().put(globalVar, element.getNoOfReferences().get(globalVar));
      }
    }

    for(int i=0; i<arguments.length; i++)
    {
      IASTExpression arg = arguments[i];

      // ignore casts
      if(arg instanceof IASTCastExpression)
        arg = ((IASTCastExpression)arg).getOperand();

      String nameOfParam = paramNames.get(i);
      String formalParamName = constructVariableName(nameOfParam, calledFunctionName);

      if(arg instanceof IASTIdExpression)
      {
        IASTIdExpression idExp = (IASTIdExpression) arg;
        String nameOfArg = idExp.getRawSignature();
        String actualParamName = constructVariableName(nameOfArg, callerFunctionName);

        if(element.contains(actualParamName))
          newElement.addInterval(formalParamName, element.getInterval(actualParamName), this.threshold);
      }

      else if(arg instanceof IASTLiteralExpression)
      {
        Long literalValue = parseLiteral(arg);

        if (literalValue != null)
          newElement.addInterval(formalParamName, new Interval(literalValue), this.threshold);

        else
          newElement.removeInterval(formalParamName);
      }

      else if(arg instanceof IASTTypeIdExpression)
        newElement.removeInterval(formalParamName);

      else if(arg instanceof IASTUnaryExpression)
      {
        IASTUnaryExpression unaryExp = (IASTUnaryExpression) arg;
        assert(unaryExp.getOperator() == IASTUnaryExpression.op_star || unaryExp.getOperator() == IASTUnaryExpression.op_amper);
      }

      else if(arg instanceof IASTFunctionCallExpression)
        assert(false);

      else if(arg instanceof IASTFieldReference)
        newElement.removeInterval(formalParamName);

      else
        newElement.removeInterval(formalParamName);
    }

    return newElement;
  }

  private IntervalAnalysisElement handleExitFromFunction(IntervalAnalysisElement element, IASTExpression expression, StatementEdge statementEdge)
    throws UnrecognizedCCodeException
  {
    return handleAssignmentToVariable(element, "___cpa_temp_result_var_", expression, statementEdge);
  }

  /**
   * This method handles assumptions.
   *
   * @author loewe
   * @param element the analysis element
   * @param expression the expression containing the assumption
   * @param cfaEdge the CFA edge corresponding to this expression
   * @param truthValue flag to determine whether this is the if or the else branch of the assumption
   * @return the successor elements
   */
  private AbstractElement handleAssumption(IntervalAnalysisElement element, IASTExpression expression, CFAEdge cfaEdge, boolean truthValue)
    throws UnrecognizedCCodeException
  {
    // first, unpack the expression to deal with a raw assumption
    if(expression instanceof IASTUnaryExpression)
    {
      IASTUnaryExpression unaryExp = ((IASTUnaryExpression)expression);

      switch(unaryExp.getOperator())
      {
        // remove brackets
        case IASTUnaryExpression.op_bracketedPrimary:
          return handleAssumption(element, unaryExp.getOperand(), cfaEdge, truthValue);

        // remove negation
        case IASTUnaryExpression.op_not:
          return handleAssumption(element, unaryExp.getOperand(), cfaEdge, !truthValue);

        default:
          throw new UnrecognizedCCodeException(cfaEdge, unaryExp);
      }
    }

    // a plain (boolean) identifier, e.g. if(a)
    else if(expression instanceof IASTIdExpression)
    {
      String functionName = cfaEdge.getPredecessor().getFunctionName();
      String variableName = constructVariableName(expression.getRawSignature(), functionName);

      Interval interval   = element.getInterval(variableName);

      if(truthValue)
      {
        if(!interval.equals(Interval.FALSE))
          return element.clone();
      }
      else
      {
        if(interval.contains(Interval.FALSE))
          return element.clone();
      }
    }

    // [exp1 op exp2]
    else if (expression instanceof IASTBinaryExpression)
    {
      IntervalAnalysisElement newElement = element.clone();

      IASTBinaryExpression binExp = ((IASTBinaryExpression)expression);

      int operator = binExp.getOperator();

      IASTExpression operand1 = binExp.getOperand1();
      IASTExpression operand2 = binExp.getOperand2();

      // both operands are literals -> no gain in information
      if((operand1 instanceof IASTLiteralExpression) && (operand2 instanceof IASTLiteralExpression))
        return newElement;

      // one of the operators is an identifier, the other one is a literal
      /*else if(operand1 instanceof IASTIdExpression && operand2 instanceof IASTLiteralExpression
           || operand1IsLiteral && operand2 instanceof IASTIdExpression)*/

      // at least one of the operators is an identifier
      else
      {
        switch(operator)
        {
          case IASTBinaryExpression.op_equals:
          case IASTBinaryExpression.op_notequals:
          case IASTBinaryExpression.op_greaterThan:
          case IASTBinaryExpression.op_greaterEqual:
          case IASTBinaryExpression.op_lessThan:
          case IASTBinaryExpression.op_lessEqual:
            newElement = processAssumption(newElement, operator, operand1, operand2, truthValue, cfaEdge);
            break;

          default:
            throw new UnrecognizedCCodeException(cfaEdge, binExp);
        }

        return newElement;//.addInterval(variableName, interval, threshold);
      }
    }

    return null;
  }

  private IntervalAnalysisElement processAssumption(IntervalAnalysisElement element, int operator, IASTExpression operand1, IASTExpression operand2, boolean truthValue, CFAEdge cfaEdge) throws UnrecognizedCCodeException
  {
    if(!truthValue)
      return processAssumption(element, negateOperator(operator), operand1, operand2, !truthValue, cfaEdge);

    /* unnecessary ..?
    if(operand1 instanceof IASTLiteralExpression)
      processAssumption(element, flipOperator(operator), operand2, operand1, truthValue, cfaEdge);
    */

    Interval interval1 = getInterval(element, operand1, cfaEdge.getPredecessor().getFunctionName(), cfaEdge);
    Interval interval2 = getInterval(element, operand2, cfaEdge.getPredecessor().getFunctionName(), cfaEdge);

    // reduce <= to <
    if(operator == IASTBinaryExpression.op_lessEqual)
    {
      interval2 = interval2.plus(1);
      operator = IASTBinaryExpression.op_lessThan;
    }

    // reduce >= to >
    else if(operator == IASTBinaryExpression.op_greaterEqual)
    {
      interval1 = interval1.plus(1);
      operator = IASTBinaryExpression.op_greaterThan;
    }

    String variableName1 = constructVariableName(operand1.getRawSignature(), cfaEdge.getPredecessor().getFunctionName());
    String variableName2 = constructVariableName(operand2.getRawSignature(), cfaEdge.getPredecessor().getFunctionName());

    // a < b, a < 1
    if(operator == IASTBinaryExpression.op_lessThan)
    {
      // a.low < b.high, so a may be less than b
      if(interval1.getLow() < interval2.getHigh())
      {
        interval1.limitUpperBoundBy(interval2);
        interval2.limitLowerBoundBy(interval1);

        element.addInterval(variableName1, interval1, threshold);
        element.addInterval(variableName2, interval2, threshold);
      }

      // a.low is greater than b.high, so there can't be a succesor
      else
        element = null;
    }

    // a > b, a > 1
    else if(operator == IASTBinaryExpression.op_greaterThan)
    {
      // a.high > b.low, so a may be greater than b
      if(interval1.getHigh() > interval2.getLow())
      {
        interval1.limitLowerBoundBy(interval2);
        interval2.limitUpperBoundBy(interval1);

        element.addInterval(variableName1, interval1, threshold);
        element.addInterval(variableName2, interval2, threshold);
      }

      // a.high is less than b.low, so a can't be greater than b, so there can't be a succesor
      else
        element = null;
    }

    // a == b, a == 1
    else if(operator == IASTBinaryExpression.op_equals)
    {
      // a and b intersect, so they may have the same value, so they may be equal
      if(interval1.intersects(interval2))
      {
        element.addInterval(variableName1, interval1.intersect(interval2), threshold);
        element.addInterval(variableName2, interval2.intersect(interval1), threshold);
      }

      // a and b do not intersect, so they can't be equal, so there can't be a successor
      else
        element = null;
    }

    // a != b, a != 1
    else if(operator == IASTBinaryExpression.op_notequals)
    {
      // a = [x, x] = b => a and b are always equal, so there can't be a successor
      if(interval1.equals(interval2) && interval1.getLow() == interval1.getHigh())
        element = null;
    }
    else
      throw new UnrecognizedCCodeException("unknown operator", cfaEdge);

    return element;
  }

  /**
   * This method return the negated counter part for a given operator
   *
   * @author loewe
   * @param operator
   * @return the negated counter part of the given operator
   */
  private int negateOperator(int operator)
  {
    switch(operator)
    {
      case IASTBinaryExpression.op_equals:
        return IASTBinaryExpression.op_notequals;

      case IASTBinaryExpression.op_notequals:
        return IASTBinaryExpression.op_equals;

      case IASTBinaryExpression.op_lessThan:
        return IASTBinaryExpression.op_greaterEqual;

      case IASTBinaryExpression.op_lessEqual:
        return IASTBinaryExpression.op_greaterThan;

      case IASTBinaryExpression.op_greaterEqual:
        return IASTBinaryExpression.op_lessThan;

      case IASTBinaryExpression.op_greaterThan:
        return IASTBinaryExpression.op_lessEqual;

      default:
        return operator;
    }
  }

  /**
   * This method return the counter part for a given operator
   *
   * @author loewe
   * @param operator
   * @return the counter part of the given operator
   */
  private int flipOperator(int operator)
  {
    switch(operator)
    {
      case IASTBinaryExpression.op_lessThan:
        return IASTBinaryExpression.op_greaterThan;

      case IASTBinaryExpression.op_lessEqual:
        return IASTBinaryExpression.op_greaterEqual;

      case IASTBinaryExpression.op_greaterEqual:
        return IASTBinaryExpression.op_lessEqual;

      case IASTBinaryExpression.op_greaterThan:
        return IASTBinaryExpression.op_lessThan;

      default:
        return operator;
    }
  }

  /**
   * This method handles variable declarations.
   *
   * So far, only primitive types are supported. Pointers are not supported either.
   *
   * @author loewe
   * @param element the analysis element
   * @param declarationEdge the CFA edge
   * @return the successor element
   */
  private IntervalAnalysisElement handleDeclaration(IntervalAnalysisElement element, DeclarationEdge declarationEdge)
  {
    IntervalAnalysisElement newElement = element.clone();

    for(IASTDeclarator declarator : declarationEdge.getDeclarators())
    {
      if(declarator != null)
      {
        // ignore pointer variables
        if(declarator.getPointerOperators().length > 0)
          continue;

        // if this is a global variable, add it to the list of global variables
        if(declarationEdge instanceof GlobalDeclarationEdge)
          globalVars.add(declarator.getName().toString());
      }
    }

    return newElement;
  }

  /**
   * This method handles unary and binary statement.
   *
   * @param element the analysis element
   * @param expression the current expression
   * @param cfaEdge the CFA edge
   * @return the successor
   * @throws UnrecognizedCCodeException
   */
  private IntervalAnalysisElement handleStatement(IntervalAnalysisElement element, IASTExpression expression, CFAEdge cfaEdge)
    throws UnrecognizedCCodeException
  {
    // expression is a binary operation, e.g. a = b;
    if(expression instanceof IASTBinaryExpression)
      return handleBinaryStatement(element, expression, cfaEdge);

    // expression is a unary operation, e.g. a++;
    else if(expression instanceof IASTUnaryExpression)
      return handleUnaryStatement(element, expression, cfaEdge);

    // external function call => do nothing
    else if(expression instanceof IASTFunctionCallExpression)
      return element.clone();

    // there is such a case
    else if(expression instanceof IASTIdExpression)
      return element.clone();

    else
      throw new UnrecognizedCCodeException(cfaEdge, expression);
  }

  /**
   * This method handles unary statements.
   *
   * @param element the analysis element
   * @param expression the current expression
   * @param cfaEdge the CFA edge
   * @return the successor
   * @throws UnrecognizedCCodeException
   */
  private IntervalAnalysisElement handleUnaryStatement(IntervalAnalysisElement element, IASTExpression expression, CFAEdge cfaEdge)
    throws UnrecognizedCCodeException
  {
    IASTUnaryExpression unaryExpression = (IASTUnaryExpression)expression;

    IASTExpression operand = unaryExpression.getOperand();

    if(operand instanceof IASTIdExpression)
    {
      int operator = unaryExpression.getOperator();

      int offset;

      // a++, ++a
      if(operator == IASTUnaryExpression.op_postFixIncr || operator == IASTUnaryExpression.op_prefixIncr)
        offset = 1;

      // a--, --a
      else if(operator == IASTUnaryExpression.op_prefixDecr || operator == IASTUnaryExpression.op_postFixDecr)
        offset = -1;

      else
        throw new UnrecognizedCCodeException(cfaEdge, unaryExpression);

      String functionName = cfaEdge.getPredecessor().getFunctionName();
      String varName = constructVariableName(operand.getRawSignature(), functionName);

      IntervalAnalysisElement newElement = element.clone();
      if(newElement.contains(varName))
        newElement.addInterval(varName, newElement.getInterval(varName).plus(offset), this.threshold);

      return newElement;
    }
    else
      throw new UnrecognizedCCodeException(cfaEdge, operand);
  }

  /**
   * This method handles binary statements.
   *
   * This method routes back to either {@link IntervalAnalysisTransferRelation#handleAssignment(IntervalAnalysisElement, IASTBinaryExpression, CFAEdge)} or {@link IntervalAnalysisTransferRelation#handleOperationAndAssign(IntervalAnalysisElement, IASTBinaryExpression, CFAEdge)} depending on the type of the binary operator.
   *
   * @author loewe
   * @param element the analysis element
   * @param expresion the expression containing the binary expression
   * @param declarationEdge the CFA edge
   * @return the successor element
   */
  private IntervalAnalysisElement handleBinaryStatement(IntervalAnalysisElement element, IASTExpression expression, CFAEdge cfaEdge)
    throws UnrecognizedCCodeException
  {
    IASTBinaryExpression binaryExpression = (IASTBinaryExpression) expression;
    switch (binaryExpression.getOperator ())
    {
      // a = ?
      case IASTBinaryExpression.op_assign:
        return handleAssignment(element, binaryExpression, cfaEdge);

      // a += 2
      case IASTBinaryExpression.op_plusAssign:
      case IASTBinaryExpression.op_minusAssign:
      case IASTBinaryExpression.op_multiplyAssign:
      case IASTBinaryExpression.op_shiftLeftAssign:
      case IASTBinaryExpression.op_shiftRightAssign:
      case IASTBinaryExpression.op_binaryAndAssign:
      case IASTBinaryExpression.op_binaryXorAssign:
      case IASTBinaryExpression.op_binaryOrAssign:
        return handleOperationAndAssign(element, binaryExpression, cfaEdge);

      default:
        throw new UnrecognizedCCodeException(cfaEdge, binaryExpression);
    }
  }

  /**
   * This method handles assigning operations, e.g. a += b.
   *
   * @author loewe
   * @param element the analysis element
   * @param binaryExpression the expression containing the binary expression
   * @param declarationEdge the CFA edge
   * @return the successor element
   */
  private IntervalAnalysisElement handleOperationAndAssign(IntervalAnalysisElement element, IASTBinaryExpression binaryExpression, CFAEdge cfaEdge)
    throws UnrecognizedCCodeException
  {
    IASTExpression leftOp = binaryExpression.getOperand1();
    IASTExpression rightOp = binaryExpression.getOperand2();

    // convert the assigning operator to a binary operator
    int operator = toBinaryOperator(binaryExpression.getOperator());

    if(operator == -1)
      throw new UnrecognizedCCodeException("unknown binary operator", cfaEdge, binaryExpression);

    // TODO handle fields, arrays
    if(!(leftOp instanceof IASTIdExpression))
      throw new UnrecognizedCCodeException("left operand of assignment has to be a variable", cfaEdge, leftOp);

    return handleAssignmentOfBinaryExp(element, leftOp.getRawSignature(), leftOp, rightOp, operator, cfaEdge);
  }

  /**
   * This method converts an assigning operator, e.g. "+=" to its binary counterpart, e.g. "+"
   *
   * @param assingingOperator the assigning operator
   * @return the respective binary operator or -1 if and invalid operator was given
   */
  private int toBinaryOperator(int assingingOperator)
  {
    switch(assingingOperator)
    {
      case IASTBinaryExpression.op_plusAssign:
      case IASTBinaryExpression.op_minusAssign:
      case IASTBinaryExpression.op_multiplyAssign:
      case IASTBinaryExpression.op_shiftLeftAssign:
      case IASTBinaryExpression.op_shiftRightAssign:
        return assingingOperator - OFFSET_ARITHMETIC_OPERATOR;

      case IASTBinaryExpression.op_binaryAndAssign:
      case IASTBinaryExpression.op_binaryXorAssign:
      case IASTBinaryExpression.op_binaryOrAssign:
        return assingingOperator - OFFSET_LOGIC_OPERATOR;

      default:
        return -1;
    }
  }

  /**
   * This method handles assignments.
   *
   * @author loewe
   * @param element the analysis element
   * @param binaryExpression the expression containing the binary expression
   * @param declarationEdge the CFA edge
   * @return the successor element
   * TODO pointer dereferencing via strengthening
   */
  private IntervalAnalysisElement handleAssignment(IntervalAnalysisElement element, IASTBinaryExpression binaryExpression, CFAEdge cfaEdge)
    throws UnrecognizedCCodeException
  {
    IASTExpression op1 = binaryExpression.getOperand1();
    IASTExpression op2 = binaryExpression.getOperand2();

    // a = ...
    if(op1 instanceof IASTIdExpression)
      return handleAssignmentToVariable(element, op1.getRawSignature(), op2, cfaEdge);

    // TODO: *a = ...
    else if(op1 instanceof IASTUnaryExpression && ((IASTUnaryExpression)op1).getOperator() == IASTUnaryExpression.op_star)
      return element.clone();

    // TODO assignment to field
    else if (op1 instanceof IASTFieldReference)
      return element.clone();

    // TODO assignment to array cell
    else if (op1 instanceof IASTArraySubscriptExpression)
      return element.clone();

    else
      throw new UnrecognizedCCodeException("left operand of assignment has to be a variable", cfaEdge, op1);
  }

  /**
   * This method handles the assignment of a variable.
   *
   * @author loewe
   * @param element the analysis element
   * @param lParam the local name of the variable to assign to
   * @param rightExp the assigning expression
   * @param cfaEdge the respective CFA edge
   * @return the successor element
   */
  private IntervalAnalysisElement handleAssignmentToVariable(IntervalAnalysisElement element, String lParam, IASTExpression rightExp, CFAEdge cfaEdge)
    throws UnrecognizedCCodeException
  {
    String functionName = cfaEdge.getPredecessor().getFunctionName();

    // a = 8.2 or "return;" (when rightExp == null)
    if(rightExp == null || rightExp instanceof IASTLiteralExpression)
      return handleAssignmentOfLiteral(element, lParam, rightExp, functionName);

    // a = b
    else if (rightExp instanceof IASTIdExpression)
      return handleAssignmentOfVariable(element, lParam, rightExp, functionName);

    // a = (cast) ?
    else if(rightExp instanceof IASTCastExpression)
      return handleAssignmentOfCast(element, lParam, (IASTCastExpression)rightExp, cfaEdge);

    // a = -b
    else if(rightExp instanceof IASTUnaryExpression)
      return handleAssignmentOfUnaryExp(element, lParam, (IASTUnaryExpression)rightExp, cfaEdge);

    // a = b op c
    else if(rightExp instanceof IASTBinaryExpression)
    {
      IASTBinaryExpression binExp = (IASTBinaryExpression)rightExp;

      return handleAssignmentOfBinaryExp(element, lParam, binExp.getOperand1(), binExp.getOperand2(), binExp.getOperator(), cfaEdge);
    }

    // TODO: track these more precisely
    else if(rightExp instanceof IASTFunctionCallExpression || rightExp instanceof IASTFieldReference)
      return element.clone().addInterval(constructVariableName(lParam, functionName), Interval.createUnboundInterval(), threshold);

    else
      throw new UnrecognizedCCodeException(cfaEdge, rightExp);
  }

  /**
   * This method handles the assignment of a casted variable to another variable.
   *
   * This method routes back to {@link IntervalAnalysisTransferRelation#handleAssignmentToVariable(IntervalAnalysisElement, String, IASTExpression, CFAEdge)} with the cast operation being removed.
   *
   * @author loewe
   * @param element the analysis element
   * @param lParam the local name of the variable to assign to
   * @param castExp the expression containing the cast
   * @param cfaEdge the respective CFA edge
   * @return the successor element
   */
  private IntervalAnalysisElement handleAssignmentOfCast(IntervalAnalysisElement element, String lParam, IASTCastExpression castExp, CFAEdge cfaEdge)
    throws UnrecognizedCCodeException
  {
    return handleAssignmentToVariable(element, lParam, castExp.getOperand(), cfaEdge);
  }

  /**
   * This method handles the assignment of a unary expressions to a variable.
   *
   * This method routes back to {@link IntervalAnalysisTransferRelation#handleAssignmentToVariable(IntervalAnalysisElement, String, IASTExpression, CFAEdge)} with the cast operation being removed.
   *
   * @author loewe
   * @param element the analysis element
   * @param lParam the local name of the variable to assign to
   * @param unaryExp the expression to evaluate
   * @param cfaEdge the respective CFA edge
   * @return the successor element
   */
  private IntervalAnalysisElement handleAssignmentOfUnaryExp(IntervalAnalysisElement element, String lParam, IASTUnaryExpression unaryExp, CFAEdge cfaEdge)
    throws UnrecognizedCCodeException
  {
    IntervalAnalysisElement newElement = element.clone();

    String functionName = cfaEdge.getPredecessor().getFunctionName();

    // name of the updated variable, so if a = -b is handled, lParam is a
    String assignedVar = constructVariableName(lParam, functionName);

    IASTExpression unaryOperand = unaryExp.getOperand();
    int unaryOperator = unaryExp.getOperator();

    // a = * b
    if (unaryOperator == IASTUnaryExpression.op_star)
      newElement.removeInterval(assignedVar);

    // a = (b + c)
    else if (unaryOperator == IASTUnaryExpression.op_bracketedPrimary)
      return handleAssignmentToVariable(element, lParam, unaryOperand, cfaEdge);

    // a = -b or similar
    else
    {
      Interval interval = getInterval(element, unaryExp, functionName, cfaEdge);

      if(interval != null)
        newElement.addInterval(assignedVar, interval, this.threshold);
      else
        newElement.removeInterval(assignedVar);
    }

    return newElement;
  }

  /**
   * This method handles the assignment of a binary expression to a variable.
   *
   * @author loewe
   * @param element  the analysis element
   * @param lParam the local name of the variable to assign to
   * @param lVarInBinaryExp the expression to the left of the operator
   * @param rVarInBinaryExp the expression to the right of the operator
   * @param binaryOperator the binary operator
   * @param cfaEdge the respective CFA edge
   * @return the successor element
   * @throws UnrecognizedCCodeException
   */
  private IntervalAnalysisElement handleAssignmentOfBinaryExp(IntervalAnalysisElement element, String lParam, IASTExpression lVarInBinaryExp, IASTExpression rVarInBinaryExp, int binaryOperator, CFAEdge cfaEdge)
    throws UnrecognizedCCodeException
  {
    String functionName = cfaEdge.getPredecessor().getFunctionName();
    // name of the updated variable, so if a = b + c is handled, lParam is a
    String assignedVar = constructVariableName(lParam, functionName);
    IntervalAnalysisElement newElement = element.clone();

    switch (binaryOperator)
    {
      case IASTBinaryExpression.op_divide:
      case IASTBinaryExpression.op_modulo:
      case IASTBinaryExpression.op_lessEqual:
      case IASTBinaryExpression.op_greaterEqual:
      case IASTBinaryExpression.op_binaryAnd:
      case IASTBinaryExpression.op_binaryOr:
        // TODO check which cases can be handled (I think all)
        newElement.removeInterval(assignedVar);
        break;

      case IASTBinaryExpression.op_plus:
      case IASTBinaryExpression.op_minus:
      case IASTBinaryExpression.op_multiply:

        Interval interval1 = null;
        Interval interval2 = null;

        // a = *b + c
        // TODO prepare for using strengthen operator to dereference pointer
        if(!(lVarInBinaryExp instanceof IASTUnaryExpression) || ((IASTUnaryExpression)lVarInBinaryExp).getOperator() != IASTUnaryExpression.op_star)
          interval1 = getInterval(element, lVarInBinaryExp, functionName, cfaEdge);

        if(interval1 != null)
          interval2 = getInterval(element, rVarInBinaryExp, functionName, cfaEdge);

        if(interval1 == null || interval2 == null)
          newElement.removeInterval(assignedVar);
        else
        {
          Interval interval;
          switch (binaryOperator)
          {
            case IASTBinaryExpression.op_plus:
              interval = interval1.plus(interval2);
              break;

            case IASTBinaryExpression.op_minus:
              interval = interval1.minus(interval2);
              break;

            case IASTBinaryExpression.op_multiply:
              interval = interval1.times(interval2);
              break;

            default:
              throw new UnrecognizedCCodeException("unkown binary operator", cfaEdge, rVarInBinaryExp.getParent());
          }
          newElement.addInterval(assignedVar, interval, this.threshold);
        }
    }

    return newElement;
  }

  /**
   * This method evaluates an expression and returns the respective interval.
   *
   * @author loewe
   * @param element the analysis element
   * @param expression the expression containing the expression to be evaluated
   * @param functionName the name of the function currently being analyzed
   * @param cfaEdge the respective CFA edge
   * @return the interval in respect to the evaluated expression of null, if the expression could not be evaluated properly
   */
  //getExpressionValue
  private Interval getInterval(IntervalAnalysisElement element, IASTExpression expression, String functionName, CFAEdge cfaEdge)
    throws UnrecognizedCCodeException
  {
    if(expression instanceof IASTLiteralExpression)
    {
      Long value = parseLiteral(expression);

      return (value == null) ? null : new Interval(value, value);
    }

    else if(expression instanceof IASTIdExpression)
    {
      String varName = constructVariableName(expression.getRawSignature(), functionName);

      return (element.contains(varName)) ? element.getInterval(varName) : Interval.createUnboundInterval();
    }

    else if(expression instanceof IASTCastExpression)
      return getInterval(element, ((IASTCastExpression)expression).getOperand(), functionName, cfaEdge);

    else if(expression instanceof IASTUnaryExpression)
    {
      IASTUnaryExpression unaryExpression = (IASTUnaryExpression)expression;

      int unaryOperator = unaryExpression.getOperator();
      IASTExpression unaryOperand = unaryExpression.getOperand();

      switch(unaryOperator)
      {
        case IASTUnaryExpression.op_minus:
          Interval interval = getInterval(element, unaryOperand, functionName, cfaEdge);

          return (interval == null) ? null : interval.negate();

        case IASTUnaryExpression.op_bracketedPrimary:
          return getInterval(element, unaryOperand, functionName, cfaEdge);

        case IASTUnaryExpression.op_amper:
          return null; // TODO valid expression, but it's a pointer value

        default:
          throw new UnrecognizedCCodeException("unknown unary operator", cfaEdge, unaryExpression);
      }
    }

    // TODO fields, arrays
    else
      throw new UnrecognizedCCodeException(cfaEdge, expression);
  }

  /**
   * This method handles the assignment of a variable to another variable.
   *
   * If a interval is tracked for the assigning variable, then the assigned variable will be associated with this interval, otherwise, a previously tracked interval for the assigned variable will be deleted.
   *
   * @author loewe
   * @param element the analysis element
   * @param lParam the local name of the variable to assign to
   * @param op2 the expression representing the assigning variable
   * @param functionName the name of the function currently being analyzed
   * @return the successor element
   */
  private IntervalAnalysisElement handleAssignmentOfVariable(IntervalAnalysisElement element, String lParam, IASTExpression op2, String functionName)
  {
    IntervalAnalysisElement newElement = element.clone();

    String assignedVar = constructVariableName(lParam, functionName);
    String assigningVar = constructVariableName(op2.getRawSignature(), functionName);

    if(newElement.contains(assigningVar))
      newElement.addInterval(assignedVar, newElement.getInterval(assigningVar), this.threshold);

    else
      newElement.removeInterval(assignedVar);

    return newElement;
  }

  /**
   * This method handles the assignment of a literal to a variable.
   *
   * @author loewe
   * @param element the analysis element
   * @param lParam the local name of the variable to assign to
   * @param op2 the expression representing the literal
   * @param functionName the name of the function currently being analyzed
   * @return the successor element
   */
  private IntervalAnalysisElement handleAssignmentOfLiteral(IntervalAnalysisElement element, String lParam, IASTExpression op2, String functionName)
    throws UnrecognizedCCodeException
  {
    IntervalAnalysisElement newElement = element.clone();

    String variableName = constructVariableName(lParam, functionName);

    // op2 may be null if this is a "return;" statement
    Long value = (op2 == null) ? 0L : parseLiteral(op2);

    // if the literal value could not be determined, remove the interval for the variable
    if(value == null)
      newElement.removeInterval(variableName);
    else
      newElement.addInterval(variableName, new Interval(value, value), threshold);

    return newElement;
  }

  /**
   * This method parses an expression to retrieve its literal value.
   *
   * @param expression the expression to parse
   * @return a number or null if the parsing failed
   * @throws UnrecognizedCCodeException
   */
  private Long parseLiteral(IASTExpression expression) throws UnrecognizedCCodeException
  {
    if(expression instanceof IASTLiteralExpression)
    {
      int typeOfLiteral = ((IASTLiteralExpression)expression).getKind();

      if(typeOfLiteral == IASTLiteralExpression.lk_integer_constant)
      {
        String s = expression.getRawSignature();
        if(s.endsWith("L") || s.endsWith("U") || s.endsWith("UL"))
        {
          s = s.replace("L", "");
          s = s.replace("U", "");
          s = s.replace("UL", "");
        }
        try
        {
          return Long.valueOf(s);
        }
        catch (NumberFormatException e)
        {
          throw new UnrecognizedCCodeException("invalid integer literal", null, expression);
        }
      }

      if(typeOfLiteral == IASTLiteralExpression.lk_string_literal)
        return (long)expression.hashCode();
    }

    return null;
  }

  /**
   * This method created a scoped variable name.
   *
   * @param variableName
   * @param functionName
   * @return a scoped variable name
   */
  public String constructVariableName(String variableName, String functionName)
  {
    if(globalVars.contains(variableName))
      return variableName;

    return functionName + "::" + variableName;
  }

  @Override
  public Collection<? extends AbstractElement> strengthen(AbstractElement element, List<AbstractElement> elements,
      CFAEdge cfaEdge, Precision precision) throws UnrecognizedCCodeException
  {
    assert element instanceof IntervalAnalysisElement;
    IntervalAnalysisElement intervalElement = (IntervalAnalysisElement)element;

    for(AbstractElement elem : elements)
    {
      if(elem instanceof PointerAnalysisElement)
        return strengthen(intervalElement, (PointerAnalysisElement)elem, cfaEdge, precision);
    }

    return null;
  }

  private Collection<? extends AbstractElement> strengthen(IntervalAnalysisElement intervalElement,
      PointerAnalysisElement pointerElement, CFAEdge cfaEdge, Precision precision) throws UnrecognizedCCodeException
  {
    return null;
  }
}