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
package org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.ForwardingCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCCodeException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;

class RightHandSideToFormulaVisitor extends ForwardingCExpressionVisitor<Formula, UnrecognizedCCodeException>
                                     implements CRightHandSideVisitor<Formula, UnrecognizedCCodeException> {

  protected final CtoFormulaConverter conv;
  protected final CFAEdge       edge;
  protected final String        function;
  protected final SSAMapBuilder ssa;
  protected final Constraints   constraints;

  public RightHandSideToFormulaVisitor(ExpressionToFormulaVisitor pDelegate) {
    super(pDelegate);
    conv = pDelegate.conv;
    edge = pDelegate.edge;
    function = pDelegate.function;
    ssa = pDelegate.ssa;
    constraints = pDelegate.constraints;
  }

  @Override
  public Formula visit(CFunctionCallExpression fexp) throws UnrecognizedCCodeException {

    CExpression fn = fexp.getFunctionNameExpression();
    List<CExpression> pexps = fexp.getParameterExpressions();
    String func;
    CType expType = fexp.getExpressionType();
    if (fn instanceof CIdExpression) {
      func = ((CIdExpression)fn).getName();
      if (func.equals(CtoFormulaConverter.ASSUME_FUNCTION_NAME) && pexps.size() == 1) {
        BooleanFormula condition = conv.toBooleanFormula(pexps.get(0).accept(this));
        constraints.addConstraint(condition);

        return conv.makeFreshVariable(func, expType, ssa);

      } else if (conv.nondetFunctions.contains(func)
          || conv.nondetFunctionsPattern.matcher(func).matches()) {
        // function call like "random()"
        // ignore parameters and just create a fresh variable for it
        return conv.makeFreshVariable(func, expType, ssa);

      } else if (conv.externModelFunctionName.equals(func)){
        assert (pexps.size()>0): "No external model given!";
        // the parameter comes in C syntax (with ")
        String filename = pexps.get(0).toASTString().replaceAll("\"", "");
        File modelFile = new File(filename);
        BooleanFormula externalModel = loadExternalFormula(modelFile);
        FormulaType<?> returnFormulaType = conv.getFormulaTypeFromCType(fexp.getExpressionType());
        Formula f = conv.bfmgr.ifThenElse(externalModel,
            conv.fmgr.makeNumber(returnFormulaType, 1),
            conv.fmgr.makeNumber(returnFormulaType, 0));
        return f;

      } else if (CtoFormulaConverter.UNSUPPORTED_FUNCTIONS.containsKey(func)) {
        throw new UnsupportedCCodeException(CtoFormulaConverter.UNSUPPORTED_FUNCTIONS.get(func), edge, fexp);

      } else if (!CtoFormulaConverter.PURE_EXTERNAL_FUNCTIONS.contains(func)) {
        if (pexps.isEmpty()) {
          // function of arity 0
          conv.log(Level.INFO, "Assuming external function " + func + " to be a constant function.");
        } else {
          conv.log(Level.INFO, "Assuming external function " + func + " to be a pure function.");
        }
      }
    } else {
      conv.log(Level.WARNING, CtoFormulaConverter.getLogMessage("Ignoring function call through function pointer", fexp));
      func = "<func>{" + CtoFormulaConverter.scoped(CtoFormulaConverter.exprToVarName(fn), function) + "}";
    }

    if (pexps.isEmpty()) {
      // This is a function of arity 0 and we assume its constant.
      return conv.makeConstant(func, expType, ssa);

    } else {
      CFunctionDeclaration declaration = fexp.getDeclaration();
      if (declaration == null) {
        // This should not happen
        conv.log(Level.WARNING, "Cant get declaration of function. Ignoring the call (" + fexp.toASTString() + ").");
        return conv.makeFreshVariable(func, expType, ssa); // BUG when expType = void
      }

      if (declaration.getType().takesVarArgs()) {
        // Create a fresh variable instead of an UF for varargs functions.
        // This is sound but slightly more imprecise (we loose the UF axioms).
        return conv.makeFreshVariable(func, expType, ssa);
      }

      List<CType> paramTypes = declaration.getType().getParameters();
      func += "{" + paramTypes.size() + "}"; // add #arguments to function name to cope with varargs functions

      if (paramTypes.size() != pexps.size()) {
        throw new UnrecognizedCCodeException("Function " + declaration + " received " + pexps.size() + " parameters instead of the expected " + paramTypes.size(), edge, fexp);
      }

      List<Formula> args = new ArrayList<>(pexps.size());
      Iterator<CType> it1 = paramTypes.iterator();
      Iterator<CExpression> it2 = pexps.iterator();
      while (it1.hasNext() && it2.hasNext()) {

        CType paramType= it1.next();
        CExpression pexp = it2.next();

        Formula arg = pexp.accept(this);
        args.add(conv.makeCast(pexp.getExpressionType(), paramType, arg));
      }
      assert !it1.hasNext() && !it2.hasNext();

      CType returnType = conv.getReturnType(fexp, edge);
      FormulaType<?> t = conv.getFormulaTypeFromCType(returnType);
      return conv.ffmgr.createFuncAndCall(func, t, args);
    }
  }

  /**
   * Loads a formula from an external dimacs file and returns it as BooleanFormula object.
   * Each variable in the dimacs file will be associated with a program variable if a corresponding (name equality) variable is known.
   * Otherwise we use internal SMT variable to represent the dimacs variable and do not introduce a program variable.
   * Might lead to problems when the program variable is introduced afterwards.
   * @param pModelFile File with the dimacs model.
   * @return BooleanFormula
   */
  private BooleanFormula loadExternalFormula(File pModelFile) {
    if (! pModelFile.getName().endsWith(".dimacs")) {
      throw new UnsupportedOperationException("Sorry, we can only load dimacs models.");
    }
    try (BufferedReader br = new BufferedReader(new FileReader(pModelFile))){
       ArrayList<String> predicates = new ArrayList<>(10000);
       //var ids in dimacs files start with 1, so we want the first var at position 1
       predicates.add("RheinDummyVar");
       BooleanFormula externalModel = conv.bfmgr.makeBoolean(true);
       Formula zero = conv.fmgr.makeNumber(FormulaType.BitvectorType.getBitvectorType(32), 0);

       String line = "";
       while ((line = br.readLine()) != null) {
         if (line.startsWith("c ")) {
           // comment line, here the vars are declared
           // c 8 LOGO_SGI_CLUT224_m
           // c 80255$ _X31351_m
           // starting with id1
           String[] parts = line.split(" ");
           int varID = Integer.parseInt(parts[1].replace("$", ""));
           assert predicates.size() == varID : "messed up the dimacs parsing!";
           predicates.add(parts[2]);
         } else if (line.startsWith("p ")) {
           //p cnf 80258 388816
           // 80258 vars
           // 388816 cnf constraints
           String[] parts = line.split(" ");
           // +1 because of the dummy var
           assert predicates.size()==Integer.parseInt(parts[2])+1: "did not get all dimcas variables?";
         } else if (line.trim().length()>0){
           //-17552 -11882 1489 48905 0
           // constraints
           BooleanFormula constraint = conv.bfmgr.makeBoolean(false);
           String[] parts = line.split(" ");
           for (String elementStr : parts) {
             if (!elementStr.equals("0") && !elementStr.isEmpty()) {
               int elem = Integer.parseInt(elementStr);
               String predName = "";
               if (elem > 0)
                 predName = predicates.get(elem);
               else
                 predName = predicates.get(-elem);
               int ssaIndex = ssa.getIndex(predName);
               BooleanFormula constraintPart = null;
               if (ssaIndex != -1) {
                 // this variable was already declared in the program
                 Formula formulaVar = conv.fmgr.makeVariable(conv.getFormulaTypeFromCType(ssa.getType(predName)), predName, ssaIndex);
                 if (elem > 0)
                   constraintPart = conv.fmgr.makeNot(conv.fmgr.makeEqual(formulaVar, zero)); // C semantics (x) <=> (x!=0)
                 else
                   constraintPart = conv.fmgr.makeEqual(formulaVar, zero);
               } else {
                 // var was not declared in the program
                 // get a new SMT-var for it (i have to pass a ssa index, choosing 1)
                 BooleanFormula formulaVar = conv.fmgr.makeVariable(FormulaType.BooleanType, predName, 1);
                 if (elem > 0)
                   constraintPart = formulaVar;
                 else
                   constraintPart = conv.bfmgr.not(formulaVar);
               }
               if (constraint == null)
                 constraint = constraintPart;
               else
                 constraint = conv.bfmgr.or(constraint, constraintPart);
             }
           }
           if (externalModel == null)
             externalModel = constraint;
           else
             externalModel = conv.bfmgr.and(externalModel, constraint);
         }
       }// end of while
      return externalModel;
    } catch (IOException e) {
      throw new RuntimeException(e); //TODO: find the proper exception
    }
  }
}