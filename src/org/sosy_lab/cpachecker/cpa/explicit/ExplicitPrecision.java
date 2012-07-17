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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
   * the current location, given by the ExplicitTransferRelation, needed for checking the white-list
   */
  private CFANode currentLocation                   = null;

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
  private CegarPrecision cegarPrecision             = null;

  private Ignore ignore = null;

  @Option(description = "ignore boolean variables. if this option is used, "
      + "booleans from the cfa should tracked with another CPA, "
      + "i.e. with BDDCPA.")
  private boolean ignoreBooleans = false;

  @Option(description = "ignore variables with only simple numbers. "
      + "if this option is used, these variables from the cfa should "
      + "tracked with another CPA, i.e. with BDDCPA.")
  private boolean ignoreSimpleNumbers = false;

  private Optional<VariableClassification> varClass;

  public ExplicitPrecision(String variableBlacklist, Configuration config,
      Optional<VariableClassification> vc) throws InvalidConfigurationException {
    config.inject(this);

    blackListPattern = Pattern.compile(variableBlacklist);
    this.varClass = vc;

    cegarPrecision        = new CegarPrecision(config);
    reachedSetThresholds  = new ReachedSetThresholds(config);
    pathThresholds        = new PathThresholds(config);
    ignore                = new Ignore(config);
  }

  /**
   * copy constructor
   *
   * @param original the ExplicitPrecision to copy
   */
  public ExplicitPrecision(ExplicitPrecision original) {

    blackListPattern = original.blackListPattern;

    cegarPrecision        = new CegarPrecision(original.cegarPrecision);
    reachedSetThresholds  = new ReachedSetThresholds(original.reachedSetThresholds);
    pathThresholds        = new PathThresholds(original.pathThresholds);
    ignore                = new Ignore(original.ignore);
  }

  public CegarPrecision getCegarPrecision() {
    return cegarPrecision;
  }

  public ReachedSetThresholds getReachedSetThresholds() {
    return reachedSetThresholds;
  }

  public PathThresholds getPathThresholds() {
    return pathThresholds;
  }

  public Ignore getIgnore() {
    return ignore;
  }

  public void setLocation(CFANode node) {
    currentLocation = node;
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
    return reachedSetThresholds.allowsTrackingOf(variable)
        && pathThresholds.allowsTrackingOf(variable)
        && cegarPrecision.allowsTrackingOf(variable)
        && ignore.allowsTrackingOf(variable)
        && !isOnBlacklist(variable)
        && !(ignoreBooleans && isBoolean(variable))
        && !(ignoreSimpleNumbers && isSimpleNumber(variable));
  }

  private boolean isBoolean(String variable) {
    Pair<String,String> var = splitVar(variable);
    return varClass.isPresent()
        && varClass.get().getBooleanVars().containsEntry(var.getFirst(), var.getSecond());
  }

  private boolean isSimpleNumber(String variable) {
    Pair<String,String> var = splitVar(variable);
    return varClass.isPresent()
        && varClass.get().getSimpleNumberVars().containsEntry(var.getFirst(), var.getSecond());
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

  @Options(prefix="cpa.explicit.precision.ignore")
  public class Ignore {
    private Multimap<CFANode, String> mapping = null;

    private Ignore(Configuration config) throws InvalidConfigurationException {
      config.inject(this);

      mapping = HashMultimap.create();
    }

    private Ignore(Ignore original) {

      if (original.mapping != null) {
        mapping = HashMultimap.create(original.mapping);
      }
    }

    public boolean allowsTrackingOf(String variable) {
      return mapping == null || !mapping.containsEntry(currentLocation, variable);
    }

    /**
     * This method sets the current mapping.
     *
     * @param mapping the mapping to be set
     */
    public void setMapping(Multimap<CFANode, String> mapping) {
      this.mapping.putAll(mapping);
    }
  }

  @Options(prefix="analysis")
  public class CegarPrecision {
    /**
     * the collection that determines which variables are tracked at a specific location - if it is null, all variables are tracked
     */
    private HashMultimap<CFANode, String> mapping = null;

    @Option(description="whether or not to use refinement or not")
    private boolean useRefinement = false;

    private CegarPrecision(Configuration config) throws InvalidConfigurationException {
      config.inject(this);

      if (useRefinement) {
        mapping = HashMultimap.create();
      }
    }

    /**
     * copy constructor
     *
     * @param original the CegarPrecison to copy
     */
    private CegarPrecision(CegarPrecision original) {
      if (original.mapping != null) {
        mapping = HashMultimap.create(original.mapping);
      }
    }

    /**
     * This method decides whether or not a variable is being tracked by this precision.
     *
     * @param variable the scoped name of the variable for which to make the decision
     * @return true, when the variable is allowed to be tracked, else false
     */
    boolean allowsTrackingOf(String variable) {
      return mapping == null
             || mapping.containsEntry(currentLocation, variable);
    }

    public boolean allowsTrackingAt(CFANode location, String variable) {
      return mapping != null && mapping.containsEntry(location, variable);
    }

    /**
     * This method adds the additional mapping to the current mapping, i.e., this precision can only grow in size, and never gets smaller.
     *
     * @param additionalMapping the additional mapping to be added to the current mapping
     */
    public void addToMapping(Multimap<CFANode, String> additionalMapping) {
      mapping.putAll(additionalMapping);
    }

    public Collection<String> getVariablesInPrecision() {
      return new HashSet<String>(mapping.values());
    }

    @Override
    public String toString() {
      return Joiner.on(",").join(mapping.entries());
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }

      else if (other == null) {
        return false;
      }

      else if (!getClass().equals(other.getClass())) {
        return false;
      }

      return ((CegarPrecision)other).mapping.equals(mapping);
    }
  }

  abstract class Thresholds {
    /**
     * the mapping of variable names to the threshold of the respective variable
     *
     * a value of null means, that the variable has reached its threshold and is no longer tracked
     */
    protected HashMap<String, Integer> thresholds = new HashMap<String, Integer>();

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
      thresholds        = new HashMap<String, Integer>(original.thresholds);
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
      thresholds        = new HashMap<String, Integer>(original.thresholds);
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
