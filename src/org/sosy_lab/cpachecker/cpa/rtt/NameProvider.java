/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.rtt;

import org.sosy_lab.cpachecker.cfa.ast.java.JArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JFieldAccess;
import org.sosy_lab.cpachecker.cfa.ast.java.JFieldDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JSimpleDeclaration;

/**
 * Class for creation of fully qualified names of object fields.
 */
public class NameProvider {

  private static final NameProvider SINGLETON = new NameProvider();
  private static final String DELIMITER = "::";

  private NameProvider() {
    // DO NOTHING
  }

  public static NameProvider getInstance() {
    return SINGLETON;
  }

  public String getObjectScope(RTTState rttState, String methodName,
      JIdExpression notScopedField) {

    // Could not resolve var
    if (notScopedField.getDeclaration() == null) {
      return null;
    }

    if (notScopedField instanceof JFieldAccess) {
      String scopedFieldName = getScopedFieldName((JFieldAccess) notScopedField, methodName, rttState);

      if (rttState.contains(scopedFieldName)) {
        return rttState.getUniqueObjectFor(scopedFieldName);
      } else {
        return null;
      }
    } else {
      if (rttState.contains(RTTState.KEYWORD_THIS)) {
        return rttState.getUniqueObjectFor(RTTState.KEYWORD_THIS);
      } else {
        return null;
      }
    }
  }

  private String getScopedFieldName(JFieldAccess pFieldAccess, String pMethodName, RTTState pRttState) {
    JExpression qualifier = pFieldAccess.getReferencedVariable();

    String variableName;
    String qualifierScope;

    if (qualifier instanceof JIdExpression) {
      JSimpleDeclaration declaration = ((JIdExpression) qualifier).getDeclaration();
      qualifierScope = getObjectScope(pRttState, pMethodName, (JIdExpression) qualifier);

      return getScopedVariableName(declaration, pMethodName, qualifierScope);

    } else if (qualifier instanceof JArraySubscriptExpression) {
      qualifierScope = "arrrr";
      variableName = "";

      return getScopedVariableName(variableName, pMethodName, qualifierScope, pRttState);
    } else {
      throw new AssertionError("Unexpected field qualifier " + qualifier);
    }


  }

  public String getScopedVariableName(String pVariableName, String pFunctionName, String pUniqueObject,
      RTTState pState) {

    if (pVariableName.equals(RTTState.KEYWORD_THIS)) {
      return pVariableName;
    }

    if (pState.isKnownAsStatic(pVariableName)) {
      return pVariableName;
    }

    if (pState.isKnownAsDynamic(pVariableName)) {
      return pUniqueObject + DELIMITER + pVariableName;
    }

    return pFunctionName + DELIMITER + pVariableName;
  }


  public String getScopedVariableName(JSimpleDeclaration pDeclaration, String functionName, String uniqueObject) {

    String variableName = pDeclaration.getName();

    if (pDeclaration instanceof JFieldDeclaration && ((JFieldDeclaration) pDeclaration).isStatic()) {
      return variableName;

    } else if (pDeclaration instanceof JFieldDeclaration) {
      return uniqueObject + DELIMITER + variableName;
    } else {
      return functionName + DELIMITER + variableName;
    }
  }
}
