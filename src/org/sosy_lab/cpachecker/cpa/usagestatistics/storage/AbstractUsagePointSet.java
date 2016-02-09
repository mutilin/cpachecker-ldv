package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsState;

public interface AbstractUsagePointSet {

  public abstract int size();
  public abstract UsageInfoSet getUsageInfo(UsagePoint point);
  public abstract int getNumberOfTopUsagePoints();
  public abstract void remove(UsageStatisticsState pUstate);
}
