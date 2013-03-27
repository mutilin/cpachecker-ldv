/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.identifiers;



public class ConstantIdentifier implements AbstractIdentifier {

  protected String name;

  public ConstantIdentifier(String nm) {
    name = nm;
  }

  @Override
  public ConstantIdentifier clone() {
    return new ConstantIdentifier(name);
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean isGlobal() {
    return false;
  }

  @Override
  public int getDereference() {
    return 0;
  }


}
