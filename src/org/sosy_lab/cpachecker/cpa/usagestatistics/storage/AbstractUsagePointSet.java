package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import java.util.Iterator;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsState;

public interface AbstractUsagePointSet {

  public abstract boolean isUnsafe();
  public abstract boolean isTrueUnsafe();
  public abstract int size();
  public abstract AbstractUsageInfoSet getUsageInfo(UsagePoint point);
  public abstract int getNumberOfTopUsagePoints();
  public abstract Pair<UsageInfo, UsageInfo> getUnsafePair();
  public abstract void remove(UsageStatisticsState pUstate);
}
