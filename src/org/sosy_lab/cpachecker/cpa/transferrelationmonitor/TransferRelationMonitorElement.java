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

import org.sosy_lab.cpachecker.util.assumptions.AvoidanceReportingElement;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperElement;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;

public class TransferRelationMonitorElement extends AbstractSingleWrapperElement implements AvoidanceReportingElement {

  private boolean shouldStop = false;

  private long timeOfTranferToComputeElement;
  private long totalTimeOnThePath;
  private int numberOfBranches = 0;
  public static long maxTimeOfTransfer = 0;
  public static long maxTotalTimeForPath = 0;
  public static long totalTimeOfTransfer = 0;
  public static long totalNumberOfTransfers = 0;
  public static long maxNumberOfBranches = 0;
  private boolean ignore = false;
  
  private int noOfNodesOnPath;

  protected TransferRelationMonitorElement(AbstractElement pWrappedElement) {
    super(pWrappedElement);
    timeOfTranferToComputeElement = 0;
    totalTimeOnThePath = 0;
    setNoOfNodesOnPath(0);
  }

  protected void setTransferTime(long pTransferTime){
    timeOfTranferToComputeElement = pTransferTime;
    totalTimeOfTransfer = totalTimeOfTransfer + pTransferTime;
    totalNumberOfTransfers ++;
    if(timeOfTranferToComputeElement > maxTimeOfTransfer){
      maxTimeOfTransfer = timeOfTranferToComputeElement;
    }
  }

  protected void setTotalTime(boolean pIsIgnore, long pTotalTime){
    ignore = pIsIgnore;
    totalTimeOnThePath = pTotalTime + timeOfTranferToComputeElement;
    if(totalTimeOnThePath > maxTotalTimeForPath){
      maxTotalTimeForPath = totalTimeOnThePath;
    }
  }

  protected void resetTotalTime(){
    totalTimeOnThePath = 0;
  }

  public long getTimeOfTranferToComputeElement() {
    return timeOfTranferToComputeElement;
  }

  public long getTotalTimeOnThePath() {
    return totalTimeOnThePath;
  }

  @Override
  public boolean equals(Object pObj) {
    if (this == pObj) {
      return true;
    } else if (pObj instanceof TransferRelationMonitorElement) {
      TransferRelationMonitorElement otherElem = (TransferRelationMonitorElement)pObj;
      return this.getWrappedElement().equals(otherElem.getWrappedElement());
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return getWrappedElement().hashCode();
  }

  public void setAsStopElement(){
    shouldStop = true;
  }

  @Override
  public boolean mustDumpAssumptionForAvoidance() {
    // returns true if the current element is the same as bottom
    if (shouldStop)
      return true;
    return false;
  }

  public boolean isIgnore() {
    return ignore;
  }

  public void setIgnore() {
    ignore = true;
  }

  public void setNoOfNodesOnPath(int noOfNodesOnPath) {
    this.noOfNodesOnPath = noOfNodesOnPath;
  }

  public int getNoOfNodesOnPath() {
    return noOfNodesOnPath;
  }
  
  @Override
  public String toString() {
    return "No of nodes> " + this.noOfNodesOnPath
    + "\n Total time> " + this.totalTimeOnThePath 
    + "\n Max Single Operation Time> " + maxTimeOfTransfer
    + "\n Number of Branches" + numberOfBranches;
  }

  public int getNoOfBranchesOnPath() {
    return numberOfBranches;
  }

  public void setNoOfBranches(int pI) {
    numberOfBranches = pI;
    if(numberOfBranches > maxNumberOfBranches){
      maxNumberOfBranches = numberOfBranches;
    }
    
  }
}