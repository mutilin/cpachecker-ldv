package org.sosy_lab.cpachecker.util.ecp;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.common.base.Preconditions;

public class ECPConcatenation implements ElementaryCoveragePattern, Iterable<ElementaryCoveragePattern> {

  private LinkedList<ElementaryCoveragePattern> mSubpatterns;

  public ECPConcatenation(ElementaryCoveragePattern pFirstSubpattern, ElementaryCoveragePattern pSecondSubpattern) {
    mSubpatterns = new LinkedList<ElementaryCoveragePattern>();

    if (pFirstSubpattern instanceof ECPConcatenation) {
      ECPConcatenation lFirstSubpattern = (ECPConcatenation)pFirstSubpattern;

      mSubpatterns.addAll(lFirstSubpattern.mSubpatterns);
    }
    else {
      mSubpatterns.add(pFirstSubpattern);
    }

    if (pSecondSubpattern instanceof ECPConcatenation) {
      ECPConcatenation lSecondSubpattern = (ECPConcatenation)pSecondSubpattern;

      mSubpatterns.addAll(lSecondSubpattern.mSubpatterns);
    }
    else {
      mSubpatterns.add(pSecondSubpattern);
    }
  }

  public ECPConcatenation(List<ElementaryCoveragePattern> pSubpatterns) {
    Preconditions.checkNotNull(pSubpatterns);
    Preconditions.checkArgument(pSubpatterns.size() > 0);

    mSubpatterns = new LinkedList<ElementaryCoveragePattern>();

    for (ElementaryCoveragePattern lSubpattern : pSubpatterns) {
      if (lSubpattern instanceof ECPConcatenation) {
        ECPConcatenation lConcatenation = (ECPConcatenation)lSubpattern;
        mSubpatterns.addAll(lConcatenation.mSubpatterns);
      }
      else {
        mSubpatterns.add(lSubpattern);
      }
    }
  }

  @Override
  public Iterator<ElementaryCoveragePattern> iterator() {
    return mSubpatterns.iterator();
  }

  public ElementaryCoveragePattern get(int lIndex) {
    return mSubpatterns.get(lIndex);
  }

  public int size() {
    return mSubpatterns.size();
  }

  public boolean isEmpty() {
    return mSubpatterns.isEmpty();
  }

  @Override
  public boolean equals(Object pOther) {
    if (this == pOther) {
      return true;
    }

    if (pOther == null) {
      return false;
    }

    if (pOther.getClass().equals(getClass())) {
      ECPConcatenation lOther = (ECPConcatenation)pOther;

      return mSubpatterns.equals(lOther.mSubpatterns);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return mSubpatterns.hashCode();
  }

  @Override
  public String toString() {
    StringBuffer lResult = new StringBuffer();

    boolean isFirst = true;

    for (ElementaryCoveragePattern lSubpattern : mSubpatterns) {
      if (isFirst) {
        isFirst = false;
      }
      else {
        lResult.append(".");
      }

      lResult.append(lSubpattern.toString());
    }

    return lResult.toString();
  }

  @Override
  public <T> T accept(ECPVisitor<T> pVisitor) {
    return pVisitor.visit(this);
  }

}
