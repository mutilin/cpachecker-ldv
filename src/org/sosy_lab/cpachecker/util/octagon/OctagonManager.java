/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.octagon;


public class OctagonManager {

  private static final OctWrapper wrapper = new OctWrapper();

  /* Initialization */
  public static boolean init(){
    return wrapper.J_init();
  }

  /* num handling function*/

  /* allocate new space for num array and init*/
  public static NumArray init_num_t (int n){
    long l = wrapper.J_init_n(n);
    return new NumArray(l);
  }

  /* num copy */
  public static void num_set (NumArray n1, NumArray n2){
    wrapper.J_num_set(n1.getArray(), n2.getArray());
  }

  /* set int */
  public static void num_set_int (NumArray n, int pos, int i){
    wrapper.J_num_set_int(n.getArray(), pos, i);
  }
  /* set float */
  public static void num_set_float (NumArray n, int pos, double d){
    wrapper.J_num_set_float(n.getArray(), pos, d);
  }
  /* set infinity */
  public static void num_set_inf (NumArray n, int pos){
    wrapper.J_num_set_inf(n.getArray(), pos);
  }

  public static long num_get_int (NumArray n, int pos){
    return wrapper.J_num_get_int(n.getArray(), pos);
  }

  public static double num_get_float (NumArray n, int pos){
    return wrapper.J_num_get_float(n.getArray(), pos);
  }

  public static boolean num_infty (NumArray n, int pos){
    return wrapper.J_num_infty(n.getArray(), pos);
  }

  public static void num_clear_n(NumArray n, int size){
    wrapper.J_num_clear_n(n.getArray(), size);
  }

  /* Octagon handling functions */

  /* Octagon Creation */
  public static Octagon empty (int n){
    long l = wrapper.J_empty(n);
    return new Octagon(l);
  }

  public static Octagon universe (int n){
    long l = wrapper.J_universe(n);
    return new Octagon(l);
  }
  public static void free (Octagon oct){
    wrapper.J_free(oct.getOctId());
  }

  public static Octagon copy (Octagon oct){
    long l = wrapper.J_copy(oct.getOctId());
    return new Octagon(l);
  }

  public static Octagon full_copy (Octagon oct){
    long l = wrapper.J_full_copy(oct.getOctId());
    return new Octagon(l);
  }

  /* Query Functions */
  public static int dimension (Octagon oct){
    return wrapper.J_dimension(oct.getOctId());
  }

  public static int nbconstraints (Octagon oct){
    return wrapper.J_nbconstraints(oct.getOctId());
  }

  /* Test Functions */
  public static boolean isEmpty (Octagon oct){
    return wrapper.J_isEmpty(oct.getOctId());
  }

  public static int isEmptyLazy (Octagon oct){
    return wrapper.J_isEmptyLazy(oct.getOctId());
  }

  public static boolean isUniverse (Octagon oct){
    return wrapper.J_isUniverse(oct.getOctId());
  }

  public static boolean isIncludedIn (Octagon oct1, Octagon oct2){
    return wrapper.J_isIncludedIn(oct1.getOctId(), oct2.getOctId());
  }

  public static int isIncludedInLazy (Octagon oct1, Octagon oct2){
    return wrapper.J_isIncludedInLazy(oct1.getOctId(), oct2.getOctId());
  }

  public static boolean isEqual (Octagon oct1, Octagon oct2){
    return wrapper.J_isEqual(oct1.getOctId(), oct2.getOctId());
  }

  public static int isEqualLazy (Octagon oct1, Octagon oct2){
    return wrapper.J_isEqualLazy(oct1.getOctId(), oct2.getOctId());
  }

  public static boolean isIn (Octagon oct1, NumArray array){
    return wrapper.J_isIn(oct1.getOctId(), array.getArray());
  }

  /* Operators */
  public static Octagon intersection (Octagon oct1, Octagon oct2){
    long l = wrapper.J_intersection (oct1.getOctId(), oct2.getOctId(), false);
    return new Octagon(l);
  }

  public static Octagon union (Octagon oct1, Octagon oct2){
    long l = wrapper.J_union (oct1.getOctId(), oct2.getOctId(), false);
    return new Octagon(l);
  }

  /* int widening = 0 -> OCT_WIDENING_FAST
   * int widening = 1 ->  OCT_WIDENING_ZERO
   * int widening = 2 -> OCT_WIDENING_UNIT*/
  public Octagon widening (Octagon oct1, Octagon oct2){
    long l = wrapper.J_widening (oct1.getOctId(), oct2.getOctId(), false, 1);
    return new Octagon(l);
  }

  public static Octagon narrowing (Octagon oct1, Octagon oct2){
    long l = wrapper.J_narrowing (oct1.getOctId(), oct2.getOctId(), false);
    return new Octagon(l);
  }

  /* Transfer Functions */
  public static Octagon forget (Octagon oct, int k){
    long l = wrapper.J_forget (oct.getOctId(), k, false);
    return new Octagon(l);
  }

  public static Octagon assingVar (Octagon oct, int k, NumArray array){
    long l = wrapper.J_assingVar(oct.getOctId(), k, array.getArray(), false);
    return new Octagon(l);
  }

  public static Octagon addBinConstraint(Octagon oct, int noOfConstraints, NumArray array){
    long  l = wrapper.J_addBinConstraints(oct.getOctId(), noOfConstraints, array.getArray(), false);
    return new Octagon(l);
  }

  public static Octagon substituteVar (Octagon oct, int x, NumArray array){
    long l = wrapper.J_substituteVar(oct.getOctId(), x, array.getArray(), false);
    return new Octagon(l);
  }

  public static Octagon addConstraint (Octagon oct, NumArray array){
    long l = wrapper.J_addConstraint(oct.getOctId(), array.getArray(), false);
    return new Octagon(l);
  }
  public static Octagon intervAssingVar (Octagon oct, int k, NumArray array){
    long l = wrapper.J_intervAssingVar(oct.getOctId(), k, array.getArray(), false);
    return new Octagon(l);
  }
  public static Octagon intervSubstituteVar (Octagon oct, int x, NumArray array){
    long l = wrapper.J_intervSubstituteVar(oct.getOctId(), x, array.getArray(), false);
    return new Octagon(l);
  }
  public static Octagon intervAddConstraint (Octagon oct, NumArray array){
    long l = wrapper.J_intervAddConstraint(oct.getOctId(), array.getArray(), false);
    return new Octagon(l);
  }

  /* change of dimensions */
  public static Octagon addDimensionAndEmbed (Octagon oct, int k){
    long l = wrapper.J_addDimenensionAndEmbed(oct.getOctId(), k, false);
    return new Octagon(l);
  }
  public static Octagon addDimensionAndProject (Octagon oct, int k){
    long l = wrapper.J_addDimenensionAndProject(oct.getOctId(), k, false);
    return new Octagon(l);
  }
  public static Octagon removeDimension (Octagon oct, int k){
    long l = wrapper.J_removeDimension(oct.getOctId(), k, false);
    return new Octagon(l);
  }

  public static void print (Octagon oct){
    wrapper.J_print(oct.getOctId());
  }

}
