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
package org.sosy_lab.cpachecker.cfa.parser.eclipse.c;

import static com.google.common.base.Preconditions.checkState;
import static org.sosy_lab.cpachecker.cfa.CFACreationUtils.isReachableNode;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTASMDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTBreakStatement;
import org.eclipse.cdt.core.dom.ast.IASTCaseStatement;
import org.eclipse.cdt.core.dom.ast.IASTCompoundStatement;
import org.eclipse.cdt.core.dom.ast.IASTConditionalExpression;
import org.eclipse.cdt.core.dom.ast.IASTContinueStatement;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDeclarationStatement;
import org.eclipse.cdt.core.dom.ast.IASTDefaultStatement;
import org.eclipse.cdt.core.dom.ast.IASTDoStatement;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpressionList;
import org.eclipse.cdt.core.dom.ast.IASTExpressionStatement;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTForStatement;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTGotoStatement;
import org.eclipse.cdt.core.dom.ast.IASTIfStatement;
import org.eclipse.cdt.core.dom.ast.IASTLabelStatement;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTNullStatement;
import org.eclipse.cdt.core.dom.ast.IASTProblem;
import org.eclipse.cdt.core.dom.ast.IASTProblemDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTProblemStatement;
import org.eclipse.cdt.core.dom.ast.IASTReturnStatement;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTSwitchStatement;
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTWhileStatement;
import org.eclipse.cdt.core.dom.ast.gnu.IGNUASTCompoundStatementExpression;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFACreationUtils;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CReturnStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.CLabelNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CDefaults;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFAUtils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * Builder to traverse AST.
 * Known Limitations:
 * -- K&R style function definitions not implemented
 * -- Inlined assembler code is ignored
 */
@Options(prefix="cfa")
class CFAFunctionBuilder extends ASTVisitor {

  // Data structure for maintaining our scope stack in a function
  private final Deque<CFANode> locStack = new ArrayDeque<>();

  // Data structures for handling loops & else conditions
  private final Deque<CFANode> loopStartStack = new ArrayDeque<>();
  private final Deque<CFANode> loopNextStack  = new ArrayDeque<>(); // For the node following the current if / while block
  private final Deque<CFANode> elseStack      = new ArrayDeque<>();

  // Data structure for handling switch-statements
  private final Deque<CExpression> switchExprStack = new ArrayDeque<>();
  private final Deque<CFANode> switchCaseStack = new ArrayDeque<>();
  private final Deque<CFANode> switchDefaultStack = new LinkedList<>(); // ArrayDeque not possible because it does not allow null

  // Data structures for handling goto
  private final Map<String, CLabelNode> labelMap = new HashMap<>();
  private final Multimap<String, CFANode> gotoLabelNeeded = ArrayListMultimap.create();

  // Data structures for handling function declarations
  private FunctionEntryNode cfa = null;
  private final List<CFANode> cfaNodes = new ArrayList<>();

  private final FunctionScope scope;
  private final ASTConverter astCreator;

  private final LogManager logger;
  private final CheckBindingVisitor checkBinding;

  private boolean encounteredAsm = false;

  @Option(description="Also initialize local variables with default values, "
      + "or leave them uninitialized.")
  private boolean initializeAllVariables = false;

  public CFAFunctionBuilder(Configuration config, LogManager pLogger, FunctionScope pScope, MachineModel pMachine) throws InvalidConfigurationException {
    config.inject(this);

    logger = pLogger;
    scope = pScope;
    astCreator = new ASTConverter(config, pScope, pLogger, pMachine);
    checkBinding = new CheckBindingVisitor(pLogger);

    shouldVisitDeclarations = true;
    shouldVisitEnumerators = true;
    shouldVisitParameterDeclarations = true;
    shouldVisitProblems = true;
    shouldVisitStatements = true;
  }

  FunctionEntryNode getStartNode() {
    checkState(cfa != null);
    return cfa;
  }

  List<CFANode> getCfaNodes() {
    checkState(cfa != null);
    return cfaNodes;
  }

  boolean didEncounterAsm() {
    return encounteredAsm;
  }

  /**
   * This method is called after parsing and checks if we left everything clean.
   */
  void finish() {
    assert astCreator.getAndResetPreSideAssignments().isEmpty();
    assert astCreator.getAndResetPostSideAssignments().isEmpty();
    assert locStack.isEmpty();
    assert loopStartStack.isEmpty();
    assert loopNextStack.isEmpty();
    assert elseStack.isEmpty();
    assert switchCaseStack.isEmpty();
    assert switchExprStack.isEmpty();
    assert gotoLabelNeeded.isEmpty();
  }


  /////////////////////////////////////////////////////////////////////////////
  // Declarations
  /////////////////////////////////////////////////////////////////////////////

  /**
   * @category declarations
   */
  @Override
  public int visit(IASTDeclaration declaration) {
    IASTFileLocation fileloc = declaration.getFileLocation();

    if (declaration instanceof IASTSimpleDeclaration) {
      return handleSimpleDeclaration((IASTSimpleDeclaration)declaration, fileloc);

    } else if (declaration instanceof IASTFunctionDefinition) {
      return handleFunctionDefinition((IASTFunctionDefinition)declaration, fileloc);

    } else if (declaration instanceof IASTProblemDeclaration) {
      // CDT parser struggles on GCC's __attribute__((something)) constructs
      // because we use C99 as default.
      // Either insert the following macro before compiling with CIL:
      // #define  __attribute__(x)  /*NOTHING*/
      // or insert "parser.dialect = GNUC" into properties file
      visit(((IASTProblemDeclaration)declaration).getProblem());
      return PROCESS_SKIP;

    } else if (declaration instanceof IASTASMDeclaration) {
      return ignoreASMDeclaration(fileloc, declaration);

    } else {
      throw new CFAGenerationRuntimeException("Unknown declaration type " + declaration.getClass().getSimpleName(), declaration);
    }
  }

  /**
   * @category declarations
   */
  private int handleSimpleDeclaration(final IASTSimpleDeclaration sd, final IASTFileLocation fileloc) {

    assert (locStack.size() > 0) : "not in a function's scope";

    CFANode prevNode = locStack.pop();

    CFANode nextNode = createEdgeForDeclaration(sd, fileloc.getStartingLineNumber(), prevNode);

    assert nextNode != null;
    locStack.push(nextNode);

    return PROCESS_SKIP; // important to skip here, otherwise we would visit nested declarations
  }

  /**
   * This method takes a list of Declarations and adds them to the CFA.
   * The edges are inserted after startNode.
   * @return the node after the last of the new declarations
   * @category declarations
   */
  private CFANode createEdgeForDeclaration(final IASTSimpleDeclaration sd,
      final int filelocStart, CFANode prevNode) {

    assert astCreator.getAndResetPostSideAssignments().isEmpty()
          : "post side assignments should occur only on declarations," +
            "but they occurred somewhere else and where not handled";
    final List<CDeclaration> declList = astCreator.convert(sd);
    final String rawSignature = sd.getRawSignature();

    prevNode = handleAllSideEffects(prevNode, filelocStart, rawSignature, true);

    // create one edge for every declaration
    for (CDeclaration newD : declList) {

      if (newD instanceof CVariableDeclaration) {
        // Variables are already declared by ASTConverter.
        // This is needed to handle the binding in the initializer correctly.
        // scope.registerDeclaration(newD);
        assert scope.lookupVariable(newD.getOrigName()) == newD;

        CInitializer init = ((CVariableDeclaration) newD).getInitializer();
        if (init != null) {
          init.accept(checkBinding);
        } else if (initializeAllVariables) {
          CInitializer initializer = CDefaults.forType(newD.getType(), newD.getFileLocation());
          newD = new CVariableDeclaration(newD.getFileLocation(),
                                          newD.isGlobal(),
                                          ((CVariableDeclaration) newD).getCStorageClass(),
                                          newD.getType(),
                                          newD.getName(),
                                          newD.getOrigName(),
                                          newD.getQualifiedName(),
                                          initializer);
        }

      } else if (newD instanceof CComplexTypeDeclaration) {
        scope.registerTypeDeclaration((CComplexTypeDeclaration)newD);
      } else {
        assert !(newD instanceof CFunctionDeclaration) : "Function declaration inside function";
      }

      CFANode nextNode = newCFANode(filelocStart);

      final CDeclarationEdge edge = new CDeclarationEdge(rawSignature, filelocStart,
          prevNode, nextNode, newD);
      addToCFA(edge);

      prevNode = nextNode;
    }
    prevNode = createEdgesForSideEffects(prevNode, astCreator.getAndResetPostSideAssignments(), rawSignature, filelocStart);

    return prevNode;
  }

  /**
   * @category declarations
   */
  private int handleFunctionDefinition(final IASTFunctionDefinition declaration,
      final IASTFileLocation fileloc) {

    if (locStack.size() != 0) {
      throw new CFAGenerationRuntimeException("Nested function declarations?");
    }

    assert labelMap.isEmpty();
    assert gotoLabelNeeded.isEmpty();
    assert cfa == null;

    final CFunctionDeclaration fdef = astCreator.convert(declaration);
    final String nameOfFunction = fdef.getName();
    assert !nameOfFunction.isEmpty();

    scope.enterFunction(fdef);

    final List<CParameterDeclaration> parameters = fdef.getParameters();
    final List<String> parameterNames = new ArrayList<>(parameters.size());

    for (CParameterDeclaration param : parameters) {
      scope.registerDeclaration(param); // declare parameter as local variable
      parameterNames.add(param.getName());
    }

    final FunctionExitNode returnNode = new FunctionExitNode(fileloc.getEndingLineNumber(), nameOfFunction);
    cfaNodes.add(returnNode);

    final FunctionEntryNode startNode = new CFunctionEntryNode(
        fileloc.getStartingLineNumber(), fdef, returnNode, parameterNames);
    cfaNodes.add(startNode);
    returnNode.setEntryNode(startNode);
    cfa = startNode;

    final CFANode nextNode = newCFANode(fileloc);
    locStack.add(nextNode);

    final BlankEdge dummyEdge = new BlankEdge("", fileloc.getStartingLineNumber(),
        startNode, nextNode, "Function start dummy edge");
    addToCFA(dummyEdge);

    return PROCESS_CONTINUE;
  }

  /**
   * @category declarations
   */
  private int ignoreASMDeclaration(final IASTFileLocation fileloc, final IASTNode asmCode) {
    logger.log(Level.FINER, "Ignoring inline assembler code at line", fileloc.getStartingLineNumber());
    encounteredAsm = true;

    final CFANode prevNode = locStack.pop();

    final CFANode nextNode = newCFANode(fileloc);
    locStack.push(nextNode);

    final BlankEdge edge = new BlankEdge(asmCode.getRawSignature(),
        fileloc.getStartingLineNumber(), prevNode, nextNode, "Ignored inline assembler code");
    addToCFA(edge);

    return PROCESS_SKIP;
  }

  /**
   * @category declarations
   */
  @Override
  public int leave(IASTDeclaration declaration) {
    if (declaration instanceof IASTFunctionDefinition) {

      if (locStack.size() != 1) {
        throw new CFAGenerationRuntimeException("Depth wrong. Geoff needs to do more work");
      }

      CFANode lastNode = locStack.pop();

      if (isReachableNode(lastNode)) {
        BlankEdge blankEdge = new BlankEdge("",
            lastNode.getLineNumber(), lastNode, cfa.getExitNode(), "default return");
        addToCFA(blankEdge);
      }

      if (!gotoLabelNeeded.isEmpty()) {
        throw new CFAGenerationRuntimeException("Following labels were not found in function "
              + cfa.getFunctionName() + ": " + gotoLabelNeeded.keySet());
      }

      Set<CFANode> reachableNodes = CFATraversal.dfs().collectNodesReachableFrom(cfa);

      for (CLabelNode n : labelMap.values()) {
        if (!reachableNodes.contains(n)) {
          logDeadLabel(n);

          // remove all entering edges
          while (n.getNumEnteringEdges() > 0) {
            CFACreationUtils.removeEdgeFromNodes(n.getEnteringEdge(0));
          }

          // now we can delete this whole unreachable part
          CFACreationUtils.removeChainOfNodesFromCFA(n);
        }
      }

      // remove node which were created but aren't part of CFA (e.g. because of dead code)
      cfaNodes.retainAll(reachableNodes);
      assert cfaNodes.size() == reachableNodes.size(); // they should be equal now
    }

    return PROCESS_CONTINUE;
  }

  /**
   * @category declarations
   */
  private void logDeadLabel(CLabelNode n) {
    Level level = Level.INFO;
    if (n.getLabel().matches("(switch|while)_(\\d+_[a-z0-9]+|[a-z0-9]+___\\d+)")) {
      // don't mention dead code produced by CIL on normal log levels
      level = Level.FINER;
    }
    logger.log(level, "Dead code detected at line", n.getLineNumber() + ": Label",
        n.getLabel(), "is not reachable.");
  }


  /////////////////////////////////////////////////////////////////////////////
  // Statements
  /////////////////////////////////////////////////////////////////////////////

  /**
   * @category statements
   */
  @Override
  public int visit(IASTStatement statement) {
    IASTFileLocation fileloc = statement.getFileLocation();

    // Handle special condition for else
    if (statement.getPropertyInParent() == IASTIfStatement.ELSE) {
      // Edge from current location to post if-statement location
      CFANode prevNode = locStack.pop();
      CFANode nextNode = locStack.peek();

      if (isReachableNode(prevNode)) {
        BlankEdge blankEdge = new BlankEdge("", nextNode.getLineNumber(), prevNode, nextNode, "");
        addToCFA(blankEdge);
      }

      //  Push the start of the else clause onto our location stack
      CFANode elseNode = elseStack.pop();
      locStack.push(elseNode);
    }

    // Handle each kind of expression
    if (statement instanceof IASTCompoundStatement) {
      if (statement.getPropertyInParent() == IGNUASTCompoundStatementExpression.STATEMENT) {
        // IGNUASTCompoundStatementExpression content is already handled
        return PROCESS_SKIP;
      }

      scope.enterBlock();
      // Do nothing, just continue visiting
    } else if (statement instanceof IASTExpressionStatement) {
      handleExpressionStatement((IASTExpressionStatement)statement, fileloc);
    } else if (statement instanceof IASTIfStatement) {
      handleIfStatement((IASTIfStatement)statement, fileloc);
    } else if (statement instanceof IASTWhileStatement) {
      handleWhileStatement((IASTWhileStatement)statement, fileloc);
    } else if (statement instanceof IASTForStatement) {
      return handleForStatement((IASTForStatement)statement, fileloc);
    } else if (statement instanceof IASTBreakStatement) {
      handleBreakStatement((IASTBreakStatement)statement, fileloc);
    } else if (statement instanceof IASTContinueStatement) {
      handleContinueStatement((IASTContinueStatement)statement, fileloc);
    } else if (statement instanceof IASTLabelStatement) {
      handleLabelStatement((IASTLabelStatement)statement, fileloc);
    } else if (statement instanceof IASTGotoStatement) {
      handleGotoStatement((IASTGotoStatement)statement, fileloc);
    } else if (statement instanceof IASTReturnStatement) {
      handleReturnStatement((IASTReturnStatement)statement, fileloc);
    } else if (statement instanceof IASTSwitchStatement) {
      return handleSwitchStatement((IASTSwitchStatement)statement, fileloc);
    } else if (statement instanceof IASTCaseStatement) {
      handleCaseStatement((IASTCaseStatement)statement, fileloc);
    } else if (statement instanceof IASTDefaultStatement) {
      handleDefaultStatement((IASTDefaultStatement)statement, fileloc);
    } else if (statement instanceof IASTNullStatement) {
      // We really don't care about blank statements
    } else if (statement instanceof IASTDeclarationStatement) {
      // these are handled by visit(IASTDeclaration)
    } else if (statement instanceof IASTProblemStatement) {
      visit(((IASTProblemStatement)statement).getProblem());
    } else if (statement instanceof IASTDoStatement) {
      handleDoWhileStatement((IASTDoStatement)statement, fileloc);
    } else {
      throw new CFAGenerationRuntimeException("Unknown AST node "
          + statement.getClass().getSimpleName(), statement);
    }

    return PROCESS_CONTINUE;
  }

  /**
   * @category statements
   */
  private void handleExpressionStatement(IASTExpressionStatement exprStatement,
      IASTFileLocation fileloc) {

    CFANode prevNode = locStack.pop();
    CFANode lastNode = null;
    String rawSignature = exprStatement.getRawSignature();

    if (exprStatement.getExpression() instanceof IASTExpressionList) {
      for (IASTExpression exp : ((IASTExpressionList) exprStatement.getExpression()).getExpressions()) {
        CStatement statement = astCreator.convertExpressionToStatement(exp);
        lastNode = createIASTExpressionStatementEdges(rawSignature, fileloc, prevNode, statement);
        prevNode = lastNode;
      }
      assert lastNode != null;
    } else {
      CStatement statement = astCreator.convert(exprStatement);
      lastNode = createIASTExpressionStatementEdges(rawSignature, fileloc, prevNode, statement);
    }
    locStack.push(lastNode);
  }

  /**
   * @category statements
   */
  private CFANode createIASTExpressionStatementEdges(String rawSignature, IASTFileLocation fileloc,
      CFANode prevNode, CStatement statement) {

    CFANode lastNode;
    boolean resultIsUsed = true;

    if (astCreator.hasConditionalExpression()
        && (statement instanceof CExpressionStatement)) {
      // this may be code where the resulting value of a ternary operator is not used, e.g. (x ? f() : g())

      List<Pair<IASTExpression, CIdExpression>> tempVars = astCreator.getConditionalExpressions();
      if ((tempVars.size() == 1) && (tempVars.get(0).getSecond() == ((CExpressionStatement)statement).getExpression())) {
        resultIsUsed = false;
      }
    }

    prevNode = handleAllSideEffects(prevNode, fileloc.getStartingLineNumber(), rawSignature, resultIsUsed);

    statement.accept(checkBinding);
    if (resultIsUsed) {
      lastNode = newCFANode(fileloc);

      CStatementEdge edge = new CStatementEdge(rawSignature, statement,
          fileloc.getStartingLineNumber(), prevNode, lastNode);
      addToCFA(edge);
    } else {
      lastNode = prevNode;
    }
    return lastNode;
  }

  /**
   * @category statements
   */
  private void handleLabelStatement(IASTLabelStatement labelStatement,
      IASTFileLocation fileloc) {

    String labelName = labelStatement.getName().toString();
    if (labelMap.containsKey(labelName)) {
      throw new CFAGenerationRuntimeException("Duplicate label " + labelName
          + " in function " + cfa.getFunctionName(), labelStatement);
    }

    CFANode prevNode = locStack.pop();

    CLabelNode labelNode = new CLabelNode(fileloc.getStartingLineNumber(),
        cfa.getFunctionName(), labelName);
    cfaNodes.add(labelNode);
    locStack.push(labelNode);
    labelMap.put(labelName, labelNode);

    if (isReachableNode(prevNode)) {
      BlankEdge blankEdge = new BlankEdge(labelStatement.getRawSignature(),
          fileloc.getStartingLineNumber(), prevNode, labelNode, "Label: " + labelName);
      addToCFA(blankEdge);
    }

    // Check if any goto's previously analyzed need connections to this label
    for (CFANode gotoNode : gotoLabelNeeded.get(labelName)) {
      String description = "Goto: " + labelName;
      BlankEdge gotoEdge = new BlankEdge(description,
          gotoNode.getLineNumber(), gotoNode, labelNode, description);
      addToCFA(gotoEdge);
    }
    gotoLabelNeeded.removeAll(labelName);
  }

  /**
   * @category statements
   */
  private void handleGotoStatement(IASTGotoStatement gotoStatement,
      IASTFileLocation fileloc) {

    String labelName = gotoStatement.getName().toString();

    CFANode prevNode = locStack.pop();
    CFANode labelNode = labelMap.get(labelName);
    if (labelNode != null) {
      BlankEdge gotoEdge = new BlankEdge(gotoStatement.getRawSignature(),
          fileloc.getStartingLineNumber(), prevNode, labelNode, "Goto: " + labelName);

      /* labelNode was analyzed before, so it is in the labelMap,
       * then there can be a jump backwards and this can create a loop.
       * If LabelNode has not been the start of a loop, Node labelNode can be
       * the start of a loop, so check if there is a path from labelNode to
       * the current Node through DFS-search */
      if (!labelNode.isLoopStart() && isPathFromTo(labelNode, prevNode)) {
        labelNode.setLoopStart();
      }

      addToCFA(gotoEdge);
    } else {
      gotoLabelNeeded.put(labelName, prevNode);
    }

    CFANode nextNode = newCFANode(fileloc.getEndingLineNumber());
    locStack.push(nextNode);
  }

  /**
   * @category statements
   */
  private void handleReturnStatement(IASTReturnStatement returnStatement,
      IASTFileLocation fileloc) {

    CFANode prevNode = locStack.pop();
    FunctionExitNode functionExitNode = cfa.getExitNode();

    CReturnStatement returnstmt = astCreator.convert(returnStatement);
    prevNode = handleAllSideEffects(prevNode, returnstmt.getFileLocation().getStartingLineNumber(), returnStatement.getRawSignature(), true);

    if (returnstmt.getReturnValue() != null) {
      returnstmt.getReturnValue().accept(checkBinding);
    }
    CReturnStatementEdge edge = new CReturnStatementEdge(returnStatement.getRawSignature(),
    returnstmt, fileloc.getStartingLineNumber(), prevNode, functionExitNode);
    addToCFA(edge);

    CFANode nextNode = newCFANode(fileloc.getEndingLineNumber());
    locStack.push(nextNode);
  }

  /**
   * @category statements
   */
  @Override
  public int leave(IASTStatement statement) {
    if (statement instanceof IASTIfStatement) {
      final CFANode prevNode = locStack.pop();
      final CFANode nextNode = locStack.peek();

      if (isReachableNode(prevNode)) {

        for (CFAEdge prevEdge : CFAUtils.allEnteringEdges(prevNode).toList()) {
          if ((prevEdge instanceof BlankEdge)
              && prevEdge.getDescription().equals("")) {

            // the only entering edge is a BlankEdge, so we delete this edge and prevNode

            CFANode prevPrevNode = prevEdge.getPredecessor();
            assert prevPrevNode.getNumLeavingEdges() == 1;
            prevNode.removeEnteringEdge(prevEdge);
            prevPrevNode.removeLeavingEdge(prevEdge);

            BlankEdge blankEdge = new BlankEdge("", prevNode.getLineNumber(),
                prevPrevNode, nextNode, "");
            addToCFA(blankEdge);
          }
        }

        if (prevNode.getNumEnteringEdges() > 0) {
          BlankEdge blankEdge = new BlankEdge("", prevNode.getLineNumber(),
              prevNode, nextNode, "");
          addToCFA(blankEdge);
        }
      }

    } else if (statement instanceof IASTCompoundStatement) {
      if (statement.getPropertyInParent() == IGNUASTCompoundStatementExpression.STATEMENT) {
        // IGNUASTCompoundStatementExpression content is already handled
        return PROCESS_SKIP;
      }

      scope.leaveBlock();

    } else if (statement instanceof IASTWhileStatement
            || statement instanceof IASTDoStatement) {
      CFANode prevNode = locStack.pop();
      CFANode startNode = loopStartStack.pop();

      if (isReachableNode(prevNode)) {
        BlankEdge blankEdge = new BlankEdge("", prevNode.getLineNumber(),
            prevNode, startNode, "");
        addToCFA(blankEdge);
      }
      CFANode nextNode = loopNextStack.pop();
      assert nextNode == locStack.peek();
    }
    return PROCESS_CONTINUE;
  }

  /**
   * @category statements
   */
  @Override
  public int visit(IASTProblem problem) {
    throw new CFAGenerationRuntimeException(problem);
  }


  /////////////////////////////////////////////////////////////////////////////
  // Generic helper methods
  /////////////////////////////////////////////////////////////////////////////

  /**
   * This method adds this edge to the leaving and entering edges
   * of its predecessor and successor respectively, but it does so only
   * if the edge does not contain dead code
   * @category helper
   */
  private void addToCFA(CFAEdge edge) {
    CFACreationUtils.addEdgeToCFA(edge, logger);
  }

  /**
   * @category helper
   */
  private CFANode newCFANode(final IASTFileLocation fileloc) {
    return newCFANode(fileloc.getStartingLineNumber());
  }

  /**
   * @category helper
   */
  private CFANode newCFANode(final int filelocStart) {
    assert cfa != null;
    CFANode nextNode = new CFANode(filelocStart, cfa.getFunctionName());
    cfaNodes.add(nextNode);
    return nextNode;
  }

  /**
   * Determines whether a forwards path between two nodes exists.
   *
   * @param fromNode starting node
   * @param toNode target node
   * @category helper
   */
  private boolean isPathFromTo(CFANode fromNode, CFANode toNode) {
    // Optimization: do two DFS searches in parallel:
    // 1) search forwards from fromNode
    // 2) search backwards from toNode
    Deque<CFANode> toProcessForwards = new ArrayDeque<>();
    Deque<CFANode> toProcessBackwards = new ArrayDeque<>();
    Set<CFANode> visitedForwards = new HashSet<>();
    Set<CFANode> visitedBackwards = new HashSet<>();

    toProcessForwards.addLast(fromNode);
    visitedForwards.add(fromNode);

    toProcessBackwards.addLast(toNode);
    visitedBackwards.add(toNode);

    // if one of the queues is empty, the search has reached a dead end
    while (!toProcessForwards.isEmpty() && !toProcessBackwards.isEmpty()) {
      // step in forwards search
      CFANode currentForwards = toProcessForwards.removeLast();
      if (visitedBackwards.contains(currentForwards)) {
        // the backwards search already has seen the current node
        // so we know there's a path from fromNode to current and a path from
        // current to toNode
        return true;
      }

      for (CFANode successor : CFAUtils.successorsOf(currentForwards)) {
        if (visitedForwards.add(successor)) {
          toProcessForwards.addLast(successor);
        }
      }

      // step in backwards search
      CFANode currentBackwards = toProcessBackwards.removeLast();
      if (visitedForwards.contains(currentBackwards)) {
        // the forwards search already has seen the current node
        // so we know there's a path from fromNode to current and a path from
        // current to toNode
        return true;
      }

      for (CFANode predecessor : CFAUtils.predecessorsOf(currentBackwards)) {
        if (visitedBackwards.add(predecessor)) {
          toProcessBackwards.addLast(predecessor);
        }
      }
    }
    return false;
  }

  /**
   * Create a statement edge for an expression (which may be an expression list).
   * @param exp The expression to put at the edge.
   * @param filelocStart The file location.
   * @param prevNode The predecessor of the new edge.
   * @param lastNode The successor of the new edge
   *         (may be null, in this case, a new node is created).
   * @return The successor of the new edge.
   * @category helper
   */
  private CFANode createEdgeForExpression(final IASTExpression expression,
      final int filelocStart, CFANode prevNode, @Nullable CFANode lastNode) {
    assert expression != null;

    if (expression instanceof IASTExpressionList) {
      IASTExpression[] expressions = ((IASTExpressionList) expression).getExpressions();
      CFANode nextNode = null;

      for (int i = 0; i < expressions.length; i++) {
        if (lastNode != null && i == expressions.length-1) {
          nextNode = lastNode;
        } else {
          nextNode = newCFANode(filelocStart);
        }

        createEdgeForExpression(expressions[i], filelocStart, prevNode, nextNode);
        prevNode = nextNode;
      }

      return nextNode;

    } else {
      String rawSignature = expression.getRawSignature();
      final CStatement stmt = astCreator.convertExpressionToStatement(expression);

      prevNode = handleAllSideEffects(prevNode, filelocStart, rawSignature, true);

      stmt.accept(checkBinding);
      if (lastNode == null) {
        lastNode = newCFANode(filelocStart);
      }

      final CStatementEdge lastEdge = new CStatementEdge(rawSignature, stmt, filelocStart, prevNode, lastNode);
      addToCFA(lastEdge);
      return lastNode;
    }
  }


  /////////////////////////////////////////////////////////////////////////////
  // Conditions
  /////////////////////////////////////////////////////////////////////////////

  /**
   * @category conditions
   */
  private void handleIfStatement(IASTIfStatement ifStatement,
      IASTFileLocation fileloc) {

    CFANode prevNode = locStack.pop();

    CFANode postIfNode = newCFANode(fileloc.getEndingLineNumber());
    locStack.push(postIfNode);

    CFANode thenNode = newCFANode(fileloc);
    locStack.push(thenNode);

    CFANode elseNode;
    // elseNode is the start of the else branch,
    // or the node after the loop if there is no else branch
    if (ifStatement.getElseClause() == null) {
      elseNode = postIfNode;
    } else {
      elseNode = newCFANode(fileloc);
      elseStack.push(elseNode);
    }

    createConditionEdges(ifStatement.getConditionExpression(),
        fileloc.getStartingLineNumber(), prevNode, thenNode, elseNode);
  }

  /**
   * This function creates the edges of a condition.
   * It expands the shortcutting operators && and || into several edges,
   * and it skips branches that are not reachable
   * (e.g., for "if (0) { }").
   * @category conditions
   */
  private void createConditionEdges(final IASTExpression condition,
      final int filelocStart, CFANode rootNode, CFANode thenNode,
      final CFANode elseNode) {

    assert condition != null;

    buildConditionTree(condition, filelocStart, rootNode, thenNode, elseNode, thenNode, elseNode, true, true);
  }

  /**
   * @category conditions
   */
  private static enum CONDITION { NORMAL, ALWAYS_FALSE, ALWAYS_TRUE }

  /**
   * @category conditions
   */
  private CONDITION getConditionKind(final IASTExpression cond) {
      if (cond instanceof IASTLiteralExpression) {
          IASTLiteralExpression literalExpression = (IASTLiteralExpression) cond;
          if (literalExpression.getKind() == IASTLiteralExpression.lk_integer_constant) {
              String s = String.valueOf(literalExpression.getValue());
              BigInteger i = astCreator.parseIntegerLiteral(s, cond);
              if (i.equals(BigInteger.ZERO)) {
                return CONDITION.ALWAYS_FALSE;
              } else {
                return CONDITION.ALWAYS_TRUE;
              }
          }
      }
      return CONDITION.NORMAL;
  }

  /**
   * @category conditions
   */
  private void buildConditionTree(IASTExpression condition, final int filelocStart,
                                  CFANode rootNode, CFANode thenNode, final CFANode elseNode,
                                  CFANode thenNodeForLastThen, CFANode elseNodeForLastElse,
                                  boolean furtherThenComputation, boolean furtherElseComputation) {

    // unwrap (a)
    if (condition instanceof IASTUnaryExpression
          && ((IASTUnaryExpression)condition).getOperator() == IASTUnaryExpression.op_bracketedPrimary) {
      buildConditionTree(((IASTUnaryExpression)condition).getOperand(), filelocStart, rootNode, thenNode, elseNode, thenNode, elseNode, true, true);

      // !a --> switch branches
    } else if (condition instanceof IASTUnaryExpression
        && ((IASTUnaryExpression) condition).getOperator() == IASTUnaryExpression.op_not) {
      buildConditionTree(((IASTUnaryExpression) condition).getOperand(), filelocStart, rootNode, elseNode, thenNode, elseNode, thenNode, true, true);

      // a && b
    } else if (condition instanceof IASTBinaryExpression
        && ((IASTBinaryExpression) condition).getOperator() == IASTBinaryExpression.op_logicalAnd) {
      // This case is not necessary,
      // but it prevents the need for a temporary variable in the common case of
      // "if (a && b)"
      CFANode innerNode = newCFANode(filelocStart);
      buildConditionTree(((IASTBinaryExpression) condition).getOperand1(), filelocStart, rootNode, innerNode, elseNode, thenNodeForLastThen, elseNodeForLastElse, true, false);
      buildConditionTree(((IASTBinaryExpression) condition).getOperand2(), filelocStart, innerNode, thenNode, elseNode, thenNodeForLastThen, elseNodeForLastElse, true, true);

      // a || b
    } else if (condition instanceof IASTBinaryExpression
        && ((IASTBinaryExpression) condition).getOperator() == IASTBinaryExpression.op_logicalOr) {
      // This case is not necessary,
      // but it prevents the need for a temporary variable in the common case of
      // "if (a || b)"
      CFANode innerNode = newCFANode(filelocStart);
      buildConditionTree(((IASTBinaryExpression) condition).getOperand1(), filelocStart, rootNode, thenNode, innerNode, thenNodeForLastThen, elseNodeForLastElse, false, true);
      buildConditionTree(((IASTBinaryExpression) condition).getOperand2(), filelocStart, innerNode, thenNode, elseNode, thenNodeForLastThen, elseNodeForLastElse, true, true);

    } else {
      final CONDITION kind = getConditionKind(condition);
      String rawSignature = condition.getRawSignature();

      switch (kind) {
      case ALWAYS_FALSE:
        // no edge connecting rootNode with thenNode,
        // so the "then" branch won't be connected to the rest of the CFA

        final BlankEdge falseEdge = new BlankEdge(rawSignature, filelocStart, rootNode, elseNode, "");
        addToCFA(falseEdge);
        return;

      case ALWAYS_TRUE:
        final BlankEdge trueEdge = new BlankEdge(rawSignature, filelocStart, rootNode, thenNode, "");
        addToCFA(trueEdge);

        // no edge connecting prevNode with elseNode,
        // so the "else" branch won't be connected to the rest of the CFA
        return;

      default:
        throw new AssertionError();

      case NORMAL:
      }

      final CExpression exp = astCreator.convertExpressionWithoutSideEffects(condition);

      rootNode = handleAllSideEffects(rootNode, filelocStart, rawSignature, true);
      exp.accept(checkBinding);

      if (furtherThenComputation) {
        thenNodeForLastThen = thenNode;
      }
      if (furtherElseComputation) {
        elseNodeForLastElse = elseNode;
      }

      if (ASTOperatorConverter.isBooleanExpression(exp)) {
        addConditionEdges(exp, rootNode, thenNodeForLastThen, elseNodeForLastElse,
            condition.getFileLocation().getStartingLineNumber());

      } else {
        // build new boolean expression: a==0 and switch branches
        CSimpleType intType = CNumericTypes.INT;
        CExpression zero =
            new CIntegerLiteralExpression(exp.getFileLocation(), intType, BigInteger.ZERO);
        CExpression conv =
            new CBinaryExpression(exp.getFileLocation(), intType, exp, zero, BinaryOperator.EQUALS);

        addConditionEdges(conv, rootNode, elseNodeForLastElse, thenNodeForLastThen,
            condition.getFileLocation().getStartingLineNumber());
      }
    }
  }

  /** This method adds 2 edges to the cfa:
   * 1. trueEdge from rootNode to thenNode and
   * 2. falseEdge from rootNode to elseNode.
   * @category conditions
   */
  private void addConditionEdges(CExpression condition, CFANode rootNode,
      CFANode thenNode, CFANode elseNode, int filelocStart) {
    // edge connecting condition with thenNode
    final CAssumeEdge trueEdge = new CAssumeEdge(condition.toASTString(),
        filelocStart, rootNode, thenNode, condition, true);
    addToCFA(trueEdge);

    // edge connecting condition with elseNode
    final CAssumeEdge falseEdge = new CAssumeEdge("!(" + condition.toASTString() + ")",
        filelocStart, rootNode, elseNode, condition, false);
    addToCFA(falseEdge);
  }


  /////////////////////////////////////////////////////////////////////////////
  // Loops
  /////////////////////////////////////////////////////////////////////////////

  /**
   * @category loops
   */
  private void handleWhileStatement(IASTWhileStatement whileStatement, IASTFileLocation fileloc) {
    final CFANode prevNode = locStack.pop();

    createLoop(whileStatement.getCondition(), fileloc);

    // connect CFA with loop start node
    final BlankEdge blankEdge = new BlankEdge("", fileloc.getStartingLineNumber(),
        prevNode, loopStartStack.peek(), "while");
    addToCFA(blankEdge);
  }

  /**
   * @category loops
   */
  private void handleDoWhileStatement(IASTDoStatement doStatement, IASTFileLocation fileloc) {
    final CFANode prevNode = locStack.pop();

    createLoop(doStatement.getCondition(), fileloc);

    // connect CFA with first node inside the loop
    // (so the condition will be skipped in the first iteration)
    final BlankEdge blankEdge = new BlankEdge("", fileloc.getStartingLineNumber(),
        prevNode, locStack.peek(), "do");
    addToCFA(blankEdge);
  }

  /**
   * Create a simple while or do-while style loop,
   * and set up all the stacks.
   * The loop will not be connected to the existing CFA,
   * the caller has to ensure this.
   * @category loops
   */
  private void createLoop(IASTExpression condition, IASTFileLocation fileloc) {
    final CFANode loopStart = newCFANode(fileloc);
    loopStart.setLoopStart();
    loopStartStack.push(loopStart);

    final CFANode firstLoopNode = newCFANode(fileloc);

    final CFANode postLoopNode = newCFANode(fileloc.getEndingLineNumber());
    loopNextStack.push(postLoopNode);

    // inverse order here!
    locStack.push(postLoopNode);
    locStack.push(firstLoopNode);

    createConditionEdges(condition, fileloc.getStartingLineNumber(),
        loopStart, firstLoopNode, postLoopNode);
  }

  /**
   * @category loops
   */
  private void handleBreakStatement(IASTBreakStatement breakStatement,
      IASTFileLocation fileloc) {

    CFANode prevNode = locStack.pop();
    CFANode postLoopNode = loopNextStack.peek();

    BlankEdge blankEdge = new BlankEdge(breakStatement.getRawSignature(),
        fileloc.getStartingLineNumber(), prevNode, postLoopNode, "break");
    addToCFA(blankEdge);

    CFANode nextNode = newCFANode(fileloc.getEndingLineNumber());
    locStack.push(nextNode);
  }

  /**
   * @category loops
   */
  private void handleContinueStatement(IASTContinueStatement continueStatement,
      IASTFileLocation fileloc) {

    CFANode prevNode = locStack.pop();
    CFANode loopStartNode = loopStartStack.peek();

    BlankEdge blankEdge = new BlankEdge(continueStatement.getRawSignature(),
        fileloc.getStartingLineNumber(), prevNode, loopStartNode, "continue");
    addToCFA(blankEdge);

    CFANode nextNode = new CFANode(fileloc.getEndingLineNumber(),
        cfa.getFunctionName());
    locStack.push(nextNode);
  }


  /////////////////////////////////////////////////////////////////////////////
  // For loops
  /////////////////////////////////////////////////////////////////////////////

  /**
   * @category forloop
   */
  private int handleForStatement(final IASTForStatement forStatement,
      final IASTFileLocation fileloc) {

    final int filelocStart = fileloc.getStartingLineNumber();
    final CFANode prevNode = locStack.pop();
    scope.enterBlock();

    // loopInit is Node before "counter = 0;"
    final CFANode loopInit = newCFANode(filelocStart);
    addToCFA(new BlankEdge("", filelocStart, prevNode, loopInit, "for"));

    // loopStart is the Node before the loop itself,
    // it is the the one after the init edge(s)
    final CFANode loopStart = createInitEdgeForForLoop(forStatement.getInitializerStatement(),
        filelocStart, loopInit);
    loopStart.setLoopStart();

    // loopEnd is Node before "counter++;"
    final CFANode loopEnd;
    final IASTExpression iterationExpression = forStatement.getIterationExpression();
    if (iterationExpression != null) {
      loopEnd = newCFANode(filelocStart);
    } else {
      loopEnd = loopStart;
    }
    loopStartStack.push(loopEnd);

    // firstLoopNode is Node after "counter < 5"
    final CFANode firstLoopNode = newCFANode(filelocStart);

    // postLoopNode is Node after "!(counter < 5)"
    final CFANode postLoopNode = newCFANode(fileloc.getEndingLineNumber());
    loopNextStack.push(postLoopNode);

    // inverse order here!
    locStack.push(postLoopNode);
    locStack.push(firstLoopNode);

    createConditionEdgesForForLoop(forStatement.getConditionExpression(),
          filelocStart, loopStart, postLoopNode, firstLoopNode);

    // visit only loopbody, not children, loop.getBody() != loop.getChildren()
    forStatement.getBody().accept(this);

    // leave loop
    final CFANode lastNodeInLoop = locStack.pop();

    // loopEnd is the Node before "counter++;"
    assert loopEnd == loopStartStack.peek();
    assert postLoopNode == loopNextStack.peek();
    assert postLoopNode == locStack.peek();
    loopStartStack.pop();
    loopNextStack.pop();

    if (isReachableNode(lastNodeInLoop)) {
      final BlankEdge blankEdge = new BlankEdge("", lastNodeInLoop.getLineNumber(),
          lastNodeInLoop, loopEnd, "");
      addToCFA(blankEdge);
    }

    // this edge connects loopEnd with loopStart and contains the statement "counter++;"
    if (iterationExpression != null) {
      createEdgeForExpression(iterationExpression, filelocStart, loopEnd, loopStart);
    } else {
      assert loopEnd == loopStart;
    }

    scope.leaveBlock();

    // skip visiting children of loop, because loopbody was handled before
    return PROCESS_SKIP;
  }

  /**
   * This function creates the edge for the init-statement of a for-loop.
   * The edge is inserted after the loopInit-Node.
   * If there are more than one declarations, more edges are inserted.
   * @return The node after the last inserted edge.
   * @category forloop
   */
  private CFANode createInitEdgeForForLoop(final IASTStatement statement,
      final int filelocStart, CFANode prevNode) {

    if (statement instanceof IASTDeclarationStatement) {
      // "int counter = 0;"
      final IASTDeclaration decl = ((IASTDeclarationStatement)statement).getDeclaration();
      if (!(decl instanceof IASTSimpleDeclaration)) {
        throw new CFAGenerationRuntimeException("Unexpected declaration in header of for loop", decl);
      }
      return createEdgeForDeclaration((IASTSimpleDeclaration)decl, filelocStart, prevNode);

    } else if (statement instanceof IASTExpressionStatement) {
      // "counter = 0;"
      IASTExpression expression = ((IASTExpressionStatement) statement).getExpression();
      return createEdgeForExpression(expression, filelocStart, prevNode, null);

    } else if (statement instanceof IASTNullStatement) {
      //";", no edge inserted
      return prevNode;

    } else {
      throw new CFAGenerationRuntimeException("Unexpected statement type in header of for loop", statement);
    }
  }

  /**
   * This function creates the condition-edges of a for-loop.
   * Normally there are 2 edges: one 'then'-edge and one 'else'-edge.
   * If the condition is ALWAYS_TRUE or ALWAYS_FALSE or 'null' only one edge is
   * created.
   * @category forloop
   */
  private void createConditionEdgesForForLoop(final IASTExpression condition,
      final int filelocStart, CFANode loopStart,
      final CFANode postLoopNode, final CFANode firstLoopNode) {

    if (condition == null) {
      // no condition -> only a blankEdge from loopStart to firstLoopNode
      final BlankEdge blankEdge = new BlankEdge("", filelocStart, loopStart,
          firstLoopNode, "");
      addToCFA(blankEdge);

    } else if (condition instanceof IASTExpressionList) {
      IASTExpression[] expl = ((IASTExpressionList) condition).getExpressions();
      for (int i = 0; i < expl.length - 1; i++) {
        loopStart = createEdgeForExpression(expl[i], filelocStart, loopStart, null);
      }
      createConditionEdges(expl[expl.length - 1], filelocStart, loopStart, firstLoopNode, postLoopNode);
    } else {
      createConditionEdges(condition, filelocStart, loopStart, firstLoopNode,
          postLoopNode);
    }
  }


  /////////////////////////////////////////////////////////////////////////////
  // Switch statement
  /////////////////////////////////////////////////////////////////////////////

  /**
   * @category switchstatement
   */
  private int handleSwitchStatement(final IASTSwitchStatement statement,
      IASTFileLocation fileloc) {

    CFANode prevNode = locStack.pop();

    CExpression switchExpression = astCreator
        .convertExpressionWithoutSideEffects(statement
            .getControllerExpression());
    prevNode = handleAllSideEffects(prevNode, switchExpression.getFileLocation().getStartingLineNumber(), statement.getRawSignature(), true);

    // firstSwitchNode is first Node of switch-Statement.
    final CFANode firstSwitchNode = newCFANode(fileloc);
    String rawSignature = "switch (" + statement.getControllerExpression().getRawSignature() + ")";
    String description = "switch (" + switchExpression.toASTString() + ")";
    addToCFA(new BlankEdge(rawSignature, fileloc.getStartingLineNumber(),
        prevNode, firstSwitchNode, description));

    switchExprStack.push(switchExpression);
    switchCaseStack.push(firstSwitchNode);

    // postSwitchNode is Node after the switch-statement
    final CFANode postSwitchNode = newCFANode(fileloc.getEndingLineNumber());
    loopNextStack.push(postSwitchNode);
    locStack.push(postSwitchNode);

    locStack.push(new CFANode(fileloc.getStartingLineNumber(),
        cfa.getFunctionName()));

    switchDefaultStack.push(null);

    // visit only body, getBody() != getChildren()
    statement.getBody().accept(this);

    // leave switch
    final CFANode lastNodeInSwitch = locStack.pop();
    final CFANode lastNotCaseNode = switchCaseStack.pop();
    final CFANode defaultCaseNode = switchDefaultStack.pop();

    switchExprStack.pop();

    assert postSwitchNode == loopNextStack.peek();
    assert postSwitchNode == locStack.peek();
    assert switchExprStack.size() == switchCaseStack.size();

    loopNextStack.pop();

    if (defaultCaseNode == null) {
      // no default case
      final BlankEdge blankEdge = new BlankEdge("", lastNotCaseNode.getLineNumber(),
          lastNotCaseNode, postSwitchNode, "");
      addToCFA(blankEdge);

    } else {
      // blank edge connecting rootNode with defaultCaseNode
      final BlankEdge defaultEdge = new BlankEdge(statement.getRawSignature(),
          defaultCaseNode.getLineNumber(), lastNotCaseNode, defaultCaseNode, "default");
      addToCFA(defaultEdge);
    }

    // fall-through of last case
    final BlankEdge blankEdge2 = new BlankEdge("", lastNodeInSwitch.getLineNumber(),
        lastNodeInSwitch, postSwitchNode, "");
    addToCFA(blankEdge2);

    // skip visiting children of loop, because loopbody was handled before
    return PROCESS_SKIP;
  }

  /**
   * @category switchstatement
   */
  private void handleCaseStatement(final IASTCaseStatement statement,
      IASTFileLocation fileloc) {

    final int filelocStart = fileloc.getStartingLineNumber();

    // build condition, left part, "a"
    final CExpression switchExpr =
        switchExprStack.peek();

    // build condition, right part, "2"
    final CExpression caseExpr =
        astCreator.convertExpressionWithoutSideEffects(statement
            .getExpression());

    // build condition, "a==2", TODO correct type?
    final CBinaryExpression binExp =
        new CBinaryExpression(ASTConverter.convert(fileloc),
            switchExpr.getExpressionType(), switchExpr, caseExpr,
            CBinaryExpression.BinaryOperator.EQUALS);

    // build condition edges, to caseNode with "a==2", to notCaseNode with "!(a==2)"
    final CFANode rootNode = switchCaseStack.pop();
    final CFANode caseNode = newCFANode(filelocStart);
    final CFANode notCaseNode = newCFANode(filelocStart);

    // fall-through (case before has no "break")
    final CFANode oldNode = locStack.pop();
    if (oldNode.getNumEnteringEdges() > 0) {
      final BlankEdge blankEdge =
          new BlankEdge("", filelocStart, oldNode, caseNode, "fall through");
      addToCFA(blankEdge);
    }


    switchCaseStack.push(notCaseNode);
    locStack.push(caseNode);

    addConditionEdges(binExp, rootNode, caseNode, notCaseNode, filelocStart);
  }

  /**
   * @category switchstatement
   */
  private void handleDefaultStatement(final IASTDefaultStatement statement,
      IASTFileLocation fileloc) {

    final int filelocStart = fileloc.getStartingLineNumber();

    // hack: use label node to mark node as reachable
    // (otherwise the following edges won't get added because it has
    // no incoming edges
    CLabelNode caseNode = new CLabelNode(fileloc.getStartingLineNumber(),
        cfa.getFunctionName(), "__switch__default__");
    cfaNodes.add(caseNode);

    // Update switchDefaultStack with the new node
    final CFANode oldDefaultNode = switchDefaultStack.pop();
    if (oldDefaultNode != null) {
      throw new CFAGenerationRuntimeException("Duplicate default statement in switch", statement);
    }
    switchDefaultStack.push(caseNode);

    // fall-through (case before has no "break")
    final CFANode oldNode = locStack.pop();
    if (oldNode.getNumEnteringEdges() > 0) {
      final BlankEdge blankEdge =
          new BlankEdge("", filelocStart, oldNode, caseNode, "fall through");
      addToCFA(blankEdge);
    }

    locStack.push(caseNode);
  }



  /////////////////////////////////////////////////////////////////////////////
  // Handling of side effects and ternary operator
  /////////////////////////////////////////////////////////////////////////////

  /**
   * This methods handles all side effects
   * and an eventual ternary or shortcutting operator.
   * @param prevNode The CFANode where to start adding edges.
   * @param filelocStart The file location.
   * @param rawSignature The raw signature.
   * @param resultIsUsed In case a ternary operator exists, is the result used in some computation?
   *         (Otherwise we can omit the temporary variable.)
   * @return The last CFANode that was created.
   * @category sideeffects
   */
  private CFANode handleAllSideEffects(CFANode prevNode, final int filelocStart,
      final String rawSignature, final boolean resultIsUsed) {

    if (astCreator.hasConditionalExpression() && !resultIsUsed) {
      List<Pair<IASTExpression, CIdExpression>> condExps = astCreator.getAndResetConditionalExpressions();
      assert condExps.size() == 1;

      // ignore side assignment
      astCreator.getAndResetPreSideAssignments();

      prevNode = handleConditionalExpression(prevNode, condExps.get(0).getFirst(), null);

    } else {

      prevNode = createEdgesForSideEffects(prevNode, astCreator.getAndResetPreSideAssignments(), rawSignature, filelocStart);

      // handle ternary operator or && or || or { }
      for (Pair<IASTExpression, CIdExpression> cond : astCreator.getAndResetConditionalExpressions()) {
        IASTExpression condExp = cond.getFirst();
        CIdExpression tempVar = cond.getSecond();

        prevNode = handleConditionalExpression(prevNode, condExp, tempVar);
      }
    }

    return prevNode;
  }

  private CFANode handleConditionalExpression(final CFANode prevNode,
      final IASTExpression condExp, final @Nullable CIdExpression tempVar) {
    if (condExp instanceof IASTConditionalExpression) {
      return handleTernaryOperator((IASTConditionalExpression)condExp, prevNode, tempVar);
    } else if (condExp instanceof IASTBinaryExpression) {
      return handleShortcuttingOperators((IASTBinaryExpression)condExp, prevNode, tempVar);
    } else if (condExp instanceof IGNUASTCompoundStatementExpression) {
      return handleCompoundStatementExpression((IGNUASTCompoundStatementExpression)condExp, prevNode, tempVar);
    } else if (condExp instanceof IASTExpressionList) {
      return handleExpressionList((IASTExpressionList)condExp, prevNode, tempVar);
    } else {
      throw new AssertionError();
    }
  }

  /**
   * @category sideeffects
   */
  private CFANode handleExpressionList(IASTExpressionList listExp,
      CFANode prevNode, final CIdExpression tempVar) {

    IASTExpression[] expressions = listExp.getExpressions();
    for (int i = 0; i < expressions.length-1; i++) {
      IASTExpression e = expressions[i];
      prevNode = createEdgeForExpression(e, e.getFileLocation().getStartingLineNumber(), prevNode, null);
    }

    IASTExpression lastExp = expressions[expressions.length-1];

    CAstNode exp = astCreator.convertExpressionWithSideEffects(lastExp);

    prevNode = handleAllSideEffects(prevNode, lastExp.getFileLocation().getStartingLineNumber(), lastExp.getRawSignature(), true);
    CStatement stmt = createStatement(ASTConverter.convert(lastExp.getFileLocation()),
        tempVar, (CRightHandSide)exp);
    CFANode lastNode = newCFANode(lastExp.getFileLocation().getEndingLineNumber());
    CFAEdge edge = new CStatementEdge(stmt.toASTString(), stmt, lastExp.getFileLocation().getStartingLineNumber(), prevNode, lastNode);
    addToCFA(edge);

    return lastNode;
  }

  /**
   * @category sideeffects
   */
  private CFANode handleCompoundStatementExpression(IGNUASTCompoundStatementExpression compoundExp,
      final CFANode rootNode, final CIdExpression tempVar) {

    scope.enterBlock();

    IASTStatement[] statements = compoundExp.getCompoundStatement().getStatements();

    int locDepth = locStack.size();
    int conditionDepth = elseStack.size();
    int loopDepth = loopStartStack.size();

    locStack.push(rootNode);
    for (int i = 0; i < statements.length-1; i++) {
      IASTStatement statement = statements[i];
      statement.accept(this);
    }
    CFANode middleNode = locStack.pop();

    assert locDepth == locStack.size();
    assert conditionDepth == elseStack.size();
    assert loopDepth == loopStartStack.size();

    IASTStatement lastStatement = statements[statements.length-1];
    if (lastStatement instanceof IASTProblemStatement) {
      throw new CFAGenerationRuntimeException((IASTProblemStatement) lastStatement);
    }
    if (!(lastStatement instanceof IASTExpressionStatement)) {
      throw new CFAGenerationRuntimeException("Unsupported statement type " + lastStatement.getClass().getSimpleName() + " at end of compound-statement expression", lastStatement);
    }
    int filelocStart = compoundExp.getFileLocation().getStartingLineNumber();

    CAstNode exp = astCreator.convertExpressionWithSideEffects(((IASTExpressionStatement)lastStatement).getExpression());

    middleNode = handleAllSideEffects(middleNode, filelocStart, lastStatement.getRawSignature(), true);
    CStatement stmt;
    if (exp instanceof CStatement) {
      stmt = (CStatement)exp;
    } else {
      stmt = createStatement(ASTConverter.convert(compoundExp.getFileLocation()),
          tempVar, (CRightHandSide)exp);
    }
    CFANode lastNode = newCFANode(compoundExp.getFileLocation().getEndingLineNumber());
    CFAEdge edge = new CStatementEdge(stmt.toASTString(), stmt, compoundExp.getFileLocation().getStartingLineNumber(), middleNode, lastNode);
    addToCFA(edge);

    scope.leaveBlock();

    return lastNode;
  }

  /**
   * @category sideeffects
   */
  private CFANode handleShortcuttingOperators(IASTBinaryExpression binExp,
      CFANode rootNode, CIdExpression tempVar) {
    int filelocStart = binExp.getFileLocation().getStartingLineNumber();

    CFANode intermediateNode = newCFANode(filelocStart);
    CFANode thenNode = newCFANode(filelocStart);
    CFANode elseNode = newCFANode(filelocStart);

    // create the four condition edges
    switch (binExp.getOperator()) {
    case IASTBinaryExpression.op_logicalAnd:
      createConditionEdges(binExp.getOperand1(), filelocStart, rootNode, intermediateNode, elseNode);
      break;
    case IASTBinaryExpression.op_logicalOr:
      createConditionEdges(binExp.getOperand1(), filelocStart, rootNode, thenNode, intermediateNode);
      break;
    default:
      throw new AssertionError();
    }
    createConditionEdges(binExp.getOperand2(), filelocStart, intermediateNode, thenNode, elseNode);

    // create the two final edges
    CFANode lastNode = newCFANode(filelocStart);
    if (tempVar != null) {
      // assign truth value to tempVar
      FileLocation loc = ASTConverter.getLocation(binExp);
      CSimpleType intType = CNumericTypes.INT;

      CExpression one = new CIntegerLiteralExpression(loc, intType, BigInteger.ONE);
      CStatement assignOne = createStatement(loc, tempVar, one);
      CFAEdge trueEdge = new CStatementEdge(binExp.getRawSignature(), assignOne, filelocStart, thenNode, lastNode);
      addToCFA(trueEdge);

      CExpression zero = new CIntegerLiteralExpression(loc, intType, BigInteger.ZERO);
      CStatement assignZero = createStatement(loc, tempVar, zero);
      CFAEdge falseEdge = new CStatementEdge(binExp.getRawSignature(), assignZero, filelocStart, elseNode, lastNode);
      addToCFA(falseEdge);

    } else {
      CFAEdge trueEdge = new BlankEdge("", filelocStart, thenNode, lastNode, "");
      addToCFA(trueEdge);
      CFAEdge falseEdge = new BlankEdge("", filelocStart, elseNode, lastNode, "");
      addToCFA(falseEdge);
    }

    return lastNode;
  }

  /**
   * @category sideeffects
   */
  private CFANode handleTernaryOperator(IASTConditionalExpression condExp,
      CFANode rootNode, CIdExpression tempVar) {
    int filelocStart = condExp.getFileLocation().getStartingLineNumber();

    CFANode thenNode = newCFANode(filelocStart);
    CFANode elseNode = newCFANode(filelocStart);
    createConditionEdges(condExp.getLogicalConditionExpression(), filelocStart, rootNode, thenNode, elseNode);

    CFANode lastNode = newCFANode(filelocStart);

    // as a gnu c extension allows omitting the second operand and the implicitly adds the first operand
    // as the second also, this is checked here
    if (condExp.getPositiveResultExpression() == null) {
      createEdgesForTernaryOperatorBranch(condExp.getLogicalConditionExpression(), lastNode, filelocStart, thenNode, tempVar);
    } else {
      createEdgesForTernaryOperatorBranch(condExp.getPositiveResultExpression(), lastNode, filelocStart, thenNode, tempVar);
    }

    createEdgesForTernaryOperatorBranch(condExp.getNegativeResultExpression(), lastNode, filelocStart, elseNode, tempVar);

    return lastNode;
  }

  /**
   * @category sideeffects
   */
  private void createEdgesForTernaryOperatorBranch(IASTExpression condExp,
      CFANode lastNode, int filelocStart, CFANode prevNode, @Nullable CIdExpression tempVar) {
    CAstNode exp = astCreator.convertExpressionWithSideEffects(condExp);

    if (!astCreator.hasConditionalExpression()) {

      prevNode = createEdgesForSideEffects(prevNode, astCreator.getAndResetPreSideAssignments(), exp.toASTString(), filelocStart);

      if (exp instanceof CStatement) {
        assert exp instanceof CAssignment;

        CFANode middle = newCFANode(filelocStart);
        CFAEdge edge  = new CStatementEdge(condExp.getRawSignature(), (CStatement) exp, filelocStart, prevNode, middle);
        addToCFA(edge);

        prevNode = middle;
        exp = ((CAssignment) exp).getLeftHandSide();
      }
      assert exp instanceof CRightHandSide;

      CStatement stmt = createStatement(ASTConverter.getLocation(condExp), tempVar, (CRightHandSide)exp);

      CFAEdge edge = new CStatementEdge(condExp.getRawSignature(), stmt, filelocStart, prevNode, lastNode);
      addToCFA(edge);

    } else {
      // nested ternary operator
      assert exp instanceof CRightHandSide;
      boolean resultIsUsed = (tempVar != null)
          || (astCreator.getConditionalExpressions().size() > 1)
          || (exp != astCreator.getConditionalExpressions().get(0).getSecond());

      prevNode = handleAllSideEffects(prevNode, filelocStart, condExp.getRawSignature(), resultIsUsed);

      if (resultIsUsed) {
        CStatement stmt = createStatement(ASTConverter.getLocation(condExp), tempVar, (CRightHandSide)exp);
        addToCFA(new CStatementEdge(stmt.toASTString(), stmt, filelocStart, prevNode, lastNode));
      } else {
        addToCFA(new BlankEdge("", filelocStart, prevNode, lastNode, ""));
      }
    }
  }

  /**
   * This method creates statement and declaration edges for all given sideassignments.
   *
   * @return the nextnode
   * @category sideeffects
   */
  private CFANode createEdgesForSideEffects(CFANode prevNode, List<CAstNode> sideeffects, String rawSignature, int filelocStart) {
    for (CAstNode sideeffect : sideeffects) {
      CFANode nextNode = newCFANode(filelocStart);

      if (sideeffect instanceof CExpression) {
        sideeffect = new CExpressionStatement(sideeffect.getFileLocation(), (CExpression) sideeffect);
      }

      CFAEdge edge;
      if (sideeffect instanceof CStatement) {
        ((CStatement) sideeffect).accept(checkBinding);
        edge = new CStatementEdge(rawSignature, (CStatement)sideeffect, filelocStart, prevNode, nextNode);

      } else if (sideeffect instanceof CDeclaration) {
        if (sideeffect instanceof CVariableDeclaration) {
          CInitializer init = ((CVariableDeclaration) sideeffect).getInitializer();
          if (init != null) {
            init.accept(checkBinding);
          }
        }

        edge = new CDeclarationEdge(rawSignature, filelocStart, prevNode, nextNode, (CDeclaration) sideeffect);
      } else {
        throw new AssertionError();
      }
      addToCFA(edge);
      prevNode = nextNode;
    }

    return prevNode;
  }

  /**
   * @category sideeffects
   */
  private CStatement createStatement(FileLocation fileLoc,
      @Nullable CIdExpression leftHandSide, CRightHandSide rightHandSide) {
    rightHandSide.accept(checkBinding);

    if (leftHandSide != null) {
      leftHandSide.accept(checkBinding);
      // create assignments

      if (rightHandSide instanceof CExpression) {
        return new CExpressionAssignmentStatement(fileLoc, leftHandSide, (CExpression) rightHandSide);
      } else if (rightHandSide instanceof CFunctionCallExpression) {
        return new CFunctionCallAssignmentStatement(fileLoc, leftHandSide, (CFunctionCallExpression) rightHandSide);
      } else {
        throw new AssertionError();
      }

    } else {
      // create ordinary statements
      if (rightHandSide instanceof CExpression) {
        return new CExpressionStatement(fileLoc, (CExpression) rightHandSide);
      } else if (rightHandSide instanceof CFunctionCallExpression) {
        return new CFunctionCallStatement(fileLoc, (CFunctionCallExpression) rightHandSide);
      } else {
        throw new AssertionError();
      }
    }
  }
}
