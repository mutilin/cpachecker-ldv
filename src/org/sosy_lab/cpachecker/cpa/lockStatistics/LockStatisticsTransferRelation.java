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
package org.sosy_lab.cpachecker.cpa.lockStatistics;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

@Options(prefix="cpa.lockStatistics")
public class LockStatisticsTransferRelation implements TransferRelation
{
  //private final Set<String> globalMutex = new HashSet<String>();

  @Option(name="lockreset",
      description="function to reset state")
  private String lockreset;

  @Option(name="lockinfo",
      description="contains all lock names")
  private Set<String> lockinfo;

  /*@Option(name="unlockfunctions",
      description="functions, that unlocks synchronization primitives")
  private List<String> unlock;

  @Option(name="exceptions",
      description="functions wuth parameters, which we don't need to use")
  private Set<String> exceptions;


  @Option(name="functionhandler", values={"LINUX", "OS"},toUppercase=true,
      description="which type of function handler we should use")
  private String HandleType = "LINUX";*/

  private FunctionHandlerOS handler;

  public LockStatisticsTransferRelation(Configuration config) throws InvalidConfigurationException {
    config.inject(this);

    Set<LockInfo> tmpInfo = new HashSet<LockInfo>();
    LockInfo tmpLockInfo;
    Map<String, Integer> lockFunctions;
    Map<String, Integer> unlockFunctions;
    Map<String, Integer> resetFunctions;
    Set<String> tmpStringSet;
    String tmpString;
    int num;

    for (String lockName : lockinfo) {
      tmpString = config.getProperty(lockName + ".lock");
      tmpStringSet = new HashSet<String>(Arrays.asList(tmpString.split(",")));
      lockFunctions = new HashMap<String, Integer>();
      for (String funcName : tmpStringSet) {
        try {
          num = Integer.parseInt(config.getProperty(lockName + "." + funcName + ".parameters"));
        } catch (NumberFormatException e) {
          num = 0;
        }
        lockFunctions.put(funcName, num);
      }
      unlockFunctions = new HashMap<String, Integer>();
      tmpString = config.getProperty(lockName + ".unlock");
      tmpStringSet = new HashSet<String>(Arrays.asList(tmpString.split(",")));
      for (String funcName : tmpStringSet) {
        try {
          num = Integer.parseInt(config.getProperty(lockName + "." + funcName + ".parameters"));
        } catch (NumberFormatException e) {
          num = 0;
        }
        unlockFunctions.put(funcName, num);
      }
      resetFunctions = new HashMap<String, Integer>();
      tmpString = config.getProperty(lockName + ".reset");
      if (tmpString != null) {
        tmpStringSet = new HashSet<String>(Arrays.asList(tmpString.split(",")));
        for (String funcName : tmpStringSet) {
          try {
            num = Integer.parseInt(config.getProperty(lockName + "." + funcName + ".parameters"));
          } catch (NumberFormatException e) {
            num = 0;
          }
          resetFunctions.put(funcName, num);
        }
      }
      tmpString = config.getProperty(lockName + ".setlevel");
      try {
        num = Integer.parseInt(config.getProperty(lockName + ".maxDepth"));
      } catch (NumberFormatException e) {
        num = 100;
      }
      tmpLockInfo = new LockInfo(lockName, lockFunctions, unlockFunctions, resetFunctions, tmpString, num);
      tmpInfo.add(tmpLockInfo);
    }

   /* if (HandleType.equals("LINUX")) {
      handler = new FunctionHandlerLinux(lock, unlock, exceptions);
    }
    else if (HandleType.equals("OS")) {*/
    handler = new FunctionHandlerOS(tmpInfo);
    /*} else {
      throw new InvalidConfigurationException("Unsupported function handler");
    }*/
  }

  @Override
  public Collection<LockStatisticsState> getAbstractSuccessors(AbstractState element, Precision pPrecision, CFAEdge cfaEdge)
    throws CPATransferException {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;

    LockStatisticsState successor;
    switch (cfaEdge.getEdgeType()) {

    case FunctionCallEdge:
      successor = handler.handleFunctionCall(lockStatisticsElement, (CFunctionCallEdge)cfaEdge);
      break;

    case FunctionReturnEdge:
      successor = lockStatisticsElement.clone();//removeLocal(cfaEdge.getPredecessor().getFunctionName());
      break;

    case StatementEdge:
      CStatement statement = ((CStatementEdge)cfaEdge).getStatement();
      if (statement instanceof CFunctionCallStatement && lockreset != null &&
        ((CFunctionCallStatement)statement).getFunctionCallExpression().getFunctionNameExpression().toASTString().equals(lockreset)) {
        successor = new LockStatisticsState();
        break;
      }

      //$FALL-THROUGH$
    default:
      successor = handleSimpleEdge(lockStatisticsElement, cfaEdge);
    }

    if (successor == null) {
      return Collections.emptySet();
    } else {
      return Collections.singleton(successor);
    }
  }

  private LockStatisticsState handleSimpleEdge(LockStatisticsState element, CFAEdge cfaEdge)
        throws CPATransferException {

    switch(cfaEdge.getEdgeType()) {
    case StatementEdge:
      CStatementEdge statementEdge = (CStatementEdge) cfaEdge;
      return handler.handleStatement(element, statementEdge.getStatement(), cfaEdge.getPredecessor().getFunctionName());

    case BlankEdge:
    case AssumeEdge:
    case ReturnStatementEdge:
    case DeclarationEdge:
      return element.clone();

    case MultiEdge:
      LockStatisticsState tmpElement = element.clone();

      for (CFAEdge edge : (MultiEdge)cfaEdge) {
        tmpElement = handleSimpleEdge(tmpElement, edge);
      }
      return tmpElement;

    default:
      throw new UnrecognizedCFAEdgeException(cfaEdge);
    }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements, CFAEdge cfaEdge, Precision precision)
    throws UnrecognizedCCodeException {
    return null;
  }

  public FunctionHandlerOS getFunctionHandler() {
    return handler;
  }
}
