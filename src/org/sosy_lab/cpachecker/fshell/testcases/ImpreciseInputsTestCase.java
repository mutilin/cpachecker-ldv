package org.sosy_lab.cpachecker.fshell.testcases;

import java.util.List;

public class ImpreciseInputsTestCase extends TestCase {

  public ImpreciseInputsTestCase(int[] pInputs) {
    super(pInputs, false);
  }

  public ImpreciseInputsTestCase(List<Integer> pInputs) {
    super(pInputs, false);
  }

}
