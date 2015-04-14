package org.sosy_lab.cpachecker.cpa.usagestatistics.caches;

import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.toState;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;

import com.google.common.base.Predicates;

public class InterpolantCache {
	private Map<UsageInfo, List<Pair<BooleanFormula, CFANode>>> visitedFunctions = new HashMap<>();
  private Set<UsageInfo> unusedKeySet;

	public void initKeySet() {
	  unusedKeySet = new HashSet<>(visitedFunctions.keySet());
	}

	public void removeUnusedCacheEntries() {
	  for (UsageInfo uinfo : unusedKeySet) {
	    visitedFunctions.remove(uinfo);
	  }
	}

  public void add(UsageInfo pUinfo, List<BooleanFormula> interpolants, List<ARGState> points) {

    List<CFANode> nodes = transformToCFANodes(points);
    assert (interpolants.size() == nodes.size());
    visitedFunctions.put(pUinfo, Pair.zipList(interpolants, nodes));
  }

  public boolean contains(UsageInfo pUinfo, List<BooleanFormula> interpolants, List<ARGState> points) {
    unusedKeySet.remove(pUinfo);

    List<CFANode> nodes = transformToCFANodes(points);
    assert (interpolants.size() == nodes.size());
    List<Pair<BooleanFormula, CFANode>> storedFormulas = visitedFunctions.get(pUinfo);
  	if (Pair.zipList(interpolants, nodes).equals(storedFormulas)) {
  	  return true;
  	}
    return false;
  }

  public void reset() {
    visitedFunctions.clear();
    unusedKeySet = Collections.emptySet();
  }

  private List<CFANode> transformToCFANodes(List<ARGState> points) {
    return from(points)
        .skip(1)
        .filter(Predicates.compose(PredicateAbstractState.FILTER_ABSTRACTION_STATES,
            toState(PredicateAbstractState.class)))
        .transform(AbstractStates.EXTRACT_LOCATION).toList();
  }
}
