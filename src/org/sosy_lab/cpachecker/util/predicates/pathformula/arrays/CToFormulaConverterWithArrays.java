/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.predicates.pathformula.arrays;

import java.util.logging.Level;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.VariableClassification;
import org.sosy_lab.cpachecker.util.predicates.interfaces.ArrayFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.ArrayFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ErrorConditions;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.Constraints;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaConverter;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaTypeHandler;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.FormulaEncodingOptions;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSetBuilder;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;


public class CToFormulaConverterWithArrays extends CtoFormulaConverter {

  protected final ArrayFormulaManagerView afmgr;

  public CToFormulaConverterWithArrays(FormulaEncodingOptions pOptions, FormulaManagerView pFmgr,
      MachineModel pMachineModel, Optional<VariableClassification> pVariableClassification, LogManager pLogger,
      ShutdownNotifier pShutdownNotifier, CtoFormulaTypeHandler pTypeHandler, AnalysisDirection pDirection) {

    super(pOptions, pFmgr, pMachineModel, pVariableClassification, pLogger, pShutdownNotifier, pTypeHandler, pDirection);

    Preconditions.checkNotNull(pFmgr.getArrayFormulaManager());
    this.afmgr = pFmgr.getArrayFormulaManager();
  }

  @Override
  protected CRightHandSideVisitor<Formula, UnrecognizedCCodeException> createCRightHandSideVisitor(CFAEdge pEdge,
      String pFunction, SSAMapBuilder pSsa, PointerTargetSetBuilder pPts, Constraints pConstraints,
      ErrorConditions pErrorConditions) {

    // Create a CRightHandSideVisitor with support for arrays!
    return new ExpressionToFormulaVisitorWithArrays(this, fmgr, machineModel, pEdge, pFunction, pSsa, pConstraints);
  }

  @Override
  protected Formula buildLvalueTerm(CLeftHandSide pExp, CFAEdge pEdge, String pFunction, SSAMapBuilder pSsa,
      PointerTargetSetBuilder pPts, Constraints pConstraints, ErrorConditions pErrorConditions)
      throws UnrecognizedCCodeException {

    return pExp.accept(new LvalueVisitorWithArrays(this, pEdge, pFunction, pSsa, pPts, pConstraints, pErrorConditions));
  }

  @Override
  protected Formula makeCast(CType pFromType, CType pToType, Formula pFormula, Constraints pConstraints, CFAEdge pEdge)
      throws UnrecognizedCCodeException {

// The following code is not needed (feedback from Philipp)
//    CType fromType = pFromType.getCanonicalType();
//    if (fromType instanceof CArrayType) {
//      fromType = ((CArrayType) fromType).getType();
//    }

    return super.makeCast(pFromType, pToType, pFormula, pConstraints, pEdge);
  }

  @Override
  protected Formula makeVariable(String pName, CType pType, SSAMapBuilder pSsa) {
    // Overwritten to make it visible for ExpressionToFormulaVisitorWithArrays
    return super.makeVariable(pName, pType, pSsa);
  }

  @SuppressWarnings("unchecked")
  protected <TI extends Formula, TE extends Formula> ArrayFormula<TI, TE> makeArrayVariable (String pName,
      CType pType, SSAMapBuilder pSsa) {

    int useIndex = getIndex(pName, pType, pSsa);
    return (ArrayFormula<TI, TE>) fmgr.makeVariable(this.getFormulaTypeFromCType(pType), pName, useIndex);
  }

  @SuppressWarnings("unchecked")
  protected <TI extends Formula, TE extends Formula> ArrayFormula<TI, TE> makeFreshArrayVariable(String pName,
      CType pType, SSAMapBuilder pSsa) {

    return (ArrayFormula<TI, TE>) makeFreshVariable(pName, pType, pSsa);
  }

  @Override
  protected boolean isRelevantLeftHandSide(CLeftHandSide pLhs) {
    if (pLhs.getExpressionType().getCanonicalType() instanceof CArrayType) {
      // TODO: isRelevantLeftHandSide is also used to determine
      // whether variables are relevant for specific analysis runs/traces
      //    Returning always true for arrays might have a negative impact on the performance!
      return true;
    }

    return super.isRelevantLeftHandSide(pLhs);
  }

  @Override
  protected BooleanFormula makeAssignment(CLeftHandSide pLhs, CLeftHandSide pLhsForChecking, CRightHandSide pRhs,
      CFAEdge pEdge, String pFunction, SSAMapBuilder pSsa, PointerTargetSetBuilder pPts, Constraints pConstraints,
      ErrorConditions pErrorConditions) throws UnrecognizedCCodeException, InterruptedException {

    CType lhsType = pLhs.getExpressionType().getCanonicalType();
    if (lhsType instanceof CArrayType) {

      // ATTENTION: WE DO NOT SUPPORT MULTI-DIMENSIONAL-ARRAYS AT THE MOMENT!
      if (((CArrayType) lhsType).getType() instanceof CArrayType) {
        logger.logOnce(Level.WARNING, "Result might be unsound. Unsupported multi-dimensional arrays found!");
        return bfmgr.makeBoolean(true);
      }

      Verify.verify(pLhs instanceof CArraySubscriptExpression);
      final CArraySubscriptExpression lhsExpr = (CArraySubscriptExpression) pLhs; // a[e]
      // .getArrayExpression() provides a CIdExpression
      //    with type CArrayType; this would be different for multi-dimensional arrays!!
      Verify.verify(lhsExpr.getArrayExpression() instanceof CIdExpression);
      final CIdExpression lhsArrExpr = (CIdExpression) lhsExpr.getArrayExpression();

      // 1. Get the (array) formula A1 that represents the LHS (destination of 'store').
      //    The SSA index of this formula stays the same.
      final ArrayFormula<Formula, Formula> unchangedArrayFormula = makeArrayVariable( //Overwrite makeVariable so that it uses a cache of formulas??
          lhsArrExpr.getDeclaration().getQualifiedName(),
          lhsArrExpr.getExpressionType(),
          pSsa);

      // 2. Get the target index IX. Use a RHS visitor on the subscript expression.
      final Formula subscriptFormula = buildTerm(lhsExpr.getSubscriptExpression(), pEdge,
          pFunction, pSsa, pPts, pConstraints, pErrorConditions);

      // 3. Create the formula FR for the RHS (visitor).
      final Formula rhsFormula = buildTerm(pRhs, pEdge,
          pFunction, pSsa, pPts, pConstraints, pErrorConditions);

      // 4. Compute a new array formula FS using 'store' (this variable has not yet a name in the solver)
      //    (store A1 IX FR)
      final ArrayFormula<Formula, Formula> storeArrayFormula = afmgr.store(unchangedArrayFormula, subscriptFormula, rhsFormula);

      // 5. Make a new array variable A2 with a new SSA index. Set this formula equivalent to FS.
      //    (= A2 (store A1 IX FR))
      final ArrayFormula<Formula, Formula> changedArrayFormula = makeFreshArrayVariable(
          lhsArrExpr.getDeclaration().getQualifiedName(),
          lhsArrExpr.getExpressionType(),
          pSsa);

      return afmgr.equivalence(changedArrayFormula, storeArrayFormula);
    } else {
      return super.makeAssignment(pLhs, pLhsForChecking, pRhs, pEdge, pFunction, pSsa, pPts, pConstraints, pErrorConditions);
    }
  }
}
