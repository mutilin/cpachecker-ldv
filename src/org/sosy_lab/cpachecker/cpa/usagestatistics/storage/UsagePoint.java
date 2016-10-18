package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;

import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.thread.ThreadState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedSet;

public class UsagePoint implements Comparable<UsagePoint> {

  private static class UsagePointWithEmptyLockSet extends UsagePoint {
    //This usage is used to distinct usage points with empty lock sets with write access from each other
    public final UsageInfo keyUsage;

    private UsagePointWithEmptyLockSet(SortedSet<LockIdentifier> pLocks, Access pAccess, UsageInfo pInfo, ThreadState tInfo) {
      super(pLocks, pAccess, tInfo);
      assert pInfo != null;
      keyUsage = pInfo;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + ((keyUsage == null) ? 0 : keyUsage.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      boolean result = super.equals(obj);
      if (!result) {
        return result;
      }
      UsagePointWithEmptyLockSet other = (UsagePointWithEmptyLockSet) obj;
      //This is for distinction usages with empty sets of locks
      if (keyUsage == null) {
        if (other.keyUsage != null) {
          return false;
        }
      } else if (!keyUsage.equals(other.keyUsage)) {
        return false;
      }
      return true;
    }

    @Override
    public int compareTo(UsagePoint o) {
      int result = super.compareTo(o);
      if (result != 0) {
        return result;
      }
      // If we have 'result == 0' above,
      // the other UsagePoint should be also the same class
      Preconditions.checkArgument(o instanceof UsagePointWithEmptyLockSet);
      return keyUsage.compareTo(((UsagePointWithEmptyLockSet)o).keyUsage);
    }

    @Override
    public boolean covers(UsagePoint o) {
      /* Key usage is important, if it is present, it is write access without locks,
       * and we should handle all of them without inserting into covered elements of the tree structure
       */
      return false;
    }

    @Override
    public String toString() {
      String result = super.toString();
      result += ", " + keyUsage.getLine();
      return result;
    }
  }

  public final ImmutableSortedSet<LockIdentifier> locks;
  public final Access access;
  //May be null
  public final ThreadState threadInfo;
  private final Set<UsagePoint> coveredUsages;

  private UsagePoint(SortedSet<LockIdentifier> pLocks, Access pAccess, ThreadState tInfo) {
    locks = ImmutableSortedSet.copyOf(pLocks);
    access = pAccess;
    coveredUsages = new HashSet<>();
    threadInfo = tInfo;
  }

  public static UsagePoint createUsagePoint(UsageInfo info) {
    SortedSet<LockIdentifier> locks = ((LockStatisticsState)info.getState(LockStatisticsState.class)).getLockIdentifiers();
    ThreadState threadInfo = (ThreadState) info.getState(ThreadState.class);
    Access accessType = info.getAccess();

    if (locks != null && (locks.size() > 0 || accessType == Access.READ)) {
      return new UsagePoint(locks, accessType, threadInfo);
    } else {
      return new UsagePointWithEmptyLockSet(locks, accessType, info, threadInfo);
    }

  }

  public boolean addCoveredUsage(UsagePoint newChild) {
    if (!coveredUsages.contains(newChild)) {
      for (UsagePoint usage : coveredUsages) {
        if (usage.covers(newChild)) {
          assert !usage.equals(newChild);
          return usage.addCoveredUsage(newChild);
        }
      }
      return coveredUsages.add(newChild);
    }
    return false;
  }

  public Set<UsagePoint> getCoveredUsages() {
    return coveredUsages;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((access == null) ? 0 : access.hashCode());
    result = prime * result + ((locks == null) ? 0 : locks.hashCode());
    result = prime * result + ((threadInfo == null) ? 0 : threadInfo.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    UsagePoint other = (UsagePoint) obj;
    if (access != other.access) {
      return false;
    }
    if (locks == null) {
      if (other.locks != null) {
        return false;
      }
    } else if (!locks.equals(other.locks)) {
      return false;
    }
    if (threadInfo == null) {
      if (other.threadInfo != null) {
        return false;
      }
    } else if (!threadInfo.equals(other.threadInfo)) {
      return false;
    }
    return true;
  }

  @Override
  public int compareTo(UsagePoint o) {
    //It is very important to compare at first the accesses, because an algorithm base on this suggestion
    int result = access.compareTo(o.access);
    if (result != 0) {
      return result;
    }
    result = locks.size() - o.locks.size();
    if (result != 0) {
      return result;
    }
    Iterator<LockIdentifier> lockIterator = locks.iterator();
    Iterator<LockIdentifier> lockIterator2 = o.locks.iterator();
    while (lockIterator.hasNext()) {
      result = lockIterator.next().compareTo(lockIterator2.next());
      if (result != 0) {
        return result;
      }
    }
    if (threadInfo != null) {
      result = threadInfo.compareTo(o.threadInfo);
      if (result != 0) {
        return result;
      }
    }
    return result;
  }

  //TODO CompareTo? with enums
  public boolean covers(UsagePoint o) {
    // access 'write' is higher than 'read', but only for nonempty locksets
    if (o.locks.containsAll(locks) && access.compareTo(o.access) <= 0) {
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    String result = "(" + locks.toString() + ", " + access;
    return result + ")";
  }
}
