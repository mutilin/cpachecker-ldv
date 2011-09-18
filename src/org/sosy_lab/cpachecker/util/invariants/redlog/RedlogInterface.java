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
package org.sosy_lab.cpachecker.util.invariants.redlog;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.ProcessExecutor;
import org.sosy_lab.cpachecker.cfa.CParser;
import org.sosy_lab.cpachecker.cfa.ast.IASTNode;

public class RedlogInterface {

  private final CParser parser;
  private final LogManager logger;

  // TODO: Repair the TreeReader class, and then reactivate the
  // diagnostic output features of this class, which it supports.
  //private boolean verbose = false;

  public RedlogInterface(LogManager pLogger) {
    logger = pLogger;
    parser = CParser.Factory.getParser(logger, CParser.Factory.getDefaultOptions());
  }

  /**
  public void setVerbose(boolean v) {
    verbose = v;
  }
  */

  public EliminationAnswer rlqea(String phi) {
    // Apply Redlog's rlqea function to the formula phi.
    // Phi should already have quantifiers in it; we won't add
    // them.
    // Redlog's output is first preprocessed by the rlwrapper.py
    // script. Then we pass the results to our build method, which
    // creates the EliminationAnswer object that we return.
    List<String> output = null;
    String wrapper_path = "src/org/sosy_lab/cpachecker/util/invariants/redlog/rlwrapper.py";
    try {
      ProcessExecutor<RuntimeException> redlog =
        new ProcessExecutor<RuntimeException>(logger,RuntimeException.class,wrapper_path);
      redlog.println(phi);
      redlog.sendEOF();
      redlog.join();
      output = redlog.getOutput();
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
    logger.log(Level.ALL,"Redlog output:\n",output);
    EliminationAnswer EA = build(output);
    return EA;
  }

  public EliminationAnswer build(List<String> lines) {

    Iterator<String> L = lines.iterator();
    String line = null;
    EliminationAnswer EA = null;
    EAPair EAP = null;
    Condition C = null;
    Solution S = null;
    Equation E = null;

    String formula = "";

    while (L.hasNext()) {
      line = L.next();
      if (line.equals("~begin-list")) {
        EA = new EliminationAnswer();
      } else if (line.equals("~begin-pair")) {
        EAP = new EAPair();
      } else if (line.equals("~begin-cond")) {
        C = new Condition();
      } else if (line.equals("~end-cond")) {
        C.setFormula(formula);
        formula = "";
        EAP.setCondition(C);
      } else if (line.equals("~begin-soln")) {
        S = new Solution();
      } else if (line.equals("~begin-eq")) {
        E = new Equation();
      } else if (line.equals("~end-eq")) {
        E.setFormula(formula);
        IASTNode tree = parse(formula);
        E.setTree(tree);
        /**
        if (verbose) {
          //System.out.println(tree.getClass().getName());
          TreeReader.verbose(this.verbose);
          System.out.println(TreeReader.read(tree));
        }
        */
        formula = "";
        S.addEquation(E);
      } else if (line.equals("~end-soln")) {
        EAP.setSolution(S);
      } else if (line.equals("~end-pair")) {
        EA.addPair(EAP);
      } else if (line.equals("~end-list")) {
        break;
      } else {
        formula += line;
      }
    }

    return EA;
  }

  private IASTNode parse(String f) {
    IASTNode root = null;
    try {
      // The statement must be wrapped inside a function
      // declaration. This however gets stripped away.
      f = "void foo() { "+f+" }";
      root = parser.parseSingleStatement(f);
    } catch (Exception e) {
      logger.log(Level.FINEST, "Parser failed to parse Redlog output formula.", e.getMessage());
    }
    return root;
  }

  public IASTNode parseFormula(String f) {
    return parse(f);
  }


}
