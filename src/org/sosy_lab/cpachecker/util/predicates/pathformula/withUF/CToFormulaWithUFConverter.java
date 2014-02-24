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
package org.sosy_lab.cpachecker.util.predicates.pathformula.withUF;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.CTypeUtils.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.cdt.internal.core.dom.parser.c.CFunctionType;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializers;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CComplexType.ComplexTypeKind;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType.CCompositeTypeMemberDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.VariableClassification;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FunctionFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ErrorConditions;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.Variable;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.Constraints;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaConverter;
import org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.Expression.Location;
import org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.Expression.Location.AliasedLocation;
import org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.Expression.Location.UnaliasedLocation;
import org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.Expression.Value;
import org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.PointerTargetSetBuilder.RealPointerTargetSetBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.pointerTarget.PointerTarget;
import org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.pointerTarget.PointerTargetPattern;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class CToFormulaWithUFConverter extends CtoFormulaConverter {

  // Overrides just for visibility in other classes of this package

  @SuppressWarnings("hiding")
  final LogManagerWithoutDuplicates logger = super.logger;
  @SuppressWarnings("hiding")
  final FormulaManagerView fmgr = super.fmgr;
  @SuppressWarnings("hiding")
  final BooleanFormulaManagerView bfmgr = super.bfmgr;
  @SuppressWarnings("hiding")
  final FunctionFormulaManagerView ffmgr = super.ffmgr;
  @SuppressWarnings("hiding")
  final MachineModel machineModel = super.machineModel;

  final CToFormulaWithUFTypeHandler typeHandler;
  final PointerTargetSetManager ptsMgr;

  final FormulaType<?> voidPointerFormulaType;
  final Formula nullPointer;

  public CToFormulaWithUFConverter(final FormulaEncodingWithUFOptions pOptions,
                                   final FormulaManagerView formulaManagerView,
                                   final MachineModel pMachineModel,
                                   final PointerTargetSetManager pPtsMgr,
                                   final Optional<VariableClassification> pVariableClassification,
                                   final LogManager logger,
                                   final CToFormulaWithUFTypeHandler pTypeHandler)
  throws InvalidConfigurationException {
    super(pOptions, formulaManagerView, pMachineModel, pVariableClassification, logger, pTypeHandler);
    variableClassification = pVariableClassification;
    options = pOptions;
    ptsMgr = pPtsMgr;
    typeHandler = pTypeHandler;

    voidPointerFormulaType = typeHandler.getFormulaTypeFromCType(CPointerType.POINTER_TO_VOID);
    nullPointer = fmgr.makeNumber(voidPointerFormulaType, 0);
  }

  public static String getUFName(final CType type) {
    String result = ufNameCache.get(type);
    if (result != null) {
      return result;
    } else {
      result = UF_NAME_PREFIX + CTypeUtils.typeToString(type).replace(' ', '_');
      ufNameCache.put(type, result);
      return result;
    }
  }

  public static boolean isUF(final String symbol) {
    return symbol.startsWith(UF_NAME_PREFIX);
  }

  @Override
  @Deprecated
  protected int makeFreshIndex(final String name, final CType type, final SSAMapBuilder ssa) {
    throw new UnsupportedOperationException("Use more specific methods instead");
  }

  Formula makeBaseAddressOfTerm(final Formula address) {
    return ffmgr.createFuncAndCall("__BASE_ADDRESS_OF__", voidPointerFormulaType, ImmutableList.of(address));
  }

  @Override
  protected Variable scopedIfNecessary(CIdExpression var) {
    return Variable.create(var.getDeclaration().getQualifiedName(),
                           CTypeUtils.simplifyType(var.getExpressionType()));
  }

  @Override
  protected void checkSsaSavedType(final String name, final CType type, final SSAMapBuilder ssa) {
    CType ssaSavedType = ssa.getType(name);
    if (ssaSavedType != null) {
      ssaSavedType = CTypeUtils.simplifyType(ssaSavedType);
    }
    if (ssaSavedType != null &&
        !ssaSavedType.equals(CTypeUtils.simplifyType(type))) {
      logger.logf(Level.FINEST,
                  "Variable %s was found with multiple types! (Type1: %s, Type2: %s)",
                  name,
                  ssaSavedType,
                  type);
    }
  }

  boolean hasIndex(final String name, final CType type, final SSAMapBuilder ssa) {
    checkSsaSavedType(name, type, ssa);
    return ssa.getIndex(name) > 0;
  }

  @Override
  protected Formula makeVariable(final String name,
                       final CType type,
                       final SSAMapBuilder ssa) {
    final int index = getIndex(name, type, ssa);
    return fmgr.makeVariable(getFormulaTypeFromCType(type), name, index);
  }

  @Override
  protected Formula makeFreshVariable(final String name,
                            final CType type,
                            final SSAMapBuilder ssa) {
    final int oldIndex = getIndex(name, type, ssa);
    final int newIndex = oldIndex + 1;
    ssa.setIndex(name, type, newIndex);
    return fmgr.makeVariable(getFormulaTypeFromCType(type),
                             name + FRESH_INDEX_SEPARATOR + newIndex);
  }

  Formula makeDereference(CType type,
                         final Formula address,
                         final SSAMapBuilder ssa,
                         final @Nullable ErrorConditions errorConditions) {
    if (errorConditions.isEnabled()) {
      errorConditions.addInvalidDerefCondition(fmgr.makeEqual(address, nullPointer));
      errorConditions.addInvalidDerefCondition(fmgr.makeLessThan(address, makeBaseAddressOfTerm(address), false));
    }
    return makeSafeDereference(type, address, ssa);
  }

  Formula makeSafeDereference(CType type,
                         final Formula address,
                         final SSAMapBuilder ssa) {
    type = CTypeUtils.simplifyType(type);
    final String ufName = getUFName(type);
    final int index = getIndex(ufName, type, ssa);
    final FormulaType<?> returnType = getFormulaTypeFromCType(type);
    return ffmgr.createFuncAndCall(ufName, index, returnType, ImmutableList.of(address));
  }

  @Override
  protected boolean isRelevantField(final CCompositeType compositeType,
                          final String fieldName) {
    return super.isRelevantField(compositeType, fieldName)
        || getSizeof(compositeType) <= options.maxPreFilledAllocationSize();
  }

  boolean isAddressedVariable(final String function, final String name) {
    return !variableClassification.isPresent() ||
           variableClassification.get().getAddressedVariables().containsEntry(function, name);
  }

  boolean isAddressedVariable(CDeclaration var) {
    final String qualifiedName = var.getQualifiedName();
    final Pair<String, String> parsedName = parseQualifiedName(qualifiedName);
    return isAddressedVariable(parsedName.getFirst(), parsedName.getSecond());
  }

  private void addAllFields(final CType type, final PointerTargetSetBuilder pts) {
    if (type instanceof CCompositeType) {
      final CCompositeType compositeType = (CCompositeType) type;
      for (CCompositeTypeMemberDeclaration memberDeclaration : compositeType.getMembers()) {
        if (isRelevantField(compositeType, memberDeclaration.getName())) {
          pts.addField(compositeType, memberDeclaration.getName());
          final CType memberType = CTypeUtils.simplifyType(memberDeclaration.getType());
          addAllFields(memberType, pts);
        }
      }
    } else if (type instanceof CArrayType) {
      final CType elementType = CTypeUtils.simplifyType(((CArrayType) type).getType());
      addAllFields(elementType, pts);
    }
  }

  void addPreFilledBase(final String base,
                        final CType type,
                        final boolean prepared,
                        final boolean forcePreFill,
                        final Constraints constraints,
                        final PointerTargetSetBuilder pts) {
    if (!prepared) {
      constraints.addConstraint(pts.addBase(base, type));
    } else {
      pts.shareBase(base, type);
    }
    if (forcePreFill ||
        (options.maxPreFilledAllocationSize() > 0 && getSizeof(type) <= options.maxPreFilledAllocationSize())) {
      addAllFields(type, pts);
    }
  }

  private void declareSharedBase(final CDeclaration declaration, final boolean shareImmediately,
      final Constraints constraints, final PointerTargetSetBuilder pts) {
    if (shareImmediately) {
      addPreFilledBase(declaration.getQualifiedName(), declaration.getType(), false, false, constraints, pts);
    } else if (isAddressedVariable(declaration) ||
               CTypeUtils.containsArray(declaration.getType())) {
      constraints.addConstraint(pts.prepareBase(declaration.getQualifiedName(),
                                                CTypeUtils.simplifyType(declaration.getType())));
    }
  }

  void addValueImportConstraints(final CFAEdge cfaEdge,
                                 final Formula address,
                                 final Variable base,
                                 final List<Pair<CCompositeType, String>> fields,
                                 final SSAMapBuilder ssa,
                                 final Constraints constraints,
                                 final PointerTargetSetBuilder pts) throws UnrecognizedCCodeException {
    final CType baseType = CTypeUtils.simplifyType(base.getType());
    if (baseType instanceof CArrayType) {
      throw new UnrecognizedCCodeException("Array access can't be encoded as a varaible", cfaEdge);
    } else if (baseType instanceof CCompositeType) {
      final CCompositeType compositeType = (CCompositeType) baseType;
      assert compositeType.getKind() != ComplexTypeKind.ENUM : "Enums are not composite: " + compositeType;
      int offset = 0;
      for (final CCompositeTypeMemberDeclaration memberDeclaration : compositeType.getMembers()) {
        final String memberName = memberDeclaration.getName();
        final CType memberType = CTypeUtils.simplifyType(memberDeclaration.getType());
        final Variable newBase = Variable.create(base.getName() + FIELD_NAME_SEPARATOR + memberName,
                                                 memberType);
        if (hasIndex(newBase.getName(), newBase.getType(), ssa) &&
            isRelevantField(compositeType, memberName)) {
          fields.add(Pair.of(compositeType, memberName));
          addValueImportConstraints(cfaEdge,
                                    fmgr.makePlus(address, fmgr.makeNumber(voidPointerFormulaType, offset)),
                                    newBase,
                                    fields,
                                    ssa,
                                    constraints,
                                    pts);
        }
        if (compositeType.getKind() == ComplexTypeKind.STRUCT) {
          offset += getSizeof(memberType);
        }
      }
    } else {
      // Make sure to not add invalid-deref constraints for this dereference
      constraints.addConstraint(fmgr.makeEqual(makeSafeDereference(baseType, address, ssa),
                                               makeVariable(base, ssa)));
    }
  }

  private void addRetentionConstraints(final PointerTargetPattern pattern,
                                       final CType lvalueType,
                                       final String ufName,
                                       final int oldIndex,
                                       final int newIndex,
                                       final FormulaType<?> returnType,
                                       final Formula lvalue,
                                       final Constraints constraints,
                                       final PointerTargetSetBuilder pts) {
    if (!pattern.isExact()) {
      for (final PointerTarget target : pts.getMatchingTargets(lvalueType, pattern)) {
        final Formula targetAddress = fmgr.makePlus(fmgr.makeVariable(voidPointerFormulaType, target.getBaseName()),
                                                    fmgr.makeNumber(voidPointerFormulaType, target.getOffset()));
        final BooleanFormula updateCondition = fmgr.makeEqual(targetAddress, lvalue);
        final BooleanFormula retention = fmgr.makeEqual(ffmgr.createFuncAndCall(ufName,
                                                                                newIndex,
                                                                                returnType,
                                                                                ImmutableList.of(targetAddress)),
                                                        ffmgr.createFuncAndCall(ufName,
                                                                                oldIndex,
                                                                                returnType,
                                                                                ImmutableList.of(targetAddress)));
       constraints.addConstraint(bfmgr.or(updateCondition, retention));
      }
    }
    for (final PointerTarget target : pts.getSpuriousTargets(lvalueType, pattern)) {
      final Formula targetAddress = fmgr.makePlus(fmgr.makeVariable(voidPointerFormulaType, target.getBaseName()),
                                                  fmgr.makeNumber(voidPointerFormulaType, target.getOffset()));
      constraints.addConstraint(fmgr.makeEqual(ffmgr.createFuncAndCall(ufName,
                                                                       newIndex,
                                                                       returnType,
                                                                       ImmutableList.of(targetAddress)),
                                               ffmgr.createFuncAndCall(ufName,
                                                                       oldIndex,
                                                                       returnType,
                                                                       ImmutableList.of(targetAddress))));
    }
  }

  private void addSemiexactRetentionConstraints(final PointerTargetPattern pattern,
                                                final CType firstElementType,
                                                final Formula startAddress,
                                                final int size,
                                                final Set<CType> types,
                                                final SSAMapBuilder ssa,
                                                final Constraints constraints,
                                                final PointerTargetSetBuilder pts) {
    final PointerTargetPattern exact = new PointerTargetPattern();
    for (final PointerTarget target : pts.getMatchingTargets(firstElementType, pattern)) {
      final Formula candidateAddress = fmgr.makePlus(fmgr.makeVariable(voidPointerFormulaType, target.getBaseName()),
                                                     fmgr.makeNumber(voidPointerFormulaType, target.getOffset()));
      final BooleanFormula negAntecedent = bfmgr.not(fmgr.makeEqual(candidateAddress, startAddress));
      exact.setBase(target.getBase());
      exact.setRange(target.getOffset(), size);
      BooleanFormula consequent = bfmgr.makeBoolean(true);
      for (final CType type : types) {
        final String ufName = getUFName(type);
        final int oldIndex = getIndex(ufName, type, ssa);
        final int newIndex = oldIndex + 1;
        final FormulaType<?> returnType = getFormulaTypeFromCType(type);
        for (final PointerTarget spurious : pts.getSpuriousTargets(type, exact)) {
          final Formula targetAddress = fmgr.makePlus(fmgr.makeVariable(voidPointerFormulaType, spurious.getBaseName()),
                                                      fmgr.makeNumber(voidPointerFormulaType, spurious.getOffset()));
          consequent = bfmgr.and(consequent, fmgr.makeEqual(ffmgr.createFuncAndCall(ufName,
                                                                                    newIndex,
                                                                                    returnType,
                                                                                    ImmutableList.of(targetAddress)),
                                                            ffmgr.createFuncAndCall(ufName,
                                                                                    oldIndex,
                                                                                    returnType,
                                                                                    ImmutableList.of(targetAddress))));
        }
      }
      constraints.addConstraint(bfmgr.or(negAntecedent, consequent));
    }
  }

  private void addInexactRetentionConstraints(final Formula startAddress,
                                              final int size,
                                              final Set<CType> types,
                                              final SSAMapBuilder ssa,
                                              final Constraints constraints,
                                              final PointerTargetSetBuilder pts) {
    final PointerTargetPattern any = new PointerTargetPattern();
    for (final CType type : types) {
      final String ufName = getUFName(type);
      final int oldIndex = getIndex(ufName, type, ssa);
      final int newIndex = oldIndex + 1;
      final FormulaType<?> returnType = getFormulaTypeFromCType(type);
      for (final PointerTarget spurious : pts.getSpuriousTargets(type, any)) {
        final Formula targetAddress = fmgr.makePlus(fmgr.makeVariable(voidPointerFormulaType, spurious.getBaseName()),
                                      fmgr.makeNumber(voidPointerFormulaType, spurious.getOffset()));
        final Formula endAddress = fmgr.makePlus(startAddress, fmgr.makeNumber(voidPointerFormulaType, size));
        constraints.addConstraint(bfmgr.or(bfmgr.and(fmgr.makeLessOrEqual(startAddress, targetAddress, false),
                                                     fmgr.makeLessOrEqual(targetAddress, endAddress,false)),
                                           fmgr.makeEqual(ffmgr.createFuncAndCall(ufName,
                                                                                  newIndex,
                                                                                  returnType,
                                                                                  ImmutableList.of(targetAddress)),
                                           ffmgr.createFuncAndCall(ufName,
                                                                   oldIndex,
                                                                   returnType,
                                                                   ImmutableList.of(targetAddress)))));
      }
    }
  }

  private void updateSSA(final @Nonnull Set<CType> types, final SSAMapBuilder ssa) {
    for (final CType type : types) {
      final String ufName = getUFName(type);
      final int newIndex = getIndex(ufName, type, ssa) + 1;
      ssa.setIndex(ufName, type, newIndex);
    }
  }

  BooleanFormula makeAssignment(@Nonnull CType lvalueType,
                                final @Nonnull CType rvalueType,
                                final @Nonnull Location lvalue,
                                final @Nonnull Expression rvalue,
                                final @Nullable PointerTargetPattern pattern,
                                final boolean useOldSSAIndices,
                                      @Nullable Set<CType> updatedTypes,
                                final @Nonnull CFAEdge edge,
                                final @Nonnull SSAMapBuilder ssa,
                                final @Nonnull Constraints constraints,
                                final @Nullable ErrorConditions errorConditions,
                                final @Nonnull PointerTargetSetBuilder pts)
  throws UnrecognizedCCodeException {
    // Its a definite value assignment, a nondet assignment (SSA index update) or a nondet assignment among other
    // assignments to the same UF version (in this case an absense of aliasing should be somehow guaranteed, as in the
    // case of initialization assignments)
    //assert rvalue != null || !useOldSSAIndices || updatedTypes != null; // otherwise the call is useless
    checkNotNull(rvalue);

    lvalueType = CTypeUtils.simplifyType(lvalueType);

    if (lvalue.isAliased() && !isSimpleType(lvalueType) && updatedTypes == null) {
      updatedTypes = new HashSet<>();
    } else {
      updatedTypes = null;
    }
    Set<Variable> updatedVariables = null;
    if (!lvalue.isAliased() && !isSimpleType(lvalueType)) {
      updatedVariables = new HashSet<>();
    }

    final BooleanFormula result = makeDestructiveAssignment(lvalueType, rvalueType,
                                                            lvalue, rvalue,
                                                            useOldSSAIndices,
                                                            updatedTypes,
                                                            updatedVariables,
                                                            edge, ssa, errorConditions, pts);

    if (!useOldSSAIndices) {
      if (lvalue.isAliased()) {
        addRetentionForAssignment(lvalueType,
                                  lvalue.asAliased().getAddress(),
                                  pattern, updatedTypes, edge, ssa, constraints, pts);
        if (updatedTypes == null) {
          assert isSimpleType(lvalueType) : "Should be impossible due to the first if statement";
          updatedTypes = Collections.singleton(lvalueType);
        }
        updateSSA(updatedTypes, ssa);
      } else { // Unaliased lvalue
        if (updatedVariables == null) {
          assert isSimpleType(lvalueType) : "Should be impossible due to the first if statement";
          updatedVariables = Collections.singleton(Variable.create(lvalue.asUnaliased().getVariableName(), lvalueType));
        }
        for (final Variable variable : updatedVariables) {
          final String name = variable.getName();
          final CType type = variable.getType();
          ssa.setIndex(name, type, getIndex(name, type, ssa) + 1);
        }
      }
    }
    return result;
  }

  void finishAssignments(@Nonnull CType lvalueType,
                         final @Nonnull AliasedLocation lvalue,
                         final @Nonnull PointerTargetPattern pattern,
                         final @Nonnull Set<CType> updatedTypes,
                         final @Nonnull CFAEdge edge,
                         final @Nonnull SSAMapBuilder ssa,
                         final @Nonnull Constraints constraints,
                         final @Nonnull PointerTargetSetBuilder pts) {
    addRetentionForAssignment(lvalueType,
                              lvalue.asAliased().getAddress(),
                              pattern, updatedTypes, edge, ssa, constraints, pts);
    updateSSA(updatedTypes, ssa);
  }

  private void addRetentionForAssignment(@Nonnull CType lvalueType,
                                         final @Nullable Formula startAddress,
                                         final @Nonnull PointerTargetPattern pattern,
                                         final Set<CType> typesToRetain,
                                         final @Nonnull CFAEdge edge,
                                         final @Nonnull SSAMapBuilder ssa,
                                         final @Nonnull Constraints constraints,
                                         final @Nonnull PointerTargetSetBuilder pts) {
    lvalueType = CTypeUtils.simplifyType(lvalueType);
    final int size = getSizeof(lvalueType);
    if (isSimpleType(lvalueType)) {
      Preconditions.checkArgument(startAddress != null,
                                  "Start address is mandatory for assigning to lvalues of simple types");
      final String ufName = getUFName(lvalueType);
      final int oldIndex = getIndex(ufName, lvalueType, ssa);
      final int newIndex = oldIndex + 1;
      final FormulaType<?> targetType = getFormulaTypeFromCType(lvalueType);
      addRetentionConstraints(pattern,
                              lvalueType,
                              ufName,
                              oldIndex,
                              newIndex,
                              targetType,
                              startAddress,
                              constraints,
                              pts);
    } else if (pattern.isExact()) {
      pattern.setRange(size);
      for (final CType type : typesToRetain) {
        final String ufName = getUFName(type);
        final int oldIndex = getIndex(ufName, type, ssa);
        final int newIndex = oldIndex + 1;
        final FormulaType<?> targetType = getFormulaTypeFromCType(type);
        addRetentionConstraints(pattern, type, ufName, oldIndex, newIndex, targetType, null, constraints, pts);
      }
    } else if (pattern.isSemiexact()) {
      Preconditions.checkArgument(startAddress != null,
                                  "Start address is mandatory for semiexact pointer target patterns");
      // For semiexact retention constraints we need the first element type of the composite
      if (lvalueType instanceof CArrayType) {
        lvalueType = CTypeUtils.simplifyType(((CArrayType) lvalueType).getType());
      } else { // CCompositeType
        lvalueType = CTypeUtils.simplifyType(((CCompositeType) lvalueType).getMembers().get(0).getType());
      }
      addSemiexactRetentionConstraints(pattern, lvalueType, startAddress, size, typesToRetain,
                                       ssa, constraints, pts);
    } else { // Inexact pointer target pattern
      Preconditions.checkArgument(startAddress != null,
                                  "Start address is mandatory for inexact pointer target patterns");
      addInexactRetentionConstraints(startAddress, size, typesToRetain, ssa, constraints, pts);
    }
  }

  private Pair<AliasedLocation, CType> shiftArrayLvalue(final AliasedLocation lvalue,
                                                        final int offset,
                                                        final CType lvalueElementType) {
    final Formula offsetFormula = fmgr.makeNumber(voidPointerFormulaType, offset);
    final AliasedLocation newLvalue = Location.ofAddress(fmgr.makePlus(lvalue.getAddress(), offsetFormula));
    return Pair.of(newLvalue, lvalueElementType);
  }

  private Pair<? extends Expression, CType> shiftArrayRvalue(final Expression rvalue,
                                                             final CType rvalueType,
                                                             final int offset,
                                                             final CType lvalueElementType) {
    // Support both initialization (with a value or nondet) and assignment (from another array location)
    switch(rvalue.getKind()) {
    case ALIASED_LOCATION: {
      assert rvalueType instanceof CArrayType : "Non-array rvalue in array assignment";
      final Formula offsetFormula = fmgr.makeNumber(voidPointerFormulaType, offset);
      final AliasedLocation newRvalue = Location.ofAddress(fmgr.makePlus(rvalue.asAliasedLocation().getAddress(),
                                                           offsetFormula));
      final CType newRvalueType = CTypeUtils.simplifyType(((CArrayType) rvalueType).getType());
      return Pair.of(newRvalue, newRvalueType);
    }
    case DET_VALUE: {
      return Pair.of(rvalue, rvalueType);
    }
    case NONDET: {
      final CType newLvalueType = isSimpleType(lvalueElementType) ? lvalueElementType : CNumericTypes.SIGNED_CHAR;
      return Pair.of(Value.nondetValue(), newLvalueType);
    }
    case UNALIASED_LOCATION: {
      throw new AssertionError("Array locations should always be aliased");
    }
    default: throw new AssertionError();
    }
  }

  private Pair<? extends Location, CType> shiftCompositeLvalue(final Location lvalue,
                                                               final int offset,
                                                               final String memberName,
                                                               final CType memberType) {
    final CType newLvalueType = CTypeUtils.simplifyType(memberType);
    if (lvalue.isAliased()) {
      final Formula offsetFormula = fmgr.makeNumber(voidPointerFormulaType, offset);
      final AliasedLocation newLvalue = Location.ofAddress(fmgr.makePlus(lvalue.asAliased().getAddress(),
                                                                         offsetFormula));
      return Pair.of(newLvalue, newLvalueType);

    } else {
      final UnaliasedLocation newLvalue = Location.ofVariableName(lvalue.asUnaliased().getVariableName() +
                                                                  FIELD_NAME_SEPARATOR + memberName);
      return Pair.of(newLvalue, newLvalueType);
    }

  }

  private Pair<? extends Expression, CType> shiftCompositeRvalue(final Expression rvalue,
                                                                 final int offset,
                                                                 final String memberName,
                                                                 final CType rvalueType,
                                                                 final CType memberType) {
    // Support both structure assignment and initialization with a value (or nondet)
    final CType newLvalueType = CTypeUtils.simplifyType(memberType);
    switch (rvalue.getKind()) {
    case ALIASED_LOCATION: {
      final Formula offsetFormula = fmgr.makeNumber(voidPointerFormulaType, offset);
      final AliasedLocation newRvalue = Location.ofAddress(fmgr.makePlus(rvalue.asAliasedLocation().getAddress(),
                                                                         offsetFormula));
      return Pair.of(newRvalue, newLvalueType);
    }
    case UNALIASED_LOCATION: {
      final UnaliasedLocation newRvalue = Location.ofVariableName(rvalue.asUnaliasedLocation().getVariableName() +
                                                                  FIELD_NAME_SEPARATOR +
                                                                  memberName);
      return Pair.of(newRvalue, newLvalueType);
    }
    case DET_VALUE: {
      return Pair.of(rvalue, rvalueType);
    }
    case NONDET: {
      final CType newRvalueType = isSimpleType(newLvalueType) ? newLvalueType : CNumericTypes.SIGNED_CHAR;
      return Pair.of(Value.nondetValue(), newRvalueType);
    }
    default: throw new AssertionError();
    }
  }

  private BooleanFormula makeDestructiveAssignment(@Nonnull CType lvalueType,
                                                   @Nonnull CType rvalueType,
                                                   final @Nonnull  Location lvalue,
                                                   final @Nonnull  Expression rvalue,
                                                   final boolean useOldSSAIndices,
                                                   final @Nullable Set<CType> updatedTypes,
                                                   final @Nullable Set<Variable> updatedVariables,
                                                   final @Nonnull CFAEdge edge,
                                                   final @Nonnull SSAMapBuilder ssa,
                                                   final @Nullable ErrorConditions errorConditions,
                                                   final @Nonnull PointerTargetSetBuilder pts)
  throws UnrecognizedCCodeException {
    lvalueType = CTypeUtils.simplifyType(lvalueType);
    rvalueType = CTypeUtils.simplifyType(rvalueType);
    BooleanFormula result;

    if (lvalueType instanceof CArrayType) {
      Preconditions.checkArgument(lvalue.isAliased(),
                                  "Array elements are always aliased (i.e. can't be encoded with variables)");
      final CArrayType lvalueArrayType = (CArrayType) lvalueType;
      final CType lvalueElementType = CTypeUtils.simplifyType(lvalueArrayType.getType());

      // There are only two cases of assignment to an array
      Preconditions.checkArgument(
        // Initializing array with a value (possibly nondet), useful for stack declarations and memset implementation
        rvalue.isValue() && isSimpleType(rvalueType) ||
        // Array assignment (needed for structure assignment implementation)
        // Only possible from another array of the same type
        rvalue.asLocation().isAliased() &&
        rvalueType instanceof CArrayType &&
        CTypeUtils.simplifyType(((CArrayType) rvalueType).getType()).equals(lvalueElementType),
        "Impossible array assignment due to incompatible types: assignment of %s to %s",
        rvalueType, lvalueType);

      Integer length = CTypeUtils.getArrayLength(lvalueArrayType);
      // Try to fix the length if it's unknown (or too big)
      // Also ignore the tail part of very long arrays to avoid very large formulae (imprecise!)
      if (length == null || length > options.maxArrayLength()) {
        final Integer rLength;
        if (rvalue.isLocation() &&
            (rLength = CTypeUtils.getArrayLength((CArrayType) rvalueType)) != null &&
            rLength <= options.maxArrayLength()) {
          length = rLength;
        } else {
          length = options.defaultArrayLength();
        }
      }

      result = bfmgr.makeBoolean(true);
      int offset = 0;
      for (int i = 0; i < length; ++i) {
        final Pair<AliasedLocation, CType> newLvalue = shiftArrayLvalue(lvalue.asAliased(), offset, lvalueElementType);
        final Pair<? extends Expression, CType> newRvalue =
                                                       shiftArrayRvalue(rvalue, rvalueType, offset, lvalueElementType);

        result = bfmgr.and(result,
                           makeDestructiveAssignment(newLvalue.getSecond(),
                                                     newRvalue.getSecond(),
                                                     newLvalue.getFirst(),
                                                     newRvalue.getFirst(),
                                                     useOldSSAIndices,
                                                     updatedTypes,
                                                     updatedVariables,
                                                     edge,
                                                     ssa,
                                                     errorConditions,
                                                     pts));
         offset += getSizeof(lvalueArrayType.getType());
      }
      return result;
    } else if (lvalueType instanceof CCompositeType) {
      final CCompositeType lvalueCompositeType = (CCompositeType) lvalueType;
      assert lvalueCompositeType.getKind() != ComplexTypeKind.ENUM : "Enums are not composite: " + lvalueCompositeType;
      // There are two cases of assignment to a structure/union
      Preconditions.checkArgument(
          // Initialization with a value (possibly nondet), useful for stack declarations and memset implementation
          rvalue.isValue() && isSimpleType(rvalueType) ||
          // Structure assignment
          rvalueType.equals(lvalueType),
          "Impossible structure assignment due to incompatible types: assignment of %s to %s",
          rvalueType, lvalueType);
      result = bfmgr.makeBoolean(true);
      int offset = 0;
      for (final CCompositeTypeMemberDeclaration memberDeclaration : lvalueCompositeType.getMembers()) {
        final String memberName = memberDeclaration.getName();
        final CType newLvalueType = CTypeUtils.simplifyType(memberDeclaration.getType());
        // Optimizing away the assignments from uninitialized fields
        if (isRelevantField(lvalueCompositeType, memberName) &&
             (!lvalue.isAliased() || // Assignment to a variable, no profit in optimizing it
              !isSimpleType(newLvalueType) || // That's not a simple assignment, check the nested composite
               rvalue.isValue() || // This is initialization, so the assignment is mandatory
               pts.tracksField(lvalueCompositeType, memberName) || // The field is tracked as essential
               // The variable representing the RHS was used somewhere (i.e. has SSA index)
               !rvalue.asLocation().isAliased() &&
                 hasIndex(rvalue.asLocation().asUnaliased().getVariableName() +
                            FIELD_NAME_SEPARATOR +
                            memberName,
                          newLvalueType,
                          ssa))) {
          final Pair<? extends Location, CType> newLvalue =
                                         shiftCompositeLvalue(lvalue, offset, memberName, memberDeclaration.getType());
          final Pair<? extends Expression, CType> newRvalue =
                             shiftCompositeRvalue(rvalue, offset, memberName, rvalueType, memberDeclaration.getType());

          result = bfmgr.and(result,
                             makeDestructiveAssignment(newLvalue.getSecond(),
                                                       newRvalue.getSecond(),
                                                       newLvalue.getFirst(),
                                                       newRvalue.getFirst(),
                                                       useOldSSAIndices,
                                                       updatedTypes,
                                                       updatedVariables,
                                                       edge,
                                                       ssa,
                                                       errorConditions,
                                                       pts));
        }

        if (lvalueCompositeType.getKind() == ComplexTypeKind.STRUCT) {
          offset += getSizeof(memberDeclaration.getType());
        }
      }
      return result;
    } else { // Simple assignment
      return makeSimpleDestructiveAssignment(lvalueType,
                                             rvalueType,
                                             lvalue,
                                             rvalue,
                                             useOldSSAIndices,
                                             updatedTypes,
                                             updatedVariables,
                                             edge,
                                             ssa,
                                             errorConditions);
    }
  }

  private BooleanFormula makeSimpleDestructiveAssignment(@Nonnull CType lvalueType,
                                                         @Nonnull CType rvalueType,
                                                         final @Nonnull Location lvalue,
                                                               @Nonnull Expression rvalue,
                                                         final boolean useOldSSAIndices,
                                                         final @Nullable Set<CType> updatedTypes,
                                                         final @Nullable Set<Variable> updatedVariables,
                                                         final @Nonnull CFAEdge edge,
                                                         final @Nonnull SSAMapBuilder ssa,
                                                         final @Nullable ErrorConditions errorConditions)
  throws UnrecognizedCCodeException {
    lvalueType = CTypeUtils.simplifyType(lvalueType);
    rvalueType = CTypeUtils.simplifyType(rvalueType);
    rvalueType = implicitCastToPointer(rvalueType); // Arrays and functions are implicitly converted to pointers

    Preconditions.checkArgument(isSimpleType(lvalueType),
                                "To assign to/from arrays/structures/unions use makeDestructiveAssignment");
    Preconditions.checkArgument(isSimpleType(rvalueType),
                                "To assign to/from arrays/structures/unions use makeDestructiveAssignment");

    final Formula value;
    switch (rvalue.getKind()) {
    case ALIASED_LOCATION:
      value = makeDereference(rvalueType, rvalue.asAliasedLocation().getAddress(), ssa, errorConditions);
      break;
    case UNALIASED_LOCATION:
      value = makeVariable(rvalue.asUnaliasedLocation().getVariableName(), rvalueType, ssa);
      break;
    case DET_VALUE:
      value = rvalue.asValue().getValue();
      break;
    case NONDET:
      value = null;
      break;
    default: throw new AssertionError();
    }

    assert !(lvalueType instanceof CFunctionType) : "Can't assign to functions";

    final String targetName = !lvalue.isAliased() ? lvalue.asUnaliased().getVariableName() : getUFName(lvalueType);
    final FormulaType<?> targetType = getFormulaTypeFromCType(lvalueType);
    final int oldIndex = getIndex(targetName, lvalueType, ssa);
    final int newIndex = !useOldSSAIndices ? oldIndex + 1 : oldIndex;
    final BooleanFormula result;

    rvalueType = implicitCastToPointer(rvalueType);
    final Formula rhs = value != null ? makeCast(rvalueType, lvalueType, value, edge) : null;
    if (!lvalue.isAliased()) { // Unaliased LHS
      if (rhs != null) {
        result = fmgr.makeEqual(fmgr.makeVariable(targetType, targetName, newIndex), rhs);
      } else {
        result = bfmgr.makeBoolean(true);
      }

      if (updatedVariables != null) {
        updatedVariables.add(Variable.create(targetName, lvalueType));
      }
    } else { // Aliased LHS
      final Formula lhs = ffmgr.createFuncAndCall(targetName,
                                                  newIndex,
                                                  targetType,
                                                  ImmutableList.of(lvalue.asAliased().getAddress()));
      if (rhs != null) {
        result = fmgr.makeEqual(lhs, rhs);
      } else {
        result = bfmgr.makeBoolean(true);
      }

      if (updatedTypes != null) {
        updatedTypes.add(lvalueType);
      }
    }

    return result;
  }

  private static List<CCharLiteralExpression> expandStringLiteral(final CStringLiteralExpression e,
                                                                  final CArrayType type) {
    Integer length = CTypeUtils.getArrayLength(type);
    final String s = e.getContentString();
    if (length == null) {
      length = s.length() + 1;
    }
    assert length >= s.length();

    // http://stackoverflow.com/a/6915917
    // As the C99 Draft Specification's 32nd Example in §6.7.8 (p. 130) states
    // char s[] = "abc", t[3] = "abc"; is identical to: char s[] = { 'a', 'b', 'c', '\0' }, t[] = { 'a', 'b', 'c' };
    final List<CCharLiteralExpression> result = new ArrayList<>();
    for (int i = 0; i < s.length(); i++) {
      result.add(new CCharLiteralExpression(e.getFileLocation(), CNumericTypes.SIGNED_CHAR, s.charAt(i)));
    }


    // http://stackoverflow.com/questions/10828294/c-and-c-partial-initialization-of-automatic-structure
    // C99 Standard 6.7.8.21
    // If there are ... fewer characters in a string literal
    // used to initialize an array of known size than there are elements in the array,
    // the remainder of the aggregate shall be initialized implicitly ...
    for (int i = s.length(); i < length; i++) {
      result.add(new CCharLiteralExpression(e.getFileLocation(), CNumericTypes.SIGNED_CHAR, '\0'));
    }

    return result;
  }

  private static List<CExpressionAssignmentStatement> expandStringLiterals(
                                                 final List<CExpressionAssignmentStatement> assignments)
  throws UnrecognizedCCodeException {
    final List<CExpressionAssignmentStatement> result = new ArrayList<>();
    for (CExpressionAssignmentStatement assignment : assignments) {
      final CExpression rhs = assignment.getRightHandSide();
      if (rhs instanceof CStringLiteralExpression) {
        final CExpression lhs = assignment.getLeftHandSide();
        final CType lhsType = lhs.getExpressionType();
        final CArrayType lhsArrayType;
        if (lhsType instanceof CArrayType) {
          lhsArrayType = (CArrayType) lhsType;
        } else if (lhsType instanceof CPointerType) {
          lhsArrayType = new CArrayType(false, false, ((CPointerType) lhsType).getType(), null);
        } else {
          throw new UnrecognizedCCodeException("Assigning string literal to " + lhsType.toString(), assignment);
        }

        List<CCharLiteralExpression> chars = expandStringLiteral((CStringLiteralExpression) rhs, lhsArrayType);

        int offset = 0;
        for (CCharLiteralExpression e : chars) {
          result.add(new CExpressionAssignmentStatement(
                       assignment.getFileLocation(),
                       new CArraySubscriptExpression(lhs.getFileLocation(),
                                                     lhsArrayType.getType(),
                                                     lhs,
                                                     new CIntegerLiteralExpression(lhs.getFileLocation(),
                                                                                   CNumericTypes.INT,
                                                                                   BigInteger.valueOf(offset))),
                       e));
          offset++;
        }
      } else {
        result.add(assignment);
      }
    }
    return result;
  }

  static List<CExpressionAssignmentStatement> expandAssignmentList(
                                                final CVariableDeclaration declaration,
                                                final List<CExpressionAssignmentStatement> explicitAssignments) {
    final CType variableType = CTypeUtils.simplifyType(declaration.getType());
    final CLeftHandSide lhs = new CIdExpression(declaration.getFileLocation(),
                                                variableType,
                                                declaration.getName(),
                                                declaration);
    final Set<String> alreadyAssigned = new HashSet<>();
    for (CExpressionAssignmentStatement statement : explicitAssignments) {
      alreadyAssigned.add(statement.getLeftHandSide().toString());
    }
    final List<CExpressionAssignmentStatement> defaultAssignments = new ArrayList<>();
    expandAssignmentList(variableType, lhs, alreadyAssigned, defaultAssignments);
    defaultAssignments.addAll(explicitAssignments);
    return defaultAssignments;
  }

  private static void expandAssignmentList(CType type,
                                           final CLeftHandSide lhs,
                                           final Set<String> alreadyAssigned,
                                           final List<CExpressionAssignmentStatement> defaultAssignments) {
    type = CTypeUtils.simplifyType(type);
    if (type instanceof CArrayType) {
      final CArrayType arrayType = (CArrayType) type;
      final CType elementType = CTypeUtils.simplifyType(arrayType.getType());
      final Integer length = CTypeUtils.getArrayLength(arrayType);
      if (length != null) {
        for (int i = 0; i < length; i++) {
          final CLeftHandSide newLhs = new CArraySubscriptExpression(
                                             lhs.getFileLocation(),
                                             elementType,
                                             lhs,
                                             new CIntegerLiteralExpression(lhs.getFileLocation(),
                                                                           CNumericTypes.INT,
                                                                           BigInteger.valueOf(i)));
          expandAssignmentList(elementType, newLhs, alreadyAssigned, defaultAssignments);
        }
      }
    } else if (type instanceof CCompositeType) {
      final CCompositeType compositeType = (CCompositeType) type;
      for (final CCompositeTypeMemberDeclaration memberDeclaration : compositeType.getMembers()) {
        final CType memberType = memberDeclaration.getType();
        final CLeftHandSide newLhs = new CFieldReference(lhs.getFileLocation(),
                                                         memberType,
                                                         memberDeclaration.getName(),
                                                         lhs, false);
        expandAssignmentList(memberType, newLhs, alreadyAssigned, defaultAssignments);
      }
    } else {
      assert isSimpleType(type);
      final CExpression zero = new CIntegerLiteralExpression(lhs.getFileLocation(),
                                                             CNumericTypes.SIGNED_CHAR,
                                                             BigInteger.ZERO);
      if (!alreadyAssigned.contains(lhs.toString())) {
        defaultAssignments.add(new CExpressionAssignmentStatement(lhs.getFileLocation(), lhs, zero));
      }
    }
  }

  @Override
  protected PointerTargetSetBuilder createPointerTargetSetBuilder(PointerTargetSet pts) {
    return new RealPointerTargetSetBuilder(pts, fmgr, ptsMgr, options);
  }

  private LvalueToPointerTargetPatternVisitor getLvalueToPointerTargetPatternVisitor(
    final CFAEdge cfaEdge,
    final PointerTargetSetBuilder pts) {
    return new LvalueToPointerTargetPatternVisitor(this, cfaEdge, pts);
  }

  private StatementToFormulaWithUFVisitor getStatementToFormulaWithUFVisitor(final CFAEdge cfaEdge,
                                                                             final String function,
                                                                             final SSAMapBuilder ssa,
                                                                             final Constraints constraints,
                                                                             final @Nullable ErrorConditions errorConditions,
                                                                             final PointerTargetSetBuilder pts) {
    final LvalueToPointerTargetPatternVisitor lvalueVisitor = getLvalueToPointerTargetPatternVisitor(cfaEdge, pts);
    return new StatementToFormulaWithUFVisitor(lvalueVisitor, this, cfaEdge, function, ssa, constraints, errorConditions, pts);
  }

  @Override
  protected BooleanFormula makeReturn(final CExpression resultExpression,
                                      final CReturnStatementEdge returnEdge,
                                      final String function,
                                      final SSAMapBuilder ssa,
                                      final PointerTargetSetBuilder pts,
                                      final Constraints constraints,
                                      final ErrorConditions errorConditions)
  throws CPATransferException {
    BooleanFormula result = super.makeReturn(resultExpression, returnEdge, function, ssa, pts, constraints, errorConditions);

    if (resultExpression != null) {
      final CFunctionDeclaration functionDeclaration =
          ((CFunctionEntryNode) returnEdge.getSuccessor().getEntryNode()).getFunctionDefinition();

      final CVariableDeclaration returnVariableDeclaraton = createReturnVariable(functionDeclaration);
      final boolean containsArray = CTypeUtils.containsArray(returnVariableDeclaraton.getType());

      declareSharedBase(returnVariableDeclaraton, containsArray, constraints, pts);
    }
    return result;
  }

  @Override
  protected BooleanFormula makeAssignment(
      final CLeftHandSide lhs, final CExpression rhs,
      final CFAEdge edge, final String function,
      final SSAMapBuilder ssa, final PointerTargetSetBuilder pts,
      final Constraints constraints, final ErrorConditions errorConditions)
          throws UnrecognizedCCodeException {

    final StatementToFormulaWithUFVisitor statementVisitor = getStatementToFormulaWithUFVisitor(edge, function, ssa, constraints, errorConditions, pts);
    return statementVisitor.handleAssignment(lhs, rhs, false, null);
  }

  private static String getLogMessage(final String msg, final CFAEdge edge) {
    return "Line " + edge.getLineNumber()
            + ": " + msg
            + ": " + edge.getDescription();
  }

  private void logDebug(final String msg, final CFAEdge edge) {
    if (logger.wouldBeLogged(Level.ALL)) {
      logger.log(Level.ALL, getLogMessage(msg, edge));
    }
  }

  @Override
  protected BooleanFormula makeStatement(
      final CStatementEdge statement, final String function,
      final SSAMapBuilder ssa, final PointerTargetSetBuilder pts,
      final Constraints constraints, final ErrorConditions errorConditions)
          throws CPATransferException {
    final StatementToFormulaWithUFVisitor statementVisitor =
        getStatementToFormulaWithUFVisitor(statement, function, ssa, constraints, errorConditions, pts);
    return statement.getStatement().accept(statementVisitor);
  }

  @Override
  protected BooleanFormula makeDeclaration(
      final CDeclarationEdge declarationEdge, final String function,
      final SSAMapBuilder ssa, final PointerTargetSetBuilder pts,
      final Constraints constraints, final ErrorConditions errorConditions)
          throws CPATransferException {

    if (declarationEdge.getDeclaration() instanceof CTypeDeclaration) {
      final CType declarationType = CTypeUtils.simplifyType(
                                      ((CTypeDeclaration) declarationEdge.getDeclaration()).getType());
      if (declarationType instanceof CCompositeType) {
        typeHandler.addCompositeTypeToCache((CCompositeType) declarationType);
      }
    }

    if (!(declarationEdge.getDeclaration() instanceof CVariableDeclaration)) {
      // function declaration, typedef etc.
      logDebug("Ignoring declaration", declarationEdge);
      return bfmgr.makeBoolean(true);
    }

    CVariableDeclaration declaration = (CVariableDeclaration) declarationEdge.getDeclaration();

    // makeFreshIndex(variableName, declaration.getType(), ssa); // TODO: Make sure about
                                                                 // correctness of SSA indices without this trick!

    CType declarationType = CTypeUtils.simplifyType(declaration.getType());

    if (!isRelevantVariable(declaration) &&
        !isAddressedVariable(declaration)) {
      // The variable is unused
      logDebug("Ignoring declaration of unused variable", declarationEdge);
      return bfmgr.makeBoolean(true);
    }

    if (errorConditions != null) {
      final Formula address = makeConstant(PointerTargetSet.getBaseName(declaration.getQualifiedName()),
                                           CTypeUtils.getBaseType(declarationType));
      constraints.addConstraint(fmgr.makeEqual(makeBaseAddressOfTerm(address), address));
    }

    // if there is an initializer associated to this variable,
    // take it into account
    final CInitializer initializer = declaration.getInitializer();

    // Fixing unsized array declarations
    if (declarationType instanceof CArrayType && ((CArrayType) declarationType).getLength() == null) {
      final Integer actualLength;
      if (initializer instanceof  CInitializerList) {
        actualLength = ((CInitializerList) initializer).getInitializers().size();
      } else if (initializer instanceof CInitializerExpression &&
                 ((CInitializerExpression) initializer).getExpression() instanceof CStringLiteralExpression) {
        actualLength = ((CStringLiteralExpression) ((CInitializerExpression) initializer).getExpression())
                         .getContentString()
                         .length() + 1;
      } else {
        actualLength = null;
      }

      if (actualLength != null) {
        declarationType = new CArrayType(declarationType.isConst(),
                                         declarationType.isVolatile(),
                                         ((CArrayType) declarationType).getType(),
                                         new CIntegerLiteralExpression(declaration.getFileLocation(),
                                                                       machineModel.getPointerDiffType(),
                                                                       BigInteger.valueOf(actualLength)));

        declaration = new CVariableDeclaration(declaration.getFileLocation(),
                                               declaration.isGlobal(),
                                               declaration.getCStorageClass(),
                                               declarationType,
                                               declaration.getName(),
                                               declaration.getOrigName(),
                                               declaration.getQualifiedName(),
                                               initializer);
      }
    }

    // Special handling for string literal initializers -- convert them into character arrays
    final CIdExpression lhs =
        new CIdExpression(declaration.getFileLocation(), declarationType, declaration.getName(), declaration);
    final StatementToFormulaWithUFVisitor statementVisitor = getStatementToFormulaWithUFVisitor(declarationEdge, function, ssa, constraints, errorConditions, pts);
    if (initializer instanceof CInitializerExpression || initializer == null) {
      declareSharedBase(declaration, false, constraints, pts);

      final BooleanFormula result;
      if (initializer != null) {
        result = statementVisitor.handleAssignment(lhs, ((CInitializerExpression) initializer).getExpression(), false, null);
      } else if (isRelevantVariable(declaration)) {
        result = statementVisitor.handleAssignment(lhs, null, false, null);
      } else {
        result = bfmgr.makeBoolean(true);
      }

      if (CTypeUtils.containsArray(declarationType)) {
        addPreFilledBase(declaration.getQualifiedName(), declarationType, true, false, constraints, pts);
      }

      return result;
    } else if (initializer instanceof CInitializerList) {
      declareSharedBase(declaration, false, constraints, pts);

      List<CExpressionAssignmentStatement> assignments =
        CInitializers.convertToAssignments(declaration, declarationEdge);
      if (options.handleStringLiteralInitializers()) {
        assignments = expandStringLiterals(assignments);
      }
      if (options.handleImplicitInitialization()) {
        assignments = expandAssignmentList(declaration, assignments);
      }

      final BooleanFormula result = statementVisitor.handleInitializationAssignments(lhs, assignments);

      if (CTypeUtils.containsArray(declarationType)) {
        addPreFilledBase(declaration.getQualifiedName(), declarationType, true, false, constraints, pts);
      }

      return result;
    } else {
      throw new UnrecognizedCCodeException("Unrecognized initializer", declarationEdge, initializer);
    }
  }

  @Override
  protected BooleanFormula makeAssume(
      final CAssumeEdge assume, final String function,
      final SSAMapBuilder ssa, final PointerTargetSetBuilder pts,
      final Constraints constraints, final ErrorConditions errorConditions)
          throws CPATransferException {

    final StatementToFormulaWithUFVisitor statementVisitor = getStatementToFormulaWithUFVisitor(assume, function, ssa, constraints, errorConditions, pts);
    return statementVisitor.visitAssume(assume.getExpression(), assume.getTruthAssumption());
  }

  @Override
  protected BooleanFormula makeFunctionCall(
      final CFunctionCallEdge edge, final String callerFunction,
      final SSAMapBuilder ssa, final PointerTargetSetBuilder pts,
      final Constraints constraints, final ErrorConditions errorConditions)
          throws CPATransferException {

    final CFunctionEntryNode entryNode = edge.getSuccessor();
    BooleanFormula result = super.makeFunctionCall(edge, callerFunction, ssa, pts, constraints, errorConditions);

    for (CParameterDeclaration formalParameter : entryNode.getFunctionParameters()) {
      final CType parameterType = CTypeUtils.simplifyType(formalParameter.getType());
      declareSharedBase(formalParameter.asVariableDeclaration(), CTypeUtils.containsArray(parameterType), constraints, pts);
    }

    return result;
  }

  @Override
  protected BooleanFormula makeExitFunction(
      final CFunctionSummaryEdge summaryEdge, final String calledFunction,
      final SSAMapBuilder ssa, final PointerTargetSetBuilder pts,
      final Constraints constraints, final ErrorConditions errorConditions)
          throws CPATransferException {

    final BooleanFormula result = super.makeExitFunction(summaryEdge, calledFunction, ssa, pts, constraints, errorConditions);

    DynamicMemoryHandler memoryHandler = new DynamicMemoryHandler(this, summaryEdge, ssa, pts, constraints, errorConditions);
    memoryHandler.handleDeferredAllocationInFunctionExit(calledFunction);

    return result;
  }

  @SuppressWarnings("hiding") // same instance with narrower type
  final FormulaEncodingWithUFOptions options;

  private final Optional<VariableClassification> variableClassification;

  static final String UF_NAME_PREFIX = "*";

  static final String FIELD_NAME_SEPARATOR = "$";

  static final String FRESH_INDEX_SEPARATOR = "#";

  private static final Map<CType, String> ufNameCache = new IdentityHashMap<>();


  // Overrides just for visibility in other classes of this package

  @Override
  protected CType getPromotedCType(CType pT) {
    return super.getPromotedCType(pT);
  }

  @Override
  protected CType getReturnType(CFunctionCallExpression pFuncCallExp, CFAEdge pEdge) throws UnrecognizedCCodeException {
    return super.getReturnType(pFuncCallExp, pEdge);
  }

  @Override
  protected <T extends Formula> T ifTrueThenOneElseZero(FormulaType<T> pType, BooleanFormula pCond) {
    return super.ifTrueThenOneElseZero(pType, pCond);
  }

  @Override
  protected CExpression makeCastFromArrayToPointerIfNecessary(CExpression pExp, CType pTargetType) {
    return super.makeCastFromArrayToPointerIfNecessary(pExp, pTargetType);
  }

  @Override
  protected <T extends Formula> BooleanFormula toBooleanFormula(T pF) {
    return super.toBooleanFormula(pF);
  }

  @Override
  protected Formula makeCast(CType pFromType, CType pToType, Formula pFormula, CFAEdge pEdge)
      throws UnrecognizedCCodeException {
    return super.makeCast(pFromType, pToType, pFormula, pEdge);
  }

  @Override
  protected Formula makeConstant(String pName, CType pType) {
    return super.makeConstant(pName, pType);
  }

  @Override
  protected Formula makeConstant(Variable pVar) {
    return super.makeConstant(pVar);
  }

  @Override
  protected int getSizeof(CType pType) {
    return super.getSizeof(pType);
  }

  @Override
  protected boolean isRelevantLeftHandSide(CLeftHandSide pLhs) {
    return super.isRelevantLeftHandSide(pLhs);
  }
}
