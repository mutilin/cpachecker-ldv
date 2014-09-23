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
package org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl;

import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;


/**
 * This is a helper interface to allow the implementation of basicimpl to encapsulate their own additional info in the typed instances
 * @param <TFormulaInfo>  the solver specific type.
 */
public interface FormulaCreator<TFormulaInfo> {
  public <T extends Formula> T encapsulate(Class<T> clazz, TFormulaInfo pTerm);

  public <T extends Formula> T encapsulate(FormulaType<T> type, TFormulaInfo pTerm);

  public TFormulaInfo extractInfo(Formula t);

  /**
   * Returns the type of the given Formula.
   */
  public <T extends Formula> FormulaType<T> getFormulaType(T formula);
}
