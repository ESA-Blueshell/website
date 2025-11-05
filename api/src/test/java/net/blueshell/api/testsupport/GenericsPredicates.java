package net.blueshell.api.testsupport;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaWildcardType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helpers for ArchUnit predicates involving generic return types.
 */
public final class GenericsPredicates {

    private GenericsPredicates() {
    }

    public static DescribedPredicate<JavaType> assignableToGeneric(Class<?> raw, Class<?>... typeArgs) {
        return assignableToGeneric(raw, false, typeArgs);
    }

    public static DescribedPredicate<JavaType> assignableToGeneric(Class<?> raw, boolean allowRaw, Class<?>... typeArgs) {
        String description = describe(raw, typeArgs);
        return DescribedPredicate.describe("assignable to %s".formatted(description), type -> {
            JavaClass erasure = type.toErasure();
            if (!erasure.isAssignableTo(raw)) return false;

            if (!(type instanceof JavaParameterizedType parameterized)) {
                return allowRaw;
            }

            List<JavaType> actualArgs = parameterized.getActualTypeArguments();
            if (actualArgs.size() != typeArgs.length) return false;

            for (int i = 0; i < typeArgs.length; i++) {
                JavaType actual = actualArgs.get(i);
                Class<?> expected = typeArgs[i];
                if (!typeArgMatches(actual, expected)) return false;
            }
            return true;
        });
    }

    private static boolean typeArgMatches(JavaType actual, Class<?> expected) {
        if (actual instanceof JavaWildcardType wildcard) {
            var uppers = wildcard.getUpperBounds();
            if (uppers.isEmpty()) {
                return expected == Object.class;
            }
            return uppers.stream().anyMatch(u -> u.toErasure().isAssignableTo(expected));
        }
        return actual.toErasure().isAssignableTo(expected);
    }

    private static String describe(Class<?> raw, Class<?>... typeArgs) {
        if (typeArgs == null || typeArgs.length == 0) return raw.getSimpleName();
        String args = Arrays.stream(typeArgs).map(Class::getSimpleName).collect(Collectors.joining(", "));
        return "%s<%s>".formatted(raw.getSimpleName(), args);
    }
}
