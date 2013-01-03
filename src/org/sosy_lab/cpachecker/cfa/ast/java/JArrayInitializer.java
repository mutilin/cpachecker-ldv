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

import java.util.List;

import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.types.java.JArrayType;


public class JArrayInitializer extends AExpression implements JAstNode , JInitializer, JExpression {

  private final List<JExpression> initializerExpressions;

  public JArrayInitializer(FileLocation pFileLocation , List<JExpression> pInitializerExpression , JArrayType pType) {
    super(pFileLocation , pType);

    initializerExpressions = pInitializerExpression;
  }

  @Override
  public JArrayType getExpressionType() {
    return (JArrayType) super.getExpressionType();
  }

  public List<JExpression> getInitializerExpressions(){
    return initializerExpressions;
  }

  @Override
  public String toASTString() {

    StringBuilder astString = new StringBuilder("{");

    for(JExpression exp : initializerExpressions){
      astString.append(exp.toASTString() + ", " );
    }

    // delete the last ','
    astString.deleteCharAt(astString.length() -1);
    astString.append("}");

    return astString.toString();
  }

  @Override
  public <R, X extends Exception> R accept(JRightHandSideVisitor<R, X> v) throws X {
    return v.visit(this);
  }

  @Override
  public <R, X extends Exception> R accept(JExpressionVisitor<R, X> v) throws X {
    return v.visit(this);
  }

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((initializerExpressions == null) ? 0 : initializerExpressions.hashCode());
    result = prime * result + super.hashCode();
    return result;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) { return true; }
    if (!super.equals(obj)) { return false; }
    if (!(obj instanceof JArrayInitializer)) { return false; }
    JArrayInitializer other = (JArrayInitializer) obj;
    if (initializerExpressions == null) {
      if (other.initializerExpressions != null) { return false; }
    } else if (!initializerExpressions.equals(other.initializerExpressions)) { return false; }

    return super.equals(other);
  }

}