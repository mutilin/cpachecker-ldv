package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsState;

public class RefinedUsagePointSet implements AbstractUsagePointSet {

  public static class DoubleRefinedUsagePointSet extends RefinedUsagePointSet {
    protected final UsageInfo target2;

    private DoubleRefinedUsagePointSet(UsageInfo newSet, UsageInfo newSet2) {
      super(newSet);
      target2 = newSet2;
    }

    @Override
    public int size() {
      return 2;
    }

    @Override
    public Pair<UsageInfo, UsageInfo> getUnsafePair() {
      return Pair.of(target, target2);
    }

    @Override
    public AbstractUsageInfoSet getUsageInfo(UsagePoint point) {
      AbstractUsageInfoSet result = super.getUsageInfo(point);
      if (result != null) {
        return result;
      }
      UsagePoint p = target2.getUsagePoint();
      if (p.equals(point)) {
        return new RefinedUsageInfoSet(target2, target2.getPath());
      }
      return null;
    }
  }

  protected final UsageInfo target;

  private RefinedUsagePointSet(UsageInfo newSet) {
    target = newSet;
  }

  public static RefinedUsagePointSet create(UsageInfo newSet, UsageInfo newSet2) {
    if (newSet == newSet2) {
      return new RefinedUsagePointSet(newSet);
    } else {
      return new DoubleRefinedUsagePointSet(newSet, newSet2);
    }
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public AbstractUsageInfoSet getUsageInfo(UsagePoint point) {
    UsagePoint p = target.getUsagePoint();
    if (p.equals(point)) {
      return new RefinedUsageInfoSet(target, target.getPath());
    }
    return null;
  }

  @Override
  public int getNumberOfTopUsagePoints() {
    return size();
  }

  public Pair<UsageInfo, UsageInfo> getUnsafePair() {
    return Pair.of(target, target);
  }

  @Override
  public void remove(UsageStatisticsState pUstate) {
    // Do nothing, we don't delete true usages
  }

}
