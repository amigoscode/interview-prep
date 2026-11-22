package com.amigoscode.threadsafety.demo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Looks at every bean of the application with reflection and answers one question per field:
 * can a request thread change this, while another request thread is using it?
 *
 * It reads types, not behaviour. It can tell that a field is a HashMap. It cannot tell whether two
 * calls on a ConcurrentHashMap needed to be one, or whether a lock is held. Those rows say so.
 */
final class BeanReport {

    enum Verdict { OK, CAREFUL, CHECK, MUTABLE }

    record Row(String bean, String beanClass, String scope, String field, String type, Verdict verdict, String why) {
    }

    private static final String SCOPED_TARGET = "scopedTarget.";

    private BeanReport() {
    }

    static List<Row> scan(ConfigurableApplicationContext context, String basePackage) {
        ConfigurableListableBeanFactory beans = context.getBeanFactory();
        Set<Object> singletons = Collections.newSetFromMap(new IdentityHashMap<>());
        singletons.addAll(beans.getBeansOfType(Object.class, false, false).values());

        List<Row> rows = new ArrayList<>();
        for (String name : beans.getBeanDefinitionNames()) {
            if (beans.containsBeanDefinition(SCOPED_TARGET + name)) {
                continue;                         // the proxy in front of a request-scoped bean: report the real one
            }
            Class<?> type = beans.getType(name, false);
            if (type == null) {
                continue;
            }
            Class<?> beanClass = ClassUtils.getUserClass(type);
            if (!beanClass.getName().startsWith(basePackage + ".") || beanClass.getName().startsWith(basePackage + ".demo.")
                    || beanClass.getPackageName().equals(basePackage)) {
                continue;                         // only the classes of this application
            }
            BeanDefinition definition = beans.getBeanDefinition(name);
            String scope = definition.getScope() == null || definition.getScope().isEmpty() ? "singleton" : definition.getScope();
            Object instance = scope.equals("singleton") ? beans.getBean(name) : null;
            String shownName = name.startsWith(SCOPED_TARGET) ? name.substring(SCOPED_TARGET.length()) : name;

            List<Field> fields = fieldsOf(beanClass);
            if (fields.isEmpty()) {
                rows.add(new Row(shownName, beanClass.getSimpleName(), scope, "(no fields)", "", Verdict.OK, "stateless"));
            }
            for (Field field : fields) {
                rows.add(judge(shownName, beanClass, scope, field, instance, singletons, beans));
            }
        }
        return rows;
    }

    private static List<Field> fieldsOf(Class<?> beanClass) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = beanClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static Row judge(String bean, Class<?> beanClass, String scope, Field field, Object instance,
                             Set<Object> singletons, ConfigurableListableBeanFactory beans) {
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        boolean isFinal = Modifier.isFinal(field.getModifiers());
        Object value = valueOf(field, isStatic ? null : instance, isStatic);
        Class<?> actual = value != null ? value.getClass() : field.getType();
        String type = (isStatic ? "static " : "") + (Modifier.isVolatile(field.getModifiers()) ? "volatile " : "")
                + field.getType().getSimpleName()
                + (value != null && !field.getType().isPrimitive() && actual != field.getType() && !singletons.contains(value)
                        ? " (" + actual.getSimpleName() + ")" : "");

        Verdict verdict;
        String why;
        if (!scope.equals("singleton") && !isStatic) {
            verdict = Verdict.OK;
            why = scope.equals("request")
                    ? "not shared: a new " + beanClass.getSimpleName() + " for every request"
                    : "not shared: a new " + beanClass.getSimpleName() + " every time the container is asked";
        } else if (!isFinal) {
            verdict = Verdict.MUTABLE;
            why = Modifier.isVolatile(field.getModifiers())
                    ? "not final. volatile makes writes visible, it does not make ++ one step"
                    : "not final: every request thread can write it";
        } else if (value instanceof ScopedObject) {
            verdict = Verdict.OK;
            why = "a proxy: each request reaches its own " + field.getType().getSimpleName();
        } else if (value != null && singletons.contains(value)) {
            verdict = Verdict.OK;
            why = "final reference to another bean";
        } else if (isPrototypeBean(field.getType(), beans)) {
            verdict = Verdict.CAREFUL;
            why = "a prototype bean, injected once: this singleton keeps the same one for ever";
        } else if (isImmutable(actual)) {
            verdict = Verdict.OK;
            why = "final and immutable";
        } else if (isThreadSafe(actual)) {
            verdict = Verdict.CAREFUL;
            why = "thread-safe type. One call is atomic, two calls in a row are not";
        } else if (isKnownUnsafe(actual)) {
            verdict = Verdict.MUTABLE;
            why = "final, but " + actual.getSimpleName() + " is not thread-safe";
        } else {
            verdict = Verdict.CHECK;
            why = "final reference: is " + actual.getSimpleName() + " immutable or thread-safe?";
        }
        return new Row(bean, beanClass.getSimpleName(), scope, field.getName(), type, verdict, why);
    }

    private static boolean isPrototypeBean(Class<?> type, ConfigurableListableBeanFactory beans) {
        for (String name : beans.getBeanNamesForType(type, true, false)) {
            if (beans.containsBeanDefinition(name) && beans.getBeanDefinition(name).isPrototype()) {
                return true;
            }
        }
        return false;
    }

    private static Object valueOf(Field field, Object instance, boolean isStatic) {
        if (!isStatic && instance == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (RuntimeException | IllegalAccessException e) {
            return null;
        }
    }

    private static boolean isImmutable(Class<?> type) {
        String name = type.getName();
        return type.isPrimitive() || type.isEnum() || type.isRecord()
                || type == String.class || type == Class.class
                || Number.class.isAssignableFrom(type) && name.startsWith("java.lang.")
                || type == Boolean.class || type == Character.class
                || name.startsWith("java.math.") || name.startsWith("java.time.");
    }

    private static boolean isThreadSafe(Class<?> type) {
        return type.getName().startsWith("java.util.concurrent.") || type == ThreadLocal.class;
    }

    private static boolean isKnownUnsafe(Class<?> type) {
        return type.isArray()
                || Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || java.text.Format.class.isAssignableFrom(type)
                || java.util.Calendar.class.isAssignableFrom(type)
                || type == java.util.Date.class
                || type == StringBuilder.class;
    }
}
