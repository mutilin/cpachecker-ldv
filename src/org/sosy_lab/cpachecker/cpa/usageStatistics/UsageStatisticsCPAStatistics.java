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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.usageStatistics.AccessType.EdgeType;
import org.sosy_lab.cpachecker.util.AbstractStates;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsCPAStatistics implements Statistics {
  private Map<Identifier, Set<UsageInfo>> Stat;
  private int FullCounter = 0;

  PrintWriter writer = null;
  FileOutputStream file = null;
  DataProcessing dataProcess = null;
  CodeCovering covering;

  @Option(name="output", description="file to write results")
  private String FileName = "race_results.txt";

  @Option(values={"SIMPLE", "SET"},toUppercase=true,
      description="which data process we should use")
  private String process = "SIMPLE";

  @Option(description="if we need to print all variables, not only unsafe cases")
  private boolean fullstatistics = true;

  @Option(description="Do we need to store statistics of all variables or only pointers")
  private boolean onlypointers = true;

  UsageStatisticsCPAStatistics(Configuration config, CodeCovering cover) throws InvalidConfigurationException{
    Stat = new HashMap<Identifier, Set<UsageInfo>>();
    config.inject(this);

    if (process.equals("SIMPLE"))
      dataProcess = new DataProcessSimple();
    else if (process.equals("SET"))
      dataProcess = new DataProcessSetAnalysis();
    else {
      System.out.println("Unknown data procession " + process);
      System.exit(0);
    }
    covering = cover;


    try {
      file = new FileOutputStream (FileName);
      writer = new PrintWriter(file);
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Cannot open file " + FileName);
      System.exit(0);
    }
    //writer.close();
  }

  public boolean addUsageDecl(Identifier id, AbstractState wrappedState) {
    LocationState locationState = AbstractStates.extractStateByType(wrappedState, LocationState.class);
    LockStatisticsState lockState = AbstractStates.extractStateByType(wrappedState, LockStatisticsState.class);
    CallstackState callstackState = AbstractStates.extractStateByType(wrappedState, CallstackState.class);

    LineInfo lineInfo = new LineInfo(locationState.getLocationNode().getLineNumber());
    AccessType acc = new AccessType(false, AccessType.EdgeType.DECLARATION);

    UsageInfo usage = new UsageInfo(lineInfo, acc, lockState, callstackState);

    Set<UsageInfo> uset;
    if (!Stat.containsKey(id)) {
      uset = new HashSet<UsageInfo>();
      Stat.put(id, uset);
    } else {
      uset = Stat.get(id);
    }

    return uset.add(usage);
  }

  Identifier createIdentifierFromDecl(CDeclaration decl, AbstractState wrappedState) {
    CallstackState callstackState = AbstractStates.extractStateByType(wrappedState, CallstackState.class);

    String name = decl.getName();
    String type = decl.getType().toASTString("");
    if(decl.isGlobal()) {
      //generate global variable id
      return new GlobalVariableIdentifier(name, type);
    } else {
      //generate local variable id
      String func = callstackState.getCurrentFunction();
      return new LocalVariableIdentifier(name, type, func);
    }
  }

  public void add(UsageStatisticsState state, CDeclaration decl){

    AbstractState wrappedState = state.getWrappedState();
    Identifier id = createIdentifierFromDecl(decl, wrappedState);

    addUsageDecl(id, wrappedState);
    FullCounter++;
  }

  public void add(UsageStatisticsState state, CExpression pStatement,
                  boolean isWrite, EdgeType ptype) {

    AbstractState wrappedState = state.getWrappedState();

    Identifier id = createIdentifier(pStatement, wrappedState);
    int line = pStatement.getFileLocation().getStartingLineNumber();

    LockStatisticsState lockState = AbstractStates.extractStateByType(wrappedState, LockStatisticsState.class);
    CallstackState callstackState = AbstractStates.extractStateByType(wrappedState, CallstackState.class);

    LineInfo lineInfo = new LineInfo(line);
    AccessType acc = new AccessType(isWrite, ptype);

    UsageInfo usage = new UsageInfo(lineInfo, acc, lockState, callstackState);

    Set<UsageInfo> uset;
    if (!Stat.containsKey(id)) {
      uset = new HashSet<UsageInfo>();
      Stat.put(id, uset);
    } else {
      uset = Stat.get(id);
    }

    uset.add(usage);

    FullCounter++;
  }

  String getType(CExpression e) {
    if (e instanceof CIdExpression) {
      CType type = ((CDeclaration)((CIdExpression)e).getDeclaration()).getType();
      if (type instanceof CPointerType)
        return ((CPointerType)type).toASTString("");
      else
        return type.toASTString("");
    }
    else if (e instanceof CUnaryExpression)
      return getType(((CUnaryExpression) e).getOperand());
    else if (e instanceof CFieldReference)
      return getType(((CFieldReference) e).getFieldOwner());
    else {
      assert true;
      return null;
      //TODO make it carefully
    }
  }

  Identifier createIdentifierForStructure(CFieldReference expression, AbstractState wrappedState) {
    String name = expression.getFieldName();
    String type = getType(expression);

    return new StructureIdentifier(name, type);
  }

  Identifier createIdentifier(CExpression expression, AbstractState wrappedState) {
    if (expression instanceof CIdExpression) {
      CSimpleDeclaration decl = ((CIdExpression)expression).getDeclaration();
      assert (decl instanceof CDeclaration);

      return createIdentifierFromDecl((CDeclaration)decl, wrappedState);
    }
    else if (expression instanceof CFieldReference) {
      return createIdentifierForStructure((CFieldReference)expression, wrappedState);
    }
    else if (expression instanceof CUnaryExpression) {
      return createIdentifier(((CUnaryExpression)expression).getOperand(), wrappedState);
    }
    else {
      System.err.println(expression.toASTString() + " " + expression.getClass());
      assert true;
      return null;
    }
  }

  public void add(UsageStatisticsState pNewState, CRightHandSide pStatement,
                  boolean isKnownPointer, boolean isWrite, EdgeType ptype) {

    if (pStatement instanceof CIdExpression) {
      CSimpleDeclaration decl = ((CIdExpression)pStatement).getDeclaration();
      if (decl != null){
        CType type = decl.getType();
        if (decl instanceof CDeclaration &&(!onlypointers || type instanceof CPointerType)) {
          add(pNewState, (CIdExpression)pStatement, isWrite, ptype);
        }
        else if (decl instanceof CDeclaration && (!onlypointers || isKnownPointer)) {
          add(pNewState, (CIdExpression)pStatement, isWrite, ptype);
        }
      }
      else if (!onlypointers){
        //real situation
        add(pNewState, (CIdExpression)pStatement, isWrite, ptype);
      }
    }
    else if (pStatement instanceof CUnaryExpression && (!onlypointers || ((CUnaryExpression)pStatement).getOperator() == UnaryOperator.STAR))
    //*a
      add(pNewState, ((CUnaryExpression)pStatement).getOperand(), true, isWrite, ptype);
    else if (pStatement instanceof CFieldReference && (!onlypointers || ((CFieldReference)pStatement).isPointerDereference())) {
    //a->b
      add(pNewState, ((CFieldReference)pStatement), isWrite, ptype);
    }
    else if (pStatement instanceof CFieldReference) {
    // it can be smth like (*a).b
      CExpression tmpExpression = ((CFieldReference)pStatement).getFieldOwner();

      if (tmpExpression instanceof CUnaryExpression &&
         ((CUnaryExpression)tmpExpression).getOperator() == UnaryOperator.STAR)
        add(pNewState, ((CUnaryExpression)tmpExpression).getOperand(), true, isWrite, ptype);
    }
    else if (pStatement instanceof CBinaryExpression) {
      add(pNewState, ((CBinaryExpression)pStatement).getOperand1(), isKnownPointer, isWrite, ptype);
      add(pNewState, ((CBinaryExpression)pStatement).getOperand2(), isKnownPointer, isWrite, ptype);
    }
  }

  /*private Collection<VariableInfo> FindUnsafeCases(Collection<VariableInfo> locks) {
  Map<Integer, Set<Set<LockStatisticsMutex>>> Cases = new HashMap<Integer, Set<Set<LockStatisticsMutex>>>();

  Collection<VariableInfo> unsafe = new HashSet<VariableInfo>();

  for (VariableInfo var : locks) {
    VariableInfo UnsafeTypes = new VariableInfo(var.getName());

    for (String type : var.keySet()) {
      TypeInfo typeInfo = var.get(type);

      Cases.clear();
      Set<Set<LockStatisticsMutex>> DifferentLocks;

      for (LineInfo line : typeInfo.getLines()) {
        if (Cases.containsKey(line.getLine())) {
          DifferentLocks = Cases.get(line.getLine());
          if (!DifferentLocks.contains(line.getLocks())) {
            DifferentLocks.add(line.getLocks());
          }
        }
        else {
          DifferentLocks = new HashSet<Set<LockStatisticsMutex>>();
          DifferentLocks.add(line.getLocks());
          Cases.put(line.getLine(), DifferentLocks);
        }
      }

      Map<Set<Set<LockStatisticsMutex>>, Set<Integer>> LocksCount = new HashMap<Set<Set<LockStatisticsMutex>>, Set<Integer>>();

      for (Integer line : Cases.keySet()) {
        DifferentLocks = Cases.get(line);
        if (!LocksCount.containsKey(DifferentLocks)) {
          Set<Integer> lines = new HashSet<Integer>();
          lines.add(line);
          LocksCount.put(DifferentLocks, lines);
        }
        else {
          Set<Integer> lines = LocksCount.get(DifferentLocks);
          lines.add(line);
        }
      }

      TypeInfo UnsafeLines = UnsafeTypes.new TypeInfo(type);

      boolean isDifferent = false;
      int FirstLine = -1;

      for (LineInfo line : typeInfo.getLines()) {
        if (LocksCount.get(Cases.get(line.getLine())).size() == 1 &&
          LocksCount.size() > 1) {
          UnsafeLines.add(line);
          if (FirstLine == -1) {
            FirstLine = line.getLine();
          }
          else {
            if (FirstLine != line.getLine()) {
              isDifferent = true;
            }
          }
        }
      }
      if (UnsafeLines.size() > 0 && isDifferent){
        UnsafeTypes.add(UnsafeLines);
      }
    }
    if (UnsafeTypes.size() > 0) {
      unsafe.add(UnsafeTypes);
    }
  }

  return unsafe;
}

/*private Map<String, Set<ActionInfo>> FindUnsafeCases3(Map<String, Set<ActionInfo>> locks) {

  Map<String, Set<ActionInfo>> unsafe = new HashMap<String, Set<ActionInfo>>();
  Map<Set<LockStatisticsMutex>, Set<ActionInfo>> MutexToAction =
    new HashMap<Set<LockStatisticsMutex>, Set<ActionInfo>>();

  for (String name : locks.keySet()) {
    MutexToAction.clear();
    Set<ActionInfo> Actions = locks.get(name);

    for (ActionInfo action : Actions) {
      if (!MutexToAction.containsKey(action.getLocks())) {
        Set<ActionInfo> lines = new HashSet<ActionInfo>();
        lines.add(action);
        MutexToAction.put(action.getLocks(), lines);
      }
      else {
        Set<ActionInfo> lines = MutexToAction.get(action.getLocks());
        if (!lines.contains(action.getLine()))
          lines.add(action);
      }
    }

    Set<ActionInfo> UnsafeActions = new HashSet<ActionInfo>();

    for (Set<LockStatisticsMutex> mutexes : MutexToAction.keySet()) {
      if (MutexToAction.get(mutexes).size() == 1 && MutexToAction.size() > 1) {
        for (ActionInfo action : MutexToAction.get(mutexes)) {
          //only one mutex is here
          UnsafeActions.add(action);
        }
      }
    }


    //check all unsafe actions
    Boolean isDifferent = false;
    Set<ActionInfo> ToRemove = new HashSet<ActionInfo>();
    for (ActionInfo action : UnsafeActions) {
      for (ActionInfo action2 : locks.get(name)) {
        if (action.getLine() != action2.getLine()) {
          isDifferent = true;
          break;
        }
      }
      if (!isDifferent)
        ToRemove.add(action);
    }

    if (ToRemove.size() > 0) {
      for (ActionInfo action : ToRemove){
        UnsafeActions.remove(action);
      }
    }
    if (UnsafeActions.size() > 0) {
      unsafe.put(name, UnsafeActions);
    }
  }
  return unsafe;
}*/

  private Set<LockStatisticsLock> FindMutexes() {
    Set<LockStatisticsLock> locks = new HashSet<LockStatisticsLock>();

    for (Identifier id : Stat.keySet()) {
      Set<UsageInfo> uset = Stat.get(id);
      for (UsageInfo uinfo : uset){
        for (LockStatisticsLock lock : uinfo.getLocks()) {
          if (!locks.contains(lock))
            locks.add(lock);
        }
      }
    }

    return locks;
  }

  private void printUnsafeCases(String comment, Collection<Identifier> identifiers, boolean details) {
  writer.println("-------------------------------------------------");
  writer.println("");

  int local = 0, global = 0, structures = 0;

  for (Identifier id : identifiers) {
    if (id instanceof GlobalVariableIdentifier)
      global++;
    else if (id instanceof LocalVariableIdentifier)
      local++;
    else if (id instanceof StructureIdentifier)
      structures++;
  }

  writer.println(comment);
  writer.println("Total unsafe cases:     " + identifiers.size());
  writer.println("--Global:               " + global);
  writer.println("--Local:                " + local);
  writer.println("--Structures:           " + structures);
  writer.println("");

  if (details && identifiers.size() > 0){

    for (Identifier id : identifiers) {
      writer.println("");
      writer.println(id.toString());
      writer.println("[");
      for (UsageInfo uinfo : Stat.get(id))
        writer.println(uinfo.toString());
      writer.println("]");
    }
    writer.println("");
  }
}


  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {

    int global = 0, local = 0, counter = 0, structures = 0;

    for (Identifier id : Stat.keySet()){
      Set<UsageInfo> uset = Stat.get(id);

      counter += uset.size();
      if (id instanceof GlobalVariableIdentifier)
        global++;
      else if (id instanceof LocalVariableIdentifier)
        local++;
      else if (id instanceof StructureIdentifier)
        structures++;
    }

    writer.println("General statistics");
    writer.println("");
    writer.println("Total variables:        " + Stat.size());
    writer.println("--Global:               " + global);
    writer.println("--Local:                " + local);
    writer.println("--Structures:           " + structures);
    writer.println("");

    writer.println("Total usages:           " + FullCounter);
    writer.println("Total unique usages:    " + counter);

    Set<LockStatisticsLock> mutexes = FindMutexes();

    writer.println("Number of used mutexes: " + mutexes.size());

    if (mutexes.size() > 0)
      writer.println("  " + mutexes.toString());


    Collection<Identifier> unsafeCases = dataProcess.process(Stat);
    printUnsafeCases(dataProcess.getDescription(), unsafeCases, true);

    /*unsafeCases = FindDifferentSets(Stat);
    printUnsafeCases("Lines with different sets of mutexes were printed", unsafeCases, true);
*/

    if (fullstatistics) {
      writer.println("");
      writer.println("=============================================");
      writer.println("");
      writer.println("Full statistics");
      writer.println("");

      for (Identifier id : Stat.keySet()) {
        writer.println("");
        writer.println(id.toString());
        writer.println("[");
        for (UsageInfo uinfo : Stat.get(id))
          writer.println(uinfo.toString());
        writer.println("]");
      }
      writer.println("");
    }

    if(file != null)
      writer.close();

    covering.generate();
  }

  @Override
  public String getName() {
    return "UsageStatisticsCPA";
  }
}
