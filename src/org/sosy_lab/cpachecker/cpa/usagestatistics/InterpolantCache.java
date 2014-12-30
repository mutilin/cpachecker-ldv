package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;

public class InterpolantCache {
	private Set<Pair<UsageInfo, List<BooleanFormula>>> visitedFunctions = new HashSet<>();

  public void add(UsageInfo pUinfo, List<BooleanFormula> interpolants) {
    visitedFunctions.add(Pair.of(pUinfo, interpolants));
  }

  public boolean contains(UsageInfo pUinfo, List<BooleanFormula> interpolants) {
  	Pair<UsageInfo, List<BooleanFormula>> newPair = Pair.of(pUinfo, interpolants);
  	for (Pair<UsageInfo, List<BooleanFormula>> testPair : visitedFunctions) {
  		if (testPair.equals(newPair)) {
  			return true;
  		}
  	}
    return false;
  }
}
