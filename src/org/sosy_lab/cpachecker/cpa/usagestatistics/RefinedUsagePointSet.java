package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.sosy_lab.common.Pair;

public class RefinedUsagePointSet implements AbstractUsagePointSet {

  public class DoubleRefinedUsagePointSet extends RefinedUsagePointSet {
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
    
    @Override
    public Iterator<UsagePoint> getPointIterator() {
      Set<UsagePoint> result = new HashSet<>();
      result.add(target.getOneExample().getUsagePoint());
      result.add(target2.getOneExample().getUsagePoint());
      return result.iterator();
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
    return new RefinedUsagePointSet(newSet).new DoubleRefinedUsagePointSet(newSet, newSet2);
  }
  
  @Override
  public boolean isUnsafe() {
    return true;
  }

  @Override
  public boolean isTrueUnsafe() {
    return true;
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public Iterator<UsagePoint> getPointIterator() {
    return Collections.singleton(target.getOneExample().getUsagePoint()).iterator();
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

  @Override
  public Pair<UsageInfo, UsageInfo> getUnsafePair() {
    return Pair.of(target.getOneExample(), target.getOneExample());
  }

  @Override
  public void add(UsageInfo usage) {
    // Do nothing, this is already true    
  }

  @Override
  public void reset() {
    // Do nothing - there are no unrefined usages
  }

  @Override
  public void remove(UsageStatisticsState pUstate) {
    // Do nothing, we don't delete true usages
  }

}
