package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.util.coverage.CoverageInformation;

import com.google.common.collect.ImmutableSet;

public class UsagePoint implements Comparable<UsagePoint> {
  public final ImmutableSet<LockIdentifier> locks;
  public final Access access;
  //This usage is used to distinct usage points with empty lock sets with write access from each other
  public final UsageInfo keyUsage;
  private final Set<UsagePoint> coveredUsages;
  private boolean isTrue;
    
  private UsagePoint(Set<LockIdentifier> pLocks, Access pAccess, UsageInfo pInfo) {
    locks = ImmutableSet.copyOf(pLocks);
    access = pAccess;
    coveredUsages = new HashSet<>();
    isTrue = false;
    keyUsage = pInfo;
  }
  
  public UsagePoint(Set<LockIdentifier> pLocks, Access pAccess) {
    this(pLocks, pAccess, null);
  }
  
  public UsagePoint(Access pAccess, UsageInfo pInfo) {
    this(new TreeSet<LockIdentifier>(), pAccess, pInfo);
  }
  
  public boolean addCoveredUsage(UsagePoint newChild) {
    if (!coveredUsages.contains(newChild)) {
      for (UsagePoint usage : coveredUsages) {
        if (usage.isHigher(newChild)) {
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
  
  public boolean removeRecursively(UsagePoint target) {
    if (getCoveredUsages().contains(target)) {
      coveredUsages.remove(target);
      coveredUsages.addAll(target.getCoveredUsages());
      return true;
    }
    for (UsagePoint point : getCoveredUsages()) {
      if (removeRecursively(point)) {
        return true;
      }
    }
    return false;
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
    result = locks.toString().compareTo(o.locks.toString());
    if (result != 0 || keyUsage == null) {
      return result;
    } else {
      return keyUsage.compareTo(o.keyUsage);
    }
  }
  
  public boolean isHigher(UsagePoint o) {
    // access 'write' is higher than 'read', but only for nonempty locksets
    if (o.locks.containsAll(locks) && access.ordinal() <= o.access.ordinal()) {
      if (locks.size() > 0/* || access == Access.READ*/) {
        return true;
      } else {
        if (keyUsage != null
         && o.keyUsage != null
         && keyUsage.getCallStack().equalsWithoutNode(o.keyUsage.getCallStack())) {
          if (access.ordinal() < o.access.ordinal()) {
            //write accesses are always higher than read ones (if we merge read accesses)
            return true;
          }
          //This ordering is very important, do not remove
          return (keyUsage.compareTo(o.keyUsage) <= 0);
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
