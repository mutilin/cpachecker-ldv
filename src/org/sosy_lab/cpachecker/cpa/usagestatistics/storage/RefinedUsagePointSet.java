package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsState;

public class RefinedUsagePointSet implements AbstractUsagePointSet {

  public static class DoubleRefinedUsagePointSet extends RefinedUsagePointSet {
    protected final RefinedUsageInfoSet target2;

    private DoubleRefinedUsagePointSet(RefinedUsageInfoSet newSet, RefinedUsageInfoSet newSet2) {
      super(newSet);
      target2 = newSet2;
    }

    @Override
    public int size() {
      return 2;
    }

    @Override
    public Pair<UsageInfo, UsageInfo> getUnsafePair() {
      return Pair.of(target.getOneExample(), target2.getOneExample());
    }

    @Override
    public AbstractUsageInfoSet getUsageInfo(UsagePoint point) {
      AbstractUsageInfoSet result = super.getUsageInfo(point);
      if (result != null) {
        return result;
      }
      UsagePoint p = target2.getOneExample().getUsagePoint();
      if (p.equals(point)) {
        return target2;
      }
      return null;
    }
  }

  protected final RefinedUsageInfoSet target;

  private RefinedUsagePointSet(RefinedUsageInfoSet newSet) {
    target = newSet;
  }

  public static RefinedUsagePointSet create(RefinedUsageInfoSet newSet) {
    return new RefinedUsagePointSet(newSet);
  }

  public static RefinedUsagePointSet create(RefinedUsageInfoSet newSet, RefinedUsageInfoSet newSet2) {
    return new DoubleRefinedUsagePointSet(newSet, newSet2);
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public AbstractUsageInfoSet getUsageInfo(UsagePoint point) {
    UsagePoint p = target.getOneExample().getUsagePoint();
    if (p.equals(point)) {
      return target;
    }
    return null;
  }

  @Override
  public int getNumberOfTopUsagePoints() {
    return size();
  }

  public Pair<UsageInfo, UsageInfo> getUnsafePair() {
    return Pair.of(target.getOneExample(), target.getOneExample());
  }

  @Override
  public void remove(UsageStatisticsState pUstate) {
    // Do nothing, we don't delete true usages
  }

}
