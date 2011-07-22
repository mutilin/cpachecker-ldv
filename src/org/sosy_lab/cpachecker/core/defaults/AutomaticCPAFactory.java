/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.defaults;

import static com.google.common.base.Preconditions.*;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.sosy_lab.common.Classes;
import org.sosy_lab.common.Classes.UnexpectedCheckedException;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.exceptions.CPAException;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;

/**
 * CPAFactory implementation that can be used to automatically instantiate
 * classes with a single constructor that has parameters.
 *
 * Parameters can be marked as optional with an annotation to specify that the
 * factory may pass null for them.
 */
public class AutomaticCPAFactory implements CPAFactory {

  /**
   * Marker interface for optional constructor parameters.
   * The factory may decide to pass null for such parameters.
   */
  @Target(ElementType.PARAMETER)
  @Retention(RetentionPolicy.RUNTIME)
  public static @interface Optional { }

  private final Class<? extends ConfigurableProgramAnalysis> type;
  private final ClassToInstanceMap<Object> injects = MutableClassToInstanceMap.create();

  public static AutomaticCPAFactory forType(Class<? extends ConfigurableProgramAnalysis> type) {
    return new AutomaticCPAFactory(type);
  }

  public AutomaticCPAFactory(Class<? extends ConfigurableProgramAnalysis> type) {
    this.type = type;
  }

  private AutomaticCPAFactory(Class<? extends ConfigurableProgramAnalysis> pType,
                              ClassToInstanceMap<Object> pInjects) {
    type = pType;
    injects.putAll(pInjects);
  }

  @Override
  public ConfigurableProgramAnalysis createInstance()
      throws InvalidConfigurationException, CPAException {

    Constructor<?>[] allConstructors = type.getDeclaredConstructors();
    if (allConstructors.length != 1) {
      // TODO if necessary, provide method which constructor should be chosen
      // or choose automatically
      throw new UnsupportedOperationException("Cannot automatically create CPAs " +
      		"with more than one constructor!");
    }
    Constructor<?> cons = allConstructors[0];
    cons.setAccessible(true);

    Class<?> formalParameters[] = cons.getParameterTypes();
    Annotation parameterAnnotations[][] = cons.getParameterAnnotations();

    Object actualParameters[] = new Object[formalParameters.length];
    for (int i = 0; i < formalParameters.length; i++) {
      Class<?> formalParam = formalParameters[i];
      Object actualParam = get(formalParam);

      boolean optional = false;
      for (Annotation a : parameterAnnotations[i]) {
        if (a instanceof Optional) {
          optional = true;
          break;
        }
      }

      if (!optional) {
        Preconditions.checkNotNull(actualParam,
            formalParam.getSimpleName() + " instance needed to create " + type.getSimpleName() + "-CPA!");
      }
      actualParameters[i] = actualParam;
    }

    // verify types of declared exceptions
    String exception = Classes.verifyDeclaredExceptions(cons, InvalidConfigurationException.class, CPAException.class);
    if (exception != null) {
      throw new UnsupportedOperationException("Cannot automatically create CPAs if the constructor declares the unsupported checked exception " + exception);
    }

    // instantiate
    try {
      return type.cast(cons.newInstance(actualParameters));
    } catch (InvocationTargetException e) {
      Throwable t = e.getCause();
      Throwables.propagateIfPossible(t, CPAException.class, InvalidConfigurationException.class);
      throw new UnexpectedCheckedException("instantiation of CPA " + type.getSimpleName(), t);

    } catch (InstantiationException e) {
      throw new UnsupportedOperationException("Cannot automatically create CPAs " +
          "that are declared abstract!");

    } catch (IllegalAccessException e) {
      throw new UnsupportedOperationException("Cannot automatically create CPAs " +
          "without an accessible constructor!");
    }
  }

  @Override
  public CPAFactory setLogger(LogManager pLogger) {
    return set(pLogger, LogManager.class);
  }

  @Override
  public CPAFactory setConfiguration(Configuration pConfiguration) {
    return set(pConfiguration, Configuration.class);
  }

  @Override
  public CPAFactory setChild(ConfigurableProgramAnalysis pChild)
      throws UnsupportedOperationException {
    return set(pChild, ConfigurableProgramAnalysis.class);
  }

  @Override
  public <T> CPAFactory set(T obj, Class<T> cls) throws UnsupportedOperationException {
    Preconditions.checkNotNull(cls);
    Preconditions.checkNotNull(obj);
    Preconditions.checkState(!injects.containsKey(cls),
        "Cannot store two objects of class " + cls.getSimpleName());

    injects.putInstance(cls, obj);
    return this;
  }

  public <T> T get(Class<T> cls) {
    return injects.getInstance(cls);
  }

  @Override
  public CPAFactory setChildren(List<ConfigurableProgramAnalysis> pChildren)
      throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Cannot automatically create CPAs " +
      "with multiple children CPAs!");
  }

  /**
   * Return a new factory for the same type, where each fresh CPA instance will be
   * given an instance of an options holder class. When the options holder instance
   * is passed to the CPA constructor, the option values will already have been
   * injected into it.
   *
   * Each CPA instance will receive their own options holder instance, and it is
   * guaranteed that no other references to this instance will remain, so the
   * CPA is free to do anything with the instance.
   *
   * This method does not modify the CPAFactory instance on which it is called.
   * All calls to the various set* methods that were made before this call
   * reflect in the CPAFactory instance returned by this method, but subsequent
   * calls to set* on this instance don't affect the new instance and vice-versa.
   *
   * It is safe to call this method again on its result, although only once for
   * each class passed as a parameter (similar to {@link #set(Object, Class)}).
   *
   * The option holder class has to have an accessible default constructor
   * (i.e., one without any parameters) and must not be abstract.
   * The default constructor may throw checked exceptions of the types
   * {@link InvalidConfigurationException} and {@link CPAException}, but of no
   * other types! Of course the options holder class has to have the {@link Options}
   * annotation. Violations of these requirements may result in either a
   * {@link IllegalArgumentException} thrown by this method or in some unchecked
   * exception when the CPA is instantiated.
   *
   * @param optionsClass The class object of the option holder class.
   * @return A new CPAFactory for the same type as this one.
   */
  public <T> AutomaticCPAFactory withOptions(final Class<T> optionsClass) {
    checkArgument(optionsClass.getAnnotation(Options.class) != null,
        "Options holder class must be annotated with the Options annotation");

    final Constructor<T> constructor;
    try {
      constructor = optionsClass.getConstructor();

    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Options holder class must have a default constructor", e);
    }

    // verify types of declared exceptions
    String exception = Classes.verifyDeclaredExceptions(constructor, InvalidConfigurationException.class, CPAException.class);
    if (exception != null) {
      throw new IllegalArgumentException("Constructor of options holder class declares illegal checked exception: " + exception);
    }

    return new AutomaticCPAFactoryWithOptions<T>(type, injects, optionsClass, constructor);
  }

  private static final class AutomaticCPAFactoryWithOptions<T> extends AutomaticCPAFactory {
  private final Class<T>       optionsClass;
  private final Constructor<T> constructor;

  private AutomaticCPAFactoryWithOptions(
      Class<? extends ConfigurableProgramAnalysis> pType,
      ClassToInstanceMap<Object> pInjects, Class<T> pOptionsClass,
      Constructor<T> pConstructor) {
    super(pType, pInjects);
    optionsClass = pOptionsClass;
    constructor = pConstructor;
  }

  @Override
  public ConfigurableProgramAnalysis createInstance() throws InvalidConfigurationException, CPAException {
    T options;
    try {
      // create options holder class instance
      options = constructor.newInstance();

    } catch (InvocationTargetException e) {
      Throwable t = e.getCause();
      Throwables.propagateIfPossible(t, CPAException.class, InvalidConfigurationException.class);
      throw new UnexpectedCheckedException("instantiation of CPA options holder class " + optionsClass.getCanonicalName(), t);

    } catch (IllegalAccessException e) {
      throw new UnsupportedOperationException("Cannot automatically create CPAs without an accessible constructor for their options class!", e);

    } catch (InstantiationException e) {
      throw new UnsupportedOperationException("Cannot automatically create CPAs with an abstract options class!", e);
    }

    // inject options into holder class
    Configuration config = get(Configuration.class);
    checkState(config != null, "Configuration object needed to create CPA");

    config.inject(options);
    set(options, optionsClass);

    return super.createInstance();
  }
}
}