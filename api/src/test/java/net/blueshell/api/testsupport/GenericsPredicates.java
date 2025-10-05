package net.blueshell.api.testsupport;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaWildcardType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class GenericsPredicates {

    private GenericsPredicates() {}

    /**
     * Matches types like {@code Raw<Arg1, Arg2, ...>} where:
     *  - the erasure is assignable to {@code raw}, and
     *  - each actual type argument is assignable to the corresponding {@code typeArgs[i]}.
     * Wildcards like {@code ? extends T} match {@code T}.
     * Raw (non-parameterized) types do NOT match (use allowRaw=true overload if you want that).
     */
    public static DescribedPredicate<JavaType> assignableToGeneric(Class<?> raw, Class<?>... typeArgs) {
        return assignableToGeneric(raw, false, typeArgs);
    }

    /**
     * Same as {@link #assignableToGeneric(Class, Class[])} but optionally allows raw (non-parameterized) matches.
     * For example, with allowRaw=true, {@code Collection} (raw) would match {@code assignableToGeneric(Collection.class, BaseDTO.class)}.
     */
    public static DescribedPredicate<JavaType> assignableToGeneric(Class<?> raw, boolean allowRaw, Class<?>... typeArgs) {
        String description = describe(raw, typeArgs);
        return DescribedPredicate.describe("assignable to %s".formatted(description), type -> {
            JavaClass erasure = type.toErasure();
            if (!erasure.isAssignableTo(raw)) return false;

            if (!(type instanceof JavaParameterizedType parameterized)) {
                return allowRaw; // only match raw types if explicitly allowed
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

    // --- helpers ---

    private static boolean typeArgMatches(JavaType actual, Class<?> expected) {
        // Handle wildcards like ? extends T, treat as matching T
        if (actual instanceof JavaWildcardType wildcard) {
            // Prefer upper-bounds semantics: ? extends X matches X (or a subtype of X)
            var uppers = wildcard.getUpperBounds();
            if (uppers.isEmpty()) {
                // Unbounded "?" behaves like "? extends Object"
                return expected == Object.class;
            }
            return uppers.stream().anyMatch(u -> u.toErasure().isAssignableTo(expected));
        }

        // Simple case: compare erasures
        return actual.toErasure().isAssignableTo(expected);
    }

    private static String describe(Class<?> raw, Class<?>... typeArgs) {
        if (typeArgs == null || typeArgs.length == 0) return raw.getSimpleName();
        String args = Arrays.stream(typeArgs).map(Class::getSimpleName).collect(Collectors.joining(", "));
        return "%s<%s>".formatted(raw.getSimpleName(), args);
    }
}
