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
package org.sosy_lab.cpachecker.util.predicates.pathformula.withUF;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes.VOID;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Triple;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentSortedMap;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.MachineModel.BaseSizeofVisitor;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CComplexType.ComplexTypeKind;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType.CCompositeTypeMemberDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CElaboratedType;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CTypeVisitor;
import org.sosy_lab.cpachecker.cfa.types.c.CTypedefType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CToFormulaWithUFConverter;
import org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.pointerTarget.PointerTarget;
import org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.pointerTarget.PointerTargetPattern;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;

public class PointerTargetSet implements Serializable {

  /**
   * The objects of the class are used to keep the set of currently tracked fields in a {@link PersistentSortedMap}.
   * Objects of {@link CompositeField} are used as keys and place-holders of type {@link Boolean} are used as values.
   * <p>
   * This allows one to check if a particular field is tracked using a temporary object of {@link CompositeField} and
   * keep the set of currently tracked fields in rather simple way (no special-case merging is required).
   * </p>
   */
  private static class CompositeField implements Comparable<CompositeField> {
    private CompositeField(final String compositeType, final String fieldName) {
      this.compositeType = compositeType;
      this.fieldName = fieldName;
    }

    public static CompositeField of(final @Nonnull String compositeType, final @Nonnull String fieldName) {
      return new CompositeField(compositeType, fieldName);
    }

//    public String compositeType() {
//      return compositeType;
//    }

//    public String fieldName() {
//      return fieldName;
//    }

    @Override
    public String toString() {
      return compositeType + "." + fieldName;
    }

    @Override
    public int compareTo(final CompositeField other) {
      final int result = this.compositeType.compareTo(other.compositeType);
      if (result != 0) {
        return result;
      } else {
        return this.fieldName.compareTo(other.fieldName);
      }
    }

    @Override
    public boolean equals(final Object obj) {
      if (this == obj) {
        return true;
      } else if (!(obj instanceof CompositeField)) {
        return false;
      } else {
        CompositeField other = (CompositeField) obj;
        return compositeType.equals(other.compositeType) && fieldName.equals(other.fieldName);
      }
    }

    @Override
    public int hashCode() {
      return compositeType.hashCode() * 17 + fieldName.hashCode();
    }

    private final String compositeType;
    private final String fieldName;
  }

  /**
   * Return the length of an array if statically given, or null.
   */
  public static Integer getArrayLength(CArrayType t) {

    final CExpression arrayLength = t.getLength();
    if (arrayLength instanceof CIntegerLiteralExpression) {
      return ((CIntegerLiteralExpression)arrayLength).getValue().intValue();
    }

    return null;
  }

  public static class CSizeofVisitor extends BaseSizeofVisitor
                                     implements CTypeVisitor<Integer, IllegalArgumentException> {

    public CSizeofVisitor(final MachineModel machineModel,
                           final FormulaEncodingWithUFOptions options) {
      super(machineModel);
      this.options = options;
    }

    @Override
    public Integer visit(final CArrayType t) throws IllegalArgumentException {
      Integer length = getArrayLength(t);

      if (length == null) {
        length = options.defaultArrayLength();
      }

      final int sizeOfType = t.getType().accept(this);
      return length * sizeOfType;
    }

    private final FormulaEncodingWithUFOptions options;
  }

  /**
   * The method is used to check if a composite type contains array as this means it can't be encoded as a bunch of
   * variables.
   * @param type any type to check, but normally a composite type
   * @return whether the {@code type} contains array
   */
  public static boolean containsArray(CType type) {
    type = simplifyType(type);
    if (type instanceof CArrayType) {
      return true;
    } else if (type instanceof CCompositeType) {
      final CCompositeType compositeType = (CCompositeType) type;
      assert compositeType.getKind() != ComplexTypeKind.ENUM : "Enums are not composite!";
      for (CCompositeTypeMemberDeclaration memberDeclaration : compositeType.getMembers()) {
        if (containsArray(memberDeclaration.getType())) {
          return true;
        }
      }
      return false;
    } else {
      return false;
    }
  }

  /**
   * <p>
   * The method returns the type of a base variable by the type of the given memory location.
   * </p>
   * <p>
   * Here we need special handling for arrays as their base variables are handled as pointers to their first
   * (zeroth) elements.
   * </p>
   * @param type The type of the memory location
   * @return The type of the base variable
   */
  public static CType getBaseType(CType type) {
    type = simplifyType(type);
    if (!(type instanceof CArrayType)) {
      return new CPointerType(false, false, type);
    } else {
      return new CPointerType(false, false, ((CArrayType) type).getType());
    }
  }

  public static String getBaseName(final String name){
    return BASE_PREFIX + name;
  }

  public static boolean isBaseName(final String name) {
    return name.startsWith(BASE_PREFIX);
  }

  public static String getBase(final String baseName) {
    return baseName.replaceFirst(BASE_PREFIX, "");
  }

  /**
   * <p>
   * The method should be used everywhere the type of any expression is determined. This is because the encoding uses
   * types for naming of the UFs as well as for over-approximating points-to sets (may-aliases). To make the encoding
   * precise enough the types should correspond to actually different types (requiring explicit cases to be
   * converted to one another), so {@link CCompositeType}s, corresponding  {@link CElaboratedType}s and
   * {@link CTypedefType}s shouldn't be distinguished and are converted to the same canonical type by this method.
   * </p>
   * <p>
   * This method will also perform {@code const} and {@code volatile} modifiers elimination.
   * </p>
   * @param type The type obtained form the CFA
   * @return The corresponding simplfied canonical type
   */
  public static CType simplifyType(final @Nonnull CType type) {
    return type.accept(typeVisitor);
  }

  /**
   * The method is used in two cases:
   * <ul>
   * <li>
   * by {@link CToFormulaWithUFConverter#getUFName(CType)} to get the UF name corresponding to the given type.
   * </li>
   * <li>
   * to convert {@link CType}s to strings in order to use them as keys in a {@link PathCopyingPersistentTreeMap}.
   * </li>
   * </ul>
   * @param type The type
   * @return The string representation of the type
   */
  public static String typeToString(final CType type) {
    return simplifyType(type).toString();
  }

  /**
   * The method is used to speed up {@code sizeof} computation by caching sizes of declared composite types.
   * @param cType
   * @return
   */
  public int getSize(CType cType) {
    cType = simplifyType(cType);
    if (cType instanceof CCompositeType) {
      if (sizes.contains(cType)) {
        return sizes.count(cType);
      } else {
        return cType.accept(sizeofVisitor);
      }
    } else {
      return cType.accept(sizeofVisitor);
    }
  }

  /**
   * The method is used to speed up member offset computation for declared composite types.
   * @param compositeType
   * @param memberName
   * @return
   */
  public int getOffset(CCompositeType compositeType, final String memberName) {
    compositeType = (CCompositeType) simplifyType(compositeType);
    assert compositeType.getKind() != ComplexTypeKind.ENUM : "Enums are not composite: " + compositeType;
    final Multiset<String> multiset = offsets.get(compositeType);
    assert multiset != null : "For handling undeclared composites used PointerTargetSetBuilder instead!";
    return multiset.count(memberName);
  }

  public Iterable<PointerTarget> getMatchingTargets(final CType type,
                                                    final PointerTargetPattern pattern) {
    return from(getAllTargets(type)).filter(pattern);
  }

  public Iterable<PointerTarget> getSpuriousTargets(final CType type,
                                                    final PointerTargetPattern pattern) {
    return from(getAllTargets(type)).filter(not(pattern));
  }

  public PersistentList<PointerTarget> getAllTargets(final CType type) {
    return firstNonNull(targets.get(typeToString(type)),
                        PersistentList.<PointerTarget>empty());
  }

  /**
   * Builder for PointerTargetSet. Its state starts with an existing set, but may be
   * changed later. It supports read access, but it is not recommended to use
   * instances of this class except for the short period of time
   * while creating a new set.
   *
   * This class is not thread-safe.
   */
  public final static class PointerTargetSetBuilder extends PointerTargetSet {

    private PointerTargetSetBuilder(final PointerTargetSet pointerTargetSet) {
      super(pointerTargetSet.machineModel,
            pointerTargetSet.sizeofVisitor,
            pointerTargetSet.options,
            pointerTargetSet.bases,
            pointerTargetSet.lastBase,
            pointerTargetSet.fields,
            pointerTargetSet.deferredAllocations,
            pointerTargetSet.targets,
            pointerTargetSet.formulaManager);
    }

    /**
     * Adds the declared composite type to the cache saving its size as well as the offset of every
     * member of the composite.
     * @param compositeType
     */
    public void addCompositeType(CCompositeType compositeType) {
      compositeType = (CCompositeType) simplifyType(compositeType);
      if (offsets.containsKey(compositeType)) {
        // Support for empty structs though it's a GCC extension
        assert sizes.contains(compositeType) || Integer.valueOf(0).equals(compositeType.accept(sizeofVisitor)) :
          "Illegal state of PointerTargetSet: no size for type:" + compositeType;
        return; // The type has already been added
      }

      final Integer size = compositeType.accept(sizeofVisitor);

      assert size != null : "Can't evaluate size of a composite type: " + compositeType;

      assert compositeType.getKind() != ComplexTypeKind.ENUM : "Enums are not composite: " + compositeType;

      final Multiset<String> members = HashMultiset.create();

      int offset = 0;
      for (final CCompositeTypeMemberDeclaration memberDeclaration : compositeType.getMembers()) {
        members.setCount(memberDeclaration.getName(), offset);
        final CType memberType = simplifyType(memberDeclaration.getType());
        final CCompositeType memberCompositeType;
        if (memberType instanceof CCompositeType) {
          memberCompositeType = (CCompositeType) memberType;
          if (memberCompositeType.getKind() == ComplexTypeKind.STRUCT ||
              memberCompositeType.getKind() == ComplexTypeKind.UNION) {
            if (!offsets.containsKey(memberCompositeType)) {
              assert !sizes.contains(memberCompositeType) :
                "Illegal state of PointerTargetSet: size for type:" + memberCompositeType;
              addCompositeType(memberCompositeType);
            }
          }
        } else {
          memberCompositeType = null;
        }
        if (compositeType.getKind() == ComplexTypeKind.STRUCT) {
          if (memberCompositeType != null) {
            offset += sizes.count(memberCompositeType);
          } else {
            offset += memberDeclaration.getType().accept(sizeofVisitor);
          }
        }
      }

      assert compositeType.getKind() != ComplexTypeKind.STRUCT || offset == size :
             "Incorrect sizeof or offset of the last member: " + compositeType;

      sizes.setCount(compositeType, size);
      offsets.put(compositeType, members);
    }

    private void addTarget(final String base,
                           final CType targetType,
                           final CType containerType,
                           final int properOffset,
                           final int containerOffset) {
      final String type = typeToString(targetType);
      PersistentList<PointerTarget> targetsForType = firstNonNull(targets.get(type),
                                                                  PersistentList.<PointerTarget>empty());
      targets = targets.putAndCopy(type, targetsForType.with(new PointerTarget(base,
                                                                               containerType,
                                                                               properOffset,
                                                                               containerOffset)));
      flag = true;
    }

  /**
   * <p>
   * Recursively adds pointer targets for every used (tracked) (sub)field of the newly allocated base.
   * </p>
   * <p>
   * Note: the recursion doesn't proceed on unused (untracked) (sub)fields.
   * </p>
   * @param base the name of the newly allocated base variable
   * @param currentType type of the allocated base or the next added pointer target
   * @param containerType either {@code null} or the type of the innermost container of the next added pointer target
   * @param properOffset either {@code 0} or the offset of the next added pointer target in its innermost container
   * @param containerOffset either {@code 0} or the offset of the innermost container (relative to the base adddress)
   */
    private void addTargets(final String base,
                            final CType currentType,
                            final @Nullable CType containerType,
                            final int properOffset,
                            final int containerOffset) {
      final CType cType = simplifyType(currentType);
      assert !(cType instanceof CElaboratedType) : "Unresolved elaborated type:" + cType;
      if (cType instanceof CArrayType) {
        final CArrayType arrayType = (CArrayType) cType;
        Integer length = getArrayLength(arrayType);
        if (length == null) {
          length = options.defaultArrayLength();
        } else if (length > options.maxArrayLength()) {
          length = options.maxArrayLength();
        }
        int offset = 0;
        for (int i = 0; i < length; ++i) {
          addTargets(base, arrayType.getType(), arrayType, offset, containerOffset + properOffset);
          offset += getSize(arrayType.getType());
        }
      } else if (cType instanceof CCompositeType) {
        final CCompositeType compositeType = (CCompositeType) cType;
        assert compositeType.getKind() != ComplexTypeKind.ENUM : "Enums are not composite: " + compositeType;
        final String type = typeToString(compositeType);
        addCompositeType(compositeType);
        int offset = 0;
        for (final CCompositeTypeMemberDeclaration memberDeclaration : compositeType.getMembers()) {
          if (fields.containsKey(CompositeField.of(type, memberDeclaration.getName()))) {
            addTargets(base, memberDeclaration.getType(), compositeType, offset, containerOffset + properOffset);
          }
          if (compositeType.getKind() == ComplexTypeKind.STRUCT) {
            offset += getSize(memberDeclaration.getType());
          }
        }
      } else {
        addTarget(base, cType, containerType, properOffset, containerOffset);
      }
    }

    public BooleanFormula prepareBase(final String name, CType type) {
      type = simplifyType(type);
      if (bases.containsKey(name)) {
        // The base has already been added
        return formulaManager.getBooleanFormulaManager().makeBoolean(true);
      }
      bases = bases.putAndCopy(name, type); // To get proper inequalities
      final BooleanFormula nextInequality = getNextBaseAddressInequality(name, lastBase);
      bases = bases.putAndCopy(name, getFakeBaseType(getSize(type))); // To prevent adding spurious targets when merging
      lastBase = name;
      return nextInequality;
    }

    public boolean shareBase(final String name, CType type) {
      type = simplifyType(type);
//      Preconditions.checkArgument(bases.containsKey(name),
//                                  "The base should be prepared beforehead with prepareBase()");

      flag = false;
      addTargets(name, type, null, 0, 0);
      bases = bases.putAndCopy(name, type);

      lastBase = name;
      return flag;
    }

    /**
     * Adds the newly allocated base of the given type for tracking along with all its tracked (sub)fields
     * (if its a structure/union) or all its elements (if its an array).
     * @param name
     * @param type
     * @return
     */
    public Pair<Boolean, BooleanFormula> addBase(final String name, CType type) {
      type = simplifyType(type);
      if (bases.containsKey(name)) {
        // The base has already been added
        return Pair.of(true, formulaManager.getBooleanFormulaManager().makeBoolean(true));
      }

      flag = false;
      addTargets(name, type, null, 0, 0);
      bases = bases.putAndCopy(name, type);

      final BooleanFormula nextInequality = getNextBaseAddressInequality(name, lastBase);
      lastBase = name;
      return Pair.of(flag, nextInequality);
    }

    public boolean tracksField(final CCompositeType compositeType, final String fieldName) {
      return fields.containsKey(CompositeField.of(typeToString(compositeType), fieldName));
    }

    /**
     * Recursively adds pointer targets for the given base variable when the newly used field is added for tracking.
     * @param base the base variable
     * @param currentType the type of the base variable or of the next subfield
     * @param containerType either {@code null} or the type of the innermost container of the next considered subfield
     * @param properOffset either {@code 0} or the offset of the next subfield in its innermost container
     * @param containerOffset either {code 0} or the offset of the innermost container relative to the base address
     * @param composite the composite of the newly used field
     * @param memberName the name of the newly used field
     */
    private void addTargets(final String base,
                            final CType currentType,
                            final @Nullable CType containerType,
                            final int properOffset,
                            final int containerOffset,
                            final String composite,
                            final String memberName) {
      final CType cType = simplifyType(currentType);
      assert !(cType instanceof CElaboratedType) : "Unresolved elaborated type:" + cType;
      if (cType instanceof CArrayType) {
        final CArrayType arrayType = (CArrayType) cType;
        Integer length = getArrayLength(arrayType);
        if (length == null) {
          length = options.defaultArrayLength();
        } else if (length > options.maxArrayLength()) {
          length = options.maxArrayLength();
        }
        int offset = 0;
        for (int i = 0; i < length; ++i) {
          addTargets(base, arrayType.getType(), arrayType, offset, containerOffset + properOffset,
                     composite, memberName);
          offset += getSize(arrayType.getType());
        }
      } else if (cType instanceof CCompositeType) {
        final CCompositeType compositeType = (CCompositeType) cType;
        assert compositeType.getKind() != ComplexTypeKind.ENUM : "Enums are not composite: " + compositeType;
        final String type = typeToString(compositeType);
        int offset = 0;
        final boolean isTargetComposite = type.equals(composite);
        for (final CCompositeTypeMemberDeclaration memberDeclaration : compositeType.getMembers()) {
          if (fields.containsKey(CompositeField.of(type, memberDeclaration.getName()))) {
            addTargets(base, memberDeclaration.getType(), compositeType, offset, containerOffset + properOffset,
                       composite, memberName);
          }
          if (isTargetComposite && memberDeclaration.getName().equals(memberName)) {
            addTargets(base, memberDeclaration.getType(), compositeType, offset, containerOffset + properOffset);
          }
          if (compositeType.getKind() == ComplexTypeKind.STRUCT) {
            offset += getSize(memberDeclaration.getType());
          }
        }
      }
    }

    public boolean addField(final CCompositeType composite, final String fieldName) {
      final String type = typeToString(composite);
      final CompositeField field = CompositeField.of(type, fieldName);
      if (fields.containsKey(field)) {
        return true; // The field has already been added
      }
      flag = false;
      for (final PersistentSortedMap.Entry<String, CType> baseEntry : bases.entrySet()) {
        addTargets(baseEntry.getKey(), baseEntry.getValue(), null, 0, 0, type, fieldName);
      }
      fields = fields.putAndCopy(field, true);
      return flag;
    }

    /**
     * Should be used to remove the newly added field if it didn't turn out to correspond to any actual pointer target.
     * This can happen if we try to track a field of a composite that has no corresponding allocated bases.
     * @param composite
     * @param fieldName
     */
    public void shallowRemoveField(final CCompositeType composite, final String fieldName) {
      final String type = typeToString(composite);
      final CompositeField field = CompositeField.of(type, fieldName);
      fields = fields.removeAndCopy(field);
    }

    void setFields(PersistentSortedMap<CompositeField, Boolean> fields) {
      this.fields = fields;
    }

    public void addDeferredAllocation(final String pointerVariable,
                                      final boolean isZeroing,
                                      final CIntegerLiteralExpression size,
                                      final String baseVariable) {
      deferredAllocations = deferredAllocations.putAndCopy(pointerVariable,
                                                           new DeferredAllocationPool(pointerVariable,
                                                                                      isZeroing,
                                                                                      size,
                                                                                      baseVariable));
    }

    public void addTemporaryDeferredAllocation(final boolean isZeroing,
                                               final CIntegerLiteralExpression size,
                                               final String baseVariable) {
      addDeferredAllocation(baseVariable, isZeroing, size, baseVariable);
    }

    public void addDeferredAllocationPointer(final String newPointerVariable,
                                             final String originalPointerVariable) {
      final DeferredAllocationPool newDeferredAllocationPool =
        deferredAllocations.get(originalPointerVariable).addPointerVariable(newPointerVariable);

      for (final String pointerVariable : newDeferredAllocationPool.getPointerVariables()) {
        deferredAllocations = deferredAllocations.putAndCopy(pointerVariable, newDeferredAllocationPool);
      }
      deferredAllocations = deferredAllocations.putAndCopy(newPointerVariable, newDeferredAllocationPool);
    }

    /**
     * Removes pointer to a deferred memory allocation from tracking.
     * @param oldPointerVariable
     * @return whether the removed variable was the only pointer to the corresponding referred allocation
     */
    public boolean removeDeferredAllocatinPointer(final String oldPointerVariable) {
      final DeferredAllocationPool newDeferredAllocationPool =
        deferredAllocations.get(oldPointerVariable).removePointerVariable(oldPointerVariable);

      deferredAllocations = deferredAllocations.removeAndCopy(oldPointerVariable);
      if (!newDeferredAllocationPool.getPointerVariables().isEmpty()) {
        for (final String pointerVariable : newDeferredAllocationPool.getPointerVariables()) {
          deferredAllocations = deferredAllocations.putAndCopy(pointerVariable, newDeferredAllocationPool);
        }
        return false;
      } else {
        return true;
      }
    }

    public DeferredAllocationPool removeDeferredAllocation(final String allocatedPointerVariable) {
      final DeferredAllocationPool deferredAllocationPool = deferredAllocations.get(allocatedPointerVariable);
      for (final String pointerVariable : deferredAllocationPool.getPointerVariables()) {
        deferredAllocations = deferredAllocations.removeAndCopy(pointerVariable);
      }
      return deferredAllocationPool;
    }

    public Collection<String> getDeferredAllocationVariables() {
      return ImmutableSet.copyOf(deferredAllocations.keySet());
    }

    public static int getNextDynamicAllocationIndex() {
      return dynamicAllocationCounter++;
    }

    @Override
    public int getOffset(CCompositeType compositeType, final String memberName) {
      compositeType = (CCompositeType) simplifyType(compositeType);
      assert compositeType.getKind() != ComplexTypeKind.ENUM : "Enums are not composite: " + compositeType;
      Multiset<String> multiset = offsets.get(compositeType);
      if (multiset == null) {
        addCompositeType(compositeType);
        multiset = offsets.get(compositeType);
        assert multiset != null : "Failed adding composite type to cache: " + compositeType;
      }
      return multiset.count(memberName);
    }

    /**
     * Returns an immutable PointerTargetSet with all the changes made to the builder.
     */
    public PointerTargetSet build() {
      return new PointerTargetSet(machineModel,
                                  sizeofVisitor,
                                  options,
                                  bases,
                                  lastBase,
                                  fields,
                                  deferredAllocations,
                                  targets,
                                  formulaManager);
    }

    private boolean flag; // Used by addBase() and addField() to detect essential additions

    private static final long serialVersionUID = 5692271309582052121L;
  }

  public boolean isTemporaryDeferredAllocationPointer(final String pointerVariable) {
    final DeferredAllocationPool deferredAllocationPool = deferredAllocations.get(pointerVariable);
    assert deferredAllocationPool == null || deferredAllocationPool.getBaseVariables().size() >= 1 :
           "Inconsistent deferred alloction pool: no bases";
    return deferredAllocationPool != null && deferredAllocationPool.getBaseVariables().get(0).equals(pointerVariable);
  }

  public boolean isDeferredAllocationPointer(final String pointerVariable) {
    return deferredAllocations.containsKey(pointerVariable);
  }

  private static final String getUnitedFieldBaseName(final int index) {
    return UNITED_BASE_FIELD_NAME_PREFIX + index;
  }

  public FormulaType<?> getPointerType() {
    return pointerType;
  }

  protected BooleanFormula getNextBaseAddressInequality(final String newBase,
                                                        final String lastBase) {
    final FormulaManagerView fm = formulaManager;
    final Formula newBaseFormula = fm.makeVariable(pointerType, getBaseName(newBase));
    if (lastBase != null) {
      final Integer lastSize = getSize(bases.get(lastBase));
      final Formula rhs = fm.makePlus(fm.makeVariable(pointerType, getBaseName(lastBase)),
                                      fm.makeNumber(pointerType, lastSize));
      // The condition rhs > 0 prevents overflows in case of bit-vector encoding
      return fm.makeAnd(fm.makeGreaterThan(rhs, fm.makeNumber(pointerType, 0L), true),
                        fm.makeGreaterOrEqual(newBaseFormula, rhs, true));
    } else {
      return fm.makeGreaterThan(newBaseFormula, fm.makeNumber(pointerType, 0L), true);
    }
  }

  /**
   * Conjoins the given formula with constraints representing disjointness of the allocated shared objects.
   *
   */
  public BooleanFormula forceDisjointnessConstraints(final BooleanFormula formula) {
    BooleanFormula disjointnessFormula = formulaManager.getBooleanFormulaManager().makeBoolean(true);
    // In case we have deferred memory allocations, add the appropriate constraints
    if (!deferredAllocations.isEmpty()) {
      final Set<String> addedBaseVariables = new HashSet<>();
      String lastBase = this.lastBase;
      for (Map.Entry<String, DeferredAllocationPool> pointerVariableEntry : deferredAllocations.entrySet()) {
        final DeferredAllocationPool deferredAllocationPool = pointerVariableEntry.getValue();
        Integer size = deferredAllocationPool.getSize() != null ?
                         deferredAllocationPool.getSize().getValue().intValue() : null;
        if (size == null) {
          size = options.defaultAllocationSize();
        }
        for (String baseVariable : deferredAllocationPool.getBaseVariables()) {
          if (!addedBaseVariables.contains(baseVariable)) {
            disjointnessFormula = formulaManager.makeAnd(disjointnessFormula,
                                                         getNextBaseAddressInequality(baseVariable,
                                                                                      lastBase));
            lastBase = baseVariable;
            addedBaseVariables.add(baseVariable);
          }
        }
      }
    }
    return formulaManager.getBooleanFormulaManager().and(formula, disjointnessFormula);
  }

  public boolean isActualBase(final String name) {
    return bases.containsKey(name) && !isFakeBaseType(bases.get(name));
  }

  public boolean isPreparedBase(final String name) {
    return bases.containsKey(name);
  }

  public boolean isBase(final String name, CType type) {
    type = simplifyType(type);
    final CType baseType = bases.get(name);
    return baseType != null && baseType.equals(type);
  }

  public static final PointerTargetSet emptyPointerTargetSet(final MachineModel machineModel,
                                                             final FormulaEncodingWithUFOptions options,
                                                             final FormulaManagerView formulaManager) {
    return new PointerTargetSet(machineModel,
                                new CSizeofVisitor(machineModel, options),
                                options,
                                PathCopyingPersistentTreeMap.<String, CType>of(),
                                null,
                                PathCopyingPersistentTreeMap.<CompositeField, Boolean>of(),
                                PathCopyingPersistentTreeMap.<String, DeferredAllocationPool>of(),
                                PathCopyingPersistentTreeMap.<String, PersistentList<PointerTarget>>of(),
                                formulaManager);
  }

  public Triple<PointerTargetSet,
                BooleanFormula,
                Pair<PersistentSortedMap<String, CType>, PersistentSortedMap<String, CType>>>
    mergeWith(final PointerTargetSet other) {

    final ConflictHandler<CType> baseUnitingConflictHandler = new ConflictHandler<CType>() {
      @Override
      public CType resolveConflict(final CType type1, final CType type2) {
        if (isFakeBaseType(type1)) {
          return type2;
        } else if (isFakeBaseType(type2)) {
          return type1;
        }
        int currentFieldIndex = 0;
        final ImmutableList.Builder<CCompositeTypeMemberDeclaration> membersBuilder =
          ImmutableList.<CCompositeTypeMemberDeclaration>builder();
        if (type1 instanceof CCompositeType) {
          final CCompositeType compositeType1 = (CCompositeType) type1;
          if (compositeType1.getKind() == ComplexTypeKind.UNION &&
              !compositeType1.getMembers().isEmpty() &&
              compositeType1.getMembers().get(0).getName().equals(getUnitedFieldBaseName(0))) {
            membersBuilder.addAll(compositeType1.getMembers());
            currentFieldIndex += compositeType1.getMembers().size();
          } else {
            membersBuilder.add(new CCompositeTypeMemberDeclaration(compositeType1,
                                                                   getUnitedFieldBaseName(currentFieldIndex++)));
          }
        } else {
          membersBuilder.add(new CCompositeTypeMemberDeclaration(type1,
                                                                 getUnitedFieldBaseName(currentFieldIndex++)));
        }
        if (type2 instanceof CCompositeType) {
          final CCompositeType compositeType2 = (CCompositeType) type2;
          if (compositeType2.getKind() == ComplexTypeKind.UNION &&
              !compositeType2.getMembers().isEmpty() &&
              compositeType2.getMembers().get(0).getName().equals(getUnitedFieldBaseName(0))) {
            for (CCompositeTypeMemberDeclaration memberDeclaration : compositeType2.getMembers()) {
              membersBuilder.add(new CCompositeTypeMemberDeclaration(memberDeclaration.getType(),
                                                                     getUnitedFieldBaseName(currentFieldIndex++)));
            }
          } else {
            membersBuilder.add(new CCompositeTypeMemberDeclaration(compositeType2,
                                                                   getUnitedFieldBaseName(currentFieldIndex++)));
          }
        } else {
          membersBuilder.add(new CCompositeTypeMemberDeclaration(type2,
                                                                 getUnitedFieldBaseName(currentFieldIndex++)));
        }
        return new CCompositeType(false,
                                  false,
                                  ComplexTypeKind.UNION,
                                  membersBuilder.build(),
                                  UNITED_BASE_UNION_TAG_PREFIX + type1.toString().replace(' ', '_') + "_and_" +
                                                                 type2.toString().replace(' ', '_'));
      }
    };
    final boolean reverseBases = other.bases.size() > bases.size();
    Triple<PersistentSortedMap<String, CType>,
           PersistentSortedMap<String, CType>,
           PersistentSortedMap<String, CType>> mergedBases =
      !reverseBases ? mergeSortedSets(bases, other.bases, baseUnitingConflictHandler) :
                      mergeSortedSets(other.bases, bases, baseUnitingConflictHandler);

    final boolean reverseFields = other.fields.size() > fields.size();
    final Triple<PersistentSortedMap<CompositeField, Boolean>,
                 PersistentSortedMap<CompositeField, Boolean>,
                 PersistentSortedMap<CompositeField, Boolean>> mergedFields =
      !reverseBases ? mergeSortedSets(fields, other.fields, PointerTargetSet.<Boolean>failOnConflict()) :
                      mergeSortedSets(other.fields, fields, PointerTargetSet.<Boolean>failOnConflict());

    final boolean reverseTargets = other.targets.size() > targets.size();
    final PersistentSortedMap<String, PersistentList<PointerTarget>> mergedTargets =
      !reverseTargets ? mergeSortedMaps(targets, other.targets,PointerTargetSet.<PointerTarget>mergeOnConflict()) :
                        mergeSortedMaps(other.targets, targets, PointerTargetSet.<PointerTarget>mergeOnConflict());

    final PointerTargetSetBuilder builder1 = new PointerTargetSetBuilder(emptyPointerTargetSet(
                                                                               machineModel,
                                                                               options,
                                                                               formulaManager)),
                                  builder2 = new PointerTargetSetBuilder(emptyPointerTargetSet(
                                                                               machineModel,
                                                                               options,
                                                                               formulaManager));
    if (reverseBases == reverseFields) {
      builder1.setFields(mergedFields.getFirst());
      builder2.setFields(mergedFields.getSecond());
    } else {
      builder1.setFields(mergedFields.getSecond());
      builder2.setFields(mergedFields.getFirst());
    }

    for (final Map.Entry<String, CType> entry : mergedBases.getSecond().entrySet()) {
      builder1.addBase(entry.getKey(), entry.getValue());
    }

    for (final Map.Entry<String, CType> entry : mergedBases.getFirst().entrySet()) {
      builder2.addBase(entry.getKey(), entry.getValue());
    }

    final Map<DeferredAllocationPool, DeferredAllocationPool> mergedDeferredAllocationPools = new HashMap<>();
    final boolean reverseDeferredAllocations = other.deferredAllocations.size() > deferredAllocations.size();
    final ConflictHandler<DeferredAllocationPool> deferredAllocationMergingConflictHandler =
      new ConflictHandler<DeferredAllocationPool>() {
      @Override
      public DeferredAllocationPool resolveConflict(DeferredAllocationPool a, DeferredAllocationPool b) {
        final DeferredAllocationPool result = a.mergeWith(b);
        final DeferredAllocationPool oldResult = mergedDeferredAllocationPools.get(result);
        if (oldResult == null) {
          mergedDeferredAllocationPools.put(result, result);
          return result;
        } else {
          final DeferredAllocationPool newResult = oldResult.mergeWith(result);
          mergedDeferredAllocationPools.put(newResult, newResult);
          return newResult;
        }
      }
    };
    final PersistentSortedMap<String, DeferredAllocationPool> mergedDeferredAllocations =
      !reverseDeferredAllocations ?
        mergeSortedMaps(deferredAllocations, other.deferredAllocations, deferredAllocationMergingConflictHandler) :
        mergeSortedMaps(other.deferredAllocations, deferredAllocations, deferredAllocationMergingConflictHandler);
    for (final DeferredAllocationPool merged : mergedDeferredAllocationPools.keySet()) {
      for (final String pointerVariable : merged.getPointerVariables()) {
        deferredAllocations = deferredAllocations.putAndCopy(pointerVariable, merged);
      }
    }

    final String lastBase;
    final BooleanFormula basesMergeFormula;
    if (this.lastBase == null && other.lastBase == null ||
        this.lastBase != null && (other.lastBase == null || this.lastBase.equals(other.lastBase))) {
      // The next check doesn't really hold anymore due to possible base unions, but these cases are suspicious
      assert this.lastBase == null ||
             other.lastBase == null ||
             isFakeBaseType(bases.get(this.lastBase)) ||
             isFakeBaseType(other.bases.get(other.lastBase)) ||
             bases.get(this.lastBase).equals(other.bases.get(other.lastBase));
      lastBase = this.lastBase;
      basesMergeFormula = formulaManager.getBooleanFormulaManager().makeBoolean(true);
      // Nothing to do, as there were no divergence with regard to base allocations
    } else if (this.lastBase == null && other.lastBase != null) {
      lastBase = other.lastBase;
      basesMergeFormula = formulaManager.getBooleanFormulaManager().makeBoolean(true);
    } else {
      final CType fakeBaseType = getFakeBaseType(0);
      final String fakeBaseName = FAKE_ALLOC_FUNCTION_NAME +
                                  CToFormulaWithUFConverter.getUFName(fakeBaseType) +
                                  CToFormulaWithUFConverter.FRESH_INDEX_SEPARATOR +
                                  PointerTargetSetBuilder.getNextDynamicAllocationIndex();
      mergedBases =
        Triple.of(mergedBases.getFirst(),
                  mergedBases.getSecond(),
                  mergedBases.getThird().putAndCopy(fakeBaseName, fakeBaseType));
      lastBase = fakeBaseName;
      basesMergeFormula = formulaManager.makeAnd(this.getNextBaseAddressInequality(fakeBaseName, this.lastBase),
                                                 other.getNextBaseAddressInequality(fakeBaseName, other.lastBase));
    }

    final ConflictHandler<PersistentList<PointerTarget>> conflictHandler =
                                                           PointerTargetSet.<PointerTarget>destructiveMergeOnConflict();

    final PointerTargetSet result  =
      new PointerTargetSet(machineModel,
                           sizeofVisitor,
                           options,
                           mergedBases.getThird(),
                           lastBase,
                           mergedFields.getThird(),
                           mergedDeferredAllocations,
                           mergeSortedMaps(
                             mergeSortedMaps(mergedTargets,
                                             builder1.targets,
                                             conflictHandler),
                             builder2.targets,
                             conflictHandler),
                           formulaManager);
    return Triple.of(result,
                     basesMergeFormula,
                     !reverseBases ? Pair.of(mergedBases.getFirst(), mergedBases.getSecond()) :
                                     Pair.of(mergedBases.getSecond(), mergedBases.getFirst()));
  }

  /**
   * Merges two {@link PersistentSortedMap}s with the given conflict handler (in the same way as
   * {@link #mergeSortedMaps(set1, set2, conflictHandler)} does) and returns two additional
   * {@link PersistentSortedMap}s: one with elements form the first map that don't contain in the second one and the
   * second with the elements from the second map that don't contain in the first.
   * @param set1 the first map
   * @param set2 the second map
   * @param conflictHandler the conflict handler
   * @return The {@link Triple} {@link Triple#of(from1, from2, union)} where {@code from1} and {@code from2} are the
   * first and the second map mentioned above.
   */
  private static <K extends Comparable<? super K>, V> Triple<PersistentSortedMap<K, V>,
                                                             PersistentSortedMap<K, V>,
                                                             PersistentSortedMap<K, V>> mergeSortedSets(
    final PersistentSortedMap<K, V> set1,
    final PersistentSortedMap<K, V> set2,
    final ConflictHandler<V> conflictHandler) {

    PersistentSortedMap<K, V> fromSet1 = PathCopyingPersistentTreeMap.<K, V>of();
    PersistentSortedMap<K, V> fromSet2 = PathCopyingPersistentTreeMap.<K, V>of();
    PersistentSortedMap<K, V> union = set1; // Here we assume that the set1 is bigger

    final Iterator<Map.Entry<K, V>> it1 = set1.entrySet().iterator();
    final Iterator<Map.Entry<K, V>> it2 = set2.entrySet().iterator();

    Map.Entry<K, V> e1 = null;
    Map.Entry<K, V> e2 = null;

    // This loop iterates synchronously through both sets
    // by trying to keep the keys equal.
    // If one iterator fails behind, the other is not forwarded until the first catches up.
    // The advantage of this is it is in O(n log(n))
    // (n iterations, log(n) per update).
    while ((e1 != null || it1.hasNext()) && (e2 != null || it2.hasNext())) {
      if (e1 == null) {
        e1 = it1.next();
      }
      if (e2 == null) {
        e2 = it2.next();
      }

      final int flag = e1.getKey().compareTo(e2.getKey());

      if (flag < 0) {
        // e1 < e2
        fromSet1 = fromSet1.putAndCopy(e1.getKey(), e1.getValue());
        // Forward e1 until it catches up with e2
        e1 = null;
      } else if (flag > 0) {
        // e1 > e2
        fromSet2 = fromSet2.putAndCopy(e2.getKey(), e2.getValue());
        assert !union.containsKey(e2.getKey());
        union = union.putAndCopy(e2.getKey(), e2.getValue());
        // Forward e2 until it catches up with e1
        e2 = null;
      } else {
        // e1 == e2
        final K key = e1.getKey();
        final V value1 = e1.getValue();
        final V value2 = e2.getValue();

        if (!value1.equals(value2)) {
          union = union.putAndCopy(key, conflictHandler.resolveConflict(value1, value2));
        }
        // Forward both iterators
        e1 = null;
        e2 = null;
      }
    }

    while (e1 != null || it1.hasNext()) {
      if (e1 == null) {
        e1 = it1.next();
      }
      fromSet1 = fromSet1.putAndCopy(e1.getKey(), e1.getValue());
      e1 = null;
    }

    while (e2 != null || it2.hasNext()) {
      if (e2 == null) {
        e2 = it2.next();
      }
      fromSet2 = fromSet2.putAndCopy(e2.getKey(), e2.getValue());
      assert !union.containsKey(e2.getKey());
      union = union.putAndCopy(e2.getKey(), e2.getValue());
      e2 = null;
    }

    return Triple.of(fromSet1, fromSet2, union);
  }

  /**
   * Merge two PersistentSortedMaps.
   * The result has all key-value pairs where the key is only in one of the map,
   * those which are identical in both map,
   * and for those keys that have a different value in both maps a handler is called,
   * and the result is put in the resulting map.
   * @param map1 The first map.
   * @param map2 The second map.
   * @param conflictHandler The handler that is called for a key with two different values.
   * @return
   */
  private static <K extends Comparable<? super K>, V> PersistentSortedMap<K, V> mergeSortedMaps(
    final PersistentSortedMap<K, V> map1,
    final PersistentSortedMap<K, V> map2,
    final ConflictHandler<V> conflictHandler) {

    // map1 is the bigger one, so we use it as the base.
    PersistentSortedMap<K, V> result = map1;

    final Iterator<Map.Entry<K, V>> it1 = map1.entrySet().iterator();
    final Iterator<Map.Entry<K, V>> it2 = map2.entrySet().iterator();

    Map.Entry<K, V> e1 = null;
    Map.Entry<K, V> e2 = null;

    // This loop iterates synchronously through both sets
    // by trying to keep the keys equal.
    // If one iterator fails behind, the other is not forwarded until the first catches up.
    // The advantage of this is it is in O(n log(n))
    // (n iterations, log(n) per update).
    while ((e1 != null || it1.hasNext()) && (e2 != null || it2.hasNext())) {
      if (e1 == null) {
        e1 = it1.next();
      }
      if (e2 == null) {
        e2 = it2.next();
      }

      final int flag = e1.getKey().compareTo(e2.getKey());

      if (flag < 0) {
        // e1 < e2
        // forward e1 until it catches up with e2
        e1 = null;
      } else if (flag > 0) {
        // e1 > e2
        // e2 is not in map
        assert !result.containsKey(e2.getKey());
        result = result.putAndCopy(e2.getKey(), e2.getValue());
        // forward e2 until it catches up with e1
        e2 = null;
      } else {
        // e1 == e2
        final K key = e1.getKey();
        final V value1 = e1.getValue();
        final V value2 = e2.getValue();

        if (!value1.equals(value2)) {
          result = result.putAndCopy(key, conflictHandler.resolveConflict(value1, value2));
        }
        // forward both iterators
        e1 = null;
        e2 = null;
      }
    }

    // Now copy the rest of the mappings from s2.
    // For s1 this is not necessary.
    while (e2 != null || it2.hasNext()) {
      if (e2 == null) {
        e2 = it2.next();
      }
      result = result.putAndCopy(e2.getKey(), e2.getValue());
      e2 = null;
    }

    assert result.size() >= Math.max(map1.size(), map2.size());
    return result;
  }

  private static interface ConflictHandler<V> {
    public V resolveConflict(V value1, V value2);
  }

  private static <T> ConflictHandler<PersistentList<T>> mergeOnConflict() {
    return new ConflictHandler<PersistentList<T>>() {
      @Override
      public PersistentList<T> resolveConflict(PersistentList<T> list1, PersistentList<T> list2) {
        return PersistentList.merge(list1, list2);
      }
    };
  }

  private static <T> ConflictHandler<PersistentList<T>> destructiveMergeOnConflict() {
    return new ConflictHandler<PersistentList<T>>() {
      @Override
      public PersistentList<T> resolveConflict(PersistentList<T> list1, PersistentList<T> list2) {
        return list2.destructiveBuildOnto(list1);
      }
    };
  }

  private static <T> ConflictHandler<T> failOnConflict() {
    return new ConflictHandler<T>() {
      @Override
      public T resolveConflict(T o1, T o2) {
        throw new UnsupportedOperationException("merging unsupported, values must be equal:" + o1 + " == " + o2);
      }
    };
  }

  @Override
  public String toString() {
    return joiner.join(bases.entrySet()) + " " + joiner.join(fields.entrySet());
  }

  @Override
  public int hashCode() {
    return (31 + bases.keySet().hashCode()) * 31 + fields.hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    } else if (!(obj instanceof PointerTargetSet)) {
      return false;
    } else {
      PointerTargetSet other = (PointerTargetSet) obj;
      return bases.equals(other.bases) && fields.equals(other.fields);
    }
  }

  private PointerTargetSet(final MachineModel machineModel,
                           final CSizeofVisitor sizeofVisitor,
                           final FormulaEncodingWithUFOptions options,
                           final PersistentSortedMap<String, CType> bases,
                           final String lastBase,
                           final PersistentSortedMap<CompositeField, Boolean> fields,
                           final PersistentSortedMap<String, DeferredAllocationPool> deferredAllocations,
                           final PersistentSortedMap<String, PersistentList<PointerTarget>> targets,
                           final FormulaManagerView formulaManager) {
    this.machineModel = machineModel;
    this.sizeofVisitor = sizeofVisitor;

    this.options = options;
    this.formulaManager = formulaManager;

    this.bases = bases;
    this.lastBase = lastBase;
    this.fields = fields;

    this.deferredAllocations = deferredAllocations;

    this.targets = targets;

    final int pointerSize = machineModel.getSizeofPtr();
    final int bitsPerByte = machineModel.getSizeofCharInBits();
    this.pointerType = this.formulaManager.getBitvectorFormulaManager()
                                          .getFormulaType(pointerSize * bitsPerByte);
  }

  /**
   * Returns a PointerTargetSetBuilder that is initialized with the current PointerTargetSet.
   */
  public PointerTargetSetBuilder builder() {
    return new PointerTargetSetBuilder(this);
  }

  private static final CType getFakeBaseType(int size) {
    return simplifyType(new CArrayType(false, false, CNumericTypes.VOID, new CIntegerLiteralExpression(null,
                                                                                        CNumericTypes.SIGNED_CHAR,
                                                                                        BigInteger.valueOf(size))));
  }

  private static final boolean isFakeBaseType(final CType type) {
    return type instanceof CArrayType && ((CArrayType) type).getType().equals(VOID);
  }

  private static final Joiner joiner = Joiner.on(" ");

  protected final FormulaEncodingWithUFOptions options;
  protected final MachineModel machineModel;
  protected final CSizeofVisitor sizeofVisitor;

  protected final FormulaManagerView formulaManager;

  /*
   * Use Multiset<String> instead of Map<String, Integer> because it is more
   * efficient. The integer value is stored as the number of instances of any
   * element in the Multiset. So instead of calling map.get(key) we just use
   * Multiset.count(key). This is better because the Multiset internally uses
   * modifiable integers instead of the immutable Integer class.
   */
  private static final Multiset<CCompositeType> sizes = HashMultiset.create();
  private static final Map<CCompositeType, Multiset<String>> offsets = new HashMap<>();

  private static final CachingCanonizingCTypeVisitor typeVisitor = new CachingCanonizingCTypeVisitor(true, true);

  // The following fields are modified in the derived class only

  protected /*final*/ PersistentSortedMap<String, CType> bases;
  protected /*final*/ String lastBase;
  protected /*final*/ PersistentSortedMap<CompositeField, Boolean> fields;

  protected /*final*/ PersistentSortedMap<String, DeferredAllocationPool> deferredAllocations;

  protected /*final*/ PersistentSortedMap<String, PersistentList<PointerTarget>> targets;

  protected final FormulaType<?> pointerType;

  private static int dynamicAllocationCounter = 0;

  private static final String UNITED_BASE_UNION_TAG_PREFIX = "__VERIFIER_base_union_of_";
  private static final String UNITED_BASE_FIELD_NAME_PREFIX = "__VERIFIER_united_base_field";

  private static final String FAKE_ALLOC_FUNCTION_NAME = "__VERIFIER_fake_alloc";
  private static final String BASE_PREFIX = "__ADDRESS_OF_";

  private static final long serialVersionUID = 2102505458322248624L;
}
