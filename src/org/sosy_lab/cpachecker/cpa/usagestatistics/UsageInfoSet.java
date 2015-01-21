package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.AbstractStates;

import com.google.common.collect.ImmutableSet;

public class UsageInfoSet {
  //We need only one refined usage to say that this point is true;
  private UsageInfo refinedUsage;
  private final Set<UsageInfo> unrefinedUsages;
  
  public UsageInfoSet() {
    refinedUsage = null;
    unrefinedUsages = new TreeSet<>();
  }

  public void add(UsageInfo newInfo) {
    assert !newInfo.isRefined();
    if (newInfo.equals(refinedUsage)) {
      return;
    }
    unrefinedUsages.add(newInfo);
  }

  public int size() {
    return unrefinedUsages.size() + (refinedUsage == null ? 0 : 1);
  }
  
  public boolean hasNoRefinedUsages() {
    return refinedUsage == null;
  }
  
  /**
   * Is called after refinement to free memory
   * otherwise all ARG will be stored on the next stage of analysis
   */
  public void reset() {
    if (refinedUsage != null) {
      refinedUsage.resetKeyState();
    }
    //TODO It seems, that the size is already 0. Check it.
    unrefinedUsages.clear();
  }

  public void remove(UsageStatisticsState pUstate) {
    Iterator<UsageInfo> iterator = unrefinedUsages.iterator();
    while (iterator.hasNext()) {
      UsageInfo uinfo = iterator.next();
      AbstractState keyState = uinfo.getKeyState();
      assert (keyState != null);
      if (AbstractStates.extractStateByType(keyState, UsageStatisticsState.class).equals(pUstate)) {
        iterator.remove();
      }
    }
  }
  
  public UsageInfo getOneExample() {
    if (refinedUsage != null) {
      return refinedUsage;
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
    refinedUsage = uinfo;
  }
  
  public ImmutableSet<UsageInfo> getUsages() {
    //Important! These usages are disordered by refinement result, we cannot say, that the true ones are the first
    Set<UsageInfo> result = new TreeSet<>(unrefinedUsages);
    if (refinedUsage != null) {
      result.add(refinedUsage);
    }
    return ImmutableSet.copyOf(result);
  }
  
  public boolean isTrue() {
    return refinedUsage != null;
  }
}
