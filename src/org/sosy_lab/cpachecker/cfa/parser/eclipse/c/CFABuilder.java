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
package org.sosy_lab.cpachecker.cfa.parser.eclipse.c;

import static com.google.common.collect.FluentIterable.from;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;

import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTASMDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTProblem;
import org.eclipse.cdt.core.dom.ast.IASTProblemDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Triple;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.cpachecker.cfa.CSourceOriginMapping;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDefDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CComplexType;
import org.sosy_lab.cpachecker.cfa.types.c.CElaboratedType;
import org.sosy_lab.cpachecker.exceptions.CParserException;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;

/**
 * Builder to traverse AST.
 *
 * After instantiating this class,
 * call {@link #analyzeTranslationUnit(IASTTranslationUnit, String)}
 * once for each translation unit that should be used
 * and finally call {@link #createCFA()}.
 */
class CFABuilder extends ASTVisitor {

  // Data structures for handling function declarations
  private final List<Triple<List<IASTFunctionDefinition>, String, GlobalScope>> functionDeclarations = new ArrayList<>();
  private final SortedMap<String, FunctionEntryNode> cfas = new TreeMap<>();
  private final SortedSetMultimap<String, CFANode> cfaNodes = TreeMultimap.create();
  private final List<String> eliminateableDuplicates = new ArrayList<>();

  // Data structure for storing global declarations
  private final List<Pair<org.sosy_lab.cpachecker.cfa.ast.ADeclaration, String>> globalDeclarations = Lists.newArrayList();

  // Data structure for checking amount of initializations per global variable
  private final Set<String> globalInitializedVariables = Sets.newHashSet();


  private GlobalScope fileScope = new GlobalScope();
  private GlobalScope globalScope = new GlobalScope();
  private ASTConverter astCreator;
  private final Function<String, String> niceFileNameFunction;
  private final CSourceOriginMapping sourceOriginMapping;

  private final MachineModel machine;
  private final LogManagerWithoutDuplicates logger;
  private final CheckBindingVisitor checkBinding;

  private final Configuration config;

  private boolean encounteredAsm = false;
  private Sideassignments sideAssignmentStack = null;

  public CFABuilder(Configuration pConfig, LogManager pLogger,
      Function<String, String> pNiceFileNameFunction,
      CSourceOriginMapping pSourceOriginMapping,
      MachineModel pMachine) throws InvalidConfigurationException {

    logger = new LogManagerWithoutDuplicates(pLogger);
    niceFileNameFunction = pNiceFileNameFunction;
    sourceOriginMapping = pSourceOriginMapping;
    machine = pMachine;
    config = pConfig;

    checkBinding = new CheckBindingVisitor(pLogger);

    shouldVisitDeclarations = true;
    shouldVisitEnumerators = true;
    shouldVisitProblems = true;
    shouldVisitTranslationUnit = true;
  }

  public void analyzeTranslationUnit(IASTTranslationUnit ast, String staticVariablePrefix) throws InvalidConfigurationException {
    sideAssignmentStack = new Sideassignments();
    fileScope = new GlobalScope(new HashMap<String, CSimpleDeclaration>(),
                                new HashMap<String, CFunctionDeclaration>(),
                                new HashMap<String, CComplexTypeDeclaration>(),
                                new HashMap<String, CTypeDefDeclaration>(),
                                globalScope.getTypes(),
                                staticVariablePrefix);
    astCreator = new ASTConverter(config, fileScope, logger, niceFileNameFunction, sourceOriginMapping, machine, staticVariablePrefix, sideAssignmentStack);
    functionDeclarations.add(Triple.of((List<IASTFunctionDefinition>)new ArrayList<IASTFunctionDefinition>(), staticVariablePrefix, fileScope));

    ast.accept(this);
  }

  /* (non-Javadoc)
   * @see org.eclipse.cdt.core.dom.ast.ASTVisitor#visit(org.eclipse.cdt.core.dom.ast.IASTDeclaration)
   */
  @Override
  public int visit(IASTDeclaration declaration) {
    sideAssignmentStack.enterBlock();
    IASTFileLocation fileloc = declaration.getFileLocation();

    if (declaration instanceof IASTSimpleDeclaration) {
      return handleSimpleDeclaration((IASTSimpleDeclaration)declaration, fileloc);

    } else if (declaration instanceof IASTFunctionDefinition) {
      IASTFunctionDefinition fd = (IASTFunctionDefinition) declaration;
      functionDeclarations.get(functionDeclarations.size() -1).getFirst().add(fd);

      // add forward declaration to list of global declarations
      CFunctionDeclaration functionDefinition = astCreator.convert(fd);
      if (sideAssignmentStack.hasPreSideAssignments()
          || sideAssignmentStack.hasPostSideAssignments()) {
        throw new CFAGenerationRuntimeException("Function definition has side effect", fd, niceFileNameFunction);
      }

      fileScope.registerFunctionDeclaration(functionDefinition);
      if (!eliminateableDuplicates.contains(functionDefinition.toASTString())) {
        globalDeclarations.add(Pair.of((ADeclaration)functionDefinition, fd.getDeclSpecifier().getRawSignature() + " " + fd.getDeclarator().getRawSignature()));
        eliminateableDuplicates.add(functionDefinition.toASTString());
      }

      sideAssignmentStack.leaveBlock();
      return PROCESS_SKIP;

    } else if (declaration instanceof IASTProblemDeclaration) {
      // CDT parser struggles on GCC's __attribute__((something)) constructs
      // because we use C99 as default.
      // Either insert the following macro before compiling with CIL:
      // #define  __attribute__(x)  /*NOTHING*/
      // or insert "parser.dialect = GNUC" into properties file
      visit(((IASTProblemDeclaration)declaration).getProblem());
      sideAssignmentStack.leaveBlock();
      return PROCESS_SKIP;

    } else if (declaration instanceof IASTASMDeclaration) {
      // TODO Assembler code is ignored here
      encounteredAsm = true;
      logger.log(Level.FINER, "Ignoring inline assembler code at line", fileloc.getStartingLineNumber());
      sideAssignmentStack.leaveBlock();
      return PROCESS_SKIP;

    } else {
      throw new CFAGenerationRuntimeException("Unknown declaration type "
          + declaration.getClass().getSimpleName(), declaration, niceFileNameFunction);
    }
  }

  private int handleSimpleDeclaration(final IASTSimpleDeclaration sd,
      final IASTFileLocation fileloc) {

    //these are unneccesary semicolons which would cause an abort of CPAchecker
    if (sd.getDeclarators().length == 0  && sd.getDeclSpecifier() instanceof IASTSimpleDeclSpecifier) {
      sideAssignmentStack.leaveBlock();
      return PROCESS_SKIP;
    }

    final List<CDeclaration> newDs = astCreator.convert(sd);
    assert !newDs.isEmpty();

    if (sideAssignmentStack.hasConditionalExpression()
        || sideAssignmentStack.hasPostSideAssignments()) {
      throw new CFAGenerationRuntimeException("Initializer of global variable has side effect", sd, niceFileNameFunction);
    }

    String rawSignature = sd.getRawSignature();

    for (CAstNode astNode : sideAssignmentStack.getAndResetPreSideAssignments()) {
      if (astNode instanceof CComplexTypeDeclaration) {
        // already registered
        globalDeclarations.add(Pair.of((ADeclaration)astNode, rawSignature));
      } else if (astNode instanceof CVariableDeclaration) {
        // If the initializer of a global struct contains a type-id expression,
        // a temporary variable is created and we need to support this.
        // We detect this case if the initializer of the temp variable is an initializer list.
        CInitializer initializer = ((CVariableDeclaration)astNode).getInitializer();
        if (initializer instanceof CInitializerList) {
          globalDeclarations.add(Pair.of((ADeclaration)astNode, rawSignature));
        } else {
          throw new CFAGenerationRuntimeException("Initializer of global variable has side effect", sd, niceFileNameFunction);
        }

      } else {
        throw new CFAGenerationRuntimeException("Initializer of global variable has side effect", sd, niceFileNameFunction);
      }
    }

    for (CDeclaration newD : newDs) {
      boolean used = true;

      if (newD instanceof CVariableDeclaration) {

        CInitializer init = ((CVariableDeclaration) newD).getInitializer();
        if (init != null) {
          init.accept(checkBinding);

          // save global initialized variable in map to check duplicates
          if (!globalInitializedVariables.add(newD.getName())) {
            throw new CFAGenerationRuntimeException("Variable " + newD.getName()
                + " initialized for the second time", newD);
          }
        }

        fileScope.registerDeclaration(newD);
      } else if (newD instanceof CFunctionDeclaration) {
        fileScope.registerFunctionDeclaration((CFunctionDeclaration) newD);
      } else if (newD instanceof CComplexTypeDeclaration) {
          used = fileScope.registerTypeDeclaration((CComplexTypeDeclaration)newD);
          if (used) {
            String qualifiedName = ((CComplexType)newD.getType()).getQualifiedName();
            CComplexType t = fileScope.lookupType(qualifiedName);
            if (t!= null && !t.getQualifiedName().equals(qualifiedName)) {
              newD = fileScope.getTypes().get(t.getQualifiedName());
            }
          }
      } else if (newD instanceof CTypeDefDeclaration) {
        used = fileScope.registerTypeDeclaration((CTypeDefDeclaration)newD);
      }

      if (used && !eliminateableDuplicates.contains(newD.toASTString())) {
        globalDeclarations.add(Pair.of((ADeclaration)newD, rawSignature));
        eliminateableDuplicates.add(newD.toASTString());
      }
    }

    sideAssignmentStack.leaveBlock();
    return PROCESS_SKIP; // important to skip here, otherwise we would visit nested declarations
  }

  //Method to handle visiting a parsing problem.  Hopefully none exist
  /* (non-Javadoc)
   * @see org.eclipse.cdt.core.dom.ast.ASTVisitor#visit(org.eclipse.cdt.core.dom.ast.IASTProblem)
   */
  @Override
  public int visit(IASTProblem problem) {
    throw new CFAGenerationRuntimeException(problem, niceFileNameFunction);
  }

  public ParseResult createCFA() throws CParserException {
    FillInAllBindingsVisitor fillInAllBindingsVisitor = new FillInAllBindingsVisitor(globalScope);
    for (ADeclaration decl : from(globalDeclarations).transform(Pair.<ADeclaration>getProjectionToFirst())) {
      ((CDeclaration)decl).getType().accept(fillInAllBindingsVisitor);
    }

    for (Triple<List<IASTFunctionDefinition>, String, GlobalScope> triple : functionDeclarations) {
      for (IASTFunctionDefinition declaration : triple.getFirst()) {
        handleFunctionDefinition(triple.getThird(), triple.getSecond(), declaration);
      }
    }

    ParseResult result = new ParseResult(cfas, cfaNodes, globalDeclarations, Language.C);

    if (encounteredAsm) {
      logger.log(Level.WARNING, "Inline assembler ignored, analysis is probably unsound!");
    }

    if (checkBinding.foundUndefinedIdentifiers()) {
      throw new CParserException("Invalid C code because of undefined identifiers mentioned above.");
    }

    return result;
  }

  private void handleFunctionDefinition(GlobalScope actScope, String fileName,
      IASTFunctionDefinition declaration) {

    FunctionScope localScope = new FunctionScope(actScope.getFunctions(),
                                                 actScope.getTypes(),
                                                 actScope.getTypeDefs(),
                                                 actScope.getGlobalVars(),
                                                 fileName);
    CFAFunctionBuilder functionBuilder;

    try {
      functionBuilder = new CFAFunctionBuilder(config, logger, localScope, niceFileNameFunction,
          sourceOriginMapping,
          machine, fileName, sideAssignmentStack, checkBinding);
    } catch (InvalidConfigurationException e) {
      throw new CFAGenerationRuntimeException("Invalid configuration");
    }

    declaration.accept(functionBuilder);

    FunctionEntryNode startNode = functionBuilder.getStartNode();
    String functionName = startNode.getFunctionName();

    if (cfas.containsKey(functionName)) {
      throw new CFAGenerationRuntimeException("Duplicate function " + functionName);
    }
    cfas.put(functionName, startNode);
    cfaNodes.putAll(functionName, functionBuilder.getCfaNodes());
    globalDeclarations.addAll(functionBuilder.getGlobalDeclarations());

    encounteredAsm |= functionBuilder.didEncounterAsm();
    functionBuilder.finish();
  }

  @Override
  public int leave(IASTTranslationUnit ast) {
    Map<String, CSimpleDeclaration> globalVars = new HashMap<>();
    Map<String, CFunctionDeclaration> functions = new HashMap<>();
    Map<String, CComplexTypeDeclaration> types = new HashMap<>();
    Map<String, CTypeDefDeclaration> typedefs = new HashMap<>();

    globalVars.putAll(globalScope.getGlobalVars());
    functions.putAll(globalScope.getFunctions());
    types.putAll(globalScope.getTypes());
    typedefs.putAll(globalScope.getTypeDefs());

    functions.putAll(fileScope.getFunctions());
    typedefs.putAll(fileScope.getTypeDefs());
    for (Entry<String, CComplexTypeDeclaration> e : fileScope.getTypes().entrySet()) {
      // only add those types to the global list which are complete, or where no
      // complete type is available at all (from the previous found type)
      if (types.containsKey(e.getKey())
          && (types.get(e.getKey()).getType().getCanonicalType() instanceof CElaboratedType
              || (types.get(e.getKey()).getType().getCanonicalType().equals(e.getValue().getType().getCanonicalType())))) {
        types.put(e.getKey(), e.getValue());

      // types which were not there before can be added without checking any
      // other constraints
      } else if (!types.containsKey(e.getKey())) {
        types.put(e.getKey(), e.getValue());
      }
    }

    globalScope= new GlobalScope(globalVars, functions, types, typedefs, new HashMap<String, CComplexTypeDeclaration>(), "");
    return PROCESS_CONTINUE;
  }
}