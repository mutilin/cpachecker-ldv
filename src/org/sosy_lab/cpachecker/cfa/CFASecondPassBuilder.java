/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.ast.IASTExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTInitializer;
import org.sosy_lab.cpachecker.cfa.ast.IASTInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.IASTStatement;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionExitNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.CallToReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;
import org.sosy_lab.cpachecker.exceptions.ParserException;

/**
 * This class takes several CFAs (each for a single function) and combines them
 * into one CFA by inserting the necessary function call and return edges.
 */
public class CFASecondPassBuilder {

  private final Map<String, CFAFunctionDefinitionNode> cfas;

  /**
   * Class constructor.
   * @param map List of all CFA's in the program.
   */
  public CFASecondPassBuilder(Map<String, CFAFunctionDefinitionNode> cfas) {
    this.cfas = cfas;
  }

  /**
   * Inserts call edges and return edges (@see {@link #insertCallEdges(CFANode)}
   * in all functions.
   * @param functionName  The function where to start processing.
   * @throws ParserException
   */
  public void insertCallEdgesRecursively() throws ParserException {
    for (CFAFunctionDefinitionNode functionStartNode : cfas.values()) {
      insertCallEdges(functionStartNode);
    }
  }

  /**
   * Traverses a CFA with the specified function name and insert call edges
   * and return edges from the call site and to the return site of the function
   * call.
   * @param initialNode CFANode where to start processing
   * @throws ParserException
   */
  private void insertCallEdges(CFAFunctionDefinitionNode initialNode) throws ParserException {
    // we use a worklist algorithm
    Deque<CFANode> workList = new ArrayDeque<CFANode>();
    Set<CFANode> processed = new HashSet<CFANode>();

    workList.addLast(initialNode);

    while (!workList.isEmpty()) {
      CFANode node = workList.pollFirst();
      if (!processed.add(node)) {
        // already handled
        continue;
      }

      int numLeavingEdges = node.getNumLeavingEdges();

      for (int edgeIdx = 0; edgeIdx < numLeavingEdges; edgeIdx++) {
        CFAEdge edge = node.getLeavingEdge(edgeIdx);
        if (edge instanceof StatementEdge) {
          StatementEdge statement = (StatementEdge)edge;
          IASTStatement expr = statement.getStatement();

          // if statement is of the form x = call(a,b); or call(a,b);
          if (shouldCreateCallEdges(expr)) {
            createCallAndReturnEdges(statement, (IASTFunctionCall)expr);
          }

        } else if (edge instanceof DeclarationEdge) {
          // check if this is "int x = f()" and f is a non-extern function
          // we don't support this currently
          IASTInitializer init = ((DeclarationEdge)edge).getInitializer();
          if (init != null && init instanceof IASTInitializerExpression) {
            IASTRightHandSide initExpression = ((IASTInitializerExpression)init).getExpression();
            if (initExpression != null && initExpression instanceof IASTFunctionCallExpression) {
              IASTFunctionCallExpression f = (IASTFunctionCallExpression)initExpression;
              String name = f.getFunctionNameExpression().getRawSignature();
              if (cfas.containsKey(name)) {
                throw new ParserException("Function call in initializer not supported", edge);
              }
            }
          }
        }

        // if successor node is not on a different CFA, add it to the worklist
        CFANode successorNode = edge.getSuccessor();
        if (node.getFunctionName().equals(successorNode.getFunctionName())) {
          workList.add(successorNode);
        }
      }
    }
  }

  private boolean shouldCreateCallEdges(IASTStatement s) {
    if (!(s instanceof IASTFunctionCall)) {
      return false;
    }
    IASTFunctionCallExpression f = ((IASTFunctionCall)s).getFunctionCallExpression();
    String name = f.getFunctionNameExpression().getRawSignature();
    return cfas.containsKey(name);
  }

  /**
   * inserts call, return and summary edges from a node to its successor node.
   * @param edge The function call edge.
   * @param functionCall If the call was an assignment from the function call
   * this keeps only the function call expression, e.g. if statement is a = call(b);
   * then functionCall is call(b).
   */
  private void createCallAndReturnEdges(StatementEdge edge, IASTFunctionCall functionCall) {
    CFANode predecessorNode = edge.getPredecessor();
    CFANode successorNode = edge.getSuccessor();
    IASTFunctionCallExpression functionCallExpression = functionCall.getFunctionCallExpression();
    String functionName = functionCallExpression.getFunctionNameExpression().getRawSignature();
    int lineNumber = edge.getLineNumber();
    CFAFunctionDefinitionNode fDefNode = cfas.get(functionName);
    CFAFunctionExitNode fExitNode = fDefNode.getExitNode();

    assert fDefNode instanceof FunctionDefinitionNode : "This code creates edges from package cfa.objectmodel.c, so the nodes need to be from this package, too.";

    //get the parameter expression
    List<IASTExpression> parameters = functionCallExpression.getParameterExpressions();

    // delete old edge
    CFACreationUtils.removeEdgeFromNodes(edge);

    // create new edges
    CallToReturnEdge calltoReturnEdge = new CallToReturnEdge(functionCall.asStatement().getRawSignature(), lineNumber, predecessorNode, successorNode, functionCall);
    predecessorNode.addLeavingSummaryEdge(calltoReturnEdge);
    successorNode.addEnteringSummaryEdge(calltoReturnEdge);

    FunctionCallEdge callEdge = new FunctionCallEdge(functionCallExpression.getRawSignature(), edge.getStatement(), lineNumber, predecessorNode, (FunctionDefinitionNode)fDefNode, parameters, calltoReturnEdge);
    predecessorNode.addLeavingEdge(callEdge);
    fDefNode.addEnteringEdge(callEdge);

    if (fExitNode.getNumEnteringEdges() == 0) {
      // exit node of called functions is not reachable, i.e. this function never returns
      // no need to add return edges, instead we can remove the part after this function call

      CFACreationUtils.removeChainOfNodesFromCFA(successorNode);

    } else {

      FunctionReturnEdge returnEdge = new FunctionReturnEdge("Return Edge to " + successorNode.getNodeNumber(), lineNumber, fExitNode, successorNode, calltoReturnEdge);
      fExitNode.addLeavingEdge(returnEdge);
      successorNode.addEnteringEdge(returnEdge);
    }
  }
}
