/*
* CPAchecker is a tool for configurable software verification.
* This file is part of CPAchecker.
*
* Copyright (C) 2007-2010 Dirk Beyer
* All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*
* CPAchecker web page:
* http://cpachecker.sosy-lab.org
*/

package org.sosy_lab.cpachecker.cpa.art;

import java.util.HashSet;
import java.util.Iterator;
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
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;
import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.ReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;

/** The Class ErrorPathShrinker gets an targetPath and creates a new Path, 
 * with only the important edges of the Path. The idea behind this Class is, 
 * that not every action (CFAEdge) before an error is important for the error, 
 * only a few actions (CFAEdges) are important. */
public class ErrorPathShrinker {

  /** Set<String> for storing the important variables */
  private final static Set<String> importantVars   = new HashSet<String>();

  private static boolean           printForTesting = false;

  /** The function shrinkErrorPath gets an targetPath and creates a new Path, 
   * with only the important edges of the Path. 
   * It only iterates the Path and checks every CFAEdge
   * 
   * @param targetPath the "long" targetPath
   * @return errorPath the "short" errorPath */
  public static final Path shrinkErrorPath(Path targetPath) {

    Path errorPath = new Path();

    // reverse iterator, from Error to rootNode
    Iterator<Pair<ARTElement, CFAEdge>> iterator =
        targetPath.descendingIterator();

    // the last element of the errorPath is the errorNode,
    errorPath.addFirst(iterator.next());

    // the last "action" before the errorNode is in the secondlast element
    // handle it to maybe get first important variables
    handle(iterator.next().getSecond());

    // iterate through the Path (backwards) and collect all important variables
    while (iterator.hasNext()) {

      Pair<ARTElement, CFAEdge> cfaEdge = iterator.next();

      boolean isCFAEdgeImportant = handle(cfaEdge.getSecond());

      if (isCFAEdgeImportant) {
        errorPath.addFirst(cfaEdge);
      }

      if (printForTesting) {
        // output for testing
        System.out.print("importantVars: { ");
        for (String var : importantVars) {
          System.out.print(var + " , ");
        }
        System.out.println(" }");
      }

    }
    return errorPath;
  }

  /** This function returns, if the edge is important. 
   * 
   * @param cfaEdge the edge to prove
   * @return isImportantEdge
   */
  private static boolean handle(CFAEdge cfaEdge) {

    // check the type of the edge
    switch (cfaEdge.getEdgeType()) {

    // if edge is a statement edge, e.g. a = b + c
    case StatementEdge:

      /* this is the statement edge which leads the function to the last node
      * of its CFA (not same as a return edge) */
      if (cfaEdge.isJumpEdge()) {
        return handleExitFromFunction(((StatementEdge) cfaEdge).getExpression());
      }
      // this is a regular statement
      else {
        return handleStatement(((StatementEdge) cfaEdge).getExpression());
      }

      // edge is a declaration edge, e.g. int a;
    case DeclarationEdge:
      return handleDeclaration((DeclarationEdge) cfaEdge);

      // this is an assumption, e.g. if(a == b)
    case AssumeEdge:
      return handleAssumption(((AssumeEdge) cfaEdge).getExpression());

    case BlankEdge:
      return false; // a blank edge is not important

    case FunctionCallEdge:
      return handleFunctionCall((FunctionCallEdge) cfaEdge);

      // this is a return edge from function, this is different from return 
      // statement of the function. See case in statement edge for details
    case ReturnEdge:
      return handleFunctionReturn((ReturnEdge) cfaEdge);

      // if edge cannot be handled, it could be important
    default:
      return true;
    }
  }

  /**
   * This method handles variable declarations.
   * 
   * @param declarationEdge the edge to prove
   * @return isImportantEdge
   */
  private static boolean handleDeclaration(DeclarationEdge declarationEdge) {

    // boolean for iteration
    boolean isImportant = false;

    // normally there is only one declarator, when are more than one?
    for (IASTDeclarator declarator : declarationEdge.getDeclarators()) {

      String varName = declarator.getName().getRawSignature();

      if (importantVars.contains(varName)) {

        // working: "int a;" --> if "a" is important, the edge is important
        // TODO problem: "int a=b+c;", if "a" is important, 
        // "b" and "c" also are important, add them to importantVars. how?
        // currently "b+c" is added to importantVars, not the single variables.
        // currently even numbers are added to importantVars.

        if (declarator.getInitializer() != null) {
          importantVars.add(declarator.getInitializer().getRawSignature());
        }

        // one important declaration is enough for an important edge
        isImportant = isImportant || importantVars.contains(varName);
      }
    }
    return isImportant;
  }

  /**
   * This method handles assumptions (a==b, a<=b, etc.).
   */
  private static boolean handleAssumption(IASTExpression assumeExp) {

    // first, unpack the expression to deal with a raw assumption
    if (assumeExp instanceof IASTUnaryExpression) {
      IASTUnaryExpression unaryExp = ((IASTUnaryExpression) assumeExp);

      switch (unaryExp.getOperator()) {
      // remove brackets
      case IASTUnaryExpression.op_bracketedPrimary:
        return handleAssumption(unaryExp.getOperand());

        // remove negation
      case IASTUnaryExpression.op_not:
        return handleAssumption(unaryExp.getOperand());

      default:
        return true;
      }
    }

    // a plain (boolean) identifier, e.g. if(a), always add to importantVars
    else if (assumeExp instanceof IASTIdExpression) {
      String varName = (assumeExp.getRawSignature());
      importantVars.add(varName);
      return true;
    }

    // "exp1 op exp2", where expX is only a literal or a identifier, 
    // add identifier to importantVars
    else if (assumeExp instanceof IASTBinaryExpression) {
      IASTBinaryExpression binExp = (IASTBinaryExpression) assumeExp;
      IASTExpression operand1 = binExp.getOperand1();
      IASTExpression operand2 = binExp.getOperand2();

      if (operand1 instanceof IASTIdExpression)
        importantVars.add(operand1.getRawSignature());

      if (operand2 instanceof IASTIdExpression)
        importantVars.add(operand2.getRawSignature());

      // TODO ((a==b) && (c||d)), get operands from difficult conditions

      return true;
    }

    // default (if edge cannot be handled, it could be important)
    return true;
  }

  private static boolean handleFunctionCall(FunctionCallEdge pCfaEdge) {
    // TODO Auto-generated method stub
    return true;
  }

  private static boolean handleFunctionReturn(ReturnEdge pCfaEdge) {
    // TODO Auto-generated method stub
    return true;
  }

  private static boolean handleExitFromFunction(IASTExpression exitExpression) {
    // nothing to do?
    return true;
  }

  private static boolean handleStatement(IASTExpression statementExp) {

    // a unary operation, e.g. a++
    // this does not change the Set of important variables, 
    // but the edge could be important
    if (statementExp instanceof IASTUnaryExpression)
      return handleUnaryStatement((IASTUnaryExpression) statementExp);

    // expression is a binary operation, e.g. a = b;
    else if (statementExp instanceof IASTBinaryExpression)
      return handleAssignment((IASTBinaryExpression) statementExp);

    // ext();
    else if (statementExp instanceof IASTFunctionCallExpression)
      return true;

    // a; 
    else if (statementExp instanceof IASTIdExpression) {
      String varName = statementExp.getRawSignature();
      return importantVars.contains(varName);
    }

    else
      return true;
  }

  /**
   * This method handles unary statements (a++, a--).
   *
   * @param statementEdge the edge to prove
   * @return isImportantEdge
   */
  private static boolean handleUnaryStatement(
      IASTUnaryExpression unaryExpression) {

    // get operand, i.e. "a"
    IASTExpression operand = unaryExpression.getOperand();

    // if operand is a identifier and is not important, the edge is not 
    // important, in any other case the edge could be important
    return !(operand instanceof IASTIdExpression && !importantVars
        .contains(operand.getRawSignature()));
  }

  /**
   * This method handles assignments (?a = ?).
   *
   * @param binaryExpression a binary expression
   * @return isImportantEdge
   */
  private static boolean handleAssignment(IASTBinaryExpression binaryExpression) {

    IASTExpression lParam = binaryExpression.getOperand1();
    IASTExpression rightExp = binaryExpression.getOperand2();

    // a = ?
    if (lParam instanceof IASTIdExpression)
      return handleAssignmentToVariable(lParam.getRawSignature(), rightExp);

    // TODO: assignment to pointer, *a = ?
    else if (lParam instanceof IASTUnaryExpression
        && ((IASTUnaryExpression) lParam).getOperator() == IASTUnaryExpression.op_star)
      return true;

    // TODO assignment to field, a->b = ?
    else if (lParam instanceof IASTFieldReference)
      return true;

    // TODO assignment to array cell, a[b] = ?
    else if (lParam instanceof IASTArraySubscriptExpression)
      return true;

    else
      // if the edge is not unimportant, this edge could be important.
      return true;
  }

  /**
   * This method handles the assignment of a variable (a = ?).
   *
   * @param lParam the local name of the variable to assign to
   * @param rightExp the assigning expression
   * @return isImportantEdge
   */
  private static boolean handleAssignmentToVariable(String lParam,
      IASTExpression rightExp) {

    // if lParam is important, the edge is important 
    // and every variable in rightExp is important.
    if (importantVars.contains(lParam)) {
      addAllVarsInExpToImportantVars(rightExp);
      return true;
    } else
      return false;
  }

  /**
   * This method adds all variables in an expression to the Set of important 
   * variables. If the expression exist of more than one sub-expressions, 
   * the expression is divided into smaller parts and the method is called 
   * recursively for each part, until there is only one variable or literal. 
   * Literals are not part of important variables. 
   *
   * @param exp the expression to be divided and added
   * @param binaryOperator the binary operator
   * @return isImportantEdge
   */
  private static void addAllVarsInExpToImportantVars(IASTExpression exp) {

    // a = 8.2 or "return;" (when rightExp == null),
    // this does not change the Set importantVars,
    if (exp instanceof IASTLiteralExpression || exp == null) {
      // do nothing
    }

    // a = b, b is an Identifier
    else if (exp instanceof IASTIdExpression) {
      importantVars.add(exp.getRawSignature());
    }
    // a = (cast) b 
    else if (exp instanceof IASTCastExpression) {
      addAllVarsInExpToImportantVars(((IASTCastExpression) exp).getOperand());
    }

    // a = -b
    else if (exp instanceof IASTUnaryExpression) {
      addAllVarsInExpToImportantVars(((IASTUnaryExpression) exp).getOperand());
    }

    // a = b op c; --> b is operand1, c is operand2
    else if (exp instanceof IASTBinaryExpression) {
      IASTBinaryExpression binExp = (IASTBinaryExpression) exp;
      addAllVarsInExpToImportantVars(binExp.getOperand1());
      addAllVarsInExpToImportantVars(binExp.getOperand2());
    }

    // a = func(); or a = b->c;
    else if (exp instanceof IASTFunctionCallExpression
        || exp instanceof IASTFieldReference) {
      // TODO: what should be added to importantVars?
    }
  }

}