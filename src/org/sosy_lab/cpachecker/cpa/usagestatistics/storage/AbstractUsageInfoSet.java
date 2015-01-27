package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsState;

public interface AbstractUsageInfoSet {
  public void remove(UsageStatisticsState state);
  public UsageInfo getOneExample();
  public int size();
  public boolean isTrue();
  public Iterable<UsageInfo> getUsages();
}
