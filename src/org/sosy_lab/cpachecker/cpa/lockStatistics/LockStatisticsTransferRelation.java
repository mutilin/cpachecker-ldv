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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CNamedType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

@Options(prefix="cpa.lockStatistics")
public class LockStatisticsTransferRelation implements TransferRelation
{
  private final Set<String> globalMutex = new HashSet<String>();

  @Option(name="lockfunctions",
      description="functions, that locks synchronization primitives")
  private List<String> lock;

  @Option(name="unlockfunctions",
      description="functions, that unlocks synchronization primitives")
  private List<String> unlock;

  @Option(name="exceptions",
      description="functions wuth parameters, which we don't need to use")
  private Set<String> exceptions;


  @Option(name="functionhandler", values={"LINUX", "OS"},toUppercase=true,
      description="which type of function handler we should use")
  private String HandleType = "LINUX";

  private FunctionHandler handler;

  public LockStatisticsTransferRelation(Configuration config) throws InvalidConfigurationException {
    config.inject(this);

    if (HandleType.equals("LINUX")) {
      handler = new FunctionHandlerLinux(lock, unlock, exceptions);
    }
    else if (HandleType.equals("OS")) {
      handler = new FunctionHandlerOS(lock, unlock, exceptions);
    }
  }

  @Override
  public Collection<LockStatisticsState> getAbstractSuccessors(AbstractState element, Precision pPrecision, CFAEdge cfaEdge)
    throws CPATransferException {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;
    LockStatisticsPrecision lockStatisticsPrecision = (LockStatisticsPrecision)pPrecision;

    LockStatisticsState successor;
    switch (cfaEdge.getEdgeType()) {

    case FunctionCallEdge:
      successor = handler.handleFunctionCall(lockStatisticsElement, (CFunctionCallEdge)cfaEdge);
      break;

    case FunctionReturnEdge:
      successor = lockStatisticsElement.clone();
      break;

    default:
      successor = handleSimpleEdge(lockStatisticsElement, lockStatisticsPrecision, cfaEdge);
    }

    if (successor == null) {
      return Collections.emptySet();
    } else {
      return Collections.singleton(successor);
    }
  }

  private LockStatisticsState handleSimpleEdge(LockStatisticsState element, LockStatisticsPrecision precision, CFAEdge cfaEdge)
        throws CPATransferException {

    switch(cfaEdge.getEdgeType()) {
    case StatementEdge:
      CStatementEdge statementEdge = (CStatementEdge) cfaEdge;
      return handler.handleStatement(element, statementEdge.getStatement());

    case BlankEdge:
    case AssumeEdge:
    case ReturnStatementEdge:
      return element.clone();

    case DeclarationEdge:
      CDeclarationEdge declarationEdge = (CDeclarationEdge) cfaEdge;
      return handleDeclaration(element.clone(), declarationEdge, precision, cfaEdge.getLineNumber());

    case MultiEdge:
      LockStatisticsState tmpElement = element.clone();

      for (CFAEdge edge : (MultiEdge)cfaEdge) {
        tmpElement = handleSimpleEdge(tmpElement, precision, edge);
      }
      return tmpElement;

    default:
      throw new UnrecognizedCFAEdgeException(cfaEdge);
    }
  }

  private LockStatisticsState handleDeclaration(LockStatisticsState newElement, CDeclarationEdge declarationEdge, LockStatisticsPrecision precision, int line)
      throws UnrecognizedCCodeException {

      if (!(declarationEdge.getDeclaration() instanceof CVariableDeclaration)) {
        return newElement;
      }

      CVariableDeclaration decl = (CVariableDeclaration)declarationEdge.getDeclaration();

      String varName = decl.getName();
      CType type = decl.getType();

      if(decl.isGlobal() && type instanceof CNamedType &&
          ((CNamedType)type).getName().contentEquals("mutex")) {
        globalMutex.add(varName);
        return newElement;
      }
      return newElement;
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements, CFAEdge cfaEdge, Precision precision)
    throws UnrecognizedCCodeException {
    return null;
  }
}
