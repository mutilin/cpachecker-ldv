package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.AbstractStates;

import com.google.common.collect.ImmutableSet;

public class UnrefinedUsageInfoSet implements AbstractUsageInfoSet {
  private final Set<UsageInfo> unrefinedUsages;
  
  public UnrefinedUsageInfoSet() {
    unrefinedUsages = new TreeSet<>();
  }

  public void add(UsageInfo newInfo) {
    unrefinedUsages.add(newInfo);
  }

  public int size() {
    return unrefinedUsages.size();
  }
  
  /**
   * Is called after refinement to free memory
   * otherwise all ARG will be stored on the next stage of analysis
   */
  public void reset() {
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
    return unrefinedUsages.iterator().next();
  }

  public Iterator<UsageInfo> getIterator() {
    return unrefinedUsages.iterator();
  }
  
  public ImmutableSet<UsageInfo> getUsages() {
    return ImmutableSet.copyOf(unrefinedUsages);
  }

  @Override
  public boolean isTrue() {
    return false;
  }
}
