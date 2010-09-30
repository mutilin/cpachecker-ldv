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
package org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA.util.symbpredabstraction.ssa;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA.util.symbpredabstraction.interfaces.SymbolicFormulaList;

import com.google.common.base.Joiner;
import com.google.common.base.Joiner.MapJoiner;
import com.google.common.collect.Lists;

/**
 * @author Alberto Griggio <alberto.griggio@disi.unitn.it>
 *
 * Maps a variable name to its latest "SSA index", that should be used when
 * referring to that variable
 */
public class SSAMap implements ReadableSSAMap {
  
  protected final Map<String, Integer> vars;
  protected final Map<FuncKey, Integer> funcs;

  public SSAMap() {
    vars = new HashMap<String, Integer>();
    funcs = new HashMap<FuncKey, Integer>();
  }
  
  public SSAMap(ReadableSSAMap pSSAMap) {
    this();
    
    if (pSSAMap instanceof SSAMap) {
      SSAMap lSSAMap = (SSAMap)pSSAMap;
      vars.putAll(lSSAMap.vars);
      funcs.putAll(lSSAMap.funcs);
    }
    else {
      for (String lVariable : pSSAMap.allVariables()) {
        setIndex(lVariable, pSSAMap.getIndex(lVariable));
      }
      
      for (Pair<String, SymbolicFormulaList> lFunc : pSSAMap.allFunctions()) {
        String lName = lFunc.getFirst();
        SymbolicFormulaList lFormula = lFunc.getSecond();
        setIndex(lName, lFormula, pSSAMap.getIndex(lName, lFormula));
      }
    }
  }
  
  protected SSAMap(Map<String, Integer> vars, Map<FuncKey, Integer> funcs) {
    this.vars = vars;
    this.funcs = funcs;
  }

  @Override
  public int getIndex(String variable) {
    Integer i = vars.get(variable);
    if (i != null) {
      return i;
    } else {
      // no index found, return -1
      return -1;
    }
  }

  public void setIndex(String variable, int idx) {
    vars.put(variable, idx);
  }

  @Override
  public int getIndex(String name, SymbolicFormulaList args) {
    Integer i = funcs.get(new FuncKey(name, args));
    if (i != null) {
      return i;
    } else {
      // no index found, return -1
      return -1;
    }
  }

  public void setIndex(String name, SymbolicFormulaList args, int idx) {
    funcs.put(new FuncKey(name, args), idx);
  }

  @Override
  public Collection<String> allVariables() {
    return Collections.unmodifiableSet(vars.keySet());
  }

  @Override
  public Collection<Pair<String, SymbolicFormulaList>> allFunctions() {
    List<Pair<String, SymbolicFormulaList>> ret = Lists.newArrayList();

    for (FuncKey k : funcs.keySet()) {
      ret.add(new Pair<String, SymbolicFormulaList>(k.getName(), k.getArgs()));
    }
    return ret;
  }

  private static final MapJoiner joiner = Joiner.on(" ").withKeyValueSeparator("@");
  
  @Override
  public String toString() {
    return joiner.join(vars) + " " + joiner.join(funcs);
  }

  /**
   * updates this map with the contents of other. That is, adds to this map
   * all the variables present in other but not in this
   */
  public void update(SSAMap other) {
    for (Entry<String, Integer> k : other.vars.entrySet()) {
      if (!vars.containsKey(k.getKey())) {
        vars.put(k.getKey(), k.getValue());
      }
    }
    for (Entry<FuncKey, Integer> k : other.funcs.entrySet()) {
      if (!funcs.containsKey(k.getKey())) {
        funcs.put(k.getKey(), k.getValue());
      }
    }
  }
  
  @Override
  public ImmutableSSAMap immutable() {
    return new UnmodifiableSSAMap(this);
  }
  
}
