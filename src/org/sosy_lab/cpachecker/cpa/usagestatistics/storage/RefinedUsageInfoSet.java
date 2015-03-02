package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import java.util.Collections;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsState;

public class RefinedUsageInfoSet implements AbstractUsageInfoSet {
  //We need only one refined usage to say that this point is true;
  private final UsageInfo refinedUsage;

  public RefinedUsageInfoSet(UsageInfo uinfo, List<CFAEdge> path) {
    refinedUsage = uinfo;
    refinedUsage.resetKeyState(path);
  }

  @Override
  public UsageInfo getOneExample() {
    return refinedUsage;
  }

  @Override
  public void remove(UsageStatisticsState pUstate) {
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public boolean isTrue() {
    return true;
  }

  @Override
  public Iterable<UsageInfo> getUsages() {
    return Collections.singleton(refinedUsage);
  }
}
