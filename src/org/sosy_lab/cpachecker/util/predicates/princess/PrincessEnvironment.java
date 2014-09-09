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
package org.sosy_lab.cpachecker.util.predicates.princess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.io.PathCounterTemplate;
import org.sosy_lab.common.log.LogManager;

import scala.collection.mutable.ArrayBuffer;
import ap.SimpleAPI;
import ap.basetypes.IdealInt;
import ap.parser.IExpression;
import ap.parser.IFormula;
import ap.parser.IFunApp;
import ap.parser.IFunction;
import ap.parser.IIntLit;
import ap.parser.ITerm;
import ap.parser.ITermITE;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/** This is a Wrapper around Princess.
 * This Wrapper allows to set a logfile for all Smt-Queries (default "princess.###.smt2").
 * // TODO logfile is only available as tmpfile in /tmp, perhaps it is not closed?
 * It also manages the "shared variables": each variable is declared for all stacks.
 */
class PrincessEnvironment {

  /**
   * Enum listing possible types for Princess.
   */
  static enum Type {
    BOOL("Bool"),
    INT("Int");
    // TODO does Princess support more types?
    // TODO merge enum with ModelTypes?

    private final String name;

    private Type(String s) {
      name = s;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public class FunctionType {

    private final IFunction funcDecl;
    private final Type resultType;
    private final Type[] args;

    FunctionType(IFunction funcDecl, Type resultType, Type[] args) {
      this.funcDecl = funcDecl;
      this.resultType = resultType;
      this.args = args;
    }

    public IFunction getFuncDecl() { return funcDecl; }
    public Type getResultType() { return resultType; }
    public Type[] getArgs() { return args; }

  }

  /** cache for variables, because they do not implement equals() and hashCode(),
   * so we need to have the same objects. */
  private final Map<String, IFormula> boolVariablesCache = new HashMap<>();
  private final Map<String, ITerm> intVariablesCache = new HashMap<>();
  private final Map<String, FunctionType> functionsCache = new HashMap<>();

  private final Map<IFunction, FunctionType> declaredFunctions = new HashMap<>();

  // order is important for abbreviations, because a abbreviation might depend on another one.
  private final List<Pair<IFormula, IFormula>> abbrevFormulas = new ArrayList<>();
  // for faster lookup, key: abbreviation, entry: long formula.
  private final BiMap<IFormula, IFormula> abbrevFormulasMap = HashBiMap.create();

  private final @Nullable PathCounterTemplate basicLogfile;

  /** formulas can be simplified through replacing them with an abbrev-formula. */
  // TODO do we have to check, that no other symbol equals an abbreviation-symbol?
  private static final String ABBREV = "ABBREV_";
  private static int abbrevIndex = 0;

  /** the wrapped api is the first created api.
   * It will never be used outside of this class and never be closed.
   * If a variable is declared, it is declared in the first api, then copied into all registered apis.
   * Each api has its own stack for formulas. */
  private final SimpleAPI api;
  private final List<SymbolTrackingPrincessStack> registeredStacks = new ArrayList<>();

  /** The Constructor creates the wrapped Element, sets some options
   * and initializes the logger. */
  public PrincessEnvironment(Configuration config, final LogManager pLogger,
      final PathCounterTemplate pBasicLogfile) throws InvalidConfigurationException {
    basicLogfile = pBasicLogfile;
    api = getNewApi(false); // this api is only used local in this environment, no need for interpolation
  }


  /** This method returns a new stack, that is registered in this environment.
   * All variables are shared in all registered stacks. */
  PrincessStack getNewStack(boolean useForInterpolation) {
    SimpleAPI newApi = getNewApi(useForInterpolation);
    SymbolTrackingPrincessStack stack = new SymbolTrackingPrincessStack(this, newApi);

    // add all symbols, that are available until now
    for (IFormula s : boolVariablesCache.values()) {
      stack.addSymbol(s);
    }
    for (ITerm s : intVariablesCache.values()) {
      stack.addSymbol(s);
    }
    for (FunctionType s : functionsCache.values()) {
      stack.addSymbol(s.funcDecl);
    }
    for (Pair<IFormula, IFormula> abbrev : abbrevFormulas) {
      stack.addAbbrev(abbrev.getFirst(), abbrev.getSecond());
    }
    registeredStacks.add(stack);
    return stack;
  }

  private SimpleAPI getNewApi(boolean useForInterpolation) {
    final SimpleAPI newApi;
    if (basicLogfile != null) {
      newApi = SimpleAPI.spawnWithLogNoSanitise(basicLogfile.getFreshPath().getAbsolutePath());
    } else {
      newApi = SimpleAPI.spawnNoSanitise();
    }
    // we do not use 'sanitise', because variable-names contain special chars like "@" and ":"

    if (useForInterpolation) {
      newApi.setConstructProofs(true); // needed for interpolation
    }
    return newApi;
  }

  void unregisterStack(SymbolTrackingPrincessStack stack) {
    assert registeredStacks.contains(stack) : "cannot remove stack, it is not registered";
    registeredStacks.remove(stack);
  }

  public List<IExpression> parseStringToTerms(String s) {
    throw new UnsupportedOperationException(); // todo: implement this
  }

  public IExpression makeVariable(Type type, String varname) {
    switch (type) {

      case BOOL: {
        if (boolVariablesCache.containsKey(varname)) {
          return boolVariablesCache.get(varname);
        } else {
          IFormula var = api.createBooleanVariable(varname);
          for (SymbolTrackingPrincessStack stack : registeredStacks) {
            stack.addSymbol(var);
          }
          boolVariablesCache.put(varname, var);
          return var;
        }
      }

      case INT: {
        if (intVariablesCache.containsKey(varname)) {
          return intVariablesCache.get(varname);
        } else {
          ITerm var = api.createConstant(varname);
          for (SymbolTrackingPrincessStack stack : registeredStacks) {
            stack.addSymbol(var);
          }
          intVariablesCache.put(varname, var);
          return var;
        }
      }

      default:
        throw new AssertionError("unsupported type: " + type);
    }
  }

  /** This function declares a new functionSymbol, that has a given number of params.
   * Princess has no support for typed params, only their number is important. */
  public FunctionType declareFun(String name, Type resultType, Type[] args) {
    if (functionsCache.containsKey(name)) {
      FunctionType function = functionsCache.get(name);
      assert function.getResultType() == resultType;
      assert Arrays.equals(function.getArgs(), args);
      return function;
    } else {
      final FunctionType type = declareFun0(name, resultType, args);
      functionsCache.put(name, type);
      return type;
    }
  }

  /** This function declares a new functionSymbol, that has a given number of params.
   * Princess has no support for typed params, only their number is important. */
  private FunctionType declareFun0(String name, Type resultType, Type[] args) {
    IFunction funcDecl = api.createFunction(name, args.length);
    for (SymbolTrackingPrincessStack stack : registeredStacks) {
       stack.addSymbol(funcDecl);
    }
    FunctionType type = new FunctionType(funcDecl, resultType, args);
    declaredFunctions.put(funcDecl, type);
    return type;
  }

  public FunctionType getFunctionDeclaration(IFunction f) {
    return declaredFunctions.get(f);
  }

  public IExpression makeFunction(IFunction funcDecl, Type resultType, List<IExpression> args) {
    final ArrayBuffer<ITerm> argsBuf = new ArrayBuffer<>();
    for (IExpression arg : args) {
      final ITerm termArg;
      if (arg instanceof IFormula) { // boolean term -> build ITE(t,0,1), TODO why not ITE(t,1,0) ??
        termArg = new ITermITE((IFormula)arg, new IIntLit(IdealInt.apply(0)), new IIntLit(IdealInt.apply(1)));
      } else {
        termArg = (ITerm) arg;
      }
      argsBuf.$plus$eq(termArg);
    }

    final ITerm t = new IFunApp(funcDecl, argsBuf.toSeq());

    switch (resultType) {
      case BOOL:
        return t;
      case INT:
        return t;
      default:
        throw new AssertionError("unknown resulttype");
    }
  }

  /** create a short replacement/abbreviation function for a long formula. */
  public IFormula abbrev(IFormula longFormula) {
    if (abbrevFormulasMap.inverse().containsKey(longFormula)) {
      return abbrevFormulasMap.inverse().get(longFormula);
    } else {
      final String abbrevName = ABBREV + abbrevIndex++;
      final IFormula abbrev = api.abbrev(longFormula, abbrevName);
      abbrevFormulas.add(Pair.of(abbrev, longFormula));
      abbrevFormulasMap.put(abbrev, longFormula);
      for (SymbolTrackingPrincessStack stack : registeredStacks) {
        stack.addAbbrev(abbrev, longFormula);
      }
      return abbrev;
    }
  }

  public String getVersion() {
    return "Princess (unknown version)";
  }
}
