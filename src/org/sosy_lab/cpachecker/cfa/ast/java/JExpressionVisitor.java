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
package org.sosy_lab.cpachecker.cfa.ast.java;



public interface JExpressionVisitor<R, X extends Exception>  {

  R visit(JCharLiteralExpression paCharLiteralExpression) throws X;

  R visit(JStringLiteralExpression paStringLiteralExpression) throws X;

  R visit (JBinaryExpression paBinaryExpression) throws X;

  R visit(JUnaryExpression pAUnaryExpression) throws X;

  R visit(JIntegerLiteralExpression pJIntegerLiteralExpression) throws X;

  R visit(JBooleanLiteralExpression pJBooleanLiteralExpression) throws X;

  R visit(JFloatLiteralExpression pJBooleanLiteralExpression) throws X;

  R visit(JArrayCreationExpression pJBooleanLiteralExpression) throws X;

  R visit(JArrayInitializer pJArrayInitializer) throws X;

  R visit(JArraySubscriptExpression pAArraySubscriptExpression) throws X;

  R visit(JVariableRunTimeType pJThisRunTimeType) throws X;

  R visit(JIdExpression pJIdExpression) throws X;

  R visit(JRunTimeTypeEqualsType pJRunTimeTypeEqualsType) throws X;

  R visit(JNullLiteralExpression pJNullLiteralExpression) throws X;

  R visit(JEnumConstantExpression pJEnumConstantExpression) throws X;

  R visit(JCastExpression pJCastExpression) throws X;

  R visit(JThisExpression pThisExpression) throws X;

}