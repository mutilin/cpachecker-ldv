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
package org.sosy_lab.cpachecker.cfa.parser.eclipse;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionTypeSpecifier;
import org.sosy_lab.cpachecker.cfa.ast.IASTSimpleDeclaration;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * Provides a symbol table that maps variable and functions to their declaration
 * (if a name is visible in the current scope).
 */
class Scope {

  private final LinkedList<Map<String, IASTSimpleDeclaration>> varsStack = Lists.newLinkedList();

  private final Map<String, IASTSimpleDeclaration> functions = new HashMap<String, IASTSimpleDeclaration>();

  public Scope() {
    enterFunction(); // enter global scope
  }

  public boolean isGlobalScope() {
    return varsStack.size() == 1;
  }

  public void enterFunction() {
    varsStack.addLast(new HashMap<String, IASTSimpleDeclaration>());
  }

  public void leaveFunction() {
    checkState(!isGlobalScope());
    varsStack.removeLast();
  }

  public IASTSimpleDeclaration lookupVariable(String name) {
    checkNotNull(name);

    Iterator<Map<String, IASTSimpleDeclaration>> it = varsStack.descendingIterator();
    while (it.hasNext()) {
      Map<String, IASTSimpleDeclaration> vars = it.next();

      IASTSimpleDeclaration binding = vars.get(name);
      if (binding != null) {
        return binding;
      }
    }
    return null;
  }

  public IASTSimpleDeclaration lookupFunction(String name) {
    return functions.get(checkNotNull(name));
  }

  public void registerDeclaration(IASTSimpleDeclaration declaration) {
    String name = declaration.getName();

    if (declaration.getDeclSpecifier() instanceof IASTFunctionTypeSpecifier) {
      // function

      checkState(isGlobalScope(), "nested functions not allowed");

      if (functions.containsKey(name)) {
        // TODO multiple function declarations are legal, as long as they are equal
        // check this and throw exception if not
//        throw new CFAGenerationRuntimeException("Function " + name + " already declared", declaration);
      }

      functions.put(name, declaration);

    } else {
      Map<String, IASTSimpleDeclaration> vars = varsStack.getLast();

      // multiple declarations of the same variable are disallowed, unless when being in global scope
      if (vars.containsKey(name) && !isGlobalScope()) {
        throw new CFAGenerationRuntimeException("Variable " + name + " already declared", declaration);
      }

      vars.put(name, declaration);
    }
  }

  @Override
  public String toString() {
    return "Functions: " + Joiner.on(' ').join(functions.keySet());
  }
}