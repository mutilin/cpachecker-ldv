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
package org.sosy_lab.cpachecker.cpa.location;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.SortedSet;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.globalinfo.CFAInfo;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedSet;

public class LocationElement implements AbstractStateWithLocation, AbstractQueryableState, Partitionable, Serializable {

  private static final long serialVersionUID = -801176497691618779L;

  public static class LocationElementFactory {
    private final LocationElement[] elements;

    public LocationElementFactory(CFA pCfa) {
      elements = initialize(checkNotNull(pCfa));
    }

    private static LocationElement[] initialize(CFA pCfa) {

      SortedSet<CFANode> allNodes = ImmutableSortedSet.copyOf(pCfa.getAllNodes());
      int maxNodeNumber = allNodes.last().getNodeNumber();
      LocationElement[] elements = new LocationElement[maxNodeNumber+1];
      for (CFANode node : allNodes) {
        elements[node.getNodeNumber()] = new LocationElement(node);
      }

      return elements;
    }

    public LocationElement getElement(CFANode node) {
      return Preconditions.checkNotNull(elements[node.getNodeNumber()]);
    }
  }

    private transient CFANode locationNode;

    private LocationElement(CFANode locationNode) {
        this.locationNode = locationNode;
    }

    @Override
    public CFANode getLocationNode() {
        return locationNode;
    }

    @Override
    public String toString() {
      return locationNode + " (line " + locationNode.getLineNumber() + ")";
    }

    @Override
    public boolean checkProperty(String pProperty) throws InvalidQueryException {
      String[] parts = pProperty.split("==");
      if (parts.length != 2) {
        throw new InvalidQueryException("The Query \"" + pProperty
            + "\" is invalid. Could not split the property string correctly.");
      } else {
        if (parts[0].toLowerCase().equals("line")) {
          try {
            int queryLine = Integer.parseInt(parts[1]);
            return this.locationNode.getLineNumber() == queryLine;
          } catch (NumberFormatException nfe) {
            throw new InvalidQueryException("The Query \"" + pProperty
                + "\" is invalid. Could not parse the integer \"" + parts[1] + "\"");
          }
        } else if (parts[0].toLowerCase().equals("functionname")) {
          return this.locationNode.getFunctionName().equals(parts[1]);
        } else {
          throw new InvalidQueryException("The Query \"" + pProperty
              + "\" is invalid. \"" + parts[0] + "\" is no valid keyword");
        }
      }
    }

    @Override
    public void modifyProperty(String pModification)
        throws InvalidQueryException {
      throw new InvalidQueryException("The location CPA does not support modification.");
    }

    @Override
    public String getCPAName() {
      return "location";
    }

    @Override
    public Boolean evaluateProperty(String pProperty)
        throws InvalidQueryException {
      return Boolean.valueOf(checkProperty(pProperty));
    }

    @Override
    public Object getPartitionKey() {
      return this;
    }

    // no equals and hashCode because there is always only one element per CFANode

    private Object writeReplace() throws ObjectStreamException {
      return new SerialProxy(locationNode.getNodeNumber());
    }

    private static class SerialProxy implements Serializable {
      private static final long serialVersionUID = 6889568471468710163L;
      private final int nodeNumber;

      public SerialProxy(int nodeNumber) {
        this.nodeNumber = nodeNumber;
      }

      private Object readResolve() throws ObjectStreamException {
        CFAInfo cfaInfo = GlobalInfo.getInstance().getCFAInfo();
        return cfaInfo.getLocationElementFactory().getElement(cfaInfo.getNodeByNodeNumber(nodeNumber));
      }
    }

}
