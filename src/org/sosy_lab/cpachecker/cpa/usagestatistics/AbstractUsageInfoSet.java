package org.sosy_lab.cpachecker.cpa.usagestatistics;

public interface AbstractUsageInfoSet {
  public void remove(UsageStatisticsState state);
  public UsageInfo getOneExample();
  public int size();
  public void reset();
  public boolean isTrue();
  public Iterable<UsageInfo> getUsages();
}
