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
package org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA.util.symbpredabstraction;

import org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA.util.symbpredabstraction.interfaces.SymbolicFormula;
import org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA.util.symbpredabstraction.ssa.UnmodifiableSSAMap;


public class PathFormula {

  private final SymbolicFormula mPathFormula;
  private final UnmodifiableSSAMap mSSAMap;
  
  public PathFormula(SymbolicFormula pPathFormula, UnmodifiableSSAMap pSSAMap) {
    mPathFormula = pPathFormula;
    mSSAMap = pSSAMap;
  }

  public SymbolicFormula getSymbolicFormula() {
    return mPathFormula;
  }

  public UnmodifiableSSAMap getSSAMap() {
    return mSSAMap;
  }

  @Override
  public String toString(){
    return getSymbolicFormula().toString();
  }
  
  @Override
  public boolean equals(Object pOther) {
    if (this == pOther) {
      return true;
    }
    
    if (pOther == null) {
      return false;
    }
    
    if (getClass().equals(pOther.getClass())) {
      PathFormula lPathFormula = (PathFormula)pOther;
      
      return mPathFormula.equals(lPathFormula.mPathFormula) && mSSAMap.equals(lPathFormula.mSSAMap);
    }
    
    return false;
  }
  
  @Override
  public int hashCode() {
    return 31 * mPathFormula.hashCode() + mSSAMap.hashCode() + 321;
  }

}
