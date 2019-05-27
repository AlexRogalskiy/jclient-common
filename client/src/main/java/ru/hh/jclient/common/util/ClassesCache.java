package ru.hh.jclient.common.util;

import java.io.ObjectStreamClass;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClassesCache {
  private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

  public static Class<?> resolveClass(ObjectStreamClass desc) {
      return CLASS_CACHE.computeIfAbsent(desc.getName(), ClassesCache::loadClass);
  }

  private synchronized static Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
