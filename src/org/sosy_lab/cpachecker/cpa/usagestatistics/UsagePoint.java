package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;

import com.google.common.collect.ImmutableSet;

public class UsagePoint implements Comparable<UsagePoint> {
  public final ImmutableSet<LockIdentifier> locks;
  public final Access access;
  //This usage is used to distinct usage points with empty lock sets with write access from each other
  public final UsageInfo keyUsage;
  private final Set<UsagePoint> coveredUsages;
  private boolean isTrue;
    
  public UsagePoint(Set<LockIdentifier> pLocks, Access pAccess) {
    locks = ImmutableSet.copyOf(pLocks);
    access = pAccess;
    coveredUsages = new HashSet<>();
    isTrue = false;
    keyUsage = null;
  }
  public UsagePoint(Access pAccess, UsageInfo pInfo) {
    assert pAccess == Access.WRITE;
    locks = ImmutableSet.copyOf(new TreeSet<LockIdentifier>());
    access = pAccess;
    coveredUsages = new HashSet<>();
    isTrue = false;
    keyUsage = pInfo;
  }
  
  public boolean addCoveredUsage(UsagePoint newChild) {
    if (!coveredUsages.contains(newChild)) {
      for (UsagePoint usage : coveredUsages) {
        if (usage.isHigherOrEqual(newChild)) {
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
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    UsagePoint other = (UsagePoint) obj;
    if (access != other.access)
      return false;
    if (locks == null) {
      if (other.locks != null)
        return false;
    } else if (!locks.equals(other.locks))
      return false;
    //This is for distinction usages with empty sets of locks
    if (keyUsage == null) {
      if (other.keyUsage != null)
        return false;
    } else if (!keyUsage.equals(other.keyUsage))
      return false;
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
    if (locks.size() > 0 || access == Access.READ) {
      return locks.toString().compareTo(o.locks.toString());
    } else {
      return keyUsage.compareTo(o.keyUsage);
    }
  }
  
  public boolean isHigherOrEqual(UsagePoint o) {
    // access 'write' is higher than 'read', but only for nonempty locksets
    if (o.locks.containsAll(locks) && access.ordinal() <= o.access.ordinal()) {
      if (locks.size() > 0 || access == Access.READ) {
        return true;
      } else {
        if (keyUsage != null
         && o.keyUsage != null
         && keyUsage.getCallStack().equals(o.keyUsage.getCallStack())) {
          //Ordering by line number is very important, do not remove
          return (keyUsage.getLine().getLine() - o.keyUsage.getLine().getLine() >= 0);
        }
        return false;
      }
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
  
  public void markAsTrue() {
    isTrue = true;
  }
  
  public boolean isTrue() {
    return isTrue;
  }
}
