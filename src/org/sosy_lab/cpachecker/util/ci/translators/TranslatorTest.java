/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.ci.translators;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.log.TestLogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.interval.Interval;
import org.sosy_lab.cpachecker.cpa.interval.IntervalAnalysisState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.cpa.sign.SIGN;
import org.sosy_lab.cpachecker.cpa.sign.SignState;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.type.NullValue;
import org.sosy_lab.cpachecker.cpa.value.type.NumericValue;
import org.sosy_lab.cpachecker.cpa.value.type.Value;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.predicates.AbstractionFormula;
import org.sosy_lab.cpachecker.util.predicates.SymbolicRegionManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;
import org.sosy_lab.cpachecker.util.predicates.interfaces.RegionManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.NumeralFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.util.test.TestDataTools;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.BooleanFormulaManager;
import org.sosy_lab.solver.api.NumeralFormula.IntegerFormula;

import com.google.common.truth.Truth;

public class TranslatorTest {

  private String[] varNames = {"var1", "var2", "var3", "fun::var1", "fun::varB", "fun::varC"};
  private CSimpleType integervariable = new CSimpleType(false, false, CBasicType.INT, false, false, false, false, false, false, false);
  private SSAMap ssaTest;

  @Before
  public void init() {
    SSAMapBuilder ssaBuilder = SSAMap.emptySSAMap().builder();
    ssaBuilder.setIndex("var1", integervariable, 1);
    ssaBuilder.setIndex("var3", integervariable, 1);
    ssaBuilder.setIndex("fun::varB", integervariable, 1);
    ssaTest = ssaBuilder.build();
  }

  @Test
  public void testValueTranslator() throws InvalidConfigurationException {
    PersistentMap<MemoryLocation, Value> constantsMap = PathCopyingPersistentTreeMap.of();
    PersistentMap<MemoryLocation, Type> locToTypeMap = PathCopyingPersistentTreeMap.of();

    constantsMap = constantsMap.putAndCopy(MemoryLocation.valueOf("var1"), new NumericValue(3));
    constantsMap = constantsMap.putAndCopy(MemoryLocation.valueOf("var3"), NullValue.getInstance());
    constantsMap = constantsMap.putAndCopy(MemoryLocation.valueOf("fun::var1"), new NumericValue(1.5));
    constantsMap = constantsMap.putAndCopy(MemoryLocation.valueOf("fun::varC"), new NumericValue(-5));

    Truth.assertThat(constantsMap).hasSize(4);

    ValueAnalysisState vStateTest = new ValueAnalysisState(constantsMap, locToTypeMap);
    Truth.assertThat(vStateTest.getConstantsMapView()).isNotEmpty();
    ValueRequirementsTranslator vReqTransTest = new ValueRequirementsTranslator(TestDataTools.configurationForTest().build(), ShutdownNotifier.create(), TestLogManager.getInstance());

    // Test of method getVarsInRequirements()
    List<String> varsInRequirements = vReqTransTest.getVarsInRequirements(vStateTest);
    Truth.assertThat(varsInRequirements).containsExactly("var1", "var3", "fun::var1", "fun::varC");

    // Test of method getListOfIndependentRequirements()
    List<String> listOfIndependentRequirements = vReqTransTest.getListOfIndependentRequirements(vStateTest, ssaTest);
    Truth.assertThat(listOfIndependentRequirements).containsExactly("(= |var1@1| 3)", "(= |fun::varC| -5)");
  }

  @Test
  public void testSignTranslator() throws InvalidConfigurationException {
    SignState sStateTest = SignState.TOP;
    sStateTest = sStateTest.assignSignToVariable("var1", SIGN.PLUS);
    sStateTest = sStateTest.assignSignToVariable("var2", SIGN.MINUS);
    sStateTest = sStateTest.assignSignToVariable("var3", SIGN.ZERO);
    sStateTest = sStateTest.assignSignToVariable("fun::var1", SIGN.PLUSMINUS);
    sStateTest = sStateTest.assignSignToVariable("fun::varB", SIGN.PLUS0);
    sStateTest = sStateTest.assignSignToVariable("fun::varC", SIGN.MINUS0);
    SignRequirementsTranslator sReqTransTest = new SignRequirementsTranslator(TestDataTools.configurationForTest().build(), ShutdownNotifier.create(), TestLogManager.getInstance());

    // Test method getVarsInRequirements()
    List<String> varsInReq = sReqTransTest.getVarsInRequirements(sStateTest);
    Truth.assertThat(varsInReq).containsExactlyElementsIn(Arrays.asList(varNames));

    // Test method getListOfIndependentRequirements()
    List<String> listOfIndepententReq = sReqTransTest.getListOfIndependentRequirements(sStateTest, ssaTest);
    List<String> content = new ArrayList<>();
    content.add("(> |var1@1| 0)");
    content.add("(< |var2| 0)");
    content.add("(= |var3@1| 0)");
    content.add("(or (> |fun::var1| 0) (< |fun::var1| 0))");
    content.add("(>= |fun::varB@1| 0)");
    content.add("(<= |fun::varC| 0)");
    Truth.assertThat(listOfIndepententReq).containsExactlyElementsIn(content);
  }

  @Test
  public void testIntervalAndCartesianTranslator() throws InvalidConfigurationException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    PersistentMap<String, Interval> intervals = PathCopyingPersistentTreeMap.of();
    PersistentMap<String, Integer> referenceMap = PathCopyingPersistentTreeMap.of();

    intervals = intervals.putAndCopy("var1", new Interval(Long.MIN_VALUE, (long) 5));
    intervals = intervals.putAndCopy("var2", new Interval((long) -7, Long.MAX_VALUE));
    intervals = intervals.putAndCopy("var3", new Interval(Long.MIN_VALUE, (long) -2));
    intervals = intervals.putAndCopy("fun::var1", new Interval((long) 0, (long) 10));
    intervals = intervals.putAndCopy("fun::varB", new Interval((long) 8, Long.MAX_VALUE));
    intervals = intervals.putAndCopy("fun::varC", new Interval((long) -15, (long) -3));

    IntervalAnalysisState iStateTest = new IntervalAnalysisState(intervals, referenceMap);
    IntervalRequirementsTranslator iReqTransTest = new IntervalRequirementsTranslator(TestDataTools.configurationForTest().build(), ShutdownNotifier.create(), TestLogManager.getInstance());

    // Test method getVarsInRequirements()
    List<String> varsInRequirements = iReqTransTest.getVarsInRequirements(iStateTest);
    Truth.assertThat(varsInRequirements).containsExactlyElementsIn(Arrays.asList(varNames));

    // Test method getListOfIndepentendRequirements()
    List<String> listOfIndependentRequirements = iReqTransTest.getListOfIndependentRequirements(iStateTest, ssaTest);
    List<String> content = new ArrayList<>();
    content.add("(<= |var1@1| 5)");
    content.add("(>= |var2| -7)");
    content.add("(<= |var3@1| -2)");
    content.add("(and (>= |fun::var1| 0) (<= |fun::var1| 10))");
    content.add("(>= |fun::varB@1| 8)");
    content.add("(and (>= |fun::varC| -15) (<= |fun::varC| -3))");
    Truth.assertThat(listOfIndependentRequirements).containsExactlyElementsIn(content);

    // Test method writeVarDefinition()
    Method writeVarDefinition = CartesianRequirementsTranslator.class.getDeclaredMethod("writeVarDefinition", new Class[]{List.class, SSAMap.class});
    writeVarDefinition.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<String> varDefinition = (List<String>) writeVarDefinition.invoke(iReqTransTest, Arrays.asList(varNames), ssaTest);
    content = new ArrayList<>();
    content.add("(declare-fun |var1@1|() Int)");
    content.add("(declare-fun |var2|() Int)");
    content.add("(declare-fun |var3@1|() Int)");
    content.add("(declare-fun |fun::var1|() Int)");
    content.add("(declare-fun |fun::varB@1|() Int)");
    content.add("(declare-fun |fun::varC|() Int)");
    Truth.assertThat(varDefinition).containsExactlyElementsIn(content);

    // Test method convertToFormula()
    Pair<List<String>, String> convertedToFormula = iReqTransTest.convertToFormula(iStateTest, ssaTest);
    Truth.assertThat(convertedToFormula.getFirst()).containsExactlyElementsIn(content);
    String s = "(define-fun req () Bool (and (and (>= |fun::var1| 0) (<= |fun::var1| 10))(and (>= |fun::varB@1| 8)(and (and (>= |fun::varC| -15) (<= |fun::varC| -3))(and (<= |var1@1| 5)(and (>= |var2| -7)(<= |var3@1| -2)))))))";
    Truth.assertThat(convertedToFormula.getSecond()).isEqualTo(s);

    // Test method convertToFormula() with empty IntervalAnalysisState
    convertedToFormula = iReqTransTest.convertToFormula(new IntervalAnalysisState(), ssaTest);
    Truth.assertThat(convertedToFormula.getFirst()).isEmpty();
    s = "(define-fun req () Bool true)";
    Truth.assertThat(convertedToFormula.getSecond()).isEqualTo(s);

    // Test method convertToFormula() with another IntervalAnalysisState
    intervals = PathCopyingPersistentTreeMap.of();
    referenceMap = PathCopyingPersistentTreeMap.of();
    intervals = intervals.putAndCopy("var1", new Interval((long) 0, Long.MAX_VALUE));
    IntervalAnalysisState anotherIStateTest = new IntervalAnalysisState(intervals, referenceMap);

    convertedToFormula = iReqTransTest.convertToFormula(anotherIStateTest, ssaTest);
    content = new ArrayList<>();
    content.add("(declare-fun |var1@1|() Int)");
    Truth.assertThat(convertedToFormula.getFirst()).containsExactlyElementsIn(content);
    s = "(define-fun req () Bool (>= |var1@1| 0))";
    Truth.assertThat(convertedToFormula.getSecond()).isEqualTo(s);
  }

  @Test
  public void testPredicateRequirementsTranslator() throws InvalidConfigurationException, CPAException,
      UnsupportedOperationException, IOException, ParserException, InterruptedException {
    Configuration config = TestDataTools.configurationForTest().build();
    LogManager logger = new BasicLogManager(config);
    PredicateCPA predicateCpa = (PredicateCPA) PredicateCPA.factory().setConfiguration(config)
        .setLogger(logger).setShutdownNotifier(ShutdownNotifier.create())
        .set(TestDataTools.makeCFA("void main(){}"), CFA.class)
        .set(new ReachedSetFactory(config, logger), ReachedSetFactory.class).createInstance();
    FormulaManagerView fmv = predicateCpa.getSolver().getFormulaManager();

    // Region used in abstractionFormula
    RegionManager regionManager = new SymbolicRegionManager(fmv, null);
    Region region = regionManager.createPredicate();

    // Initialize formula manager
    BooleanFormulaManager bfmgr = fmv.getBooleanFormulaManager();
    BooleanFormula bf = bfmgr.makeBoolean(true);

    // create empty path formula
    PathFormula pathFormula = new PathFormula(bf, SSAMap.emptySSAMap(), PointerTargetSet.emptyPointerTargetSet(), 0);

    // create PredicateAbstractState ptrueState
    AbstractionFormula aFormula = new AbstractionFormula(fmv, region, bf, bf, pathFormula, Collections.<Integer>emptySet());
    PredicateAbstractState ptrueState = PredicateAbstractState.mkAbstractionState(pathFormula, aFormula, PathCopyingPersistentTreeMap.<CFANode, Integer>of());

    // create PredicateAbstractState pf1State
    NumeralFormulaManagerView<IntegerFormula, IntegerFormula> ifmgr = fmv.getIntegerFormulaManager();
    BooleanFormula bf11 = ifmgr.greaterThan(ifmgr.makeVariable("var1"), ifmgr.makeNumber(0));
    BooleanFormula bf12 = ifmgr.equal(ifmgr.makeVariable("var3"), ifmgr.makeNumber(0));
    BooleanFormula bf13 = ifmgr.lessThan(ifmgr.makeVariable("fun::var1"), ifmgr.makeNumber(0));
    BooleanFormula bf14 = bfmgr.or(bf11, bf12);
    BooleanFormula bf1 = bfmgr.and(bf14, bf13);
    aFormula = new AbstractionFormula(fmv, region, bf1, bfmgr.makeBoolean(true), pathFormula, Collections.<Integer>emptySet());
    PredicateAbstractState pf1State = PredicateAbstractState.mkAbstractionState(pathFormula, aFormula, PathCopyingPersistentTreeMap.<CFANode, Integer>of());

    // create PredicateAbstractState pf2State
    BooleanFormula bf21 = ifmgr.greaterThan(ifmgr.makeVariable("var2"), ifmgr.makeVariable("fun::varB"));
    BooleanFormula bf22 = ifmgr.lessThan(ifmgr.makeVariable("fun::varC"), ifmgr.makeNumber(0));
    BooleanFormula bf2 = bfmgr.and(bf21, bf22);
    aFormula = new AbstractionFormula(fmv, region, bf2, bfmgr.makeBoolean(true), pathFormula, Collections.<Integer>emptySet());
    PredicateAbstractState pf2State = PredicateAbstractState.mkAbstractionState(pathFormula, aFormula, PathCopyingPersistentTreeMap.<CFANode, Integer>of());

    PredicateRequirementsTranslator pReqTrans = new PredicateRequirementsTranslator(predicateCpa);

    // Test method convertToFormula()
    Pair<List<String>, String> convertedFormula = pReqTrans.convertToFormula(ptrueState, ssaTest);
    Truth.assertThat(convertedFormula.getFirst()).isEmpty();
    String s = "(define-fun .defci0 Bool()  true)";
    Truth.assertThat(convertedFormula.getSecond()).isEqualTo(s);

    convertedFormula = pReqTrans.convertToFormula(pf1State, ssaTest);
    List<String> list = new ArrayList<>();
    list.add("(declare-fun |fun::var1| () Int)");
    list.add("(declare-fun var3@1 () Int)");
    list.add("(declare-fun var1@1 () Int)");
    Truth.assertThat(convertedFormula.getFirst()).containsExactlyElementsIn(list);
    s = "(define-fun .defci1 Bool()  (and (or (> var1@1 0) (= var3@1 0)) (< |fun::var1| 0)))";
    Truth.assertThat(convertedFormula.getSecond()).isEqualTo(s);

    // Test method convertRequirements()
    Pair<Pair<List<String>, String>, Pair<List<String>, String>> convertedRequirements = pReqTrans.convertRequirements(pf1State, Collections.<AbstractState>emptyList(), ssaTest);
    list.clear();
    list.add("(declare-fun var1 () Int)");
    list.add("(declare-fun var3 () Int)");
    list.add("(declare-fun |fun::var1| () Int)");
    Truth.assertThat(convertedRequirements.getFirst().getFirst()).containsExactlyElementsIn(list);
    s = "(define-fun pre Bool()  (and (or (> var1 0) (= var3 0)) (< |fun::var1| 0)))";
    Truth.assertThat(convertedRequirements.getFirst().getSecond()).isEqualTo(s);
    Truth.assertThat(convertedRequirements.getSecond().getFirst()).isEmpty();
    s = "(define-fun post Bool() false)";
    Truth.assertThat(convertedRequirements.getSecond().getSecond()).isEqualTo(s);

    Collection<PredicateAbstractState> pAbstrStates = new ArrayList<>();
    pAbstrStates.add(ptrueState);
    convertedRequirements = pReqTrans.convertRequirements(pf2State, pAbstrStates, ssaTest);
    list.clear();
    list.add("(declare-fun var2 () Int)");
    list.add("(declare-fun |fun::varB| () Int)");
    list.add("(declare-fun |fun::varC| () Int)");
    Truth.assertThat(convertedRequirements.getFirst().getFirst()).containsExactlyElementsIn(list);
    s = "(define-fun pre Bool()  (and (> var2 |fun::varB|) (< |fun::varC| 0)))";
    Truth.assertThat(convertedRequirements.getFirst().getSecond()).isEqualTo(s);
    Truth.assertThat(convertedRequirements.getSecond().getFirst()).isEmpty();
    s = "(define-fun post Bool ()  true)";
    Truth.assertThat(convertedRequirements.getSecond().getSecond()).isEqualTo(s);

    pAbstrStates = new ArrayList<>();
    pAbstrStates.add(pf1State);
    pAbstrStates.add(pf2State);
    convertedRequirements = pReqTrans.convertRequirements(ptrueState, pAbstrStates, ssaTest);
    Truth.assertThat(convertedRequirements.getFirst().getFirst()).isEmpty();
    s = "(define-fun pre Bool()  true)";
    Truth.assertThat(convertedRequirements.getFirst().getSecond()).isEqualTo(s);
    list.clear();
    list.add("(declare-fun var1@1 () Int)");
    list.add("(declare-fun var3@1 () Int)");
    list.add("(declare-fun |fun::var1| () Int)");
    list.add("(declare-fun var2 () Int)");
    list.add("(declare-fun |fun::varB@1| () Int)");
    list.add("(declare-fun |fun::varC| () Int)");
    Truth.assertThat(convertedRequirements.getSecond().getFirst()).containsExactlyElementsIn(list);
    s = "(define-fun post Bool () (or (and (or (> var1@1 0) (= var3@1 0)) (< |fun::var1| 0))(and (> var2 |fun::varB@1|) (< |fun::varC| 0))))";
    Truth.assertThat(convertedRequirements.getSecond().getSecond()).isEqualTo(s);
  }
}
