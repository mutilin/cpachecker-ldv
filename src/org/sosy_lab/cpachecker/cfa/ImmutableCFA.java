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

import static com.google.common.base.Preconditions.*;

import java.util.Map;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.util.CFAUtils.Loop;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

/**
 * This class represents a CFA after it has been fully created (parsing, linking
 * of functions, etc.).
 */
class ImmutableCFA implements CFA {

  private final ImmutableMap<String, CFAFunctionDefinitionNode> functions;
  private final CFAFunctionDefinitionNode mainFunction;
  private final Optional<ImmutableMultimap<String, Loop>> loopStructure;

  ImmutableCFA(Map<String, CFAFunctionDefinitionNode> pFunctions,
      Multimap<String, CFANode> pAllNodes,
      CFAFunctionDefinitionNode pMainFunction,
      Optional<ImmutableMultimap<String, Loop>> pLoopStructure) {
    functions = ImmutableMap.copyOf(pFunctions);
    mainFunction = checkNotNull(pMainFunction);
    loopStructure = pLoopStructure;

    checkArgument(functions.get(mainFunction.getFunctionName()) == mainFunction);
  }

  private ImmutableCFA() {
    functions = ImmutableMap.of();
    mainFunction = null;
    loopStructure = Optional.absent();
  }

  static ImmutableCFA empty() {
    return new ImmutableCFA();
  }

  @Override
  public boolean isEmpty() {
    return functions.isEmpty();
  }

  @Override
  public int getNumberOfFunctions() {
    return functions.size();
  }

  @Override
  public ImmutableSet<String> getAllFunctionNames() {
    return functions.keySet();
  }

  @Override
  public ImmutableCollection<CFAFunctionDefinitionNode> getAllFunctionHeads() {
    return functions.values();
  }

  @Override
  public CFAFunctionDefinitionNode getFunctionHead(String name) {
    return functions.get(name);
  }

  @Override
  public ImmutableMap<String, CFAFunctionDefinitionNode> getAllFunctions() {
    return functions;
  }

  @Override
  public CFAFunctionDefinitionNode getMainFunction() {
    return mainFunction;
  }

  @Override
  public Optional<ImmutableMultimap<String, Loop>> getLoopStructure() {
    return loopStructure;
  }
}
