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
package org.sosy_lab.cpachecker.cpa.lockstatistics;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
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
  @Option(name="lockreset",
      description="function to reset state")
  private String lockreset;

  private Map<String, AnnotationInfo> annotatedfunctions;

  /*@Option(name="exceptions",
      description="functions with parameters, which we don't need to use")
  private Set<String> exceptions;


  @Option(name="functionhandler", values={"LINUX", "OS"},toUppercase=true,
      description="which type of function handler we should use")
  private String HandleType = "LINUX";*/

  private FunctionHandlerOS handler;
  private LogManager logger;

  public LockStatisticsTransferRelation(Configuration config, LogManager logger) throws InvalidConfigurationException {
    config.inject(this);
    this.logger = logger;

    ConfigurationParser parser = new ConfigurationParser(config);

    Set<LockInfo> tmpInfo = parser.parseLockInfo();
    annotatedfunctions = parser.parseAnnotatedFunctions();
   /* if (HandleType.equals("LINUX")) {
      handler = new FunctionHandlerLinux(lock, unlock, exceptions);
    }
    else if (HandleType.equals("OS")) {*/
    handler = new FunctionHandlerOS(tmpInfo, logger);
    /*} else {
      throw new InvalidConfigurationException("Unsupported function handler");
    }*/
  }

  @Override
  public Collection<LockStatisticsState> getAbstractSuccessors(AbstractState element, Precision pPrecision
      , CFAEdge cfaEdge)
    throws CPATransferException {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;

    LockStatisticsState successor;
    /*if (cfaEdge.getLineNumber() == 8081) {
      System.out.println("In CondWait()");
    }*/
    switch (cfaEdge.getEdgeType()) {

      case FunctionCallEdge:
        String fCallName = ((CFunctionCallEdge)cfaEdge).getSuccessor().getFunctionName();
      	if (annotatedfunctions != null && annotatedfunctions.containsKey(fCallName)) {
      		CFANode pred = ((CFunctionCallEdge)cfaEdge).getPredecessor();
      		logger.log(Level.FINER,"annotated name=" + fCallName + ", call"
                     + ", node=" + pred
                     + ", line=" + pred.getLineNumber()
                         + ", successor=" + lockStatisticsElement
                     );
      	}
        successor = handler.handleFunctionCall(lockStatisticsElement, (CFunctionCallEdge)cfaEdge);
        if (annotatedfunctions != null && annotatedfunctions.containsKey(fCallName) &&
            annotatedfunctions.get(fCallName).restoreLocks != null) {
          successor.setRestoreState(lockStatisticsElement);
        }
        break;

      case FunctionReturnEdge:
        CFANode tmpNode = ((CFunctionReturnEdge)cfaEdge).getSummaryEdge().getPredecessor();
        String fName =((CFunctionReturnEdge)cfaEdge).getSummaryEdge().getExpression().getFunctionCallExpression().getFunctionNameExpression().toASTString();
        if (annotatedfunctions != null && annotatedfunctions.containsKey(fName)) {
          successor = lockStatisticsElement.clone();
          if (annotatedfunctions.get(fName).restoreLocks != null) {

            if (lockStatisticsElement.getRestoreState() == null) {
              System.err.println("Can't restore state in function " + fName + ", because of null restore state");
            } else {
              successor = successor.restore(lockStatisticsElement.getRestoreState(), annotatedfunctions.get(fName).restoreLocks, logger);
      		    successor.setRestoreState(lockStatisticsElement.getRestoreState().getRestoreState());

      		    logger.log(Level.FINER, "annotated name=" + fName + ", return"
                  + ", node=" + tmpNode
                  + ", line=" + tmpNode.getLineNumber()
                  + ",\n\t successor=" + successor
                  + ",\n\t element=" + element
                  );
            }

          }
          if (annotatedfunctions.get(fName).freeLocks != null) {
            //free some locks
            successor = successor.free(annotatedfunctions.get(fName).freeLocks, logger);
          }
          if (annotatedfunctions.get(fName).resetLocks != null) {
            successor = successor.reset(annotatedfunctions.get(fName).resetLocks, logger);
          }
          break;
        }
        successor = lockStatisticsElement.clone();
        break;

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
        CStatement statement = ((CStatementEdge)cfaEdge).getStatement();
        if (statement instanceof CFunctionCallStatement && lockreset != null &&
          ((CFunctionCallStatement)statement).getFunctionCallExpression().getFunctionNameExpression().toASTString().equals(lockreset)) {
          return new LockStatisticsState();
        } else {
          return handler.handleStatement(element, statement, cfaEdge.getPredecessor().getFunctionName());
        }

      case BlankEdge:
      case AssumeEdge:
      case ReturnStatementEdge:
      case DeclarationEdge:
      case CallToReturnEdge:
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
