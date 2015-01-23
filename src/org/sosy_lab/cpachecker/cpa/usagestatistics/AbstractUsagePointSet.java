package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Iterator;

import org.sosy_lab.common.Pair;

public interface AbstractUsagePointSet {

  public abstract boolean isUnsafe();
  public abstract boolean isTrueUnsafe();
  public abstract int size();
  public abstract Iterator<UsagePoint> getPointIterator();
  public abstract AbstractUsageInfoSet getUsageInfo(UsagePoint point);
  public abstract int getNumberOfTopUsagePoints();
  public abstract Pair<UsageInfo, UsageInfo> getUnsafePair();
  public abstract void add(UsageInfo usage);
  public abstract void reset();
  public abstract void remove(UsageStatisticsState pUstate);
}
