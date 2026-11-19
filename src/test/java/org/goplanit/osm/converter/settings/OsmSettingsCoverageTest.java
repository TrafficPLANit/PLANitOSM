package org.goplanit.osm.converter.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderSettings;
import org.goplanit.utils.reflection.AnnotationUtils;
import org.goplanit.utils.reflection.ReflectionUtils;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;
import org.goplanit.utils.settings.annotations.NoSettingsKey;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsSection;
import org.goplanit.utils.settings.annotations.SettingsWrite;
import org.junit.jupiter.api.Test;

/**
 * Tests that the annotations of every settings class the OSM intermodal reader settings reach cover those settings:
 * every method changing settings is a key, is covered by one, or is marked as no key; every getter of nested settings
 * is a section, the read of a key, or marked as no key; every key has a read; and every key a convenience is covered
 * by exists
 * <p>
 * A method changing settings that a settings file cannot reach goes unnoticed until someone looks for the key, so
 * these checks fail as soon as one is added without a marker. The classes are found by following, from the intermodal
 * reader settings, every getter returning settings, such as the getter of the lane defaults, so no class hides from
 * the checks. A class is settings when it names a section or has a method carrying a settings annotation. A method
 * changes settings when it returns nothing or its name starts with a verb that changes, such as {@code set} or
 * {@code activate}; putting settings back to their defaults, logging them, writing them to a file and copying them are
 * no keys and are left out.
 * </p>
 *
 * @author markr
 */
public class OsmSettingsCoverageTest {

  /** the annotations of which exactly one marks how a method changing settings is covered */
  private static final List<Class<? extends Annotation>> COVERAGE_ANNOTATIONS =
      List.of(SettingsWrite.class, CoveredBySettingsKey.class, NoSettingsKey.class);

  /** the annotations of which one marks a getter of nested settings */
  private static final List<Class<? extends Annotation>> NESTED_SETTINGS_ANNOTATIONS =
      List.of(SettingsSection.class, SettingsRead.class, NoSettingsKey.class);

  /** the annotations by which a class is known as settings */
  private static final List<Class<? extends Annotation>> SETTINGS_ANNOTATIONS = List.of(SettingsWrite.class,
      SettingsRead.class, SettingsSection.class, CoveredBySettingsKey.class, NoSettingsKey.class);

  /** names of public methods that are no keys: defaults back, writing a file, copying, and Java's own */
  private static final Set<String> NOT_KEYS =
      Set.of("reset", "writeSettingsFile", "shallowClone", "deepClone", "equals", "hashCode", "toString");

  /** what the name of a public method logging the settings starts with, which is no key either */
  private static final String LOGGING_VERB = "log";

  /** what the name of a method changing settings starts with, when it returns a value */
  private static final List<String> CHANGING_VERBS = List.of("set", "activate", "deactivate", "add", "remove",
      "register", "override", "overwrite", "exclude", "include", "suppress", "keep", "put", "clear", "enable",
      "disable", "replace", "update");

  /** the package of every PLANit class, whose methods are checked */
  private static final String PLANIT_PACKAGE = "org.goplanit";

  /**
   * Verify whether a method is a public method of an object, declared by a PLANit class, and written in the code
   *
   * @param method to check
   * @return true when it is
   */
  private static boolean isPlanitMethod(Method method) {
    return Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()) &&
        !method.isSynthetic() && !method.isBridge() &&
        method.getDeclaringClass().getPackageName().startsWith(PLANIT_PACKAGE);
  }

  /**
   * Verify whether a method is one of those that are no keys: putting the settings back to their defaults, logging
   * them, writing them to a file, copying them, and Java's own methods
   *
   * @param method to check
   * @return true when it is no key
   */
  private static boolean isNoKey(Method method) {
    return NOT_KEYS.contains(method.getName()) || method.getName().startsWith(LOGGING_VERB);
  }

  /**
   * Verify whether a method changes settings: it returns nothing or its name starts with a verb that changes, and it
   * is not one of the methods that are no keys
   *
   * @param method to check
   * @return true when it changes settings
   */
  private static boolean changesSettings(Method method) {
    return !isNoKey(method) && (method.getReturnType() == void.class ||
        CHANGING_VERBS.stream().anyMatch(verb -> method.getName().startsWith(verb)));
  }

  /**
   * Verify whether a method is a getter: it takes nothing, returns a value, does not change settings and is not one of
   * the methods that are no keys
   *
   * @param method to check
   * @return true when it is a getter
   */
  private static boolean isGetter(Method method) {
    return method.getParameterCount() == 0 && method.getReturnType() != void.class && !changesSettings(method) &&
        !isNoKey(method);
  }

  /**
   * The classes a declared type is made of: its own class, the component of an array, and its type arguments, such as
   * the value class of a map
   *
   * @param type declared
   * @return the classes
   */
  private static Stream<Class<?>> classesIn(Type type) {
    var rawClass = ReflectionUtils.getRawClass(type);
    var arguments = type instanceof ParameterizedType ?
        Arrays.stream(((ParameterizedType) type).getActualTypeArguments()) : Stream.<Type>empty();
    var component = rawClass.isArray() ? Stream.<Type>of(rawClass.getComponentType()) : Stream.<Type>empty();
    return Stream.concat(Stream.of(rawClass),
        Stream.concat(component, arguments).flatMap(OsmSettingsCoverageTest::classesIn));
  }

  /**
   * Verify whether a class is settings: a PLANit class naming a section, or with a public method carrying a settings
   * annotation
   *
   * @param candidate to check
   * @return true when it is settings
   */
  private static boolean isSettingsClass(Class<?> candidate) {
    return candidate.getPackageName().startsWith(PLANIT_PACKAGE) &&
        (candidate.isAnnotationPresent(SettingsSection.class) || Arrays.stream(candidate.getMethods())
            .anyMatch(method -> AnnotationUtils.hasAnyAnnotation(method, SETTINGS_ANNOTATIONS)));
  }

  /**
   * The settings classes a getter returns, through the classes its declared type is made of
   *
   * @param getter to look at
   * @return the settings classes, empty when it returns none
   */
  private static Set<Class<?>> settingsClassesReturnedBy(Method getter) {
    return classesIn(getter.getGenericReturnType()).filter(OsmSettingsCoverageTest::isSettingsClass)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * The settings classes the OSM intermodal reader settings reach, themselves included, by following every getter
   * returning settings
   *
   * @return the classes, in the order found
   */
  private static Set<Class<?>> findReachableSettingsClasses() {
    var found = new LinkedHashSet<Class<?>>();
    var toVisit = new ArrayDeque<Class<?>>(List.of(OsmIntermodalReaderSettings.class));
    while (!toVisit.isEmpty()) {
      var settingsClass = toVisit.poll();
      if (!found.add(settingsClass)) {
        continue;
      }
      Arrays.stream(settingsClass.getMethods()).filter(method -> isPlanitMethod(method) && isGetter(method))
          .flatMap(getter -> settingsClassesReturnedBy(getter).stream()).forEach(toVisit::add);
    }
    return found;
  }

  /**
   * The public methods of PLANit classes the settings classes have, each once, inherited ones included
   *
   * @param settingsClasses to take the methods of
   * @return the methods
   */
  private static Set<Method> findPlanitMethods(Collection<Class<?>> settingsClasses) {
    return settingsClasses.stream().flatMap(settingsClass -> Arrays.stream(settingsClass.getMethods()))
        .filter(OsmSettingsCoverageTest::isPlanitMethod).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * The keys and sections of a settings class, by name: its writes, reads and sections, inherited ones included
   *
   * @param settingsClass to take the keys of
   * @return the names
   */
  private static Set<String> findKeysAndSections(Class<?> settingsClass) {
    var names = new HashSet<String>();
    for (var method : settingsClass.getMethods()) {
      Stream.of(method.getAnnotation(SettingsWrite.class)).filter(write -> write != null)
          .forEach(write -> names.add(write.key()));
      Stream.of(method.getAnnotation(SettingsRead.class)).filter(read -> read != null)
          .forEach(read -> names.add(read.key()));
      Stream.of(method.getAnnotation(SettingsSection.class)).filter(section -> section != null)
          .forEach(section -> names.add(section.key()));
    }
    return names;
  }

  /**
   * The classes of the sections of a key among settings classes, through the getters of that section
   *
   * @param settingsClasses to look in
   * @param sectionKey of the sections
   * @return the classes of the sections
   */
  private static Set<Class<?>> findSectionClasses(Collection<Class<?>> settingsClasses, String sectionKey) {
    return settingsClasses.stream().flatMap(settingsClass -> Arrays.stream(settingsClass.getMethods()))
        .filter(getter -> getter.isAnnotationPresent(SettingsSection.class) &&
            getter.getAnnotation(SettingsSection.class).key().equals(sectionKey))
        .map(Method::getReturnType).collect(Collectors.toSet());
  }

  /**
   * Verify whether a key path names a key or section of a settings class: the section's name, a dot and the key for a
   * key of a nested section. A class holding the settings of sections at the root, such as the intermodal settings,
   * has their keys at the root too
   *
   * @param settingsClass the path is relative to
   * @param keyPath to look up
   * @return true when it names a key or section
   */
  private static boolean namesKeyOrSection(Class<?> settingsClass, String keyPath) {
    Collection<Class<?>> classes = Stream.concat(Stream.of(settingsClass), Arrays.stream(settingsClass.getMethods())
        .filter(getter -> isPlanitMethod(getter) && isGetter(getter) &&
            getter.getReturnType().isAnnotationPresent(SettingsSection.class))
        .map(Method::getReturnType)).collect(Collectors.toSet());
    var keys = keyPath.split("\\.");
    for (int index = 0; index < keys.length - 1; index++) {
      classes = findSectionClasses(classes, keys[index]);
    }
    var lastKey = keys[keys.length - 1];
    return classes.stream().anyMatch(keyClass -> findKeysAndSections(keyClass).contains(lastKey));
  }

  /**
   * A method as a failure lists it, by its class and name
   *
   * @param method to name
   * @return the name
   */
  private static String nameOf(Method method) {
    return method.getDeclaringClass().getSimpleName() + "#" + method.getName() +
        Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(",", "(", ")"));
  }

  /**
   * Every method changing settings has exactly one of {@link SettingsWrite}, {@link CoveredBySettingsKey} or
   * {@link NoSettingsKey}
   */
  @Test
  public void everyMethodChangingSettingsHasExactlyOneMarker() {
    var unmarked = findPlanitMethods(findReachableSettingsClasses()).stream()
        .filter(method -> changesSettings(method) &&
            COVERAGE_ANNOTATIONS.stream().filter(method::isAnnotationPresent).count() != 1)
        .map(OsmSettingsCoverageTest::nameOf).sorted().collect(Collectors.toList());
    assertEquals(List.of(), unmarked);
  }

  /**
   * Every getter of nested settings is a section, the read of a key, or marked as no key, or returns settings naming
   * their own section at the root, such as the network settings the intermodal settings hold
   */
  @Test
  public void everyGetterOfNestedSettingsIsMarked() {
    var unmarked = findPlanitMethods(findReachableSettingsClasses()).stream()
        .filter(method -> isGetter(method) && !settingsClassesReturnedBy(method).isEmpty() &&
            !AnnotationUtils.hasAnyAnnotation(method, NESTED_SETTINGS_ANNOTATIONS) &&
            !method.getReturnType().isAnnotationPresent(SettingsSection.class))
        .map(OsmSettingsCoverageTest::nameOf).sorted().collect(Collectors.toList());
    assertEquals(List.of(), unmarked);
  }

  /**
   * Every key has a read of the same key in its class, the {@code +} and {@code -} forms of a list that of the whole
   * list, so settings can be written out
   */
  @Test
  public void everyKeyHasARead() {
    var withoutRead = new HashSet<String>();
    for (var settingsClass : findReachableSettingsClasses()) {
      var readKeys = Arrays.stream(settingsClass.getMethods()).filter(method -> method.isAnnotationPresent(
          SettingsRead.class)).map(method -> method.getAnnotation(SettingsRead.class).key()).collect(Collectors.toSet());
      Arrays.stream(settingsClass.getMethods()).filter(method -> method.isAnnotationPresent(SettingsWrite.class))
          .map(method -> method.getAnnotation(SettingsWrite.class).key())
          .filter(key -> !readKeys.contains(key) &&
              readKeys.stream().noneMatch(listKey -> key.equals(listKey + "+") || key.equals(listKey + "-")))
          .forEach(key -> withoutRead.add(settingsClass.getSimpleName() + ": " + key));
    }
    assertEquals(List.of(), withoutRead.stream().sorted().collect(Collectors.toList()));
  }

  /**
   * Every key or section a convenience is covered by exists on the class of the convenience, a key of a nested section
   * found through the path of its sections
   */
  @Test
  public void everyCoveringKeyExists() {
    var unknown = findPlanitMethods(findReachableSettingsClasses()).stream()
        .filter(method -> method.isAnnotationPresent(CoveredBySettingsKey.class) &&
            !Arrays.stream(method.getAnnotation(CoveredBySettingsKey.class).value())
                .allMatch(keyPath -> namesKeyOrSection(method.getDeclaringClass(), keyPath)))
        .map(OsmSettingsCoverageTest::nameOf).sorted().collect(Collectors.toList());
    assertEquals(List.of(), unknown);
  }
}
