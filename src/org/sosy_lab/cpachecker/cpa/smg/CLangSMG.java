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
package org.sosy_lab.cpachecker.cpa.smg;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.counterexample.IDExpression;
import org.sosy_lab.cpachecker.cpa.smg.SMGTransferRelation.SMGAddress;
import org.sosy_lab.cpachecker.cpa.smg.objects.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.objects.SMGRegion;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

import com.google.common.collect.Sets;

/**
 * Extending SMG with notions specific for programs in C language:
 *  - separation of global, heap and stack objects
 *  - null object and value
 */
public class CLangSMG extends SMG {
  /**
   * A container for object found on the stack:
   *  - local variables
   *  - parameters
   *
   * TODO: [STACK-FRAME-STRUCTURE] Perhaps it could be wrapped in a class?
   */
  final private Deque<CLangStackFrame> stack_objects = new ArrayDeque<>();

  /**
   * A container for objects allocated on heap
   */
  final private Set<SMGObject> heap_objects = new HashSet<>();

  /**
   * A container for global objects
   */
  final private Map<String, SMGRegion> global_objects = new HashMap<>();

  /**
   * A flag signifying the edge leading to this state caused memory to be leaked
   * TODO: Seems pretty arbitrary: perhaps we should have a more general solution,
   *       like a container with (type, message) error witness kind of thing?
   */
  private boolean has_leaks = false;

  static private LogManager logger = null;

  /**
   * A flag setting if the class should perform additional consistency checks.
   * It should be useful only during debugging, when is should find bad
   * external calls closer to their origin. We probably do not want t
   * run the checks in the production build.
   */
  static private boolean perform_checks = false;

  static public void setPerformChecks(boolean pSetting, LogManager logger) {
    CLangSMG.perform_checks = pSetting;
    CLangSMG.logger = logger;
  }

  static public boolean performChecks() {
    return CLangSMG.perform_checks;
  }

  /**
   * Constructor.
   *
   * Keeps consistency: yes
   *
   * Newly constructed CLangSMG contains a single nullObject with an address
   * pointing to it, and is empty otherwise.
   */
  public CLangSMG(MachineModel pMachineModel) {
    super(pMachineModel);
    heap_objects.add(getNullObject());
  }

  /**
   * Copy constructor.
   *
   * Keeps consistency: yes
   *
   * @param pHeap The original CLangSMG
   */
  public CLangSMG(CLangSMG pHeap) {
    super(pHeap);

    for (CLangStackFrame stack_frame : pHeap.stack_objects) {
      CLangStackFrame new_frame = new CLangStackFrame(stack_frame);
      stack_objects.add(new_frame);
    }

    heap_objects.addAll(pHeap.heap_objects);
    global_objects.putAll(pHeap.global_objects);
    has_leaks = pHeap.has_leaks;
  }

  /**
   * Add a object to the heap.
   *
   * Keeps consistency: no
   *
   * With checks: throws {@link IllegalArgumentException} when asked to add
   * an object already present.
   *
   * @param pObject Object to add.
   */
  public void addHeapObject(SMGObject pObject) {
    if (CLangSMG.performChecks() && heap_objects.contains(pObject)) {
      throw new IllegalArgumentException("Heap object already in the SMG: [" + pObject + "]");
    }
    heap_objects.add(pObject);
    addObject(pObject);
  }

  /**
   * Add a global object to the SMG
   *
   * Keeps consistency: no
   *
   * With checks: throws {@link IllegalArgumentException} when asked to add
   * an object already present, or an global object with a label identifying
   * different object

   * @param pObject Object to add
   */
  public void addGlobalObject(SMGRegion pObject) {
    if (CLangSMG.performChecks() && global_objects.values().contains(pObject)) {
      throw new IllegalArgumentException("Global object already in the SMG: [" + pObject + "]");
    }

    if (CLangSMG.performChecks() && global_objects.containsKey(pObject.getLabel())) {
      throw new IllegalArgumentException("Global object with label [" + pObject.getLabel() + "] already in the SMG");
    }

    global_objects.put(pObject.getLabel(), pObject);
    super.addObject(pObject);
  }

  /**
   * Adds an object to the current stack frame
   *
   * Keeps consistency: no
   *
   * @param pObject Object to add
   *
   * TODO: [SCOPES] Scope visibility vs. stack frame issues: handle cases where a variable is visible
   * but is is allowed to override (inner blocks)
   * TODO: Consistency check (allow): different objects with same label inside a frame, but in different block
   * TODO: Test for this consistency check
   *
   * TODO: Shall we need an extension for putting objects to upper frames?
   */
  public void addStackObject(SMGRegion pObject) {
    super.addObject(pObject);
    stack_objects.peek().addStackVariable(pObject.getLabel(), pObject);
  }

  /**
   * Add a new stack frame for the passed function.
   *
   * Keeps consistency: yes
   *
   * @param pFunctionDeclaration A function for which to create a new stack frame
   */
  public void addStackFrame(CFunctionDeclaration pFunctionDeclaration) {
    CLangStackFrame newFrame = new CLangStackFrame(pFunctionDeclaration, getMachineModel());

    // Return object is NULL for void functions
    SMGObject returnObject = newFrame.getReturnObject();
    if (returnObject != null) {
      super.addObject(newFrame.getReturnObject());
    }
    stack_objects.push(newFrame);
  }

  /**
   * Sets a flag indicating this SMG is a successor over the edge causing a
   * memory leak.
   *
   * Keeps consistency: yes
   */
  public void setMemoryLeak() {
    has_leaks = true;
  }

  /**
   * Remove a top stack frame from the SMG, along with all objects in it, and
   * any edges leading from/to it.
   *
   * TODO: A testcase with (invalid) passing of an address of a dropped frame object
   * outside, and working with them. For that, we should probably keep those as invalid, so
   * we can spot such bug.
   *
   * Keeps consistency: yes
   */
  public void dropStackFrame() {
    CLangStackFrame frame = stack_objects.pop();
    for (SMGObject object : frame.getAllObjects()) {
      removeObjectAndEdges(object);
    }

    if (CLangSMG.performChecks()) {
      CLangSMGConsistencyVerifier.verifyCLangSMG(CLangSMG.logger, this);
    }
  }

  /**
   * Prune the SMG: remove all unreachable objects (heap ones: global and stack
   * are always reachable) and values.
   *
   * TODO: Too large. Refactor into fewer pieces
   *
   * Keeps consistency: yes
   */
  public void pruneUnreachable() {
    Set<SMGObject> seen = new HashSet<>();
    Set<Integer> seen_values = new HashSet<>();
    Queue<SMGObject> workqueue = new ArrayDeque<>();

    // TODO: wrap to getStackObjects(), perhaps just internally?
    for (CLangStackFrame frame : getStackFrames()) {
      for (SMGObject stack_object : frame.getAllObjects()) {
        workqueue.add(stack_object);
      }
    }

    workqueue.addAll(getGlobalObjects().values());

    SMGEdgeHasValueFilter filter = new SMGEdgeHasValueFilter();

    /*
     * TODO: Refactor into generic methods for obtaining reachable/unreachable
     * subSMGs
     *
     * TODO: Perhaps introduce a SubSMG class which would be a SMG tied
     * to a certain (Clang)SMG and guaranteed to be a subset of it?
     */

    while ( ! workqueue.isEmpty()) {
      SMGObject processed = workqueue.remove();
      if ( ! seen.contains(processed)) {
        seen.add(processed);
        filter.filterByObject(processed);
        for (SMGEdgeHasValue outbound : getHVEdges(filter)) {
          SMGObject pointedObject = getObjectPointedBy(outbound.getValue());
          if ( pointedObject != null && ! seen.contains(pointedObject)) {
            workqueue.add(pointedObject);
          }
          if ( ! seen_values.contains(Integer.valueOf(outbound.getValue()))) {
            seen_values.add(Integer.valueOf(outbound.getValue()));
          }
        }
      }
    }

    /*
     * TODO: Refactor into generic methods for substracting SubSMGs (see above)
     */
    Set<SMGObject> stray_objects = new HashSet<>(Sets.difference(getObjects(), seen));
    for (SMGObject stray_object : stray_objects) {
      if (stray_object.notNull()) {
        if (isObjectValid(stray_object)) {
          setMemoryLeak();
        }
        removeObjectAndEdges(stray_object);
        heap_objects.remove(stray_object);

      }
    }

    Set<Integer> stray_values = new HashSet<>(Sets.difference(getValues(), seen_values));
    for (Integer stray_value : stray_values) {
      if (stray_value != getNullValue()) {
        // Here, we can't just remove stray value, we also have to remove the points-to edge
        if (isPointer(stray_value)) {
          removePointsToEdge(stray_value);
        }

        removeValue(stray_value);
      }
    }

    if (CLangSMG.performChecks()) {
      CLangSMGConsistencyVerifier.verifyCLangSMG(CLangSMG.logger, this);
    }
  }

  /* ********************************************* */
  /* Non-modifying functions: getters and the like */
  /* ********************************************* */

  /**
   * Getter for obtaining a string representation of the CLangSMG. Constant.
   *
   * @return String representation of the CLangSMG
   */
  @Override
  public String toString() {
    return "CLangSMG [\n stack_objects=" + stack_objects + "\n heap_objects=" + heap_objects + "\n global_objects="
        + global_objects + "\n " + valuesToString() + "\n " + ptToString() + "\n " + hvToString()
        + "\n" + getMapOfMemoryLocationsWithValue().toString() + "\n]";
  }

  private Map<MemoryLocation, Integer> getMapOfMemoryLocationsWithValue() {

    Set<MemoryLocation> memlocs = getTrackedMemoryLocations();
    Map<MemoryLocation, Integer> result = new HashMap<>();

    for (MemoryLocation memloc : memlocs) {
      Set<SMGEdgeHasValue> edge = getHVEdgeFromMemoryLocation(memloc);
      result.put(memloc, edge.iterator().next().getValue());
    }

    return result;
  }

  /**
   * Returns an SMGObject tied to the variable name. The name must be visible in
   * the current scope: it needs to be visible either in the current frame, or it
   * is a global variable. Constant.
   *
   * @param pVariableName A name of the variable
   * @return An object tied to the name, if such exists in the visible scope. Null otherwise.
   *
   * TODO: [SCOPES] Test for getting visible local object hiding other local object
   */
  public SMGRegion getObjectForVisibleVariable(String pVariableName) {
    // Look in the local frame
    if (stack_objects.size() != 0) {
      if (stack_objects.peek().containsVariable(pVariableName)) {
        return stack_objects.peek().getVariable(pVariableName);
      }
    }

    // Look in the global scope
    if (global_objects.containsKey(pVariableName)) {
      return global_objects.get(pVariableName);
    }
    return null;
  }

  /**
   * Returns the (modifiable) stack of frames containing objects. Constant.
   *
   * @return Stack of frames
   */
  public Deque<CLangStackFrame> getStackFrames() {
    //TODO: [FRAMES-STACK-STRUCTURE] This still allows modification, as queues
    // do not have the appropriate unmodifiable method. There is probably some good
    // way how to provide a read-only view for iteration, but I do not know it
    return stack_objects;
  }

  /**
   * Constant.
   *
   * @return Unmodifiable view of the set of the heap objects
   */
  public Set<SMGObject> getHeapObjects() {
    return Collections.unmodifiableSet(heap_objects);
  }

  /**
   * Constant.
   *
   * Checks whether given object is on the heap.
   *
   * @param object SMGObject to be checked.
   * @return True, if the given object is referenced in the set of heap objects, false otherwise.
   *
   */
  public boolean isHeapObject(SMGObject object) {
    return heap_objects.contains(object);
  }

  /**
   * Constant.
   *
   * @return Unmodifiable map from variable names to global objects.
   */
  public Map<String, SMGRegion> getGlobalObjects() {
    return Collections.unmodifiableMap(global_objects);
  }

  /**
   * Constant.
   *
   * @return True if the SMG is a successor over the edge causing some memory
   * to be leaked. Returns false otherwise.
   */
  public boolean hasMemoryLeaks() {
    // TODO: [MEMLEAK DETECTION] There needs to be a proper graph algorithm
    //       in the future. Right now, we can discover memory leaks only
    //       after unassigned malloc call result, so we know that immediately.
    return has_leaks;
  }

  /**
   * Constant.
   *
   * @return a {@link SMGObject} for current function return value
   */
  public SMGObject getFunctionReturnObject() {
    return stack_objects.peek().getReturnObject();
  }

  @Nullable
  public String getFunctionName(SMGObject pObject) {
    for (CLangStackFrame cLangStack : stack_objects) {
      if (cLangStack.getAllObjects().contains(pObject)) {
        return cLangStack.getFunctionDeclaration().getName();
      }
    }

    return null;
  }

  @Override
  public void mergeValues(int v1, int v2) {

    super.mergeValues(v1, v2);

    if (CLangSMG.performChecks()) {
      CLangSMGConsistencyVerifier.verifyCLangSMG(CLangSMG.logger, this);
    }
  }

  final public void removeHeapObjectAndEdges(SMGObject pObject) {
    heap_objects.remove(pObject);
    removeObjectAndEdges(pObject);
  }

  public IDExpression createIDExpression(SMGObject pObject) {

    if (global_objects.containsValue(pObject)) {
      // TODO Breaks if label is changed
      return new IDExpression(pObject.getLabel());
    }

    for (CLangStackFrame frame : stack_objects) {
      if (frame.getVariables().containsValue(pObject)) {
        // TODO Breaks if label is changed

        return new IDExpression(pObject.getLabel(), frame.getFunctionDeclaration().getName());
      }
    }

    return null;
  }

  private Set<SMGEdgeHasValue> getHVEdgeFromMemoryLocation(MemoryLocation pLocation) {

    SMGObject objectAtLocation = getObjectFromMemoryLocation(pLocation);

    if(objectAtLocation == null) {
      return Collections.emptySet();
    }

    SMGEdgeHasValueFilter filter = SMGEdgeHasValueFilter.objectFilter(objectAtLocation);

    filter.filterAtOffset((int) pLocation.getOffset());

    // Remember, edges may overlap with different types
    Set<SMGEdgeHasValue> edgesToForget = getHVEdges(filter);

    return edgesToForget;
  }

  @Nullable
  private SMGObject getObjectFromMemoryLocation(MemoryLocation pLocation) {

    String locId = pLocation.getIdentifier();

    if (pLocation.isOnFunctionStack()) {

      if (!hasStackFrame(pLocation.getFunctionName())) {
        return null;
      }

      CLangStackFrame frame = getStackFrame(pLocation.getFunctionName());

      if(locId.equals("___cpa_temp_result_var_")) {
        return frame.getReturnObject();
      }

      if (!frame.hasVariable(locId)) {
        return null;
      }

      return frame.getVariable(locId);
    } else if (global_objects.containsKey(locId)) {

      return global_objects.get(locId);
    } else if (hasHeapObjectWithId(locId)) {

      return getHeapObjectWithId(locId);
    } else {
      return null;
    }
  }

  public SMGStateInformation forget(MemoryLocation pLocation) {

    Set<SMGEdgeHasValue> edgesToForget = getHVEdgeFromMemoryLocation(pLocation);

    if(edgesToForget.isEmpty()) {
      return SMGStateInformation.of();
    }

    for(SMGEdgeHasValue edgeToForget : edgesToForget) {
      removeHasValueEdge(edgeToForget);
    }

    return SMGStateInformation.of(edgesToForget, getPTEdges());
  }

  private SMGObject getHeapObjectWithId(String pLocId) {

    for (SMGObject object : heap_objects) {
      if (object.getLabel().equals(pLocId)) {
        return object;
      }
    }

    throw new AssertionError("Heap has no such object");
  }

  private boolean hasHeapObjectWithId(String pLocId) {

    for (SMGObject object : heap_objects) {
      if (object.getLabel().equals(pLocId)) {
        return true;
      }
    }

    return false;
  }

  /*
   * Returns stack frame of given function name
   */
  private boolean hasStackFrame(String pFunctionName) {

    for (CLangStackFrame frame : stack_objects) {
      String frameName = frame.getFunctionDeclaration().getName();
      if (frameName.equals(pFunctionName)) {
        return true;
      }
    }

    return false;
  }

  /*
   * Returns stack frame of given function name
   */
  private CLangStackFrame getStackFrame(String pFunctionName) {

    for (CLangStackFrame frame : stack_objects) {
      String frameName = frame.getFunctionDeclaration().getName();
      if (frameName.equals(pFunctionName)) {
        return frame;
      }
    }

    throw new AssertionError("No stack frame " + pFunctionName + " exists.");
  }

  public void remember(MemoryLocation pLocation, SMGStateInformation pForgottenInformation) {
    for(SMGEdgeHasValue edge : pForgottenInformation.getHvEdges()) {
      if(!getHVEdges().contains(edge)) {
        rememberHasValueEdge(edge, pForgottenInformation.getPtEdges(), pLocation);
      }
    }
  }

  private void rememberHasValueEdge(SMGEdgeHasValue pEdge, Map<Integer, SMGEdgePointsTo> pPtEdges,
      MemoryLocation pLocation) {
    SMGObject edgeObject = pEdge.getObject();
    Integer value = pEdge.getValue();

    assert getObjectFromMemoryLocation(pLocation) == edgeObject : "SMG object at location "
        + pLocation.getAsSimpleString() + " is unexpected object";

    if (!getValues().contains(value)) {
      addValue(value);
    }

    addHasValueEdge(pEdge);

    if (pPtEdges.containsKey(value)) {
      SMGEdgePointsTo ptEdge = pPtEdges.get(value);

      // FIXME does not keep consistency
      if (!getPTEdges().containsKey(value)) {
        addPointsToEdge(ptEdge);
      }
    }
  }

  public Set<MemoryLocation> getTrackedMemoryLocations() {

    Set<MemoryLocation> result = new HashSet<>();

    for (SMGEdgeHasValue hvedge : getHVEdges()) {
      result.add(resolveMemLoc(hvedge));
    }

    return result;
  }

  private MemoryLocation resolveMemLoc(SMGEdgeHasValue hvEdge) {

    SMGObject object = hvEdge.getObject();
    long offset = hvEdge.getOffset();

    if (global_objects.containsValue(object) || isHeapObject(object)) {
      return MemoryLocation.valueOf(object.getLabel(), offset);
    } else {

      CLangStackFrame frame = getStackFrameOfObject(object);

      String functionName = frame.getFunctionDeclaration().getName();

      return MemoryLocation.valueOf(functionName, object.getLabel(), offset);
    }
  }

  private CLangStackFrame getStackFrameOfObject(SMGObject pObject) {

    for (CLangStackFrame frame : stack_objects) {
      if(frame.containsVariable(pObject.getLabel())
          || pObject == frame.getReturnObject()){
        return frame;
      }
    }

    throw new AssertionError("object " + pObject.getLabel() + " is not on a function stack");
  }

  public MemoryLocation resolveMemLoc(SMGAddress pValue, String pFunctionName) {
    SMGObject object = pValue.getObject();
    long offset = pValue.getOffset().getAsLong();

    if (global_objects.containsValue(object) || isHeapObject(object)) {
      return MemoryLocation.valueOf(object.getLabel(), offset);
    } else {
      return MemoryLocation.valueOf(pFunctionName, object.getLabel(), offset);
    }
  }

  @Nullable
  public CType getTypeForMemoryLocation(MemoryLocation pMemoryLocation) {
    Set<SMGEdgeHasValue> edgesToForget = getHVEdgeFromMemoryLocation(pMemoryLocation);

    if(edgesToForget.isEmpty()) {
      return null;
    }

    return edgesToForget.iterator().next().getType();
  }
}

class CLangSMGConsistencyVerifier {
  private CLangSMGConsistencyVerifier() {} /* utility class */

  /**
   * Records a result of a single check to a logger along with a message
   *
   * @param pResult Result of the check
   * @param pLogger Logger to log the message
   * @param pMessage Message to be logged
   * @return The result of the check, i.e. equivalent to pResult
   */
  static private boolean verifyCLangSMGProperty(boolean pResult, LogManager pLogger, String pMessage) {
    pLogger.log(Level.FINEST, pMessage, ":", pResult);
    return pResult;
  }

  /**
   * Verifies that heap and global object sets are disjunct
   *
   * @param pLogger Logger to log the message
   * @param pSmg SMG to check
   * @return True if {@link pSmg} is consistent w.r.t. this criteria. False otherwise.
   */
  static private boolean verifyDisjunctHeapAndGlobal(LogManager pLogger, CLangSMG pSmg) {
    Map<String, SMGRegion> globals = pSmg.getGlobalObjects();
    Set<SMGObject> heap = pSmg.getHeapObjects();

    boolean toReturn = Collections.disjoint(globals.values(), heap);

    if (! toReturn) {
      pLogger.log(Level.SEVERE, "CLangSMG inconsistent, heap and global objects are not disjoint");
    }

    return toReturn;
  }

  /**
   * Verifies that heap and stack object sets are disjunct
   *
   * @param pLogger Logger to log the message
   * @param pSmg SMG to check
   * @return True if {@link pSmg} is consistent w.r.t. this criteria. False otherwise.
   */
  static private boolean verifyDisjunctHeapAndStack(LogManager pLogger, CLangSMG pSmg) {
    Deque<CLangStackFrame> stack_frames = pSmg.getStackFrames();
    Set<SMGObject> stack = new HashSet<>();

    for (CLangStackFrame frame: stack_frames) {
      stack.addAll(frame.getAllObjects());
    }
    Set<SMGObject> heap = pSmg.getHeapObjects();

    boolean toReturn = Collections.disjoint(stack, heap);

    if (! toReturn) {
      pLogger.log(Level.SEVERE, "CLangSMG inconsistent, heap and stack objects are not disjoint: " + Sets.intersection(stack, heap));
    }

    return toReturn;
  }

  /**
   * Verifies that global and stack object sets are disjunct
   *
   * @param pLogger Logger to log the message
   * @param pSmg SMG to check
   * @return True if {@link pSmg} is consistent w.r.t. this criteria. False otherwise.
   */
  static private boolean verifyDisjunctGlobalAndStack(LogManager pLogger, CLangSMG pSmg) {
    Deque<CLangStackFrame> stack_frames = pSmg.getStackFrames();
    Set<SMGObject> stack = new HashSet<>();

    for (CLangStackFrame frame: stack_frames) {
      stack.addAll(frame.getAllObjects());
    }
    Map<String, SMGRegion> globals = pSmg.getGlobalObjects();

    boolean toReturn = Collections.disjoint(stack, globals.values());

    if (! toReturn) {
      pLogger.log(Level.SEVERE, "CLangSMG inconsistent, global and stack objects are not disjoint");
    }

    return toReturn;
  }

  /**
   * Verifies that heap, global and stack union is equal to the set of all objects
   *
   * @param pLogger Logger to log the message
   * @param pSmg SMG to check
   * @return True if {@link pSmg} is consistent w.r.t. this criteria. False otherwise.
   */
  static private boolean verifyStackGlobalHeapUnion(LogManager pLogger, CLangSMG pSmg) {
    HashSet<SMGObject> object_union = new HashSet<>();

    object_union.addAll(pSmg.getHeapObjects());
    object_union.addAll(pSmg.getGlobalObjects().values());

    for (CLangStackFrame frame : pSmg.getStackFrames()) {
      object_union.addAll(frame.getAllObjects());
    }

    boolean toReturn = object_union.containsAll(pSmg.getObjects()) &&
                       pSmg.getObjects().containsAll(object_union);

    if (! toReturn) {
      pLogger.log(Level.SEVERE, "CLangSMG inconsistent: union of stack, heap and global object is not the same set as the set of SMG objects");
    }

    return toReturn;
  }

  /**
   * Verifies several NULL object-related properties
   * @param pLogger Logger to log the message
   * @param pSmg SMG to check
   *
   * @return True if {@link pSmg} is consistent w.r.t. this criteria. False otherwise.
   */
  static private boolean verifyNullObjectCLangProperties(LogManager pLogger, CLangSMG pSmg) {
    // Verify that there is no NULL object in global scope
    for (SMGObject obj: pSmg.getGlobalObjects().values()) {
      if (! obj.notNull()) {
        pLogger.log(Level.SEVERE, "CLangSMG inconsistent: null object in global object set [" + obj + "]");
        return false;
      }
    }

    // Verify there is no more than one NULL object in the heap object set
    SMGObject firstNull = null;
    for (SMGObject obj: pSmg.getHeapObjects()) {
      if (! obj.notNull()) {
        if (firstNull != null) {
          pLogger.log(Level.SEVERE, "CLangSMG inconsistent: second null object in heap object set [first=" + firstNull + ", second=" + obj +"]" );
          return false;
        } else {
          firstNull = obj;
        }
      }
    }

    // Verify there is no NULL object in the stack object set
    for (CLangStackFrame frame: pSmg.getStackFrames()) {
      for (SMGObject obj: frame.getAllObjects()) {
        if (! obj.notNull()) {
          pLogger.log(Level.SEVERE, "CLangSMG inconsistent: null object in stack object set [" + obj + "]");
          return false;
        }
      }
    }

    // Verify there is at least one NULL object
    if (firstNull == null) {
      pLogger.log(Level.SEVERE, "CLangSMG inconsistent: no null object");
      return false;
    }

    return true;
  }

  /**
   * Verify the global scope is consistent: each record points to an
   * appropriately labeled object
   *
   * @param pLogger Logger to log the message
   * @param pSmg SMG to check
   * @return True if {@link pSmg} is consistent w.r.t. this criteria. False otherwise.
   */
  static private boolean verifyGlobalNamespace(LogManager pLogger, CLangSMG pSmg) {
    Map<String, SMGRegion> globals = pSmg.getGlobalObjects();

    for (String label: pSmg.getGlobalObjects().keySet()) {
      String globalLabel = globals.get(label).getLabel();
      if (! globalLabel.equals(label)) {
        pLogger.log(Level.SEVERE,  "CLangSMG inconsistent: label [" + label + "] points to an object with label [" + pSmg.getGlobalObjects().get(label).getLabel() + "]");
        return false;
      }
    }

    return true;
  }

  /**
   * Verify the stack name space: each record points to an appropriately
   * labeled object
   *
   * @param pLogger Logger to log the message
   * @param pSmg
   * @return True if {@link pSmg} is consistent w.r.t. this criteria. False otherwise.
   */
  static private boolean verifyStackNamespaces(LogManager pLogger, CLangSMG pSmg) {
    HashSet<SMGObject> stack_objects = new HashSet<>();

    for (CLangStackFrame frame : pSmg.getStackFrames()) {
      for (SMGObject object : frame.getAllObjects()) {
        if (stack_objects.contains(object)) {
          pLogger.log(Level.SEVERE, "CLangSMG inconsistent: object [" + object + "] present multiple times in the stack");
          return false;
        }
        stack_objects.add(object);
      }
    }

    return true;
  }

  /**
   * Verify all the consistency properties related to CLangSMG
   *
   * @param pLogger Logger to log results
   * @param pSmg SMG to check
   * @return True if {@link pSmg} is consistent w.r.t. this criteria. False otherwise.
   */
  static public boolean verifyCLangSMG(LogManager pLogger, CLangSMG pSmg) {
    boolean toReturn = SMGConsistencyVerifier.verifySMG(pLogger, pSmg);

    pLogger.log(Level.FINEST, "Starting constistency check of a CLangSMG");

    toReturn = toReturn && verifyCLangSMGProperty(
        verifyDisjunctHeapAndGlobal(pLogger, pSmg),
        pLogger,
        "Checking CLangSMG consistency: heap and global object sets are disjunt");
    toReturn = toReturn && verifyCLangSMGProperty(
        verifyDisjunctHeapAndStack(pLogger, pSmg),
        pLogger,
        "Checking CLangSMG consistency: heap and stack objects are disjunct");
    toReturn = toReturn && verifyCLangSMGProperty(
        verifyDisjunctGlobalAndStack(pLogger, pSmg),
        pLogger,
        "Checking CLangSMG consistency: global and stack objects are disjunct");
    toReturn = toReturn && verifyCLangSMGProperty(
        verifyStackGlobalHeapUnion(pLogger, pSmg),
        pLogger,
        "Checking CLangSMG consistency: global, stack and heap object union contains all objects in SMG");
    toReturn = toReturn && verifyCLangSMGProperty(
        verifyNullObjectCLangProperties(pLogger, pSmg),
        pLogger,
        "Checking CLangSMG consistency: null object invariants hold");
    toReturn = toReturn && verifyCLangSMGProperty(
        verifyGlobalNamespace(pLogger, pSmg),
        pLogger,
        "Checking CLangSMG consistency: global namespace problem");
    toReturn = toReturn && verifyCLangSMGProperty(
        verifyStackNamespaces(pLogger, pSmg),
        pLogger,
        "Checking CLangSMG consistency: stack namespace");

    pLogger.log(Level.FINEST, "Ending consistency check of a CLangSMG");

    return toReturn;
  }
}
