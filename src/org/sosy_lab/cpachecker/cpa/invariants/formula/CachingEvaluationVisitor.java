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
package org.sosy_lab.cpachecker.cpa.invariants.formula;

import java.util.Map;
import java.util.WeakHashMap;


public class CachingEvaluationVisitor<T> extends DefaultFormulaVisitor<T, T> {

  private static final boolean useCaching = false;

  private final Map<? extends String, ? extends InvariantsFormula<T>> environment;

  private final WeakHashMap<InvariantsFormula<T>, T> cache = useCaching ? new WeakHashMap<InvariantsFormula<T>, T>() : null;

  private final FormulaEvaluationVisitor<T> actualEvaluationVisitor;

  public CachingEvaluationVisitor(Map<? extends String, ? extends InvariantsFormula<T>> pEnvironment, FormulaEvaluationVisitor<T> pEvaluationVisitor) {
    this.environment = pEnvironment;
    this.actualEvaluationVisitor = pEvaluationVisitor;
  }

  @Override
  protected T visitDefault(InvariantsFormula<T> pFormula) {
    if (useCaching) {
      T evaluated = cache.get(pFormula);
      if (evaluated != null) {
        return evaluated;
      }
      evaluated = pFormula.accept(actualEvaluationVisitor, environment);
      cache.put(pFormula, evaluated);
      return evaluated;
    }
    return pFormula.accept(actualEvaluationVisitor, environment);
  }

  @Override
  public T visit(Constant<T> pConstant) {
    return pConstant.getValue();
  }

  public void clearCache() {
    if (useCaching) {
      this.cache.clear();
    }
  }

}
