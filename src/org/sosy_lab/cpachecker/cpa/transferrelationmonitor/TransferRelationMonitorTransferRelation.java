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
package org.sosy_lab.cpachecker.cpa.transferrelationmonitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.core.algorithm.CEGARAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

import com.google.common.base.Throwables;

@Options(prefix="trackabstractioncomputation")
public class TransferRelationMonitorTransferRelation implements TransferRelation {

  private final TransferRelation transferRelation;
  public static long maxSizeOfSinglePath = 0;

  @Option(name="limit")
  private long timeLimit = 0; // given in milliseconds

  @Option(name="pathcomputationlimit")
  private long timeLimitForPath = 0;

  @Option(name="pathlengthlimit")
  private long nodeLimitForPath = 0;
  
  @Option(name="brancheslimit")
  private long limitForBranches = 0;
  
  public TransferRelationMonitorTransferRelation(ConfigurableProgramAnalysis pWrappedCPA,
      Configuration config) throws InvalidConfigurationException {
    config.inject(this);

    transferRelation = pWrappedCPA.getTransferRelation();
  }

  @Override
  public Collection<TransferRelationMonitorElement> getAbstractSuccessors(
      AbstractElement pElement, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException {
    TransferRelationMonitorElement element = (TransferRelationMonitorElement)pElement;
    Collection<? extends AbstractElement> successors;
    long start = System.currentTimeMillis();

    TransferCallable tc = new TransferCallable(transferRelation, pCfaEdge,
        element.getWrappedElement(), pPrecision);

    if (timeLimit == 0) {
      successors = tc.call();
    } else {
    
      Future<Collection<? extends AbstractElement>> future = CEGARAlgorithm.executor.submit(tc);
      try {
        // here we get the result of the post computation but there is a time limit
        // given to complete the task specified by timeLimit
        successors = future.get(timeLimit, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e){
        return Collections.emptySet();
        
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Collections.emptySet();

      } catch (ExecutionException e) {
        Throwables.propagateIfPossible(e.getCause(), CPATransferException.class);
        // TransferRelation.getAbstractSuccessors() threw unexpected checked exception!
        throw new AssertionError(e);
      }
    }

    long timeOfExecution = System.currentTimeMillis() - start;

    if (successors.isEmpty()) {
      return Collections.emptySet();
    }

    List<TransferRelationMonitorElement> wrappedSuccessors = new ArrayList<TransferRelationMonitorElement>(successors.size());
    for (AbstractElement absElement : successors) {
      TransferRelationMonitorElement successorElem = new TransferRelationMonitorElement(absElement);
      successorElem.setTransferTime(timeOfExecution);
      successorElem.setTotalTime(element.isIgnore(), element.getTotalTimeOnThePath());
      successorElem.setNoOfNodesOnPath(element.getNoOfNodesOnPath()+1);
      if(pCfaEdge instanceof AssumeEdge){
        successorElem.setNoOfBranches(element.getNoOfBranchesOnPath()+1);  
      }
      if(element.getNoOfNodesOnPath() > maxSizeOfSinglePath){
        maxSizeOfSinglePath = element.getNoOfNodesOnPath();
        if(limitForBranches > 0 &&  successorElem.getNoOfBranchesOnPath() > limitForBranches){
          return Collections.emptySet();
        }
      }
      if(    (timeLimitForPath > 0 && successorElem.getTotalTimeOnThePath() > timeLimitForPath)
          || (nodeLimitForPath > 0 && successorElem.getNoOfNodesOnPath() > nodeLimitForPath)
          ){
        return Collections.emptySet();
      }
      wrappedSuccessors.add(successorElem);
    }
    return wrappedSuccessors;
  }

  @Override
  public Collection<? extends AbstractElement> strengthen(AbstractElement element,
      List<AbstractElement> otherElements, CFAEdge cfaEdge,
      Precision precision) {
    TransferRelationMonitorElement monitorElement = (TransferRelationMonitorElement)element;
    AbstractElement wrappedElement = monitorElement.getWrappedElement();
    List<AbstractElement> retList = new ArrayList<AbstractElement>();
    Collection<? extends AbstractElement> returnedElems = null;
    
    long start = System.currentTimeMillis();

    StrengthenCallable sc = new StrengthenCallable(transferRelation, wrappedElement,
        otherElements, cfaEdge, precision);

    ExecutorService executor = Executors.newSingleThreadExecutor();    
    
    if (timeLimit == 0) {
      try {
        returnedElems = sc.call();
      } catch (CPATransferException e) {
        e.printStackTrace();
      }
    } else {
      Future<Collection<? extends AbstractElement>> future = executor.submit(sc);
      try {
        // here we get the result of the post computation but there is a time limit
        // given to complete the task specified by timeLimit
        returnedElems = future.get(timeLimit, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e){
        System.out.println("timed out");
        executor.shutdownNow();
        return Collections.emptySet();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        executor.shutdownNow();
        return Collections.emptySet();
      } catch (ExecutionException e) {
        // TransferRelation.getAbstractSuccessors() threw unexpected checked exception!
        executor.shutdownNow();
        throw new AssertionError(e);
      }
    }
    executor.shutdownNow();
    
    long timeOfExecution = System.currentTimeMillis() - start;
    
//    try {
      //Collection<? extends AbstractElement> wrappedList = transferRelation.strengthen(wrappedElement, otherElements, cfaEdge, precision);
      // if the returned list is null return null
      if(returnedElems == null)
        return null;
      // TODO we assume that only one element is returned or empty set to represent bottom
      assert(returnedElems.size() < 2);
      // if bottom return empty list
      if(returnedElems.size() == 0){
        return retList;
      }

      AbstractElement wrappedReturnElement = returnedElems.iterator().next();
      TransferRelationMonitorElement retElem = new TransferRelationMonitorElement(wrappedReturnElement);
      retElem.setTransferTime(timeOfExecution);
      retElem.setTotalTime(monitorElement.isIgnore(), monitorElement.getTotalTimeOnThePath());
      retList.add(new TransferRelationMonitorElement(wrappedReturnElement));
      if(    (timeLimitForPath > 0 && retElem.getTotalTimeOnThePath() > timeLimitForPath)
          || (nodeLimitForPath > 0 && retElem.getNoOfNodesOnPath() > nodeLimitForPath)
          ){
        return Collections.emptySet();
      }
      return retList;
//    } catch (CPATransferException e) {
//      e.printStackTrace();
//    }

  }

  private static class TransferCallable implements Callable<Collection<? extends AbstractElement>>{

    private final TransferRelation transferRelation;
    private final CFAEdge cfaEdge;
    private final AbstractElement abstractElement;
    private final Precision precision;

    private TransferCallable(TransferRelation transferRelation, CFAEdge cfaEdge,
        AbstractElement abstractElement, Precision precision) {
      this.transferRelation = transferRelation;
      this.cfaEdge = cfaEdge;
      this.abstractElement = abstractElement;
      this.precision = precision;
    }

    @Override
    public Collection<? extends AbstractElement> call() throws CPATransferException {
      return transferRelation.getAbstractSuccessors(abstractElement, precision, cfaEdge);
    }
  }
  
  private static class StrengthenCallable implements Callable<Collection<? extends AbstractElement>>{

    private final TransferRelation transferRelation;
    private final CFAEdge cfaEdge;
    private final AbstractElement abstractElement;
    private final List<AbstractElement> otherElements;
    private final Precision precision;

    private StrengthenCallable(TransferRelation transferRelation, AbstractElement abstractElement, 
        List<AbstractElement> otherElements, CFAEdge cfaEdge, Precision precision) {
      this.transferRelation = transferRelation;
      this.cfaEdge = cfaEdge;
      this.abstractElement = abstractElement;
      this.otherElements = otherElements;
      this.precision = precision;
    }

    @Override
    public Collection<? extends AbstractElement> call() throws CPATransferException {
      return transferRelation.strengthen(abstractElement, otherElements, cfaEdge, precision);
    }
  }
}