package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;

public class InterpolantCache {
	private Map<UsageInfo, List<BooleanFormula>> visitedFunctions = new HashMap<>();
  private Set<UsageInfo> unusedKeySet;
	
	public void initKeySet() {
	  unusedKeySet = new HashSet<>(visitedFunctions.keySet());
	}
	
	public void removeUnusedCacheEntries() {
	  for (UsageInfo uinfo : unusedKeySet) {
	    visitedFunctions.remove(uinfo);
	  }
	}
	
  public void add(UsageInfo pUinfo, List<BooleanFormula> interpolants) {
    visitedFunctions.put(pUinfo, interpolants);
  }

  public boolean contains(UsageInfo pUinfo, List<BooleanFormula> interpolants) {
    unusedKeySet.remove(pUinfo);
    List<BooleanFormula> storedFormulas = visitedFunctions.get(pUinfo);
  	if (interpolants.equals(storedFormulas)) {
  	  return true;
  	}
    return false;
  }
  
  public void reset() {
    visitedFunctions.clear();
    unusedKeySet = Collections.emptySet();
  }
}
