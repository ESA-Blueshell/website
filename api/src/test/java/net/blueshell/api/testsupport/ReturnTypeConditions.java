package net.blueshell.api.testsupport;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

public final class ReturnTypeConditions {

    private ReturnTypeConditions() {}

    /** Existing: passes if the return type matches the predicate */
    public static ArchCondition<JavaMethod> haveReturnType(DescribedPredicate<? super JavaType> predicate) {
        String desc = "have return type %s".formatted(predicate.getDescription());
        return new ArchCondition<>(desc) {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaType actual = method.getReturnType(); // includes generics
                boolean ok = predicate.test(actual);
                if (!ok) {
                    String message = "Method <%s> has return type <%s> which does not %s"
                            .formatted(method.getFullName(), actual.getName(), predicate.getDescription());
                    events.add(SimpleConditionEvent.violated(method, message));
                }
            }
        };
    }

    /** New: passes if the return type does NOT match the predicate */
    public static ArchCondition<JavaMethod> notHaveReturnType(DescribedPredicate<? super JavaType> predicate) {
        String desc = "not have return type %s".formatted(predicate.getDescription());
        return new ArchCondition<>(desc) {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaType actual = method.getReturnType(); // includes generics
                boolean matches = predicate.test(actual);
                if (matches) {
                    String message = "Method <%s> has return type <%s> which unexpectedly %s"
                            .formatted(method.getFullName(), actual.getName(), predicate.getDescription());
                    events.add(SimpleConditionEvent.violated(method, message));
                }
            }
        };
    }
}
