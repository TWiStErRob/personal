package net.twisterrob.java.utils;

import java.lang.reflect.*;

import javax.annotation.*;

import org.slf4j.*;

public class ReflectionTools {
	private static final Logger LOG = LoggerFactory.getLogger(ReflectionTools.class);

	@SuppressWarnings("unchecked")
	public static <T> T getStatic(@Nonnull Class<?> clazz, @Nonnull String fieldName) {
		try {
			Field field = findDeclaredField(clazz, fieldName);
			field.setAccessible(true);
			return (T)field.get(null);
		} catch (Exception ex) {
			LOG.warn("Cannot read static field {} of {}", fieldName, clazz, ex);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getStatic(@Nonnull String className, @Nonnull String fieldName) {
		try {
			return getStatic(Class.forName(className), fieldName);
		} catch (Exception ex) {
			LOG.warn("Cannot read static field {} of {}", fieldName, className, ex);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T> T get(@Nonnull Object object, @Nonnull String fieldName) {
		try {
			Field field = findDeclaredField(object.getClass(), fieldName);
			field.setAccessible(true);
			return (T)field.get(object);
		} catch (Exception ex) {
			//noinspection ConstantConditions prevent NPE when object is null, even though it was declared not null
			LOG.warn("Cannot read field {} of ({}){}", fieldName, object != null? object.getClass() : null, object, ex);
		}
		return null;
	}

	/**
	 * Like {@link Class#getDeclaredField}, but looking in all superclasses as well.
	 * @throws NoSuchFieldException if a field with the specified name is not found in the class hierarchy.
	 * @see Class#getDeclaredField(String)
	 */
	public static @Nonnull Field findDeclaredField(@Nonnull Class<?> clazz,
			@Nonnull String fieldName) throws NoSuchFieldException {
		do {
			try {
				return clazz.getDeclaredField(fieldName);
			} catch (NoSuchFieldException ex) {
				clazz = clazz.getSuperclass();
			}
		} while (clazz != null);
		throw new NoSuchFieldException(fieldName);
	}

	public static boolean instanceOf(String clazz, Object value) {
		try {
			return Class.forName(clazz).isInstance(value);
		} catch (ClassNotFoundException ex) {
			LOG.warn("Cannot find class {} to check instanceof {}", clazz, value, ex);
			return false;
		}
	}

	/**
	 * Like {@link Class#getDeclaredMethod}, but looking in all superclasses as well.
	 * @throws NoSuchMethodException if a method with the specified name is not found in the class hierarchy.
	 * @see Class#getDeclaredMethod(String, Class[])
	 */
	public static @Nonnull Method findDeclaredMethod(@Nonnull Class<?> clazz,
			@Nonnull String fieldName, Class<?>... parameterTypes) throws NoSuchMethodException {
		do {
			try {
				return clazz.getDeclaredMethod(fieldName, parameterTypes);
			} catch (NoSuchMethodException ex) {
				clazz = clazz.getSuperclass();
			}
		} while (clazz != null);
		throw new NoSuchMethodException(fieldName);
	}

	public static @Nullable Method tryFindDeclaredMethod(@Nonnull Class<?> clazz,
			@Nonnull String fieldName, Class<?>... parameterTypes) {
		try {
			return findDeclaredMethod(clazz, fieldName, parameterTypes);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	public static <T extends AccessibleObject> T trySetAccessible(T reflected) {
		try {
			if (reflected != null) {
				reflected.setAccessible(true);
			}
		} catch (Exception ex) {
			return null;
		}
		return reflected;
	}
}
