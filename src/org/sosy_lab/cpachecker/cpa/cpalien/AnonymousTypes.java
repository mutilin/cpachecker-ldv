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
package org.sosy_lab.cpachecker.cpa.cpalien;

import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;


public class AnonymousTypes {
  static final public CSimpleType dummyChar = new CSimpleType(false, false, CBasicType.CHAR, false, false, true, false, false, false, false);
  static final public CSimpleType dummyInt = new CSimpleType(false, false, CBasicType.INT, true, false, false, true, false, false, false);
  static final public CSimpleType dummyVoid = new CSimpleType(false, false, CBasicType.VOID, false, false, false, false, false, false, false);
  static final public CPointerType dummyPointer = new CPointerType(false, false, dummyVoid);
}
