/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.uninitvars;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Triple;

import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.AbstractWrapperElement;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;

/**
 * @author Gregor Endler
 *
 * Statistics for UninitializedVariablesCPA.
 * Displays warnings about all uninitialized variables found.
 */
public class UninitializedVariablesStatistics implements Statistics {

  private boolean printWarnings;

  public UninitializedVariablesStatistics(String printWarnings) {
    super();
    this.printWarnings = Boolean.parseBoolean(printWarnings);
  }

  @Override
  public String getName() {
    return "UninitializedVariablesCPA";
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {

    int noOfWarnings = 0;
    
    if (printWarnings) {

      Set<Pair<Integer, String>> warningsDisplayed = new HashSet<Pair<Integer, String>>();
      
      //find all UninitializedVariablesElements and get their warnings
      for (AbstractElement reachedElement : pReached) {
        if (reachedElement instanceof AbstractWrapperElement) {
          UninitializedVariablesElement uninitElement =
            ((AbstractWrapperElement)reachedElement).retrieveWrappedElement(UninitializedVariablesElement.class);
          if (uninitElement != null) {
            Collection<Triple<Integer, String, String>> warnings = uninitElement.getWarnings();
            //warnings are identified by line number and variable name
            Pair<Integer, String> warningIndex;
            for(Triple<Integer, String, String> warning : warnings) {
              //check if a warning has already been displayed
              warningIndex = new  Pair<Integer, String>(warning.getFirst(), warning.getSecond());
              if (!warningsDisplayed.contains(warningIndex)) {
                warningsDisplayed.add(warningIndex);
                pOut.println(warning.getThird());
                noOfWarnings++;
              }
            }
          }
        }
      }
      if (warningsDisplayed.isEmpty()) {
        pOut.println("No uninitialized variables found");
      }
      else{
        pOut.println("No of uninitialized vars : " + noOfWarnings);
      }
    } else {
      pOut.println("Output deactivated by configuration option");
    }
  }
}
