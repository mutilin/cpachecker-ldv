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
package org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula;

import static org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.types.CtoFormulaTypeUtils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Triple;
import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.cpachecker.cfa.ast.IAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializers;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CComplexType.ComplexTypeKind;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType.CCompositeTypeMemberDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CElaboratedType;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType;
import org.sosy_lab.cpachecker.cfa.types.c.CFunctionType;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CTypedefType;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BitvectorFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FunctionFormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.BitvectorFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FunctionFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.RationalFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ErrorConditions;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.Variable;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.types.CFieldTrackType;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.types.CtoFormulaTypeUtils.CtoFormulaSizeofVisitor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Class containing all the code that converts C code into a formula.
 */
public class CtoFormulaConverter {

  static final String ASSUME_FUNCTION_NAME = "__VERIFIER_assume";

  // list of functions that are pure (no side-effects)
  static final Set<String> PURE_EXTERNAL_FUNCTIONS
      = ImmutableSet.of("__assert_fail", "free", "kfree",
          "fprintf", "printf", "puts", "printk", "sprintf", "swprintf",
          "strcasecmp", "strchr", "strcmp", "strlen", "strncmp", "strrchr", "strstr"
          );

  // set of functions that may not appear in the source code
  // the value of the map entry is the explanation for the user
  static final Map<String, String> UNSUPPORTED_FUNCTIONS
      = ImmutableMap.of("pthread_create", "threads");

  //names for special variables needed to deal with functions
  private static final String VAR_RETURN_NAME = "__retval__";

  private static final String EXPAND_VARIABLE = "__expandVariable__";
  private int expands = 0;

  private static final String FIELD_VARIABLE = "__field_of__";

  private static final Set<String> SAFE_VAR_ARG_FUNCTIONS = ImmutableSet.of(
      "printf", "printk"
      );

  private final Map<String, BitvectorFormula> stringLitToFormula = new HashMap<>();
  private int nextStringLitIndex = 0;

  private final Map<CType, FormulaType<?>> typeCache = new IdentityHashMap<>();

  final FormulaEncodingOptions options;
  final MachineModel machineModel;
  private final CtoFormulaSizeofVisitor sizeofVisitor;

  final FormulaManagerView fmgr;
  final BooleanFormulaManagerView bfmgr;
  private final RationalFormulaManagerView nfmgr;
  final BitvectorFormulaManagerView efmgr;
  final FunctionFormulaManagerView ffmgr;
  final LogManagerWithoutDuplicates logger;

  static final int                 VARIABLE_UNSET          = -1;
  static final int                 VARIABLE_UNINITIALIZED  = 2;

  private final FunctionFormulaType<BitvectorFormula> stringUfDecl;

  public CtoFormulaConverter(FormulaEncodingOptions pOptions, FormulaManagerView fmgr,
      MachineModel pMachineModel, LogManager logger) {

    this.fmgr = fmgr;
    this.options = pOptions;
    this.machineModel = pMachineModel;
    this.sizeofVisitor = new CtoFormulaSizeofVisitor(pMachineModel);

    this.bfmgr = fmgr.getBooleanFormulaManager();
    this.nfmgr = fmgr.getRationalFormulaManager();
    this.efmgr = fmgr.getBitvectorFormulaManager();
    this.ffmgr = fmgr.getFunctionFormulaManager();
    this.logger = new LogManagerWithoutDuplicates(logger);

    FormulaType<BitvectorFormula> pointerType =
        efmgr.getFormulaType(machineModel.getSizeofPtr() * machineModel.getSizeofCharInBits());
    stringUfDecl = ffmgr.createFunction(
            "__string__", pointerType, FormulaType.RationalType);
  }

  void logfOnce(Level level, CFAEdge edge, String msg, Object... args) {
    if (logger.wouldBeLogged(level)) {
      logger.logfOnce(level, "Line %d: %s: %s",
          edge.getLineNumber(),
          String.format(msg, args),
          edge.getDescription());
    }
  }

  void logfOnce(Level level, CAstNode astNode, String msg, Object... args) {
    if (logger.wouldBeLogged(level)) {
      logger.logfOnce(level, "Line %d: %s: %s",
          astNode.getFileLocation().getStartingLineNumber(),
          String.format(msg, args),
          astNode.toASTString());
    }
  }

  /**
   * Returns the size in bytes of the given type.
   * Always use this method instead of machineModel.getSizeOf,
   * because this method can handle dereference-types.
   * @param pType the type to calculate the size of.
   * @return the size in bytes of the given type.
   */
  int getSizeof(CType pType) {
    int size = pType.accept(sizeofVisitor);
    if (size == 0) {
      CType type = getCanonicalType(pType);
      if (type instanceof CArrayType) {
        // C11 §6.7.6.2 (1)
        logger.logOnce(Level.WARNING, "Type", pType, "is a zero-length array, this is undefined.");
      } else if (type instanceof CCompositeType) {
        // UNDEFINED: http://stackoverflow.com/questions/1626446/what-is-the-size-of-an-empty-struct-in-c
        logger.logOnce(Level.WARNING, "Type", pType, "has no fields, this is undefined.");
      } else {
        logger.logOnce(Level.WARNING, "Type", pType, "has size 0 bytes.");
      }
    }
    return size;
  }

  Variable scopedIfNecessary(CIdExpression var, SSAMapBuilder ssa, String function) {
    return Variable.create(var.getDeclaration().getQualifiedName(), var.getExpressionType());
  }

  Variable makeFieldVariable(Variable pName, CFieldReference fExp, SSAMapBuilder ssa) throws UnrecognizedCCodeException {
    Pair<Integer, Integer> msb_lsb = getFieldOffsetMsbLsb(fExp);
    String fieldVarName = makeFieldVariableName(pName.getName(), msb_lsb, ssa);

    // For explanation of the following types,
    // c.f. CFieldTrackType
    CType fieldType = fExp.getExpressionType();
    CType structType = getRealFieldOwner(fExp).getExpressionType();
    structType = structType.getCanonicalType();
    if (!(structType instanceof CCompositeType)) {
      throw new UnrecognizedCCodeException("Accessing field in non-compsite type", fExp);
    }

    // NOTE: ALWAYS use pName.getType() as the actual type,
    // because pName.getType() could be an instance of CFieldTrackType
    CType ownerTypeWithoutCasts = pName.getType();

    if (!getCanonicalType(ownerTypeWithoutCasts).equals(structType)) {
      logger.logOnce(Level.WARNING, "Cast of non-matching type", ownerTypeWithoutCasts, "to",
          structType, "in field access", fExp, "so analysis could be imprecise");
    }

    return Variable.create(fieldVarName,
        new CFieldTrackType(fieldType, ownerTypeWithoutCasts, (CCompositeType)structType));
  }

  public FormulaType<?> getFormulaTypeFromCType(CType type) {
    FormulaType<?> result = typeCache.get(type);
    if (result == null) {
      int byteSize = getSizeof(type);

      int bitsPerByte = machineModel.getSizeofCharInBits();
      // byte to bits
      result = efmgr.getFormulaType(byteSize * bitsPerByte);
      typeCache.put(type, result);
    }
    return result;
  }

  static boolean hasRepresentableDereference(Variable v) {
    return isRepresentableType(dereferencedType(v.getType()));
  }

  static boolean hasRepresentableDereference(CExpression e) {
    return isRepresentableType(dereferencedType(e.getExpressionType()));
  }

  /** prefixes function to variable name
  * Call only if you are sure you have a local variable!
  */
  static String scoped(String var, String function) {
    return (function + "::" + var).intern();
  }

  /**
   * Create a variable name that is used to store the return value of a function
   * temporarily (between the return statement and the re-entrance in the caller function).
   */
  static String getReturnVarName(String function) {
    return scoped(VAR_RETURN_NAME, function);
  }

  /**
   * This method eleminates all spaces from an expression's ASTString and returns
   * the new String.
   *
   * @param e the expression which should be named
   * @return the name of the expression
   */
  static String exprToVarName(IAstNode e) {
    return e.toASTString().replaceAll("[ \n\t]", "");
  }

  static String getTypeName(final CType tp) {

    if (tp instanceof CPointerType) {
      return getTypeName(((CPointerType)tp).getType());

    } else if (tp instanceof CTypedefType) {
      return getTypeName(((CTypedefType)tp).getRealType());

    } else if (tp instanceof CCompositeType) {
      CCompositeType compositeType = ((CCompositeType)tp);
      return compositeType.getKind().toASTString() + " " + compositeType.getName();

    } else if (tp instanceof CSimpleType) {
      return tp.toASTString("");

    } else {
      throw new AssertionError("Unknown type " + tp.getClass().getName());
    }
  }

  /**
   * Produces a fresh new SSA index for an assignment
   * and updates the SSA map.
   */
  int makeFreshIndex(String name, CType type, SSAMapBuilder ssa) {
    return getIndex(name, type, ssa, true);
  }

  /**
   * This method returns the index of the given variable in the ssa map, if there
   * is none, it creates one with the value 1.
   *
   * @return the index of the variable
   */
  int getIndex(String name, CType type, SSAMapBuilder ssa) {
    return getIndex(name, type, ssa, false);
  }

  private int getIndex(String name, CType type, SSAMapBuilder ssa, boolean makeFresh) {
    int idx = ssa.getIndex(name);
    if (makeFresh) {
      if (idx > 0) {
        idx = idx+1;
      } else {
        idx = VARIABLE_UNINITIALIZED; // AG - IMPORTANT!!! We must start from 2 and
        // not from 1, because this is an assignment,
        // so the SSA index must be fresh.
      }
      setSsaIndex(ssa, name, type, idx);
    } else {
      if (idx <= 0) {
        logger.log(Level.ALL, "WARNING: Auto-instantiating variable:", name);
        idx = 1;
        setSsaIndex(ssa, name, type, idx);
      } else {
        checkSsaSavedType(name, type, ssa);
      }
    }

    return idx;
  }

  private void checkSsaSavedType(String name, CType type, SSAMapBuilder ssa) {

    // Check if types match

    // Assert when a variable already exists, that it has the same type
    // TODO: Un-comment when parser and code-base is stable enough
//    Variable t;
//    assert
//         (t = ssa.getType(name)) == null
//      || CTypeUtils.equals(t, type)
//      : "Saving variables with mutliple types is not possible!";
    CType t = ssa.getType(name);
    if (t != null && !areEqualWithMatchingPointerArray(t, type)) {

      if (getFormulaTypeFromCType(t) != getFormulaTypeFromCType(type)) {
        throw new UnsupportedOperationException(
            "Variable " + name + " used with types of different sizes! " +
                "(Type1: " + t + ", Type2: " + type + ")");
      } else {
        logger.logf(Level.FINEST, "Variable %s was found with multiple types!"
                + " (Type1: %s, Type2: %s)", name, t, type);
      }
    }
  }

  private void setSsaIndex(SSAMapBuilder ssa, String name, CType type, int idx) {
    if (isDereferenceType(type)) {
      CType guess = getGuessedType(type);
      if (guess == null) {
        // This should not happen when guessing aliasing types would always work
        logger.logOnce(Level.FINE, "No Type-Guess for variable", name, "of type", type);
        CType oneByte = CNumericTypes.CHAR;
        type = setGuessedType(type, oneByte);
      }
    }

    assert
      !isDereferenceType(type) ||
      getGuessedType(type) != null
      : "The guess should be resolved now!";

    checkSsaSavedType(name, type, ssa);

    ssa.setIndex(name, type, idx);
  }

  /**
   * Create a formula for a given variable, which is assumed to be constant.
   * This method does not handle scoping!
   */
  Formula makeConstant(String name, CType type, SSAMapBuilder ssa) {
    // TODO better use variables without index (this piece of code prevents
    // SSAMapBuilder from checking for strict monotony)
    int idx = ssa.getIndex(name);
    assert idx <= 1 : name + " is assumed to be constant there was an assignment to it";
    if (idx != 1) {
      setSsaIndex(ssa, name, type, 1); // set index so that predicates will be instantiated correctly
    }

    return fmgr.makeVariable(this.getFormulaTypeFromCType(type), name, 1);
  }
  Formula makeConstant(Variable var, SSAMapBuilder ssa) {
    return makeConstant(var.getName(), var.getType(), ssa);
  }

  /**
   * Create a formula for a given variable with a fresh index for the left-hand if needed
   * side of an assignment.
   * This method does not handle scoping and the NON_DET_VARIABLE!
   */
  private Formula resolveFields(String name, CType type, SSAMapBuilder ssa, boolean makeFreshIndex) {
    // Resolve Fields

    if (!isFieldVariable(name)) {
      int idx = getIndex(name, type, ssa, makeFreshIndex);

      assert !isFieldVariable(name)
        : "Never make variables for field! Always use the underlaying bitvector! Fieldvariable-Names are only used as intermediate step!";
      return fmgr.makeVariable(this.getFormulaTypeFromCType(type), name, idx);
    }

    assert options.handleFieldAccess() : "Field Variables are only allowed with handleFieldAccess";

    Pair<String, Pair<Integer, Integer>> data = removeFieldVariable(name);
    String structName = data.getFirst();
    Pair<Integer, Integer> msb_lsb = data.getSecond();
    // With this we are able to track the types properly
    assert type instanceof CFieldTrackType
     : "Was not able to track types of Field-references";

    CFieldTrackType trackType = (CFieldTrackType)type;
    CType ownerType = trackType.getOwnerTypeWithoutCasts();
    Formula struct = resolveFields(structName, ownerType, ssa, makeFreshIndex);

    // If there is a cast inside the field access expression,
    // the types may differ (c.f. #makeFieldVariable()).
    // The only thing we can do at this point is to expand the variable with nondet bits.
    CCompositeType structType = trackType.getStructType();
    BitvectorFormula realStruct = makeExtractOrConcatNondet(ownerType, structType, struct);

    return accessField(msb_lsb, realStruct);
  }

  /**
   * Create a formula for a given variable.
   * This method does not handle scoping and the NON_DET_VARIABLE!
   * But it does handles Fields.
   *
   * This method does not update the index of the variable.
   */
  Formula makeVariable(String name, CType type, SSAMapBuilder ssa) {
    return resolveFields(name, type, ssa, false);
  }
  Formula makeVariable(Variable var, SSAMapBuilder ssa) {
    return makeVariable(var.getName(), var.getType(), ssa);
  }

  /**
   * Create a formula for a given variable with a fresh index if needed.
   * This method does not handle scoping and the NON_DET_VARIABLE!
   * But it does handles Fields.
   *
   * This method does not update the index of the variable.
   */
  private Formula makeVariable(String name, CType type, SSAMapBuilder ssa, boolean makeFreshIndex) {
    return resolveFields(name, type, ssa, makeFreshIndex);
  }

  /**
   * Create a formula for a given variable with a fresh index for the left-hand
   * side of an assignment.
   * This method does not handle scoping and the NON_DET_VARIABLE!
   * But it does handles Fields.
   */
  Formula makeFreshVariable(String name, CType type, SSAMapBuilder ssa) {
    return resolveFields(name, type, ssa, true);
  }


  /** Takes a (scoped) struct variable name and returns the field variable name. */
  @VisibleForTesting
  static String makeFieldVariableName(String scopedId, Pair<Integer, Integer> msb_lsb, SSAMapBuilder ssa) {
    return FIELD_VARIABLE + scopedId +
          "__in__" + String.format("[%d:%d]", msb_lsb.getFirst(), msb_lsb.getSecond()) +
          "__at__" + ssa.getIndex(scopedId) +
          "__end";
  }

  @VisibleForTesting
  static boolean isFieldVariable(String var) {
    return var.startsWith(FIELD_VARIABLE);
  }

  /**
   * Takes a field variable name and returns the name and offset of the associated
   * struct variable.
   */
  static Pair<String, Pair<Integer, Integer>> removeFieldVariable(String fieldVariable) {
    assert isFieldVariable(fieldVariable);

    String name = fieldVariable.substring(FIELD_VARIABLE.length(), fieldVariable.lastIndexOf("__in__"));
    String msbLsbString =
        fieldVariable.substring(
            fieldVariable.lastIndexOf("__in__") + "__in__".length(),
            fieldVariable.lastIndexOf("__at__"));
    // Remove []
    msbLsbString = msbLsbString.substring(1, msbLsbString.length() - 1);
    String[] splits = msbLsbString.split(":");
    assert splits.length == 2 : "Expect msb and lsb part";
    return Pair.of(name, Pair.of(Integer.parseInt(splits[0]), Integer.parseInt(splits[1])));
  }


  BitvectorFormula makeStringLiteral(String literal) {
    BitvectorFormula result = stringLitToFormula.get(literal);

    if (result == null) {
      // generate a new string literal. We generate a new UIf
      int n = nextStringLitIndex++;
      result = ffmgr.createUninterpretedFunctionCall(
          stringUfDecl, ImmutableList.of(nfmgr.makeNumber(n)));
      stringLitToFormula.put(literal, result);
    }

    return result;
  }

  /**
   * Makes a fresh variable out of the varName and assigns the rightHandSide to it.
   * To be used when the left-hand side is no expression
   * (e.g. because we assign to a special hard-coded variable like __retval__).
   *
   * @param leftName has to be scoped already
   * @param rightHandSide The expression to evaluate and assign to left.
   * @param function The function where rightHandSide should be evaluated in.
   * @return the new Formula (lhs = rhs)
   */
  private BooleanFormula makeAssignment(String leftName, CType leftType,
      CExpression rightHandSide,
      String function, SSAMapBuilder ssa, CFAEdge edge, Constraints constraints) throws UnrecognizedCCodeException {

    Formula rhs = buildTerm(rightHandSide, edge, function, ssa, constraints);
    rhs = makeCast(rightHandSide.getExpressionType(), leftType, rhs, edge);

    Formula lhs = makeFreshVariable(leftName, leftType, ssa);

    return fmgr.assignment(lhs, rhs);
  }

  /**
   * Used for implicit and explicit type casts between CTypes.
   * @param fromType the origin Type of the expression.
   * @param toType the type to cast into.
   * @param formula the formula of the expression.
   * @return the new formula after the cast.
   */
  Formula makeCast(CType fromType, CType toType, Formula formula, CFAEdge edge) throws UnrecognizedCCodeException {
    // UNDEFINED: Casting a numeric value into a value that can't be represented by the target type (either directly or via static_cast)

    fromType = fromType.getCanonicalType();
    toType = toType.getCanonicalType();

    if (fromType.equals(toType)) {
      return formula; // No cast required;
    }

    if (fromType instanceof CFunctionType) {
      // references to functions can be seen as function pointers
      fromType = new CPointerType(false, false, fromType);
    }

    boolean fromCanBeHandledAsInt, toCanBeHandledAsInt;
    boolean fromIsPointer, toIsPointer;
    if ((fromCanBeHandledAsInt =
          ((fromIsPointer = fromType instanceof CPointerType) ||
           fromType instanceof CEnumType ||
          (fromType instanceof CElaboratedType &&
              ((CElaboratedType)fromType).getKind() == ComplexTypeKind.ENUM))) |
        (toCanBeHandledAsInt =
          ((toIsPointer = toType instanceof CPointerType) ||
           toType instanceof CEnumType ||
          (toType instanceof CElaboratedType &&
              ((CElaboratedType)toType).getKind() == ComplexTypeKind.ENUM)))) {

      // See Enums/Pointers as Integers
      if (fromCanBeHandledAsInt && !(toType instanceof CArrayType)) {
        fromType = fromIsPointer ? machineModel.getPointerEquivalentSimpleType() : CNumericTypes.INT;
        fromType = fromType.getCanonicalType();

        // a stringliteralepxression casted to an arraytype
      } else if (toType instanceof CArrayType
          && edge instanceof CDeclarationEdge
          && ((CDeclarationEdge)edge).getDeclaration() instanceof CVariableDeclaration) {

        CInitializer rhs = ((CVariableDeclaration)((CDeclarationEdge)edge).getDeclaration()).getInitializer();
        if (rhs instanceof CInitializerExpression) {
          if (((CInitializerExpression) rhs).getExpression() instanceof CStringLiteralExpression) {
            logger.logf(Level.INFO, "Ignoring cast from %s to %s. This was an assignment of a String to a Char Array.", fromType, toType);
            int sfrom = machineModel.getSizeof(fromType);
            int sto = machineModel.getSizeof(toType);

            int bitsPerByte = machineModel.getSizeofCharInBits();

            // Currently everything is a bitvector
            if (sfrom > sto) {
              return fmgr.makeExtract(formula, sto * bitsPerByte - 1, 0);

            } else if (sfrom < sto) {
              int bitsToExtend = (sto - sfrom) * bitsPerByte;
              return fmgr.makeExtend(formula, bitsToExtend, false);

            } else {
              return formula;
            }
          }
        }
      }

      if (toCanBeHandledAsInt) {
        toType = toIsPointer ? machineModel.getPointerEquivalentSimpleType() : CNumericTypes.INT;
        toType = toType.getCanonicalType();
      }
    }



    if (fromType instanceof CSimpleType) {
      CSimpleType sfromType = (CSimpleType)fromType;
      if (toType instanceof CSimpleType) {
        CSimpleType stoType = (CSimpleType)toType;
        return makeSimpleCast(sfromType, stoType, formula);
      }
    }

    if (fromType instanceof CPointerType ||
        toType instanceof CPointerType) {
      // Ignore casts between Pointer and right sized types
      if (getFormulaTypeFromCType(toType) == getFormulaTypeFromCType(fromType)) {
        return formula;
      }
    }

    if (getSizeof(fromType) == getSizeof(toType)) {
      // We can most likely just ignore this cast
      logger.logfOnce(Level.WARNING, "Ignoring cast from %s to %s.", fromType, toType);
      return formula;
    } else {
      throw new UnrecognizedCCodeException("Cast from " + fromType + " to " + toType + " not supported!", edge);
    }
  }

  Formula makeCast(CCastExpression e, Formula inner, CFAEdge edge) throws UnrecognizedCCodeException {
    CType after = e.getExpressionType();
    CType before = e.getOperand().getExpressionType();
    return makeCast(before, after, inner, edge);
  }

  CExpression makeCastFromArrayToPointerIfNecessary(CExpression exp, CType targetType) {
    if (exp.getExpressionType().getCanonicalType() instanceof CArrayType) {
      targetType = targetType.getCanonicalType();
      if (targetType instanceof CPointerType || targetType instanceof CSimpleType) {
        return makeCastFromArrayToPointer(exp);
      }
    }
    return exp;
  }

  private static CExpression makeCastFromArrayToPointer(CExpression arrayExpression) {
    // array-to-pointer conversion
    CArrayType arrayType = (CArrayType)arrayExpression.getExpressionType().getCanonicalType();
    CPointerType pointerType = new CPointerType(arrayType.isConst(),
        arrayType.isVolatile(), arrayType.getType());

    return new CUnaryExpression(arrayExpression.getFileLocation(), pointerType,
        arrayExpression, UnaryOperator.AMPER);
  }

  /**
   * Change the size of the given formula from fromType to toType.
   * This method extracts or concats with nondet-bits.
   */
  BitvectorFormula makeExtractOrConcatNondet(CType pFromType, CType pToType, Formula pFormula) {
    assert pFormula instanceof BitvectorFormula
      : "Can't makeExtractOrConcatNondet for something other than Bitvectors";
    int sfrom = getSizeof(pFromType);
    int sto = getSizeof(pToType);

    int bitsPerByte = machineModel.getSizeofCharInBits();
    return changeFormulaSize(sfrom * bitsPerByte, sto * bitsPerByte, (BitvectorFormula)pFormula);
  }

  /**
   * Change the given Formulasize from the given size to the new size.
   * if sfrom > sto an extract will be done.
   * if sto > sfrom an concat with nondet-bits will be done.
   * else pFormula is returned.
   * @param sfrom
   * @param sto
   * @param pFormula
   * @return the resized formula
   */
  BitvectorFormula changeFormulaSize(int sfrombits, int stobits, BitvectorFormula pFormula) {
    assert fmgr.getFormulaType(pFormula) == efmgr.getFormulaType(sfrombits)
         : "expected to get sfrombits sized formula!";

    // Currently everything is a bitvector
    BitvectorFormula ret;
    if (sfrombits > stobits) {
      if (stobits == 0) {
        ret = efmgr.makeBitvector(0, 0);
      } else {
        ret = fmgr.makeExtract(pFormula, stobits - 1, 0);
      }
    } else if (sfrombits < stobits) {
      // Sign extend with ones when pfromType is signed and sign bit is set
      int bitsToExtend = stobits - sfrombits;
      FormulaType<BitvectorFormula> t = efmgr.getFormulaType(bitsToExtend);
      BitvectorFormula extendBits = fmgr.makeVariable(t, CtoFormulaConverter.EXPAND_VARIABLE + expands++, 0); // for every call a new variable
      ret = fmgr.makeConcat(extendBits, pFormula);
    } else {
      ret = pFormula;
    }

    assert fmgr.getFormulaType(ret) == efmgr.getFormulaType(stobits);
    return ret;
  }

  /**
   * Handles casts between simple types.
   * When the fromType is a signed type a bit-extension will be done,
   * on any other case it will be filled with 0 bits.
   */
  private Formula makeSimpleCast(CSimpleType pfromType, CSimpleType ptoType, Formula pFormula) {
    int sfrom = machineModel.getSizeof(pfromType);
    int sto = machineModel.getSizeof(ptoType);

    int bitsPerByte = machineModel.getSizeofCharInBits();

    // Currently everything is a bitvector
    Formula ret;
    if (sfrom > sto) {
      ret = fmgr.makeExtract(pFormula, sto * bitsPerByte - 1, 0);
    } else if (sfrom < sto) {
      boolean signed = machineModel.isSigned(pfromType);
      int bitsToExtend = (sto - sfrom) * bitsPerByte;
      ret = fmgr.makeExtend(pFormula, bitsToExtend, signed);

    } else {
      ret = pFormula;
    }

    assert fmgr.getFormulaType(ret) == getFormulaTypeFromCType(ptoType);
    return ret;
  }

  CType getPromotedCType(CType t) {
    t = t.getCanonicalType();
    if (t instanceof CSimpleType) {
      // Integer types smaller than int are promoted when an operation is performed on them.
      // If all values of the original type can be represented as an int, the value of the smaller type is converted to an int;
      // otherwise, it is converted to an unsigned int.
      CSimpleType s = (CSimpleType) t;
      if (machineModel.getSizeof(s) < machineModel.getSizeofInt()) {
        return CNumericTypes.INT;
      }
    }
    return t;
  }

//  @Override
  public Pair<PathFormula, ErrorConditions> makeAnd(PathFormula oldFormula, CFAEdge edge)
      throws CPATransferException {
    // this is where the "meat" is... We have to parse the statement
    // attached to the edge, and convert it to the appropriate formula
    ErrorConditions errorConditions = new ErrorConditions(bfmgr);

    if (edge.getEdgeType() == CFAEdgeType.BlankEdge) {

      // in this case there's absolutely nothing to do, so take a shortcut
      return Pair.of(oldFormula, errorConditions);
    }

    String function = (edge.getPredecessor() != null)
                          ? edge.getPredecessor().getFunctionName() : null;

    SSAMapBuilder ssa = oldFormula.getSsa().builder();
    Constraints constraints = new Constraints(bfmgr);

    BooleanFormula edgeFormula = createFormulaForEdge(edge, function, ssa, constraints);

    edgeFormula = bfmgr.and(edgeFormula, constraints.get());

    SSAMap newSsa = ssa.build();
    if (bfmgr.isTrue(edgeFormula) && (newSsa == oldFormula.getSsa())) {
      // formula is just "true" and SSAMap is identical
      // i.e. no writes to SSAMap, no branching and length should stay the same
      return Pair.of(oldFormula, errorConditions);
    }

    BooleanFormula newFormula = bfmgr.and(oldFormula.getFormula(), edgeFormula);
    int newLength = oldFormula.getLength() + 1;
    return Pair.of(new PathFormula(newFormula, newSsa, newLength), errorConditions);
  }

  /**
   * This helper method creates a formula for an CFA edge, given the current function, SSA map and constraints.
   *
   * @param edge the edge for which to create the formula
   * @param function the current scope
   * @param ssa the current SSA map
   * @param constraints the current constraints
   * @return the formula for the edge
   * @throws CPATransferException
   */
  private BooleanFormula createFormulaForEdge(CFAEdge edge, String function, SSAMapBuilder ssa, Constraints constraints) throws CPATransferException {
    switch (edge.getEdgeType()) {
    case StatementEdge: {
      CStatementEdge statementEdge = (CStatementEdge) edge;
      StatementToFormulaVisitor v = getStatementVisitor(edge, function, ssa, constraints);
      return statementEdge.getStatement().accept(v);
    }

    case ReturnStatementEdge: {
      CReturnStatementEdge returnEdge = (CReturnStatementEdge)edge;
      return makeReturn(returnEdge.getExpression(), returnEdge, function, ssa, constraints);
    }

    case DeclarationEdge: {
      CDeclarationEdge d = (CDeclarationEdge)edge;
      return makeDeclaration(d, function, ssa, constraints);
    }

    case AssumeEdge: {
      return makeAssume((CAssumeEdge)edge, function, ssa, constraints);
    }

    case BlankEdge: {
      assert false : "Handled above";
      return bfmgr.makeBoolean(true);
    }

    case FunctionCallEdge: {
      return makeFunctionCall((CFunctionCallEdge)edge, function, ssa, constraints);
    }

    case FunctionReturnEdge: {
      // get the expression from the summary edge
      CFunctionSummaryEdge ce = ((CFunctionReturnEdge)edge).getSummaryEdge();
      return makeExitFunction(ce, function, ssa, constraints);
    }

    case MultiEdge: {
      List<BooleanFormula> multiEdgeFormulas = new ArrayList<>(((MultiEdge)edge).getEdges().size());

      // unroll the MultiEdge
      for (CFAEdge singleEdge : (MultiEdge)edge) {
        if (singleEdge instanceof BlankEdge) {
          continue;
        }
        multiEdgeFormulas.add(createFormulaForEdge(singleEdge, function, ssa, constraints));
      }

      return bfmgr.and(multiEdgeFormulas);
    }

    default:
      throw new UnrecognizedCFAEdgeException(edge);
    }
  }

  private BooleanFormula makeDeclaration(
      CDeclarationEdge edge, String function, SSAMapBuilder ssa,
      Constraints constraints) throws CPATransferException {

    if (!(edge.getDeclaration() instanceof CVariableDeclaration)) {
      // struct prototype, function declaration, typedef etc.
      logfOnce(Level.FINEST, edge, "Ignoring declaration");
      return bfmgr.makeBoolean(true);
    }

    CVariableDeclaration decl = (CVariableDeclaration)edge.getDeclaration();
    final String varName = decl.getQualifiedName();

    // if the var is unsigned, add the constraint that it should
    // be > 0
    //    if (((CSimpleType)spec).isUnsigned()) {
    //    long z = mathsat.api.msat_make_number(msatEnv, "0");
    //    long mvar = buildMsatVariable(var, idx);
    //    long t = mathsat.api.msat_make_gt(msatEnv, mvar, z);
    //    t = mathsat.api.msat_make_and(msatEnv, m1.getTerm(), t);
    //    m1 = new MathsatFormula(t);
    //    }

    // just increment index of variable in SSAMap
    // (a declaration contains an implicit assignment, even without initializer)
    // In case of an existing initializer, we increment the index twice
    // (here and below) so that the index 2 only occurs for uninitialized variables.
    // DO NOT OMIT THIS CALL, even without an initializer!
    makeFreshIndex(varName, decl.getType(), ssa);

    // if there is an initializer associated to this variable,
    // take it into account
    BooleanFormula result = bfmgr.makeBoolean(true);

    if (decl.getInitializer() instanceof CInitializerList) {
      // If there is an initializer, all fields/elements not mentioned
      // in the initializer are set to 0 (C standard § 6.7.9 (21)

      int size = machineModel.getSizeof(decl.getType());
      if (size > 0) {
        Variable v = Variable.create(varName, decl.getType());
        Formula var = makeVariable(v, ssa);
        Formula zero = fmgr.makeNumber(getFormulaTypeFromCType(decl.getType()), 0L);
        result = bfmgr.and(result, fmgr.assignment(var, zero));
      }
    }

    StatementToFormulaVisitor v = getStatementVisitor(edge, function, ssa, constraints);

    for (CExpressionAssignmentStatement assignment : CInitializers.convertToAssignments(decl, edge)) {
      result = bfmgr.and(result, assignment.accept(v));
    }

    return result;
  }

  private BooleanFormula makeExitFunction(CFunctionSummaryEdge ce, String function,
      SSAMapBuilder ssa, Constraints constraints) throws CPATransferException {

    CFunctionCall retExp = ce.getExpression();
    if (retExp instanceof CFunctionCallStatement) {
      // this should be a void return, just do nothing...
      return bfmgr.makeBoolean(true);

    } else if (retExp instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement exp = (CFunctionCallAssignmentStatement)retExp;
      String retVarName = getReturnVarName(function);

      CFunctionCallExpression funcCallExp = exp.getRightHandSide();
      CType retType = getReturnType(funcCallExp, ce);

      Formula retVar = makeVariable(retVarName, retType, ssa);
      CLeftHandSide e = exp.getLeftHandSide();

      function = ce.getSuccessor().getFunctionName();
      Formula outvarFormula = buildLvalueTerm(e, ce, function, ssa, constraints);
      retVar = makeCast(retType, e.getExpressionType(), retVar, ce);
      BooleanFormula assignments = fmgr.assignment(outvarFormula, retVar);

      return assignments;
    } else {
      throw new UnrecognizedCCodeException("Unknown function exit expression", ce, retExp);
    }
  }

  CType getReturnType(CFunctionCallExpression funcCallExp, CFAEdge edge) throws UnrecognizedCCodeException {
    // NOTE: When funCallExp.getExpressionType() does always return the return type of the function we don't
    // need this function. However I'm not sure because there can be implicit casts. Just to be safe.
    CType retType;
    CFunctionDeclaration funcDecl = funcCallExp.getDeclaration();
    if (funcDecl == null) {
      // Check if we have a function pointer here.
      CExpression functionNameExpression = funcCallExp.getFunctionNameExpression();
      CType expressionType = functionNameExpression.getExpressionType().getCanonicalType();
      if (expressionType instanceof CFunctionType) {
        CFunctionType funcPtrType = (CFunctionType)expressionType;
        retType = funcPtrType.getReturnType();
      } else if (expressionType instanceof CPointerType &&
                 ((CPointerType) expressionType).getType().getCanonicalType() instanceof CFunctionType) {
        CFunctionType funcPtrType = (CFunctionType) ((CPointerType) expressionType).getType().getCanonicalType();
        retType = funcPtrType.getReturnType();
      } else {
        throw new UnrecognizedCCodeException("Cannot handle function pointer call with unknown type " + expressionType, edge, funcCallExp);
      }
      assert retType != null;
    } else {
      retType = funcDecl.getType().getReturnType();
    }

    CType expType = funcCallExp.getExpressionType();
    if (!expType.getCanonicalType().equals(retType.getCanonicalType())) {
      // Bit ignore for now because we sometimes just get ElaboratedType instead of CompositeType
      logfOnce(Level.WARNING, edge,
          "Return type of function %s is %s, but result is used as type %s",
          funcDecl.getName(), retType, expType);
    }
    return expType;
  }


  private BooleanFormula makeFunctionCall(CFunctionCallEdge edge,
      String callerFunction, SSAMapBuilder ssa, Constraints constraints) throws CPATransferException {

    List<CExpression> actualParams = edge.getArguments();

    CFunctionEntryNode fn = edge.getSuccessor();
    List<CParameterDeclaration> formalParams = fn.getFunctionParameters();

    if (fn.getFunctionDefinition().getType().takesVarArgs()) {
      if (formalParams.size() > actualParams.size()) {
        throw new UnrecognizedCCodeException("Number of parameters on function call does " +
            "not match function definition", edge);
      }

      if (!SAFE_VAR_ARG_FUNCTIONS.contains(fn.getFunctionName())) {
        logfOnce(Level.WARNING, edge,
            "Ignoring parameters passed as varargs to function %s",
            fn.getFunctionName());
      }

    } else {
      if (formalParams.size() != actualParams.size()) {
        throw new UnrecognizedCCodeException("Number of parameters on function call does " +
            "not match function definition", edge);
      }
    }

    int i = 0;
    BooleanFormula result = bfmgr.makeBoolean(true);
    for (CParameterDeclaration formalParam : formalParams) {
      final String varName = formalParam.getQualifiedName();
      final CType paramType = formalParam.getType();
      CExpression paramExpression = actualParams.get(i++);
      paramExpression = makeCastFromArrayToPointerIfNecessary(paramExpression, paramType);

      BooleanFormula eq =
          makeAssignment(
              varName, paramType,
              paramExpression,
              callerFunction, ssa, edge, constraints);

      result = bfmgr.and(result, eq);
    }

    return result;
  }

  private BooleanFormula makeReturn(CExpression rightExp, CReturnStatementEdge edge, String function,
      SSAMapBuilder ssa, Constraints constraints) throws CPATransferException {
    if (rightExp == null) {
      // this is a return from a void function, do nothing
      return bfmgr.makeBoolean(true);
    } else {

      // we have to save the information about the return value,
      // so that we can use it later on, if it is assigned to
      // a variable. We create a function::__retval__ variable
      // that will hold the return value
      String retVarName = getReturnVarName(function);

      CType returnType =
          ((CFunctionEntryNode)edge.getSuccessor().getEntryNode())
            .getFunctionDefinition()
            .getType()
            .getReturnType();
      BooleanFormula assignments = makeAssignment(retVarName, returnType,
          rightExp, function, ssa, edge, constraints);

      return assignments;
    }
  }

  private BooleanFormula makeAssume(CAssumeEdge assume, String function,
      SSAMapBuilder ssa, Constraints constraints) throws CPATransferException {

    return makePredicate(assume.getExpression(), assume.getTruthAssumption(),
        assume, function, ssa, constraints);
  }

  Formula buildTerm(CExpression exp, CFAEdge edge, String function,
      SSAMapBuilder ssa, Constraints constraints) throws UnrecognizedCCodeException {
    return exp.accept(getCExpressionVisitor(edge, function, ssa, constraints));
  }

  Formula buildLvalueTerm(CLeftHandSide exp, CFAEdge edge, String function,
      SSAMapBuilder ssa, Constraints constraints) throws UnrecognizedCCodeException {
    return exp.accept(getLvalueVisitor(edge, function, ssa, constraints));
  }

  BooleanFormula makeNondetAssignment(Formula left, Formula right) {
    BitvectorFormulaManagerView bitvectorFormulaManager = efmgr;
    FormulaType<Formula> tl = fmgr.getFormulaType(left);
    FormulaType<Formula> tr = fmgr.getFormulaType(right);
    if (tl == tr) {
      return fmgr.assignment(left, right);
    }

    if (tl.isBitvectorType() && tr.isBitvectorType()) {

      BitvectorFormula leftBv = (BitvectorFormula) left;
      BitvectorFormula rightBv = (BitvectorFormula) right;
      int leftSize = bitvectorFormulaManager.getLength(leftBv);
      int rightSize = bitvectorFormulaManager.getLength(rightBv);

      // Expand the smaller one with nondet-bits
      if (leftSize < rightSize) {
        leftBv =
            changeFormulaSize(leftSize, rightSize, leftBv);
      } else {
        rightBv =
            changeFormulaSize(rightSize, leftSize, rightBv);
      }
      return bitvectorFormulaManager.equal(leftBv, rightBv);
    }

    throw new IllegalArgumentException("Assignment between different types");
  }

  void warnToComplex(CAstNode node) {
    if (logger.wouldBeLogged(Level.FINEST)) {
      if (options.handleFieldAccess()) {
        logfOnce(Level.FINEST, node,
            "Ignoring pointer aliasing because statement is too complex, please simplify");
      } else {
        logfOnce(Level.FINEST, node,
            "Ignoring pointer aliasing because statement is too complex, please simplify or enable handleFieldAccess and handleFieldAliasing");
      }
    }
  }

  <T extends Formula> T ifTrueThenOneElseZero(FormulaType<T> type, BooleanFormula pCond) {
    T one = fmgr.makeNumber(type, 1);
    T zero = fmgr.makeNumber(type, 0);
    return bfmgr.ifThenElse(pCond, one, zero);
  }

  <T extends Formula> BooleanFormula toBooleanFormula(T pF) {
    // If this is not a predicate, make it a predicate by adding a "!= 0"
    assert !fmgr.getFormulaType(pF).isBooleanType();

    T zero = fmgr.makeNumber(fmgr.getFormulaType(pF), 0);

    if (bfmgr.isIfThenElse(pF)) {
      Triple<BooleanFormula, T, T> parts = bfmgr.splitIfThenElse(pF);

      T one = fmgr.makeNumber(fmgr.getFormulaType(pF), 1);

      if (parts.getSecond().equals(one) && parts.getThird().equals(zero)) {
        return parts.getFirst();
      } else if (parts.getSecond().equals(zero) && parts.getThird().equals(one)) {
        return bfmgr.not(parts.getFirst());
      }
    }

    return bfmgr.not(fmgr.makeEqual(pF, zero));
  }

  private BooleanFormula makePredicate(CExpression exp, boolean isTrue, CFAEdge edge,
      String function, SSAMapBuilder ssa, Constraints constraints) throws UnrecognizedCCodeException {

    if (IndirectionVisitor.getIndirectionLevel(exp) > supportedIndirectionLevel) {
      warnToComplex(exp);
    }

    Formula f = exp.accept(getCExpressionVisitor(edge, function, ssa, constraints));
    BooleanFormula result = toBooleanFormula(f);

    if (!isTrue) {
      result = bfmgr.not(result);
    }
    return result;
  }

  public BooleanFormula makePredicate(CExpression exp, CFAEdge edge, String function, SSAMapBuilder ssa) throws UnrecognizedCCodeException {
    Constraints constraints = new Constraints(bfmgr);
    BooleanFormula f = makePredicate(exp, true, edge, function, ssa, constraints);
    return bfmgr.and(f, constraints.get());
  }

  private StatementToFormulaVisitor getStatementVisitor(CFAEdge pEdge, String pFunction,
      SSAMapBuilder pSsa, Constraints pConstraints) {
    ExpressionToFormulaVisitor ev = getCExpressionVisitor(pEdge, pFunction, pSsa, pConstraints);
    return new StatementToFormulaVisitor(ev);
  }

  private ExpressionToFormulaVisitor getCExpressionVisitor(CFAEdge pEdge, String pFunction,
      SSAMapBuilder pSsa, Constraints pCo) {
    return new ExpressionToFormulaVisitor(this, pEdge, pFunction, pSsa, pCo);
  }

  private LvalueVisitor getLvalueVisitor(CFAEdge pEdge, String pFunction, SSAMapBuilder pSsa, Constraints pCo) {
    return new LvalueVisitor(this, pEdge, pFunction, pSsa, pCo);
  }

  /**
   * Creates a Formula which accesses the given bits.
   */
  private BitvectorFormula accessField(Pair<Integer, Integer> msb_Lsb, BitvectorFormula f) {
    return fmgr.makeExtract(f, msb_Lsb.getFirst(), msb_Lsb.getSecond());
  }

  /**
   * Creates a Formula which accesses the given Field
   */
  BitvectorFormula accessField(CFieldReference fExp, Formula f) throws UnrecognizedCCodeException {
    assert options.handleFieldAccess() : "Fieldaccess if only allowed with handleFieldAccess";
    assert f instanceof BitvectorFormula : "Fields need to be represented with bitvectors";
    // Get the underlaying structure
    Pair<Integer, Integer> msb_Lsb = getFieldOffsetMsbLsb(fExp);
    return accessField(msb_Lsb, (BitvectorFormula)f);
  }

  /**
   * Return the bitvector for a struct with the bits for one field replaced
   * by another bitvector, or left out completely.
   * @param fExp The field of the struct to replace.
   * @param pLVar The full struct.
   * @param pRightVariable The replacement bitvector, or nothing.
   * @return If pRightVariable is present, a formula of the same size as pLVar, but with some bits replaced.
   * If pRightVariable is not present, a formula that is smaller then pLVar (with the field bits missing).
   */
  Formula replaceField(CFieldReference fExp, Formula pLVar, Optional<Formula> pRightVariable) throws UnrecognizedCCodeException {
    assert options.handleFieldAccess() : "Fieldaccess if only allowed with handleFieldAccess";

    Pair<Integer, Integer> msb_Lsb = getFieldOffsetMsbLsb(fExp);

    int size = efmgr.getLength((BitvectorFormula) pLVar);
    assert size > msb_Lsb.getFirst() : "pLVar is too small";
    assert 0 <= msb_Lsb.getSecond() && msb_Lsb.getFirst() >= msb_Lsb.getSecond() : "msb_Lsb is invalid";

    // create a list with three formulas:
    // - prefix of struct (before the field)
    // - the replaced field
    // - suffix of struct (after the field)
    List<Formula> parts = new ArrayList<>(3);

    if (msb_Lsb.getFirst() + 1 < size) {
      parts.add(fmgr.makeExtract(pLVar, size - 1, msb_Lsb.getFirst() + 1));
    }

    if (pRightVariable.isPresent()) {
      assert efmgr.getLength((BitvectorFormula) pRightVariable.get()) == msb_Lsb.getFirst() + 1 - msb_Lsb.getSecond() : "The new formula has not the right size";
      parts.add(pRightVariable.get());
    }

    if (msb_Lsb.getSecond() > 0) {
      parts.add(fmgr.makeExtract(pLVar, msb_Lsb.getSecond() - 1, 0));
    }

    if (parts.isEmpty()) {
      // struct with no other fields, return empty bitvector
      return efmgr.makeBitvector(0, 0);
    }
    return fmgr.makeConcat(parts);
  }

  /**
   * Returns the offset of the given CFieldReference within the structure in bits.
   */
  private Pair<Integer, Integer> getFieldOffsetMsbLsb(CFieldReference fExp) throws UnrecognizedCCodeException {
    CExpression fieldRef = getRealFieldOwner(fExp);
    CCompositeType structType = (CCompositeType)fieldRef.getExpressionType().getCanonicalType();

    // f is now the structure, access it:
    int bitsPerByte = machineModel.getSizeofCharInBits();

    int offset;
    switch (structType.getKind()) {
    case UNION:
      offset = 0;
      break;
    case STRUCT:
      offset = getFieldOffset(structType, fExp.getFieldName()) * bitsPerByte;
      break;
    default:
      throw new UnrecognizedCCodeException("Unexpected field access", fExp);
    }

    int fieldSize = getSizeof(fExp.getExpressionType()) * bitsPerByte;

    // Crude hack for unions with zero-sized array fields produced by LDV
    // (ldv-consumption/32_7a_cilled_true_linux-3.8-rc1-32_7a-fs--ceph--ceph.ko-ldv_main7_sequence_infinite_withcheck_stateful.cil.out.c)
    if (fieldSize == 0 && structType.getKind() == ComplexTypeKind.UNION) {
      fieldSize = getSizeof(fieldRef.getExpressionType());
    }

    int lsb = offset;
    int msb = offset + fieldSize - 1;
    assert(lsb >= 0);
    assert(msb >= lsb);
    Pair<Integer, Integer> msb_Lsb = Pair.of(msb, lsb);
    return msb_Lsb;
  }

  /**
   * Returns the offset of the given field in the given struct in bytes.
   *
   * This function does not handle UNIONs or ENUMs!
   */
  private int getFieldOffset(CCompositeType structType, String fieldName) {
    int off = 0;
    for (CCompositeTypeMemberDeclaration member : structType.getMembers()) {
      if (member.getName().equals(fieldName)) {
        return off;
      }

      off += getSizeof(member.getType());
    }

    throw new AssertionError("field " + fieldName + " was not found in " + structType);
  }

  static CExpression removeCast(CExpression exp) {
    if (exp instanceof CCastExpression) {
      return removeCast(((CCastExpression) exp).getOperand());
    }
    return exp;
  }

  static CRightHandSide removeCast(CRightHandSide exp) {
    if (exp instanceof CCastExpression) {
      return removeCast(((CCastExpression) exp).getOperand());
    }
    return exp;
  }

  /**
   * Indicates which level of indirection is supported.
   * This should stay 1 unless you know what you are doing.
   * The main reason for this limit is that we would have to emit a lot more formulas for every additional level.
   */
  protected final int supportedIndirectionLevel = 1;

  /**
   * Returns true when we are able to produce a variable<CType> from this expression.
   * With this method we are able to control which expressions we handle and
   * which we just create variables for.
   * @param exp the expression.
   * @param level the current level of indirection.
   * @return true if we can create a variable from this expression.
   */
  boolean isSupportedExpression(CExpression exp, int level) {
    if (level > supportedIndirectionLevel) {
      return false;
    }

    if (exp instanceof CIdExpression) {
      return true;
    } else if (options.handleFieldAccess() && exp instanceof CFieldReference) {
      CFieldReference fexp = (CFieldReference)exp;
      return isSupportedExpression(getRealFieldOwner(fexp), level);
    } else if (exp instanceof CCastExpression) {
      CCastExpression cexp = (CCastExpression)exp;
      return isSupportedExpression(cexp.getOperand(), level);
    } else if (exp instanceof CUnaryExpression) {
      CUnaryExpression uexp = (CUnaryExpression)exp;
      UnaryOperator op = uexp.getOperator();
      return
          (op == UnaryOperator.AMPER) &&
          isSupportedExpression(uexp.getOperand(), level + 1);
    } else if (exp instanceof CPointerExpression) {
      return isSupportedExpression(((CPointerExpression)exp).getOperand(), level +1);
    }

    return false;
  }

  /**
   * Returns true when we are able to produce a variable<CType> from this expression.
   * With this method we are able to control which expressions we handle and
   * which we just create variables for.
   * @param exp the expression
   * @return true if we can create a variable from this expression.
   */
  boolean isSupportedExpression(CExpression exp) {
    return isSupportedExpression(exp, 0);
  }

  /**
   * We call this method for unsupported Expressions and just make a new Variable.
   */
  Formula makeVariableUnsafe(CExpression exp, String function, SSAMapBuilder ssa, boolean makeFresh) {
    if (makeFresh) {
      logger.logOnce(Level.WARNING, "Program contains array, or pointer (multiple level of indirection), or field (enable handleFieldAccess and handleFieldAliasing) access; analysis is imprecise in case of aliasing.");
    }
    logfOnce(Level.FINEST, exp, "Unhandled expression treated as free variable");

    String var = scoped(exprToVarName(exp), function);
    return makeVariable(var, exp.getExpressionType(), ssa, makeFresh);
  }
}
