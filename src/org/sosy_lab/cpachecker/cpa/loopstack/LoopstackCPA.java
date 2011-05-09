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
package org.sosy_lab.cpachecker.cpa.loopstack;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.CFA.Loop;

public class LoopstackCPA extends AbstractCPA {
  
  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(LoopstackCPA.class);
  }
  
  public LoopstackCPA(Configuration config) throws InvalidConfigurationException, CPAException {
    super("sep", "sep", new LoopstackTransferRelation(config));
  }
  
  @Override
  public AbstractElement getInitialElement(CFANode pNode) {
    if (pNode instanceof CFAFunctionDefinitionNode) {
      // shortcut for the common case, a function start node can never be in a loop
      // (loops don't span across functions)
      return new LoopstackElement();
    }
      
    Loop loop = null;
    for (Loop l : CFACreator.loops.get(pNode.getFunctionName())) {
      if (l.getLoopNodes().contains(pNode)) {
        assert loop == null : "Cannot create initial nodes for locations in nested loops";
      }
      loop = l;
    }
    
    LoopstackElement e = new LoopstackElement(); // the bottom element of the stack
    
    if (loop != null) {
      // if loop is present, push one element on the stack for it
      e = new LoopstackElement(e, loop, 0, false);
    }
    return e;
  }
}