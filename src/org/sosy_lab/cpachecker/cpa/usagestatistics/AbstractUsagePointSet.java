package org.sosy_lab.cpachecker.cpa.usagestatistics;

public interface AbstractUsagePointSet {

  public abstract boolean isUnsafe();
  public abstract boolean isTrueUnsafe();
}
