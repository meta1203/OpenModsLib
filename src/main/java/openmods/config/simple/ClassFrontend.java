package openmods.config.simple;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

import openmods.Log;
import openmods.config.simple.ConfigProcessor.ValueSink;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public class ClassFrontend {

	public static void processClass(File file, final Class<?> cls) {
		ConfigProcessor processor = new ConfigProcessor();

		final Map<String, Field> fields = Maps.newHashMap();

		for (Field f : cls.getFields()) {
			Entry e = f.getAnnotation(Entry.class);
			if (e == null) continue;
			final Class<?> type = f.getType();
			if (!(type.equals(String.class))) {
				Log.warn("Field %s has @Entry annotation, but invalid type %s (should be String)", f, type);
				continue;
			}

			if (!Modifier.isStatic(f.getModifiers())) {
				Log.warn("Field %s has @Entry annotation, but isn't static", f);
				continue;
			}

			String name = e.name();
			if (name.equals(Entry.SAME_AS_FIELD)) name = f.getName();

			Field prev = fields.put(name, f);
			Preconditions.checkState(prev == null, "Duplicate field name: %s, fields: %s, %s", name, f, prev);

			String defaultValue;
			try {
				f.setAccessible(true);
				defaultValue = (String)f.get(null);
			} catch (Throwable t) {
				throw new IllegalStateException(String.format("Failed to get default value from field %s", f), t);
			}

			Preconditions.checkNotNull(defaultValue, "Field %s has no default value", f);
			processor.addEntry(name, e.version(), defaultValue, e.comment());
		}

		processor.process(file, new ValueSink() {
			@Override
			public void valueParsed(String name, String value) {
				Field f = fields.get(name);
				Preconditions.checkNotNull(f, "Ooops, %s : %s", cls, name);
				try {
					f.set(null, value);
				} catch (Throwable t) {
					throw new IllegalStateException(String.format("Failed to set value to field %s", f), t);
				}
			}
		});
	}
}