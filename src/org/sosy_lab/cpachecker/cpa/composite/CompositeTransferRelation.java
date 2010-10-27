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
package org.sosy_lab.cpachecker.cpa.composite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

public class CompositeTransferRelation implements TransferRelation{

  private final ImmutableList<TransferRelation> transferRelations;

  // private LocationTransferRelation locationTransferRelation;

  public CompositeTransferRelation(ImmutableList<TransferRelation> transferRelations) {
    this.transferRelations = transferRelations;
  }

  @Override
  public Collection<CompositeElement> getAbstractSuccessors(AbstractElement element, Precision precision, CFAEdge cfaEdge)
        throws CPATransferException {
    CompositeElement compositeElement = (CompositeElement) element;
    Collection<CompositeElement> results;

    if (cfaEdge == null) {
      CFANode node = compositeElement.retrieveLocationElement().getLocationNode();
      results = new ArrayList<CompositeElement>(node.getNumLeavingEdges());

      for (int edgeIdx = 0; edgeIdx < node.getNumLeavingEdges(); edgeIdx++) {
        CFAEdge edge = node.getLeavingEdge(edgeIdx);
        getAbstractSuccessorForEdge(compositeElement, precision, edge, results);
      }

    } else {
      results = new ArrayList<CompositeElement>(1);
      getAbstractSuccessorForEdge(compositeElement, precision, cfaEdge, results);

    }

    return results;
  }

  private void getAbstractSuccessorForEdge(CompositeElement compositeElement, Precision precision, CFAEdge cfaEdge,
      Collection<CompositeElement> compositeSuccessors) throws CPATransferException {
    assert cfaEdge != null;

    assert(precision instanceof CompositePrecision);
    CompositePrecision lCompositePrecision = (CompositePrecision)precision;

    int resultCount = 1;
    List<AbstractElement> componentElements = compositeElement.getElements();
    List<Collection<? extends AbstractElement>> allComponentsSuccessors = new ArrayList<Collection<? extends AbstractElement>>(transferRelations.size());

    for (int idx = 0; idx < transferRelations.size (); idx++) {
      TransferRelation transfer = transferRelations.get(idx);
      AbstractElement componentElement = componentElements.get(idx);

      Precision lPrecision = lCompositePrecision.get(idx);

      Collection<? extends AbstractElement> componentSuccessors = transfer.getAbstractSuccessors(componentElement, lPrecision, cfaEdge);
      resultCount *= componentSuccessors.size();
      
      if (resultCount == 0) {
        // shortcut
        break;
      }
      
      allComponentsSuccessors.add(componentSuccessors);
    }

    Collection<List<AbstractElement>> allResultingElements;

    switch (resultCount) {
    case 0:
      // at least one CPA decided that there is no successor
      return;

    case 1:
      List<AbstractElement> resultingElements = new ArrayList<AbstractElement>(allComponentsSuccessors.size());
      for (Collection<? extends AbstractElement> componentSuccessors : allComponentsSuccessors) {
        resultingElements.add(Iterables.getOnlyElement(componentSuccessors));
      }
      allResultingElements = Collections.singleton(resultingElements);
      break;

    default:
      // create cartesian product of all componentSuccessors and store the result in allResultingElements
      List<AbstractElement> initialPrefix = Collections.emptyList();
      allResultingElements = new ArrayList<List<AbstractElement>>(resultCount);
      createCartesianProduct(allComponentsSuccessors, initialPrefix, allResultingElements);
    }

    assert resultCount == allResultingElements.size();

    for (List<AbstractElement> lReachedElement : allResultingElements) {
      
      List<Collection<? extends AbstractElement>> lStrengthenedElements = new ArrayList<Collection<? extends AbstractElement>>(transferRelations.size());
      
      int lNumberOfResultingElements = 1;
      
      for (int lIndex = 0; lIndex < transferRelations.size() && lNumberOfResultingElements > 0; lIndex++) {
        
        AbstractElement lCurrentElement = lReachedElement.get(lIndex);
        TransferRelation lCurrentTransferRelation = transferRelations.get(lIndex);
        
        Collection<? extends AbstractElement> lResultsList = lCurrentTransferRelation.strengthen(lCurrentElement, lReachedElement, cfaEdge, (lCompositePrecision == null) ? null : lCompositePrecision.get(lIndex));

        if (lResultsList == null) {
          lStrengthenedElements.add(Collections.singleton(lCurrentElement));
        }
        else {
          lNumberOfResultingElements *= lResultsList.size();
          
          if (lNumberOfResultingElements == 0) {
            // shortcut
            break;
          }
          
          lStrengthenedElements.add(lResultsList);
        }
      }
      
      if (lNumberOfResultingElements > 0) {
        Collection<List<AbstractElement>> lResultingElements = new ArrayList<List<AbstractElement>>(lNumberOfResultingElements);
        List<AbstractElement> lInitialPrefix = Collections.emptyList();
        createCartesianProduct(lStrengthenedElements, lInitialPrefix, lResultingElements);
        
        for (List<AbstractElement> lList : lResultingElements) {
          compositeSuccessors.add(new CompositeElement(lList));
        }
      }
    }
  }

  private static void createCartesianProduct(List<Collection<? extends AbstractElement>> allComponentsSuccessors,
      List<AbstractElement> prefix, Collection<List<AbstractElement>> allResultingElements) {

    if (prefix.size() == allComponentsSuccessors.size()) {
      allResultingElements.add(prefix);

    } else {
      int depth = prefix.size();
      Collection<? extends AbstractElement> myComponentsSuccessors = allComponentsSuccessors.get(depth);

      for (AbstractElement currentComponent : myComponentsSuccessors) {
        List<AbstractElement> newPrefix = new ArrayList<AbstractElement>(prefix);
        newPrefix.add(currentComponent);

        createCartesianProduct(allComponentsSuccessors, newPrefix, allResultingElements);
      }
    }
  }

  @Override
  public Collection<? extends AbstractElement> strengthen(AbstractElement element,
      List<AbstractElement> otherElements, CFAEdge cfaEdge,
      Precision precision) {
    // strengthen is only called by the composite CPA on its component CPAs
    return null;
  }
}
