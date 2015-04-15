package org.sosy_lab.cpachecker.cpa.usagestatistics.storage;

import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;

import com.google.common.collect.ImmutableSortedSet;

public class UsagePoint implements Comparable<UsagePoint> {
  public final ImmutableSortedSet<LockIdentifier> locks;
  public final Access access;
  //This usage is used to distinct usage points with empty lock sets with write access from each other
  public final UsageInfo keyUsage;
  private final Set<UsagePoint> coveredUsages;
  private boolean isTrue;

  private UsagePoint(SortedSet<LockIdentifier> pLocks, Access pAccess, UsageInfo pInfo) {
    locks = ImmutableSortedSet.copyOf(pLocks);
    access = pAccess;
    coveredUsages = new HashSet<>();
    isTrue = false;
    keyUsage = pInfo;
  }

  public UsagePoint(SortedSet<LockIdentifier> pLocks, Access pAccess) {
    this(pLocks, pAccess, null);
  }

  public UsagePoint(UsageInfo pInfo) {
    this(new TreeSet<LockIdentifier>(), Access.WRITE, pInfo);
  }

  public boolean addCoveredUsage(UsagePoint newChild) {
    assert (!isTrue);
    if (!coveredUsages.contains(newChild)) {
      for (UsagePoint usage : coveredUsages) {
        if (usage.isHigher(newChild) && !usage.isTrue) {
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
    //This is for distinction usages with empty sets of locks
    result = prime * result + ((keyUsage == null) ? 0 : keyUsage.hashCode());
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
    int result = (isTrue == o.isTrue) ? 0 : (isTrue ? -1 : 1);
    if (result != 0) {
      return result;
    }
    //It is very important to compare at first the accesses, because an algorithm base on this suggestion
    result = access.compareTo(o.access);
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
    if (keyUsage == null) {
      return result;
    } else {
      return keyUsage.compareTo(o.keyUsage);
    }
  }

  //TODO CompareTo? with enums
  public boolean isHigher(UsagePoint o) {
    // access 'write' is higher than 'read', but only for nonempty locksets
    if (o.locks.containsAll(locks) && access.compareTo(o.access) <= 0 && keyUsage == null) {
      /* Key usage is important, if it is present, it is write access without locks,
       * and we should handle all of them without inserting into covered elements of the tree structure
       */
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    String result = "(" + locks.toString() + ", " + access;
    if (keyUsage != null) {
      result += ", " + keyUsage.getLine();
    }
    return result + ")";
  }

  public void markAsTrue(List<CFAEdge> path) {
    isTrue = true;
    if (keyUsage != null) {
      keyUsage.resetKeyState(path);
    }
    coveredUsages.clear();
  }
}
