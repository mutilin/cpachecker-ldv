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
package org.sosy_lab.cpachecker.cpa.usageStatistics;


/**
 * Interface for classes, which will generate input files for Lcov
 */
public interface CodeCovering {

  /**
   * Add directive FN, i.e. function
   * @param line - line of declaration function
   * @param functionName - name of function
   * @throws CPAException
   */
  //public void addFunction(int line, String functionName) throws CPATransferException;

  /**
   * Increase counter of usage function @param functionName
   */
  public void addFunctionUsage(String functionName);

  /**
   * Increase counter of usage @param line
   */
  //public void addLine(int line);


  /**
   * Marks line, as exception. Needs to show it white color, f.e. declarations
   * @param line
   */
  public void addException(int line);

  /**
   * Generate file and finish work
   */
  public void generate();
}
