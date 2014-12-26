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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.AccessPoint;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsLock;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

public class CallTreeNode implements Comparable<CallTreeNode> {
  protected final String code;
  protected final int line;
  protected final Set<CallTreeNode> children;

  private static CallTreeNode trunk;
  
  protected CallTreeNode(String c, int l) {
    code = c;
    line = l;
    children = new TreeSet<>();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((code == null) ? 0 : code.hashCode());
    result = prime * result + line;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CallTreeNode other = (CallTreeNode) obj;
    if (code == null) {
      if (other.code != null) {
        return false;
      }
    } else if (!code.equals(other.code)) {
      return false;
    }
    if (line != other.line) {
      return false;
    }
    return true;
  }
  
  private CallTreeNode add(CallTreeNode returnLeaf) {
  	if (this instanceof FunctionLeaf) {
	  	for (CallTreeNode leaf : this.children) {
	      if (leaf.equals(returnLeaf)) {
	        return leaf;
	      }
	    }
  	}
  	this.children.add(returnLeaf);
    return returnLeaf;
  }

  public CallTreeNode add(CallstackState callstack) {
    return add(new FunctionLeaf(callstack.getCurrentFunction(),
        callstack.getCallNode().getLeavingEdge(0).getLineNumber()));
  }
  
  public void add(UsageInfo usage, SingleIdentifier id) {
    add(new UsageLeaf(usage.createUsageView(id), usage.getLine().getLine()));
  }
  
  public void add(LockStatisticsLock lock, AccessPoint accessPoint) {
    add(new LockLeaf(lock.toString(), accessPoint.getLineInfo().getLine()));
  }
  
  public Iterator<CallTreeNode> getChildrenIterator() {
    return children.iterator();
  }

  public static CallTreeNode getTrunkState() {
    if (trunk == null) {
      trunk = new CallTreeNode("tmp_leaf", 0).new FunctionLeaf("super_main", 0);
    }
    return trunk;
  }

  public static CallTreeNode clearTrunkState() {
  	if (trunk != null) {
      trunk.children.clear();
  	}
    return getTrunkState();
  }
  
  private class FunctionLeaf extends CallTreeNode {
  	protected FunctionLeaf(String code, int line) {
  		super(code, line);
  	}

    @Override
    public int compareTo(CallTreeNode arg0) {
    	if (arg0 instanceof LockLeaf) {
    		return 1;
    	}
      return this.line - arg0.line;
    }
    
  	@Override
    public String toString() {
      return "Line " + line + ":     N0 -{" + code + "()" + "}-> N0\n";
    }
  }
  
  private class UsageLeaf extends CallTreeNode {
  	protected UsageLeaf(String code, int line) {
  		super(code, line);
  	}
  	
  	@Override
    public int compareTo(CallTreeNode arg0) {
    	if (arg0 instanceof LockLeaf) {
    		return 1;
    	}
      return this.line - arg0.line;
    }
  	
  	@Override
    public String toString() {
      return "Line " + line + ":     N0 -{" + code + "}-> N0\n";
    }
  }
  
  private class LockLeaf extends CallTreeNode {
  	protected LockLeaf(String code, int line) {
  		super(code, line);
  	}
  	@Override
    public int compareTo(CallTreeNode arg0) {
    	if (arg0 instanceof LockLeaf) {
        return this.line - arg0.line;
    	} else {
    		return -1;
    	}
    }
  	@Override
    public String toString() {
      return "Line " + line + ":     N0 -{" + code + "}-> N0\n";
    }
  }

	@Override
	public int compareTo(CallTreeNode o) {
		//it shouldn't be used, there are special comparators in extenders
		assert false;
		return 0;
	}
}
