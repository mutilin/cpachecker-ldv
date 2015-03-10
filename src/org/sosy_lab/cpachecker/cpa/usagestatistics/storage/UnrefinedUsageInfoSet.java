package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsState;
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

  @Override
  public int size() {
    return unrefinedUsages.size();
  }

  @Override
  public boolean remove(UsageStatisticsState pUstate) {
    Iterator<UsageInfo> iterator = unrefinedUsages.iterator();
    boolean changed = false;
    while (iterator.hasNext()) {
      UsageInfo uinfo = iterator.next();
      AbstractState keyState = uinfo.getKeyState();
      assert (keyState != null);
      if (AbstractStates.extractStateByType(keyState, UsageStatisticsState.class).equals(pUstate)) {
        iterator.remove();
        changed = true;
      }
    }
    return changed;
  }

  @Override
  public UsageInfo getOneExample() {
    return unrefinedUsages.iterator().next();
  }

  @Override
  public ImmutableSet<UsageInfo> getUsages() {
    return ImmutableSet.copyOf(unrefinedUsages);
  }

  @Override
  public boolean isTrue() {
    return false;
  }
}
