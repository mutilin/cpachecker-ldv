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
package org.sosy_lab.cpachecker.cpa.octagon;

import org.sosy_lab.cpachecker.util.octagon.Octagon;
import org.sosy_lab.cpachecker.util.octagon.OctagonManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * An element of octagon abstract domain. This element contains an {@link Octagon} which
 * is the concrete representation of the octagon and a map which
 * provides a mapping from variable names to variables.
 * see {@link Variable}.
 *
 * @author erkan
 *
 */
public class OctElement implements AbstractElement{

  // the octagon representation
  private Octagon octagon;
  
	// mapping from variable name to its identifier
	private BiMap<String, Integer> variableToIndexMap;

	private final OctElement previousElement;
	
	// also top element
	public OctElement() {
    octagon = OctagonManager.universe(0);
    variableToIndexMap = HashBiMap.create();
    previousElement = null;
  }
	
	public OctElement(Octagon oct, BiMap<String, Integer> map, OctElement previousElement){
	  octagon = oct;
	  variableToIndexMap = map;
	  this.previousElement = previousElement;
	}
	
  @Override
  public boolean equals(Object pObj) {
    if(!(pObj instanceof OctElement))
      return false;
    OctElement otherOct = (OctElement) pObj;
    return this.octagon.equals(otherOct.octagon);
  }

  public void printOctagon() {
    OctagonManager.print(octagon);
  }
  
  @Override
  public String toString() {
    return variableToIndexMap + " [octagon]: " + octagon;
  }

  public Octagon getOctagon() {
    return octagon;
  }
  
  public int sizeOfVariables(){
    return variableToIndexMap.size();
  }
  
  public OctElement getPreviousElement() {
    return previousElement;
  }
  
  public BiMap<String, Integer> getVariableToIndexMap() {
    return variableToIndexMap;
  }

  public void addVariable(String pVarName, boolean pIsGlobal,
      String pFunctionName) {
    
    if(sizeOfVariables() == 0){
      
    }
  }
  
  @Override
  protected Object clone() throws CloneNotSupportedException {
    Octagon newOct = OctagonManager.full_copy(octagon);
    // TODO
//    return new OctElement(newOct, variableToIndexMap);
    return null;
  }

  public boolean addVar(String pVarName, String pFunctionName, boolean pIsGlobal) {
    
    // create variable name
    String varName = pIsGlobal? pVarName : pFunctionName + "::" + pVarName;

    // normally it should not contain
    if(!variableToIndexMap.containsKey(pVarName)){
      int sizeOfMap = variableToIndexMap.size();
      variableToIndexMap.put(varName, sizeOfMap);
    }
    // TODO
    return false;
  }
  
  
  
//	/**
//	 * Update the element with a new octagon element. This method is accessed frequently by {@link OctTransferRelation}
//	 * to update the current region.
//	 * @param ent
//	 */
//	public void update(OctElement ent){
//		this.oct = ent.oct;
//		this.variables = ent.variables;
//	}

//	public String (int i){
//	  
//	}getVarNameForId

//	@Override
//	public String toString() {
//
//		String s = "";
//
//		if (oct.getState() == 0){
//			s = s + "[ empty ] \n";
//			return s;
//		}
//
//		if (oct.getState() == 2){
//			s = s + " [ closed ]";
//		}
//
//		for (int i=0; i<oct.getDimension(); i++) {
//			String varName = getVarNameForId(i);
//			if (oct.getMatrix()[oct.matPos(2*i,2*i)].f > 0) {
//				s = s + "\n  " + varName + "-" + varName + " <= " + oct.getMatrix()[oct.matPos(2*i, 2*i)].f;
//			}
//			if (oct.getMatrix()[oct.matPos(2*i+1,2*i+1)].f > 0) {
//				s = s + "\n  " + "-"+ varName + "+" + varName + " <= " + oct.getMatrix()[oct.matPos(2*i+1,2*i+1)].f;
//			}
//			if ((oct.getMatrix()[oct.matPos(2*i+1,2*i)].f != Double.NEGATIVE_INFINITY) &&
//					(oct.getMatrix()[oct.matPos(2*i+1,2*i)].f != Double.POSITIVE_INFINITY)) {
//				s = s + "\n  " + varName + " <= " + (oct.getMatrix()[oct.matPos(2*i+1,2*i)].f)/2;
//			}
//			if ((oct.getMatrix()[oct.matPos(2*i,2*i+1)].f != Double.NEGATIVE_INFINITY) &&
//					(oct.getMatrix()[oct.matPos(2*i,2*i+1)].f != Double.POSITIVE_INFINITY)) {
//				s = s + "\n  " + "-" + varName + " <= " + (oct.getMatrix()[oct.matPos(2*i,2*i+1)].f)/2;
//			}
//		}
//
//		for (int i=0; i<oct.getDimension(); i++){
//			for (int j=i+1; j<oct.getDimension(); j++) {
//				String iVarName = getVarNameForId(i);
//				String jVarName = getVarNameForId(j);
//
//				if((oct.getMatrix()[oct.matPos(2*j,2*i)].f != Double.NEGATIVE_INFINITY) &&
//						(oct.getMatrix()[oct.matPos(2*j,2*i)].f != Double.POSITIVE_INFINITY)){
//					s = s + "\n  " + iVarName + "-" + jVarName +" <= " + (oct.getMatrix()[oct.matPos(2*j,2*i)].f);
//				}
//				// 2*j,2*i+1
//				if ((oct.getMatrix()[oct.matPos(2*j,2*i+1)].f != Double.NEGATIVE_INFINITY) &&
//						(oct.getMatrix()[oct.matPos(2*j,2*i+1)].f != Double.POSITIVE_INFINITY)) {
//					s = s + "\n  " + "-" + iVarName + "-" + jVarName +" <= " + (oct.getMatrix()[oct.matPos(2*j,2*i+1)].f);
//				}
//				// 2*j+1,2*i
//				if ((oct.getMatrix()[oct.matPos(2*j+1,2*i)].f != Double.NEGATIVE_INFINITY) &&
//						(oct.getMatrix()[oct.matPos(2*j+1,2*i)].f != Double.POSITIVE_INFINITY)) {
//					s = s + "\n  " + iVarName + "+" + jVarName +" <= " + (oct.getMatrix()[oct.matPos(2*j+1,2*i)]);
//				}
//				// 2*j+1,2*i+1
//				if ((oct.getMatrix()[oct.matPos(2*j+1,2*i+1)].f != Double.NEGATIVE_INFINITY) &&
//						(oct.getMatrix()[oct.matPos(2*j+1,2*i+1)].f != Double.POSITIVE_INFINITY)){
//					s = s + "\n  " + jVarName + "-" + iVarName +" <= " + (oct.getMatrix()[oct.matPos(2*j+1,2*i+1)]);
//				}
//			}
//		}
//		s = s + "\n";
//		return s;
//
//	}

//	/**
//	 * Adds a new variable in form of functionName::variableName. For example if a
//	 * new variable 'a' is declared in the function main(), main::a is added to the
//	 * list of variables. After this operation you have to increase octagon
//	 * dimension manually!
//	 * @param varName Name of the variable.
//	 * @param funcName Name of the function that contains the variable
//	 * @return true if variable is added succesfully
//	 */
//	public boolean addVar(String varName, String funcName) {
//		String variableName = funcName + "::" + varName;
//		// add if the variable is not already in the variables set
//		if(!variables.containsKey(variableName)){
//			variables.put(variableName, getNumberOfVars());
//			return true;
//		}
//		return false;
//	}
//
//	/**
//	 * Remove a variable from the list by its variable id.
//	 * @param varId Variable id.
//	 */
//	public void removeVar(int varId, int numOfDims) {
//		// TODO
//		OctWrapper ow = new OctWrapper();
//		oct = ow.J_removeDimensionAtPosition(oct, varId, numOfDims, true);
//		variables.remove(getVarNameForId(varId));
//	}
//
//	/**
//	 * Retrieve the variable's id by its name.
//	 * @param globalVars List of global variables
//	 * @param varName Name of the variable.
//	 * @param functionName Name of the function.
//	 * @return id of the variable
//	 */
//	public int getVariableId(List<String> globalVars, String varName, String functionName){
//		String variableName = "";
//		if(globalVars.contains(varName)){
//			variableName = "::" + varName;
//		}
//		else{
//			variableName = functionName + "::" + varName;
//		}
//		return variables.get(variableName);
//	}
//
//	/**
//	 * Total number of variables.
//	 * @return
//	 */
//	public int getNumberOfVars() {
////		assert(variables.size() == oct.getDimension());
//		return variables.size();
//	}
//
//	/** Is octagon empty?
//	 * @return true if octagon is empty, false o.w.
//	 */
//	public boolean isEmpty(){
//		return LibraryAccess.isEmpty(this);
//	}
//
//	/**
//	 * Retrieve the map of variables.
//	 * @return map of variables.
//	 */
//	public HashMap<String, Integer> getVariableMap(){
//		return variables;
//	}
//
//	/**
//	 * Asks if the variable list contains a variable.
//	 * @param varName Name of the variable.
//	 * @return true if the list contains variable, false o.w.
//	 */
//	public boolean contains(String varName) {
//		return variables.containsKey(varName);
//	}
//
//	public void changeVarId(String varName, int id){
//		variables.remove(varName);
//		variables.put(varName, id);
//	}
//
//	public void changeVarName(String formerName, String newName){
//		//sdf
//	}
//
//	public void setVariableMap(HashMap<String, Integer> newVariablesMap) {
//		variables = newVariablesMap;
//
//	}
}
