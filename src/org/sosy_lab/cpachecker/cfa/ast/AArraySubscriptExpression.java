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
package org.sosy_lab.cpachecker.cfa.ast;

import org.sosy_lab.cpachecker.cfa.types.Type;


public abstract class AArraySubscriptExpression extends AExpression {


  protected final IAExpression arrayExpression;
  protected final IAExpression subscriptExpression;

  public AArraySubscriptExpression(FileLocation pFileLocation,
      Type pType,
      final IAExpression pArrayExpression,
      final IAExpression pSubscriptExpression) {
    super(pFileLocation, pType);
    arrayExpression = pArrayExpression;
    subscriptExpression = pSubscriptExpression;

  }

  @Override
  public Type getExpressionType() {
    return  type;
  }

  public IAExpression getArrayExpression() {
    return arrayExpression;
  }

  public IAExpression getSubscriptExpression() {
    return subscriptExpression;
  }

  @Override
  public String toASTString() {
    String left = (arrayExpression instanceof AArraySubscriptExpression) ? arrayExpression.toASTString() : arrayExpression.toParenthesizedASTString();
    return left + "[" + subscriptExpression.toASTString() + "]";
  }

}