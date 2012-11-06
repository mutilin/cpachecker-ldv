/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.exceptions;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;



public class StopAnalysisException extends HandleCodeException {

  private static final long serialVersionUID = -6559132286640085407L;
  private final CFANode node;
  private final CFAEdge edge;

  public StopAnalysisException(String pMsg, CFANode n) {
    super(pMsg);
    node = n;
    edge = null;
  }

  public StopAnalysisException(String pMsg, CFAEdge n) {
    super(pMsg);
    node = null;
    edge = n;
  }

  public StopAnalysisException(String pMsg, CFANode n, CFAEdge e) {
    super(pMsg);
    node = n;
    edge = e;
  }

  public CFANode getNode() {
    return node;
  }

  public CFAEdge getEdge() {
    return edge;
  }
}
