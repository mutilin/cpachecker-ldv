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
package org.sosy_lab.cpachecker.util.predicates.interfaces.view.wrapping;

import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.NumeralFormulaManagerView;


public abstract class NumeralFormulaView<T extends NumeralFormula> extends FormulaView<T> implements NumeralFormula {

  private NumeralFormulaView(T pWrapped, NumeralFormulaManagerView pView) {
    super(pWrapped, pView.getViewManager());
  }

  public static class IntegerFormulaView extends NumeralFormulaView<IntegerFormula> implements IntegerFormula {
    public IntegerFormulaView(IntegerFormula pWrapped, NumeralFormulaManagerView pView) {
      super(pWrapped, pView);
    }
  }

  public static class RationalFormulaView extends NumeralFormulaView<RationalFormula> implements RationalFormula {
    public RationalFormulaView(RationalFormula pWrapped, NumeralFormulaManagerView pView) {
      super(pWrapped, pView);
    }
  }

}
