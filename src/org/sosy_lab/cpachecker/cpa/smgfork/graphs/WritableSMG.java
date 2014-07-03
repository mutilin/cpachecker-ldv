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
package org.sosy_lab.cpachecker.cpa.smgfork.graphs;

import java.util.Set;

import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cpa.smgfork.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smgfork.SMGEdgePointsTo;
import org.sosy_lab.cpachecker.cpa.smgfork.objects.SMGObject;
import org.sosy_lab.cpachecker.cpa.smgfork.objects.SMGRegion;


public interface WritableSMG extends ReadableSMG {
  public void addObject(SMGObject pObject);
  public void addGlobalObject(SMGRegion pObject);
  public void addStackObject(SMGRegion pObject);
  public void addStackFrame(CFunctionDeclaration pFunction);

  public void addValue(Integer pValue);

  public void addPointsToEdge(SMGEdgePointsTo pEdge);

  public void addHasValueEdge(SMGEdgeHasValue pEdge);
  public void replaceHVSet(Set<SMGEdgeHasValue> pHV);

  public void setValidity(SMGRegion pRegion, boolean pValidity);
}
