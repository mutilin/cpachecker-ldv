package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.AbstractStates;

import com.google.common.collect.ImmutableSet;

public class UsageInfoSet {
  private final Set<UsageInfo> refinedUsages;
  private final Set<UsageInfo> unrefinedUsages;
  
  public UsageInfoSet() {
    refinedUsages = new TreeSet<>();
    unrefinedUsages = new TreeSet<>();
  }

  public void add(UsageInfo newInfo) {
    assert !newInfo.isRefined();
    if (refinedUsages.contains(newInfo)) {
      return;
    }
    unrefinedUsages.add(newInfo);
  }

  public int size() {
    return refinedUsages.size() + unrefinedUsages.size();
  }
  
  public boolean hasNoRefinedUsages() {
    return refinedUsages.isEmpty();
  }
  
  /**
   * Is called after refinement to free memory
   * otherwise all ARG will be stored on the next stage of analysis
   */
  public void reset() {
    for (UsageInfo uinfo : refinedUsages) {
      uinfo.setKeyState(null);
    }
    //TODO It seems, that the size is already 0. Check it.
    unrefinedUsages.clear();
  }

  public void remove(UsageStatisticsState pUstate) {
    Set<UsageInfo> toDelete = new HashSet<>();
    for (UsageInfo uinfo : unrefinedUsages) {
      AbstractState keyState = uinfo.getKeyState();
      assert (keyState != null);
      if (AbstractStates.extractStateByType(keyState, UsageStatisticsState.class).equals(pUstate)) {
        if (!uinfo.isRefined()) {
          toDelete.add(uinfo);
        }
      }
    }
    unrefinedUsages.removeAll(toDelete);
    toDelete.clear();
  }
  
  public UsageInfo getOneExample() {
    if (refinedUsages.size() > 0) {
      return refinedUsages.iterator().next();
    } else {
      return unrefinedUsages.iterator().next();
    }
  }

  public Iterator<UsageInfo> getIterator() {
    return unrefinedUsages.iterator();
  }

  public void markAsRefined(UsageInfo uinfo) {
    unrefinedUsages.remove(uinfo);
    uinfo.setRefineFlag();
    refinedUsages.add(uinfo);
  }
  
  public ImmutableSet<UsageInfo> getUsages() {
    Set<UsageInfo> result = new TreeSet<>(refinedUsages);
    result.addAll(unrefinedUsages);
    return ImmutableSet.copyOf(result);
  }
  
  public boolean isTrue() {
    return !refinedUsages.isEmpty();
  }
}
