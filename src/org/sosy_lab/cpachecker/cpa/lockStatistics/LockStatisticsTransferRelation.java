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

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
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

  @Option(name="annotate",
      description=" annotated functions, which are known to works right")
  private Set<String> annotated;

  private Map<String, AnnotationInfo> annotatedfunctions;

  @Option(name="lockfree",
      description="functions, which are returned without locks")
  private Set<String> freefunctions;
  private Map<String, Set<String>> lockfree = new HashMap<String, Set<String>>();

  private Map<CFANode, LockStatisticsState> returnedStates = new HashMap<CFANode, LockStatisticsState>();

  /*@Option(name="exceptions",
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
    Set<String> tmpStringSet, tmpStringSet2;
    String tmpString;
    AnnotationInfo tmpAnnotationInfo;
    int num;

    for (String lockName : lockinfo) {
      tmpString = config.getProperty(lockName + ".lock");
      tmpStringSet = new HashSet<String>(Arrays.asList(tmpString.split(", *")));
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
      tmpStringSet = new HashSet<String>(Arrays.asList(tmpString.split(", *")));
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
        tmpStringSet = new HashSet<String>(Arrays.asList(tmpString.split(", *")));
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
    if (freefunctions != null) {
      for (String name : freefunctions) {
        tmpString = config.getProperty("lockfree." + name);
        tmpStringSet = new HashSet<String>(Arrays.asList(tmpString.split(", *")));
        lockfree.put(name, tmpStringSet);
      }
    }

    if (annotated != null) annotatedfunctions = new HashMap<String, AnnotationInfo>();

    for (String fName : annotated) {
      tmpString = config.getProperty("annotate." + fName + ".free");
      if (tmpString != null)
        tmpStringSet = new HashSet<String>(Arrays.asList(tmpString.split(", *")));
      else
        tmpStringSet = new HashSet<String>();
      tmpString = config.getProperty("annotate." + fName + ".restore");
      if (tmpString != null)
        tmpStringSet2 = new HashSet<String>(Arrays.asList(tmpString.split(", *")));
      else
        tmpStringSet2 = new HashSet<String>(lockinfo);
      tmpAnnotationInfo = new AnnotationInfo(fName, tmpStringSet, tmpStringSet2);
      annotatedfunctions.put(fName, tmpAnnotationInfo);
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

      if (annotated != null && annotated.contains(((CFunctionCallEdge)cfaEdge).getSuccessor().getFunctionName())) {
        if (annotatedfunctions != null &&
            annotatedfunctions.get(((CFunctionCallEdge)cfaEdge).getSuccessor().getFunctionName()).restoreLocks.size() > 0)
          returnedStates.put(((CFunctionCallEdge)cfaEdge).getPredecessor(), lockStatisticsElement);
      }
      successor = handler.handleFunctionCall(lockStatisticsElement, (CFunctionCallEdge)cfaEdge);
      break;

    case FunctionReturnEdge:
      CFANode tmpNode = ((CFunctionReturnEdge)cfaEdge).getSummaryEdge().getPredecessor();
      String fName =((CFunctionReturnEdge)cfaEdge).getSummaryEdge().getExpression().getFunctionCallExpression().getFunctionNameExpression().toASTString();
      AnnotationInfo tmpAnnotationInfo;

      if (returnedStates != null && returnedStates.containsKey(tmpNode)) {
        assert (annotatedfunctions.containsKey(fName));

        Set<Pair<String, String>> toDelete = new HashSet<Pair<String, String>>();
        Set<String> toReset = new HashSet<String>();

        tmpAnnotationInfo = annotatedfunctions.get(fName);
        successor = returnedStates.get(tmpNode).clone();
        //System.out.println("Function: "  + fName);
        //System.out.println("Cached state: " + successor.toString());
       // System.out.println("New state   : " + lockStatisticsElement.toString());

        for (LockStatisticsLock lock : lockStatisticsElement.getLocks()) {
          if (!(successor.contains(lock.getName())) && !(tmpAnnotationInfo.restoreLocks.contains(lock.getName())))
            successor.add(lock);
        }

        for (LockStatisticsLock lock : successor.getLocks()) {
          //we can't delete just now!
          if (tmpAnnotationInfo.freeLocks.contains(lock.getName()))
            toReset.add(lock.getName());
          if (!(lockStatisticsElement.contains(lock.getName())) && !(tmpAnnotationInfo.restoreLocks.contains(lock.getName())))
            toDelete.add(Pair.of(lock.getName(), lock.getVariable()));
        }

        for (String name : toReset)
          successor.reset(name);

        for (Pair<String, String> pair : toDelete)
          successor.delete(pair.getFirst(), pair.getSecond(), false);

        //returnedStates.remove(tmpNode);
       // System.out.println("Result state: " + successor.toString());

      } else if (lockfree.size() > 0  && lockfree.containsKey(((CFunctionReturnEdge)cfaEdge).getPredecessor().getFunctionName())) {
        Set<String> freelocks = lockfree.get(((CFunctionReturnEdge)cfaEdge).getPredecessor().getFunctionName());
        successor = lockStatisticsElement.clone();
        for (String lockName : freelocks) {
          if (successor.contains(lockName)) {
            successor.delete(lockName, "", true);
          }
        }

      } else {
        successor = lockStatisticsElement.clone();//removeLocal(cfaEdge.getPredecessor().getFunctionName());
      }
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
