/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.composite;

import java.util.ArrayList;
import java.util.List;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;


public class CompositeReducer implements Reducer {

  private final List<Reducer> wrappedReducers;

  public CompositeReducer(List<Reducer> pWrappedReducers) {
    wrappedReducers = pWrappedReducers;
  }

  @Override
  public AbstractElement getVariableReducedElement(
      AbstractElement pExpandedElement, Block pContext,
      CFANode pLocation) {

    List<AbstractElement> result = new ArrayList<AbstractElement>();
    int i = 0;
    for (AbstractElement expandedElement : ((CompositeElement)pExpandedElement).getWrappedElements()) {
      result.add(wrappedReducers.get(i++).getVariableReducedElement(expandedElement, pContext, pLocation));
    }
    return new CompositeElement(result);
  }

  @Override
  public AbstractElement getVariableExpandedElement(
      AbstractElement pRootElement, Block pReducedContext,
      AbstractElement pReducedElement) {

    List<AbstractElement> rootElements = ((CompositeElement)pRootElement).getWrappedElements();
    List<AbstractElement> reducedElements = ((CompositeElement)pReducedElement).getWrappedElements();

    List<AbstractElement> result = new ArrayList<AbstractElement>();
    int i = 0;
    for (Pair<AbstractElement, AbstractElement> p : Pair.zipList(rootElements, reducedElements)) {
      result.add(wrappedReducers.get(i++).getVariableExpandedElement(p.getFirst(), pReducedContext, p.getSecond()));
    }
    return new CompositeElement(result);
  }

  @Override
  public Object getHashCodeForElement(AbstractElement pElementKey, Precision pPrecisionKey) {

    List<AbstractElement> elements = ((CompositeElement)pElementKey).getWrappedElements();
    List<Precision> precisions = ((CompositePrecision)pPrecisionKey).getPrecisions();

    List<Object> result = new ArrayList<Object>(elements.size());
    int i = 0;
    for (Pair<AbstractElement, Precision> p : Pair.zipList(elements, precisions)) {
      result.add(wrappedReducers.get(i++).getHashCodeForElement(p.getFirst(), p.getSecond()));
    }
    return result;
  }

  @Override
  public Precision getVariableReducedPrecision(Precision pPrecision,
      Block pContext) {
    List<Precision> precisions = ((CompositePrecision)pPrecision).getPrecisions();
    List<Precision> result = new ArrayList<Precision>(precisions.size());

    int i = 0;
    for (Precision precision : precisions) {
      result.add(wrappedReducers.get(i++).getVariableReducedPrecision(precision, pContext));
    }

    return new CompositePrecision(result);
  }

  @Override
  public Precision getVariableExpandedPrecision(Precision pRootPrecision, Block pRootContext, Precision pReducedPrecision) {
    List<Precision> rootPrecisions = ((CompositePrecision)pRootPrecision).getPrecisions();
    List<Precision> reducedPrecisions = ((CompositePrecision)pReducedPrecision).getPrecisions();
    List<Precision> result = new ArrayList<Precision>(rootPrecisions.size());

    int i = 0;
    for (Precision rootPrecision : rootPrecisions) {
      result.add(wrappedReducers.get(i).getVariableExpandedPrecision(rootPrecision, pRootContext, reducedPrecisions.get(i)));
      i++;
    }

    return new CompositePrecision(result);
  }
}
