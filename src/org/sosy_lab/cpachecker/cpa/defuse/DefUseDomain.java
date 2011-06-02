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
package org.sosy_lab.cpachecker.cpa.defuse;

import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;

public class DefUseDomain implements AbstractDomain
{
  @Override
  public boolean isLessOrEqual(AbstractElement element1, AbstractElement element2) {
            DefUseElement defUseElement1 = (DefUseElement) element1;
            DefUseElement defUseElement2 = (DefUseElement) element2;
            
            return defUseElement2.containsAllOf(defUseElement1);
    }

    @Override
    public AbstractElement join(AbstractElement element1, AbstractElement element2) {
            // Useless code, but helps to catch bugs by causing cast exceptions
            DefUseElement defUseElement1 = (DefUseElement) element1;
            DefUseElement defUseElement2 = (DefUseElement) element2;

            Set<DefUseDefinition> joined = new HashSet<DefUseDefinition> ();
            for (DefUseDefinition definition : defUseElement1)
                joined.add(definition);

            for (DefUseDefinition definition : defUseElement2)
            {
                if (!joined.contains(definition))
                    joined.add (definition);
            }

            return new DefUseElement (joined);
    }
}
