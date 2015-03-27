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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;

@Options(prefix="cpa.usagestatistics.skippedvariables")
public class VariableSkipper {
  @Option(description = "variables, which will be filtered by its name")
  private Set<String> byName = null;

  @Option(description = "variables, which will be filtered by its type")
  private Set<String> byType = null;

  public VariableSkipper(Configuration pConfig) throws InvalidConfigurationException {
    pConfig.inject(this);
  }

  public boolean shouldBeSkipped(AbstractIdentifier id) {

    if (id instanceof SingleIdentifier) {
      SingleIdentifier singleId = (SingleIdentifier) id;
      if (checkId(singleId)) {
        return true;
      } else if (byName != null && singleId instanceof StructureIdentifier) {
        AbstractIdentifier owner = singleId;
        while (owner instanceof StructureIdentifier) {
          owner = ((StructureIdentifier)owner).getOwner();
          if (owner instanceof SingleIdentifier && checkId((SingleIdentifier)owner)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean checkId(SingleIdentifier singleId) {
    if (byName != null) {
      if (byName.contains(singleId.getName())) {
        return true;
      }
    }
    if (byType != null) {
      String idType = singleId.getType().toString();
      idType = idType.replaceAll("\\(", "");
      idType = idType.replaceAll("\\)", "");
      if (byType.contains(idType)) {
        return true;
      }
    }
    return false;
  }
}
