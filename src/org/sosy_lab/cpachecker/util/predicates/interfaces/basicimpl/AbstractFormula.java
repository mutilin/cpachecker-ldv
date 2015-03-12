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


import java.io.Serializable;

import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.predicates.interfaces.ArrayFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FloatingPointFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.RationalFormula;

/**
 * A Formula represented as a TFormulaInfo object.
 * @param <TFormulaInfo> the solver specific type.
 */
abstract class AbstractFormula<TFormulaInfo> implements Formula, Serializable {

  private static final long serialVersionUID = 7662624283533815801L;

  private final TFormulaInfo formulaInfo;

  protected AbstractFormula(TFormulaInfo formulaInfo) {
    assert formulaInfo != null;

    this.formulaInfo = formulaInfo;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AbstractFormula)) { return false; }
    return formulaInfo.equals(((AbstractFormula<?>) o).formulaInfo);
  }

  TFormulaInfo getFormulaInfo() {
    return formulaInfo;
  }

  @Override
  public int hashCode() {
    return formulaInfo.hashCode();
  }

  @Override
  public String toString() {
    return formulaInfo.toString();
  }
}

/**
 * Simple ArrayFormula implementation.
 */
@SuppressWarnings("serial")
class ArrayFormulaImpl<TI extends Formula, TE extends Formula, TFormulaInfo>
    extends AbstractFormula<TFormulaInfo>
    implements ArrayFormula<TI, TE> {

  private final FormulaType<TI> indexType;
  private final FormulaType<TE> elementType;

  public ArrayFormulaImpl(TFormulaInfo info, FormulaType<TI> pIndexType, FormulaType<TE> pElementType) {
    super(info);
    this.indexType = pIndexType;
    this.elementType = pElementType;
  }

  public FormulaType<TI> getIndexType() {
    return indexType;
  }

  public FormulaType<TE> getElementType() {
    return elementType;
  }
}

/**
 * Simple BooleanFormula implementation. Just tracing the size and the sign-treatment
 */
@SuppressWarnings("serial")
class BitvectorFormulaImpl<TFormulaInfo> extends AbstractFormula<TFormulaInfo> implements BitvectorFormula {
  public BitvectorFormulaImpl(TFormulaInfo info) {
    super(info);
  }
}

/**
 * Simple FloatingPointFormula implementation.
 */
@SuppressWarnings("serial")
class FloatingPointFormulaImpl<TFormulaInfo> extends AbstractFormula<TFormulaInfo> implements FloatingPointFormula {
  public FloatingPointFormulaImpl(TFormulaInfo info) {
    super(info);
  }
}

/**
 * Simple BooleanFormula implementation.
 */
@SuppressWarnings("serial")
class BooleanFormulaImpl<TFormulaInfo> extends AbstractFormula<TFormulaInfo> implements BooleanFormula {
  public BooleanFormulaImpl(TFormulaInfo pT) {
    super(pT);
  }

  private Object writeReplace() {
    return new SerialProxyFormula(GlobalInfo.getInstance().getFormulaManager().dumpFormula(this).toString());
  }

  private static class SerialProxyFormula implements Serializable {

    private static final long serialVersionUID = -7575415230982043491L;
    private final String formula;

    public SerialProxyFormula(final String pF) {
      formula = pF;
    }

    private Object readResolve() {
      return GlobalInfo.getInstance().getFormulaManager().parse(formula);
    }

  }
}

/**
 * Simple IntegerFormula implementation.
 */
@SuppressWarnings("serial")
class IntegerFormulaImpl<TFormulaInfo> extends AbstractFormula<TFormulaInfo> implements IntegerFormula {
  public IntegerFormulaImpl(TFormulaInfo pTerm) {
    super(pTerm);
  }
}

/**
 * Simple RationalFormula implementation.
 */
@SuppressWarnings("serial")
class RationalFormulaImpl<TFormulaInfo> extends AbstractFormula<TFormulaInfo> implements RationalFormula {
  public RationalFormulaImpl(TFormulaInfo pTerm) {
    super(pTerm);
  }
}

