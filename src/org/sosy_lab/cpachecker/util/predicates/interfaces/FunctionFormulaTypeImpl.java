/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.predicates.interfaces;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * A simple straightforward implementation of FunctionFormulaType<T>
 */
public class FunctionFormulaTypeImpl<T extends Formula> extends FunctionFormulaType<T> {
  private FormulaType<T> returnType;
  private List<FormulaType<?>> argumentTypes;
  public FunctionFormulaTypeImpl(FormulaType<T> returnType, FormulaType<?>... argumentTypes){
    this.returnType = returnType;
    this.argumentTypes =
        Collections.unmodifiableList(java.util.Arrays.asList(argumentTypes));
  }

  public FunctionFormulaTypeImpl(FormulaType<T> returnType, List<FormulaType<?>> argumentTypes){
    this.returnType = returnType;
    this.argumentTypes =
        Collections.unmodifiableList(new LinkedList<>(argumentTypes));
  }

  @Override
  public List<FormulaType<?>> getArgumentTypes(){
    return argumentTypes;
  }

  @Override
  public FormulaType<T> getReturnType(){
    return returnType;
  }

  @Override
  public Class<T> getInterfaceType() {
    return returnType.getInterfaceType();
  }


  @Override
  public String toString() {
    String args = "";
    for (FormulaType<? extends Formula> arg : this.argumentTypes) {
      args += arg.toString() + ",";
    }

    return "(" + returnType.toString() + ") func(" + args.substring(0, args.length() - 1) + ")";
  }
}
