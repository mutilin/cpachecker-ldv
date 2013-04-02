/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.explicit;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.util.VariableClassification;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

@Options(prefix="cpa.explicit.precision")
public class ExplicitPrecision implements Precision {

  /**
   * the pattern describing variable names that are not being tracked - if it is null, no variables are black-listed
   */
  private final Pattern blackListPattern;

  /**
   * the component responsible for thresholds concerning the reached set
   */
  private ReachedSetThresholds reachedSetThresholds = null;

  /**
   * the component responsible for thresholds concerning paths
   */
  private PathThresholds pathThresholds             = null;

  /**
   * the component responsible for variables that need to be tracked, according to refinement
   */
  private RefinablePrecision refinablePrecision     = null;

  @Option(description = "whether or not to add newly-found variables only to the exact program location or to the respective scope of the variable.")
  private String scope = "location-scope";

  @Option(description = "ignore boolean variables. if this option is used, "
      + "booleans from the cfa should tracked with another CPA, "
      + "i.e. with BDDCPA.")
  private boolean ignoreBoolean = false;

  @Option(description = "ignore variables, that are only compared for equality. "
      + "if this option is used, these variables from the cfa should "
      + "tracked with another CPA, i.e. with BDDCPA.")
  private boolean ignoreIntEqual = false;

  @Option(description = "ignore variables, that are only used in simple " +
      "calculations (add, sub, lt, gt, eq). "
      + "if this option is used, these variables from the cfa should "
      + "tracked with another CPA, i.e. with BDDCPA.")
  private boolean ignoreIntAdd = false;

  private Optional<VariableClassification> varClass;

  public ExplicitPrecision(String variableBlacklist, Configuration config,
      Optional<VariableClassification> vc,
      Multimap<CFANode, String> mapping) throws InvalidConfigurationException {
    config.inject(this);

    blackListPattern = Pattern.compile(variableBlacklist);
    this.varClass = vc;

    if (Boolean.parseBoolean(config.getProperty("analysis.useRefinement"))) {
      refinablePrecision = createInstance(scope);
    }
    else {
      refinablePrecision = new FullPrecision();
    }

    reachedSetThresholds  = new ReachedSetThresholds(config);
    pathThresholds        = new PathThresholds(config);
  }

  /**
   * copy constructor
   *
   * @param original the ExplicitPrecision to copy
   */
  public ExplicitPrecision(ExplicitPrecision original, Multimap<CFANode, String> increment) {
    refinablePrecision    = original.refinablePrecision.refine(increment);

    blackListPattern      = original.blackListPattern;
    reachedSetThresholds  = new ReachedSetThresholds(original.reachedSetThresholds);
    pathThresholds        = new PathThresholds(original.pathThresholds);
  }

  public RefinablePrecision getRefinablePrecision() {
    return refinablePrecision;
  }

  public ReachedSetThresholds getReachedSetThresholds() {
    return reachedSetThresholds;
  }

  public PathThresholds getPathThresholds() {
    return pathThresholds;
  }

  public void setLocation(CFANode node) {
    refinablePrecision.setLocation(node);
  }

  boolean isOnBlacklist(String variable) {
    return this.blackListPattern.matcher(variable).matches();
  }

  /**
   * This method tells if the precision demands the given variable to be tracked.
   *
   * A variable is demanded to be tracked if it does not exceed a threshold (when given),
   * it is on the white-list (when not null), and is not on the black-list.
   *
   * @param variable the scoped name of the variable to check
   * @return true, if the variable has to be tracked, else false
   */
  public boolean isTracking(String variable) {
    boolean result = reachedSetThresholds.allowsTrackingOf(variable)
            && pathThresholds.allowsTrackingOf(variable)
            && refinablePrecision.contains(variable)
            && !isOnBlacklist(variable)
            && !isInIgnoredVarClass(variable);

    return result;
  }

  /** returns true, iff the variable is in an varClass, that should be ignored. */
  private boolean isInIgnoredVarClass(String variable) {
    if (varClass==null || !varClass.isPresent()) { return false; }

    Pair<String, String> var = splitVar(variable);

    final boolean isBoolean = varClass.get().getBooleanVars().containsEntry(var.getFirst(), var.getSecond());
    final boolean isIntEqual = varClass.get().getIntEqualVars().containsEntry(var.getFirst(), var.getSecond());
    final boolean isIntAdd = varClass.get().getIntAddVars().containsEntry(var.getFirst(), var.getSecond());

    final boolean isIgnoredBoolean = ignoreBoolean && isBoolean;

    // if a var is boolean and intEqual, it is not handled as intEqual.
    final boolean isIgnoredIntEqual = ignoreIntEqual && !isBoolean && isIntEqual;

    // if a var is (boolean or intEqual) and intAdd, it is not handled as intAdd.
    final boolean isIgnoredIntAdd = ignoreIntAdd && !isBoolean && !isIntEqual && isIntAdd;

    return isIgnoredBoolean || isIgnoredIntEqual || isIgnoredIntAdd;
  }

  /** split var into function and varName */
  private Pair<String, String> splitVar(String variable) {
    int i = variable.indexOf("::");
    String function;
    String varName;
    if (i == -1) { // global variable, no splitting
      function = null;
      varName = variable;
    } else { // split function::varName
      function = variable.substring(0, i);
      varName = variable.substring(i + 2);
    }
    return Pair.of(function, varName);
  }

  RefinablePrecision createInstance(String scope) {
    return scope.equals("location-scope") ? new LocalizedRefinablePrecision() : new ScopedRefinablePrecision();
  }

  @Options(prefix="cpa.explicit.precision.refinement")
  abstract public static class RefinablePrecision {
    public static final String DELIMITER = ", ";

    /**
     * the current location needed for checking containment
     */
    CFANode location = null;

    /**
     * This method decides whether or not a variable is being tracked by this precision.
     *
     * @param variable the scoped name of the variable for which to make the decision
     * @return true, when the variable is allowed to be tracked, else false
     */
    abstract public boolean contains(String variable);

    abstract protected RefinablePrecision refine(Multimap<CFANode, String> increment);

    private void setLocation(CFANode node) {
      location = node;
    }

    abstract void serialize(Writer writer) throws IOException;

    abstract void join(RefinablePrecision consolidatedPrecision);
  }

  public static class LocalizedRefinablePrecision extends RefinablePrecision {
    /**
     * the collection that determines which variables are tracked at a specific location - if it is null, all variables are tracked
     */
    private HashMultimap<CFANode, String> rawPrecision = HashMultimap.create();


    @Override
    public LocalizedRefinablePrecision refine(Multimap<CFANode, String> increment) {
      LocalizedRefinablePrecision refinedPrecision = new LocalizedRefinablePrecision();

      refinedPrecision.rawPrecision = HashMultimap.create(rawPrecision);
      refinedPrecision.rawPrecision.putAll(increment);

      return refinedPrecision;
    }

    /**
     * This method decides whether or not a variable is being tracked by this precision.
     *
     * @param variable the scoped name of the variable for which to make the decision
     * @return true, when the variable is allowed to be tracked, else false
     */
    @Override
    public boolean contains(String variable) {
      return rawPrecision.containsEntry(location, variable);
    }

    @Override
    void serialize(Writer writer) throws IOException {
      for(CFANode currentLocation : rawPrecision.keySet()) {
        writer.write("\n" + currentLocation + ":\n");

        for(String variable : rawPrecision.get(currentLocation)) {
          writer.write(variable + "\n");
        }
      }
    }

    @Override
    public void join(RefinablePrecision consolidatedPrecision) {
      assert(getClass().equals(consolidatedPrecision.getClass()));
      this.rawPrecision.putAll(((LocalizedRefinablePrecision)consolidatedPrecision).rawPrecision);
    }
  }

  public static class ScopedRefinablePrecision extends RefinablePrecision {
    /**
     * the collection that determines which variables are tracked within a specific scope
     */
    private Set<String> rawPrecision = new HashSet<>();

    /**
     * This method decides whether or not a variable is being tracked by this precision.
     *
     * @param variable the scoped name of the variable for which to make the decision
     * @return true, when the variable is allowed to be tracked, else false
     */
    @Override
    public boolean contains(String variable) {
      return rawPrecision.contains(variable);
    }

    /**
     * This method adds the additional mapping to the current mapping, i.e., this precision can only grow in size, and never gets smaller.
     *
     * @param additionalMapping the additional mapping to be added to the current mapping
     */
    @Override
    public ScopedRefinablePrecision refine(Multimap<CFANode, String> increment) {
      ScopedRefinablePrecision refinedPrecision = new ScopedRefinablePrecision();

      refinedPrecision.rawPrecision = new HashSet<>(rawPrecision);
      refinedPrecision.rawPrecision.addAll(increment.values());

      return refinedPrecision;
    }

    @Override
    void serialize(Writer writer) throws IOException {
      SortedSet<String> sortedPrecision = new TreeSet<>(rawPrecision);

      ArrayList<String> globals = new ArrayList<>();
      String previousScope      = null;
      for(String variable : sortedPrecision) {
        if(variable.contains("::")) {
          String functionName = variable.substring(0, variable.indexOf("::"));
          if(!functionName.equals(previousScope)) {
            writer.write("\n" + functionName + ":\n");
          }
          writer.write(variable + "\n");

          previousScope = functionName;
        }
        else {
          globals.add(variable);
        }
      }

      if(previousScope != null) {
        writer.write("\n");
      }

      writer.write("*:\n" + Joiner.on("\n").join(globals));
    }

    @Override
    public void join(RefinablePrecision consolidatedPrecision) {
      assert(getClass().equals(consolidatedPrecision.getClass()));
      this.rawPrecision.addAll(((ScopedRefinablePrecision)consolidatedPrecision).rawPrecision);
    }
  }

  public static class FullPrecision extends RefinablePrecision {
    /**
     * This method decides whether or not a variable is being tracked by this precision.
     *
     * @param variable the scoped name of the variable for which to make the decision
     * @return true, when the variable is allowed to be tracked, else false
     */
    @Override
    public boolean contains(String variable) {
      return true;
    }

    @Override
    public FullPrecision refine(Multimap<CFANode, String> additionalMapping) {
      return this;
    }

    @Override
    void serialize(Writer writer) throws IOException {
      writer.write("# full precision used - nothing to show here");
    }

    @Override
    public void join(RefinablePrecision consolidatedPrecision) {
      assert(getClass().equals(consolidatedPrecision.getClass()));
    }
  }

  abstract class Thresholds {
    /**
     * the mapping of variable names to the threshold of the respective variable
     *
     * a value of null means, that the variable has reached its threshold and is no longer tracked
     */
    protected HashMap<String, Integer> thresholds = new HashMap<>();

    /**
     * This method decides whether or not a variable is being tracked by this precision.
     *
     * @param variable the scoped name of the variable for which to make the decision
     * @return true, when the variable is allowed to be tracked, else false
     */
    boolean allowsTrackingOf(String variable) {
      return !thresholds.containsKey(variable) || thresholds.get(variable) != null;
    }

    /**
     * This method declares the given variable to have exceeded its threshold.
     *
     * @param variable the name of the variable
     */
    void setExceeded(String variable) {
      thresholds.put(variable, null);
    }
  }

  @Options(prefix="cpa.explicit.precision.reachedSet")
  class ReachedSetThresholds extends Thresholds {

    /**
     * the default threshold
     */
    @Option(description="threshold for amount of different values that "
        + "are tracked for one variable within the reached set (-1 means infinitely)")
    protected Integer defaultThreshold = -1;

    private ReachedSetThresholds(Configuration config) throws InvalidConfigurationException {
      config.inject(this);
    }

    /**
     * copy constructor
     *
     * @param original the ReachedSetThresholds to copy
     */
    private ReachedSetThresholds(ReachedSetThresholds original) {
      defaultThreshold  = original.defaultThreshold;
      thresholds        = new HashMap<>(original.thresholds);
    }

    /**
     * This method decides if the given variable with the given count exceeds the threshold.
     *
     * @param variable the scoped name of the variable to check
     * @param count the value count to compare to the threshold
     * @return true, if the variable with the given count exceeds the threshold, else false
     */
    boolean exceeds(String variable, Integer count) {
      if (defaultThreshold == -1) {
        return false;
      }

      else if ((thresholds.containsKey(variable) && thresholds.get(variable) == null)
          || (thresholds.containsKey(variable) && thresholds.get(variable) < count)
          || (!thresholds.containsKey(variable) && defaultThreshold < count)) {
        return true;
      }

      return false;
    }
  }

  @Options(prefix="cpa.explicit.precision.path")
  class PathThresholds extends Thresholds {
    /**
     * the default threshold
     */
    @Option(description="threshold for amount of different values that "
        + "are tracked for one variable per path (-1 means infinitely)")
    protected Integer defaultThreshold = -1;

    private PathThresholds(Configuration config) throws InvalidConfigurationException {
      config.inject(this);
    }

    /**
     * copy constructor
     *
     * @param original the PathThresholds to copy
     */
    private PathThresholds(PathThresholds original) {
      defaultThreshold  = original.defaultThreshold;
      thresholds        = new HashMap<>(original.thresholds);
    }

    /**
     * This method decides if the given variable with the given count exceeds the threshold.
     *
     * @param variable the scoped name of the variable to check
     * @param count the value count to compare to the threshold
     * @return true, if the variable with the given count exceeds the threshold, else false
     */
    boolean exceeds(String variable, Integer count) {
      if (defaultThreshold == -1) {
        return false;
      }

      else if ((thresholds.containsKey(variable) && thresholds.get(variable) == null)
          || (thresholds.containsKey(variable) && thresholds.get(variable) < count)
          || (!thresholds.containsKey(variable) && defaultThreshold < count)) {
        return true;
      }

      return false;
    }
  }
}
