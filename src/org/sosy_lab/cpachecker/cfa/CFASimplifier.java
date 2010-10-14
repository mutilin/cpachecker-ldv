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
package org.sosy_lab.cpachecker.cfa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;

import org.sosy_lab.cpachecker.cfa.objectmodel.BlankEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.MultiDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.MultiStatementEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;

/**
 * Used to simplify CPA by removing blank edges and combining block statements
 * @author erkan
 */
public class CFASimplifier {

  private final boolean combineBlockStatements;
  private final boolean removeDeclarations;

	public CFASimplifier(boolean combineBlockStatements, boolean removeDeclarations) {
	  this.combineBlockStatements = combineBlockStatements;
	  this.removeDeclarations = removeDeclarations;
	}

	/**
	 * Run the simplification algorithm on a given CFA. Uses BFS approach.
	 * @param cfa CFA to be simplified
	 */
	public void simplify (CFAFunctionDefinitionNode cfa)
	{
		Set<CFANode> visitedNodes = new HashSet<CFANode> ();
		Deque<CFANode> waitingNodeList = new ArrayDeque<CFANode> ();
		Set<CFANode> waitingNodeSet = new HashSet<CFANode> ();

		waitingNodeList.add (cfa);
		waitingNodeSet.add (cfa);
		while (!waitingNodeList.isEmpty ())
		{
			CFANode node = waitingNodeList.poll ();
			waitingNodeSet.remove (node);

			visitedNodes.add (node);

			int leavingEdgeCount = node.getNumLeavingEdges ();
			for (int edgeIdx = 0; edgeIdx < leavingEdgeCount; edgeIdx++)
			{
				CFAEdge edge = node.getLeavingEdge (edgeIdx);
				CFANode successor = edge.getSuccessor ();

				if ((!visitedNodes.contains (successor)) && (!waitingNodeSet.contains (successor)))
				{
					waitingNodeList.add (successor);
					waitingNodeSet.add (successor);
				}
			}

			// The actual simplification part
	    if (combineBlockStatements) {
	      makeMultiStatement(node);
	      makeMultiDeclaration(node);
	    }

	    if (removeDeclarations) {
	      removeDeclarations(node);
	    }
	  }
	}

	/**
	 * Removes declaration edges when cfa.removeDeclarations is set
	 * to true.
	 * @param node
	 */
	private void removeDeclarations(CFANode node) {

		if (node.getNumLeavingEdges() != 1) {
			return;
		}

		CFAEdge leavingEdge = node.getLeavingEdge(0);
		if (leavingEdge.getEdgeType() != CFAEdgeType.DeclarationEdge) {
			return;
		}
    CFANode successor = leavingEdge.getSuccessor ();

		node.removeLeavingEdge(leavingEdge);
		successor.removeEnteringEdge(leavingEdge);

		BlankEdge be = new BlankEdge("removed declaration", leavingEdge.getLineNumber(), node, successor);
		be.addToCFA(null);
	}

	private void makeMultiStatement (CFANode cfa)
	{
		if ((cfa.getNumEnteringEdges () != 1) || (cfa.getNumLeavingEdges () != 1) || (cfa.hasJumpEdgeLeaving ()))
			return;

		CFAEdge leavingEdge = cfa.getLeavingEdge (0);
		if (leavingEdge.getEdgeType () != CFAEdgeType.StatementEdge)
			return;

		StatementEdge leavingStatementEdge = (StatementEdge) leavingEdge;

		CFAEdge enteringEdge = cfa.getEnteringEdge (0);
		if (enteringEdge.getEdgeType () == CFAEdgeType.StatementEdge)
		{
			List<IASTExpression> expressions = new ArrayList<IASTExpression> ();
			expressions.add (((StatementEdge)enteringEdge).getExpression ());
			expressions.add (leavingStatementEdge.getExpression ());

			CFANode priorNode = enteringEdge.getPredecessor ();
			CFANode afterNode = leavingEdge.getSuccessor ();

			priorNode.removeLeavingEdge (enteringEdge);
			afterNode.removeEnteringEdge (leavingEdge);

			MultiStatementEdge msEdge = new MultiStatementEdge("multi-statement edge", enteringEdge.getLineNumber(), priorNode, afterNode, expressions);
			msEdge.addToCFA(null);
		}
		else if (enteringEdge.getEdgeType () == CFAEdgeType.MultiStatementEdge)
		{
			MultiStatementEdge msEdge = (MultiStatementEdge) enteringEdge;
			List<IASTExpression> expressions = msEdge.getExpressions ();
			expressions.add (leavingStatementEdge.getExpression ());

	    CFANode priorNode = enteringEdge.getPredecessor ();
	    CFANode afterNode = leavingEdge.getSuccessor ();

	    priorNode.removeLeavingEdge (enteringEdge);
	    afterNode.removeEnteringEdge (leavingEdge);

	    MultiStatementEdge newMsEdge = new MultiStatementEdge("multi-statement edge", enteringEdge.getLineNumber(), priorNode, afterNode, expressions);
	    newMsEdge.addToCFA(null);
		}
	}

	private void makeMultiDeclaration (CFANode cfa)
	{
		if ((cfa.getNumEnteringEdges () != 1) || (cfa.getNumLeavingEdges () != 1) || (cfa.hasJumpEdgeLeaving ()))
			return;

		CFAEdge leavingEdge = cfa.getLeavingEdge (0);
		if (leavingEdge.getEdgeType () != CFAEdgeType.DeclarationEdge)
			return;

		DeclarationEdge leavingDeclarationEdge = (DeclarationEdge) leavingEdge;

		CFAEdge enteringEdge = cfa.getEnteringEdge (0);
		if (enteringEdge.getEdgeType () == CFAEdgeType.DeclarationEdge)
		{
		  DeclarationEdge enteringDeclarationEdge = (DeclarationEdge)enteringEdge;
		  if (enteringDeclarationEdge.isGlobal() != leavingDeclarationEdge.isGlobal()) {
		    return;
		  }
		  
			List<IASTSimpleDeclaration> declarations = new ArrayList<IASTSimpleDeclaration>();
			List<String> rawStatements = new ArrayList<String> ();

			declarations.add(enteringDeclarationEdge.getRawAST());
			declarations.add(leavingDeclarationEdge.getRawAST());

			rawStatements.add (enteringEdge.getRawStatement ());
			rawStatements.add (leavingDeclarationEdge.getRawStatement ());

			CFANode priorNode = enteringEdge.getPredecessor ();
			CFANode afterNode = leavingEdge.getSuccessor ();

			priorNode.removeLeavingEdge (enteringEdge);
			afterNode.removeEnteringEdge (leavingEdge);

			MultiDeclarationEdge mdEdge = new MultiDeclarationEdge("multi-declaration edge",
			    enteringEdge.getLineNumber(), priorNode, afterNode, declarations,
			    rawStatements, enteringDeclarationEdge.isGlobal());
			mdEdge.addToCFA(null);
		}
		else if (enteringEdge.getEdgeType () == CFAEdgeType.MultiDeclarationEdge)
		{
			MultiDeclarationEdge mdEdge = (MultiDeclarationEdge) enteringEdge;
      if (mdEdge.isGlobal() != leavingDeclarationEdge.isGlobal()) {
        return;
      }

			List<IASTSimpleDeclaration> declarations = mdEdge.getDeclarators();
			declarations.add(leavingDeclarationEdge.getRawAST());

			List<String> rawStatements = mdEdge.getRawStatements ();
			rawStatements.add (leavingDeclarationEdge.getRawStatement ());

	    CFANode priorNode = enteringEdge.getPredecessor ();
	    CFANode afterNode = leavingEdge.getSuccessor ();

      priorNode.removeLeavingEdge (enteringEdge);
	    afterNode.removeEnteringEdge (leavingEdge);

      MultiDeclarationEdge newMdEdge = new MultiDeclarationEdge("multi-declaration edge",
          enteringEdge.getLineNumber(), priorNode, afterNode, declarations,
          rawStatements, mdEdge.isGlobal());
      newMdEdge.addToCFA(null);
		}
	}
}
