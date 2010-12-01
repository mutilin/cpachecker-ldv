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
package org.sosy_lab.cpachecker.cpa.automaton;

import java.util.logging.Level;
import java.util.regex.Pattern;

import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFALabelNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableElement;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;

/**
 * Implements a boolean expression that evaluates and returns a <code>MaybeBoolean</code> value when <code>eval()</code> is called.
 * The Expression can be evaluated multiple times.
 * @author rhein
 */
interface AutomatonBoolExpr extends AutomatonExpression {
  static final ResultValue<Boolean> CONST_TRUE = new ResultValue<Boolean>(Boolean.TRUE);
  static final ResultValue<Boolean> CONST_FALSE = new ResultValue<Boolean>(Boolean.FALSE);

  @Override
  abstract ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs);

  public class MatchProgramExit implements AutomatonBoolExpr {
  
    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      if (pArgs.getCfaEdge().getSuccessor().getNumLeavingEdges() == 0) {
        return CONST_TRUE;
      } else {
        return CONST_FALSE;
      }
    }
  
  }

  /**
   * Implements a regex match on the label after the current CFAEdge.
   * The eval method returns false if there is no label following the CFAEdge.
   * (".*" in java-regex means "any characters")
   * @author rhein
   */
  static class MatchLabelRegEx implements AutomatonBoolExpr {

    private final Pattern pattern;

    public MatchLabelRegEx(String pPattern) {
      pattern = Pattern.compile(pPattern);
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      CFANode successorNode = pArgs.getCfaEdge().getSuccessor();
      if (successorNode instanceof CFALabelNode) {
        String label = ((CFALabelNode)successorNode).getLabel();
        if (pattern.matcher(label).matches()) {
          return CONST_TRUE;
        } else {
          return CONST_FALSE;
        }
      } else {
        return CONST_FALSE;
        //return new ResultValue<Boolean>("cannot evaluate if the CFAEdge is not a CFALabelNode", "MatchLabelRegEx.eval(..)");
      }
    }

    @Override
    public String toString() {
      return "MATCH LABEL [" + pattern + "]";
    }
  }


  /**
   * This is a efficient implementation of the ASTComparison (it caches the generated ASTs for the pattern).
   * It also displays error messages if the AST contains problems/errors.
   * The AST Comparison evaluates the pattern (coming from the Automaton Definition) and the C-Statement on the CFA Edge to ASTs and compares these with a Tree comparison algorithm.
   * @author rhein
   */
  static class MatchCFAEdgeASTComparison implements AutomatonBoolExpr {

    private final IASTNode patternAST;

    public MatchCFAEdgeASTComparison(String pPattern) throws InvalidAutomatonException {
      this.patternAST = AutomatonASTComparator.generatePatternAST(pPattern);
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      IASTNode ast = pArgs.getCfaEdge().getRawAST();
      //AutomatonASTComparator.printAST(ast);
      //AutomatonASTComparator.printAST(patternAST);
      if (ast != null) {
        // some edges do not have an AST node attached to them, e.g. BlankEdges
        if(AutomatonASTComparator.compareASTs(ast, patternAST, pArgs)) {
          return CONST_TRUE;
        } else {
          return CONST_FALSE;
        }
      }
      return CONST_FALSE;
    }

    @Override
    public String toString() {
      return "MATCH {" + patternAST.getRawSignature() + "}";
    }
  }


  static class MatchCFAEdgeRegEx implements AutomatonBoolExpr {

    private final Pattern pattern;

    public MatchCFAEdgeRegEx(String pPattern) {
      pattern = Pattern.compile(pPattern);
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      if (pattern.matcher(pArgs.getCfaEdge().getRawStatement()).matches()) {
        return CONST_TRUE;
      } else {
        return CONST_FALSE;
      }
    }

    @Override
    public String toString() {
      return "MATCH [" + pattern + "]";
    }
  }


  static class MatchCFAEdgeExact implements AutomatonBoolExpr {

    private final String pattern;

    public MatchCFAEdgeExact(String pPattern) {
      pattern = pPattern;
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      if (pArgs.getCfaEdge().getRawStatement().equals(pattern)) {
        return CONST_TRUE;
      } else {
        return CONST_FALSE;
      }
    }

    @Override
    public String toString() {
      return "MATCH \"" + pattern + "\"";
    }
  }

  /**
   * Sends a query string to all available AbstractElements.
   * Returns TRUE if one Element returned TRUE;
   * Returns FALSE if all Elements returned either FALSE or an InvalidQueryException.
   * Returns MAYBE if no Element is available or the Variables could not be replaced.
   * @author rhein
   */
  public static class ALLCPAQuery implements AutomatonBoolExpr {
    private final String queryString;
    
    public ALLCPAQuery(String pString) {
      queryString = pString;
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      if (pArgs.getAbstractElements().isEmpty()) {
        return new ResultValue<Boolean>("No CPA elements available", "AutomatonBoolExpr.ALLCPAQuery");
      } else {
        // replace transition variables
        String modifiedQueryString = pArgs.replaceVariables(queryString);
        if (modifiedQueryString == null) {
          return new ResultValue<Boolean>("Failed to modify queryString \"" + queryString + "\"", "AutomatonBoolExpr.ALLCPAQuery");
        }
        for (AbstractElement ae : pArgs.getAbstractElements()) {
          if (ae instanceof AbstractQueryableElement) {
            AbstractQueryableElement aqe = (AbstractQueryableElement) ae;
            try {
              Object result = aqe.evaluateProperty(modifiedQueryString);
              if (result instanceof Boolean) {
                if (((Boolean)result).booleanValue()) {
                  String message = "CPA-Check succeeded: ModifiedCheckString: \"" + 
                  modifiedQueryString + "\" CPAElement: (" + aqe.getCPAName() + ") \"" +
                  aqe.toString() + "\"";
                  pArgs.getLogger().log(Level.FINER, message);
                  return CONST_TRUE;
                }
              }
            } catch (InvalidQueryException e) {
              // do nothing;
            }
          }
        }
        return CONST_FALSE;
      }
    }
  }
  /**
   * Sends a query-String to an <code>AbstractElement</code> of another analysis and returns the query-Result.
   * @author rhein
   */
  static class CPAQuery implements AutomatonBoolExpr {
    private final String cpaName;
    private final String queryString;

    public CPAQuery(String pCPAName, String pQuery) {
      cpaName = pCPAName;
      queryString = pQuery;
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      // replace transition variables
      String modifiedQueryString = pArgs.replaceVariables(queryString);
      if (modifiedQueryString == null) {
        return new ResultValue<Boolean>("Failed to modify queryString \"" + queryString + "\"", "AutomatonBoolExpr.CPAQuery");
      }

      for (AbstractElement ae : pArgs.getAbstractElements()) {
        if (ae instanceof AbstractQueryableElement) {
          AbstractQueryableElement aqe = (AbstractQueryableElement) ae;
          if (aqe.getCPAName().equals(cpaName)) {
            try {
              Object result = aqe.evaluateProperty(modifiedQueryString);
              if (result instanceof Boolean) {
                if (((Boolean)result).booleanValue()) {
                  String message = "CPA-Check succeeded: ModifiedCheckString: \"" + 
                  modifiedQueryString + "\" CPAElement: (" + aqe.getCPAName() + ") \"" +
                  aqe.toString() + "\"";
                  pArgs.getLogger().log(Level.FINER, message);
                  return CONST_TRUE;
                } else {
                  String message = "CPA-Check failed: ModifiedCheckString: \"" + 
                  modifiedQueryString + "\" CPAElement: (" + aqe.getCPAName() + ") \"" +
                  aqe.toString() + "\"";
                  pArgs.getLogger().log(Level.FINER, message);
                  return CONST_FALSE;
                }
              } else {
                pArgs.getLogger().log(Level.WARNING,
                    "Automaton got a non-Boolean value during Query of the "
                    + cpaName + " CPA on Edge " + pArgs.getCfaEdge().getRawStatement() + 
                    ". Assuming FALSE.");
                return CONST_FALSE;
              }
            } catch (InvalidQueryException e) {
              pArgs.getLogger().logException(Level.WARNING, e,
                  "Automaton encountered an Exception during Query of the "
                  + cpaName + " CPA on Edge " + pArgs.getCfaEdge().getRawStatement());
              return CONST_FALSE;
            }
          }
        }
      }
      return new ResultValue<Boolean>("No State of CPA \"" + cpaName + "\" was found!", "AutomatonBoolExpr.CPAQuery");
    }
    
    @Override
    public String toString() {
      return "CHECK(" + cpaName + "(\"" + queryString + "\"))";
    }
  }
  
  /** Constant for true.
   * @author rhein
   */
  static AutomatonBoolExpr TRUE = new AutomatonBoolExpr() {
    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      return CONST_TRUE;
    }

    @Override
    public String toString() {
      return "TRUE";
    }
  };

  /** Constant for false.
   * @author rhein
   */
  static AutomatonBoolExpr FALSE = new AutomatonBoolExpr() {
    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      return CONST_FALSE;
    }

    @Override
    public String toString() {
      return "FALSE";
    }
  };


  /** Tests the equality of the values of two instances of {@link AutomatonIntExpr}.
   * @author rhein
   */
  static class IntEqTest implements AutomatonBoolExpr {

    private final AutomatonIntExpr a;
    private final AutomatonIntExpr b;

    public IntEqTest(AutomatonIntExpr pA, AutomatonIntExpr pB) {
      this.a = pA;
      this.b = pB;
    }
    
    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      ResultValue<Integer> resA = a.eval(pArgs);
      ResultValue<Integer> resB = b.eval(pArgs);
      if (resA.canNotEvaluate()) return new ResultValue<Boolean>(resA);
      if (resB.canNotEvaluate()) return new ResultValue<Boolean>(resB);
      if (resA.getValue().equals(resB.getValue())) {
        return CONST_TRUE;
      } else {
        return CONST_FALSE;
      }
    }

    @Override
    public String toString() {
      return a + " == " + b;
    }
  }


  /** Tests whether two instances of {@link AutomatonIntExpr} evaluate to different integers.
   * @author rhein
   */
  static class IntNotEqTest implements AutomatonBoolExpr {

    private final AutomatonIntExpr a;
    private final AutomatonIntExpr b;

    public IntNotEqTest(AutomatonIntExpr pA, AutomatonIntExpr pB) {
      this.a = pA;
      this.b = pB;
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      ResultValue<Integer> resA = a.eval(pArgs);
      ResultValue<Integer> resB = b.eval(pArgs);
      if (resA.canNotEvaluate()) return new ResultValue<Boolean>(resA);
      if (resB.canNotEvaluate()) return new ResultValue<Boolean>(resB);
      if (! resA.getValue().equals(resB.getValue())) {
        return CONST_TRUE;
      } else {
        return CONST_FALSE;
      }
    }

    @Override
    public String toString() {
      return a + " != " + b;
    }
  }


  /** Computes the disjunction of two {@link AutomatonBoolExpr} (lazy evaluation).
   * @author rhein
   */
  static class Or implements AutomatonBoolExpr {

    private final AutomatonBoolExpr a;
    private final AutomatonBoolExpr b;

    public Or(AutomatonBoolExpr pA, AutomatonBoolExpr pB) {
      this.a = pA;
      this.b = pB;
    }

    public @Override ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      /* OR:
       * True  || _ -> True
       * _ || True -> True
       * false || false -> false
       * every other combination returns the result that can not evaluate
       */
      ResultValue<Boolean> resA = a.eval(pArgs);
      if (resA.canNotEvaluate()) {
        ResultValue<Boolean> resB = b.eval(pArgs);
        if ((!resB.canNotEvaluate()) && resB.getValue().equals(Boolean.TRUE)) {
          return resB;
        } else {
          return resA;
        }
      } else {
        if (resA.getValue().equals(Boolean.TRUE)) {
          return resA;
        } else {
          ResultValue<Boolean> resB = b.eval(pArgs);
          if (resB.canNotEvaluate()) return resB;
          if (resB.getValue().equals(Boolean.TRUE))
            return resB;
          else
            return resA;
        }
      }
    }

    @Override
    public String toString() {
      return "(" + a + " || " + b + ")";
    }
  }


  /** Computes the conjunction of two {@link AutomatonBoolExpr} (lazy evaluation).
   * @author rhein
   */
  static class And implements AutomatonBoolExpr {

    private final AutomatonBoolExpr a;
    private final AutomatonBoolExpr b;

    public And(AutomatonBoolExpr pA, AutomatonBoolExpr pB) {
      this.a = pA;
      this.b = pB;
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      /* AND:
       * false && _ -> false
       * _ && false -> false
       * true && true -> true
       * every other combination returns the result that can not evaluate
       */
      ResultValue<Boolean> resA = a.eval(pArgs);
      if (resA.canNotEvaluate()) {
        ResultValue<Boolean> resB = b.eval(pArgs);
        if ((! resB.canNotEvaluate()) && resB.getValue().equals(Boolean.FALSE)) {
          return resB;
        } else {
          return resA;
        }
      } else {
        if (resA.getValue().equals(Boolean.FALSE)) {
          return resA;
        } else {
          ResultValue<Boolean> resB = b.eval(pArgs);
          if (resB.canNotEvaluate()) return resB;
          if (resB.getValue().equals(Boolean.FALSE))
            return resB;
          else
            return resA;
        }
      }
    }

    @Override
    public String toString() {
      return "(" + a + " && " + b + ")";
    }
  }


  /**
   * Negates the result of a {@link AutomatonBoolExpr}. If the result is MAYBE it is returned unchanged.
   * @author rhein
   */
  static class Negation implements AutomatonBoolExpr {

    private final AutomatonBoolExpr a;

    public Negation(AutomatonBoolExpr pA) {
      this.a = pA;
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      ResultValue<Boolean> resA = a.eval(pArgs);
      if (resA.canNotEvaluate()) return resA;
      if (resA.getValue().equals(Boolean.TRUE)) {
        return CONST_FALSE;
      } else {
        return CONST_TRUE;
      }
    }

    @Override
    public String toString() {
      return "!" + a;
    }
  }


  /**
   * Boolean Equality
   * @author rhein
   */
  static class BoolEqTest implements AutomatonBoolExpr {

    private final AutomatonBoolExpr a;
    private final AutomatonBoolExpr b;

    public BoolEqTest(AutomatonBoolExpr pA, AutomatonBoolExpr pB) {
      this.a = pA;
      this.b = pB;
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      ResultValue<Boolean> resA = a.eval(pArgs);
      if (resA.canNotEvaluate()) 
        return resA;
      ResultValue<Boolean> resB = b.eval(pArgs);
      if (resB.canNotEvaluate()) 
        return resB;
      if (resA.getValue().equals(resB.getValue())) {
        return CONST_TRUE;
      } else {
        return CONST_FALSE;
      }
    }

    @Override
    public String toString() {
      return a + " == " + b;
    }
  }


  /**
   * Boolean !=
   * @author rhein
   */
  static class BoolNotEqTest implements AutomatonBoolExpr {

    private final AutomatonBoolExpr a;
    private final AutomatonBoolExpr b;

    public BoolNotEqTest(AutomatonBoolExpr pA, AutomatonBoolExpr pB) {
      this.a = pA;
      this.b = pB;
    }

    @Override
    public ResultValue<Boolean> eval(AutomatonExpressionArguments pArgs) {
      ResultValue<Boolean> resA = a.eval(pArgs);
      if (resA.canNotEvaluate()) 
        return resA;
      ResultValue<Boolean> resB = b.eval(pArgs);
      if (resB.canNotEvaluate()) 
        return resB;
      if (! resA.getValue().equals(resB.getValue())) {
        return CONST_TRUE;
      } else {
        return CONST_FALSE;
      }
    }

    @Override
    public String toString() {
      return a + " != " + b;
    }
  }
}
