package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.domain.JavaParameterizedType
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import jakarta.persistence.*
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import net.blueshell.api.architecture.support.ArchModules
import org.junit.jupiter.api.Test

class JpaMappingArchTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `to-one many-to-one must be lazy`(): Unit =
        arch("@ManyToOne fetch must be LAZY") {
            fields()
                .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
                .and().areAnnotatedWith(ManyToOne::class.java)
                .should(haveFetchTypeLazyFromRuntimeAnnotation(ManyToOne::class.java))
                .because("@ManyToOne defaults to EAGER; prefer LAZY.")
        }

    @Test
    fun `one-to-one owning side must be lazy`(): Unit =
        arch("Owning @OneToOne must declare fetch = LAZY") {
            fields()
                .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
                .and().areAnnotatedWith(OneToOne::class.java)
                .should(oneToOneOwningSideMustBeLazy())
                .because("LAZY only has a chance to work on the owning side; make intent explicit.")
        }

    @Test
    fun `one-to-one inverse side must not claim to be lazy`(): Unit =
        arch("Inverse @OneToOne must not specify fetch = LAZY (it is usually ignored)") {
            fields()
                .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
                .and().areAnnotatedWith(OneToOne::class.java)
                .should(oneToOneInverseSideMustNotSpecifyLazy())
                .because("JPA providers often ignore LAZY on mappedBy side; don't encode misleading intent in mappings.")
        }

    @Test
    fun `mappedBy must not name a type from another module`(): Unit =
        arch("Inverse-side associations stay inside their own module") {
            fields()
                .that().areAnnotatedWith(OneToMany::class.java)
                .or().areAnnotatedWith(OneToOne::class.java)
                .or().areAnnotatedWith(ManyToMany::class.java)
                .should(mappedByMustStayInsideItsModule())
                .because(
                    "architecture ADR-003: an inverse side makes the owning module's table a member of " +
                        "this aggregate and loads it on traversal. What another module needs goes through " +
                        "its api surface or a query keyed on the foreign key it already holds."
                )
        }

    @Test
    fun `relations with separate id column must be read-only or mapsId`(): Unit =
        arch("If both <name>Id and relation exist, join must be read-only or @MapsId") {
            fields()
                .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
                .and().areAnnotatedWith(ManyToOne::class.java)
                .or().areAnnotatedWith(OneToOne::class.java)
                .should(relationWithIdFieldMustBeReadOnlyOrMapsId())
                .because("If you keep both <name>Id + <name> relation, ensure a single source of truth.")
        }

    @Test
    fun `one-to-many associations should use private underscore backing field`(): Unit =
        arch("Collections are private underscore-backed vals") {
            fields()
                .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
                .and().areAnnotatedWith(OneToMany::class.java)
                .should(bePrivateUnderscoreAndFinal())
                .because("Keep mutable collections private; expose read-only views.")
        }

    @Test
    fun `idClass types must implement serializable`(): Unit =
        arch("@IdClass values must implement Serializable") {
            classes()
                .that().areAnnotatedWith(IdClass::class.java)
                .should(idClassValueMustImplementSerializable())
                .because("JPA requires IdClass types to be Serializable.")
                .allowEmptyShould(true)
        }

    @Test
    fun `no lombok in persistence layer`(): Unit =
        arch("No Lombok in persistence layer") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.PERSISTENCE)
                .should().dependOnClassesThat().resideInAnyPackage("lombok..")
                .because("Lombok in Kotlin entities is a code smell; prefer Kotlin-native patterns")
        }

    // ---- New “safety rails” ----

    @Test
    fun `entities must not be final`(): Unit =
        arch("Entities must not be final (proxy-safe)") {
            classes()
                .that().areAnnotatedWith(Entity::class.java)
                .should(notHaveModifier(JavaModifier.FINAL))
                .because("Hibernate proxies require non-final entities unless you rely on bytecode enhancement for everything.")
        }

    @Test
    fun `to-one associations must declare join strategy`(): Unit =
        arch("To-one associations must declare join strategy") {
            fields()
                .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
                .and().areAnnotatedWith(ManyToOne::class.java)
                .or().areAnnotatedWith(OneToOne::class.java)
                .should(toOneMustDeclareJoinColumnOrMappedByOrMapsId())
                .because("Avoid implicit join columns; make FK/mapping decisions explicit for readability and migrations.")
        }

    @Test
    fun `entities should not depend on jackson`(): Unit =
        arch("Entities must not depend on Jackson") {
            noClasses()
                .that().areAnnotatedWith(Entity::class.java)
                .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson..")
                .because("Jackson annotations on entities often hide lazy-loading + serialization problems. Prefer DTOs.")
        }

    @Test
    fun `entities should not be kotlin data classes`(): Unit =
        arch("Entities should not be Kotlin data classes") {
            classes()
                .that().areAnnotatedWith(Entity::class.java)
                .should(notBeKotlinDataClass())
                .because("Data classes generate equals/hashCode/copy over all properties, which is dangerous for JPA entities and proxies.")
        }

    // ---------------------------------------------------------------------------------------------
    // Conditions (existing + new)
    // ---------------------------------------------------------------------------------------------

    private fun <A : Annotation> haveFetchTypeLazyFromRuntimeAnnotation(annotationType: Class<A>): ArchCondition<JavaField> =
        object : ArchCondition<JavaField>("have fetch = LAZY on @${annotationType.simpleName}") {
            override fun check(item: JavaField, events: ConditionEvents) {
                val ann: A = item.getAnnotationOfType(annotationType) ?: return
                val fetch = ann.readEnumPropertyOrNull("fetch", FetchType::class.java) ?: FetchType.EAGER
                val ok = fetch == FetchType.LAZY

                events.add(
                    SimpleConditionEvent(
                        item,
                        ok,
                        "${item.owner.fullName}.${item.name} @${annotationType.simpleName}(fetch=$fetch) should be LAZY"
                    )
                )
            }
        }

    private fun bePrivateAndUnderscorePrefixed(): ArchCondition<JavaField> =
        object : ArchCondition<JavaField>("be private and name start with '_'") {
            override fun check(item: JavaField, events: ConditionEvents) {
                val isPrivate = item.modifiers.contains(JavaModifier.PRIVATE)
                val underscore = item.name.startsWith("_")
                val ok = isPrivate && underscore
                events.add(
                    SimpleConditionEvent(
                        item,
                        ok,
                        "${item.owner.fullName}.${item.name} should be private and underscore-prefixed (e.g. _user)"
                    )
                )
            }
        }

    /**
     * The module a `mappedBy` field points at, read from the association's type argument for a
     * collection and from the field type itself for a to-one. A type outside `net.blueshell.api`
     * — the collection interface a raw signature would erase to — belongs to no module and is
     * skipped, so the rule reports what it could resolve rather than what it could not.
     */
    private fun mappedByMustStayInsideItsModule(): ArchCondition<JavaField> =
        object : ArchCondition<JavaField>("name a type from the declaring module") {
            override fun check(item: JavaField, events: ConditionEvents) {
                val mappedBy = item.mappedByOrNull() ?: return
                val ownerModule = ArchModules.moduleOf(item.owner) ?: return

                item.associationTargets()
                    .mapNotNull { target -> ArchModules.moduleOf(target)?.let { target to it } }
                    .filter { (_, targetModule) -> targetModule != ownerModule }
                    .forEach { (target, targetModule) ->
                        events.add(
                            SimpleConditionEvent.violated(
                                item,
                                "${item.owner.fullName}.${item.name} is the inverse side of " +
                                    "'$mappedBy' on ${target.fullName}, which module '$targetModule' " +
                                    "owns while '$ownerModule' declares the field"
                            )
                        )
                    }
            }
        }

    private fun JavaField.mappedByOrNull(): String? =
        sequenceOf(
            getAnnotationOfTypeOrNull(OneToMany::class.java)?.mappedBy,
            getAnnotationOfTypeOrNull(OneToOne::class.java)?.mappedBy,
            getAnnotationOfTypeOrNull(ManyToMany::class.java)?.mappedBy,
        ).filterNotNull().firstOrNull { it.isNotBlank() }

    private fun JavaField.associationTargets(): List<JavaClass> =
        when (val fieldType = type) {
            is JavaParameterizedType -> fieldType.actualTypeArguments.map { it.toErasure() }
            else -> listOf(fieldType.toErasure())
        }

    private fun relationWithIdFieldMustBeReadOnlyOrMapsId(): ArchCondition<JavaField> =
        object : ArchCondition<JavaField>("be read-only if a matching <name>Id field exists, or use @MapsId") {
            override fun check(item: JavaField, events: ConditionEvents) {
                val owner = item.owner
                val associationName = item.name.removePrefix("_")
                val idFieldName = associationName + "Id"

                val hasIdField = owner.allFields.any { it.name == idFieldName }
                if (!hasIdField) return

                val hasMapsId = item.getAnnotationOfTypeOrNull(MapsId::class.java) != null

                val joinColumn: JoinColumn? = item.getAnnotationOfTypeOrNull(JoinColumn::class.java)
                val insertable = joinColumn?.insertable
                val updatable = joinColumn?.updatable
                val readOnlyJoin = (insertable == false && updatable == false)

                val ok = hasMapsId || readOnlyJoin

                val msg = buildString {
                    append("${owner.fullName}.${item.name} has matching FK field '$idFieldName'. ")
                    append("Use @MapsId OR @JoinColumn(insertable=false, updatable=false). ")
                    append("Actual: mapsId=$hasMapsId, joinColumn(insertable=$insertable, updatable=$updatable)")
                }

                events.add(SimpleConditionEvent(item, ok, msg))
            }
        }

    private fun bePrivateUnderscoreAndFinal(): ArchCondition<JavaField> =
        object : ArchCondition<JavaField>("be private, underscore-prefixed, and final (val)") {
            override fun check(item: JavaField, events: ConditionEvents) {
                val isPrivate = item.modifiers.contains(JavaModifier.PRIVATE)
                val underscore = item.name.startsWith("_")
                val isFinal = item.modifiers.contains(JavaModifier.FINAL)
                val ok = isPrivate && underscore && isFinal
                events.add(
                    SimpleConditionEvent(
                        item,
                        ok,
                        "${item.owner.fullName}.${item.name} should be private val and underscore-prefixed for collection backing fields"
                    )
                )
            }
        }

    private fun idClassValueMustImplementSerializable(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("have @IdClass value implement Serializable") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val ann: IdClass = item.getAnnotationOfType(IdClass::class.java) ?: return
                val idType: Class<*> = ann.value.java

                val ok = java.io.Serializable::class.java.isAssignableFrom(idType)
                events.add(
                    SimpleConditionEvent(
                        item,
                        ok,
                        "${item.fullName} uses @IdClass(${idType.name}) but IdClass type must implement java.io.Serializable"
                    )
                )
            }
        }

    private fun notHaveModifier(mod: JavaModifier): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("not have modifier $mod") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val ok = !item.modifiers.contains(mod)
                if (!ok) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.fullName} must not be $mod"
                        )
                    )
                }
            }
        }

    private fun toOneMustDeclareJoinColumnOrMappedByOrMapsId(): ArchCondition<JavaField> =
        object : ArchCondition<JavaField>("declare join strategy (@JoinColumn/@JoinTable/@MapsId or mappedBy)") {
            override fun check(item: JavaField, events: ConditionEvents) {
                val isManyToOne = item.getAnnotationOfTypeOrNull(ManyToOne::class.java) != null
                val oneToOne = item.getAnnotationOfTypeOrNull(OneToOne::class.java)

                if (!isManyToOne && oneToOne == null) return

                val hasJoinColumn =
                    item.getAnnotationOfTypeOrNull(JoinColumn::class.java) != null ||
                            item.getAnnotationOfTypeOrNull(JoinColumns::class.java) != null ||
                            item.getAnnotationOfTypeOrNull(JoinTable::class.java) != null

                val hasMapsId = item.getAnnotationOfTypeOrNull(MapsId::class.java) != null
                val hasMappedBy = oneToOne?.mappedBy?.isNotBlank() == true

                val ok = hasJoinColumn || hasMapsId || hasMappedBy

                val msg = buildString {
                    append("${item.owner.fullName}.${item.name} must declare how it joins. ")
                    append("Expected: @JoinColumn/@JoinColumns/@JoinTable OR @MapsId OR (for @OneToOne) mappedBy. ")
                    append("Actual: join=$hasJoinColumn, mapsId=$hasMapsId, mappedBy=$hasMappedBy")
                }

                events.add(SimpleConditionEvent(item, ok, msg))
            }
        }

    private fun notBeKotlinDataClass(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("not be a Kotlin data class") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                // heuristic: data classes have copy() and component1()
                val methodNames = item.methods.map { it.name }.toSet()
                val looksLikeDataClass =
                    ("copy" in methodNames || methodNames.any { it.startsWith("copy$") }) &&
                            methodNames.any { it == "component1" || it.startsWith("component") }

                val ok = !looksLikeDataClass
                if (!ok) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.fullName} looks like a Kotlin data class (copy/componentN). Avoid data classes for JPA entities."
                        )
                    )
                }
            }
        }

    private fun oneToOneOwningSideMustBeLazy(): ArchCondition<JavaField> =
        object : ArchCondition<JavaField>("have fetch = LAZY on owning @OneToOne") {
            override fun check(item: JavaField, events: ConditionEvents) {
                val ann = item.getAnnotationOfTypeOrNull(OneToOne::class.java) ?: return

                val isInverse = ann.mappedBy.isNotBlank()
                if (isInverse) return // owning-side-only rule

                val fetch = ann.readEnumPropertyOrNull("fetch", FetchType::class.java) ?: FetchType.EAGER
                val ok = fetch == FetchType.LAZY

                events.add(
                    SimpleConditionEvent(
                        item,
                        ok,
                        "${item.owner.fullName}.${item.name} is owning @OneToOne(mappedBy is blank) and should specify fetch=LAZY (was $fetch)"
                    )
                )
            }
        }

    private fun oneToOneInverseSideMustNotSpecifyLazy(): ArchCondition<JavaField> =
        object : ArchCondition<JavaField>("not specify fetch = LAZY on inverse @OneToOne (mappedBy)") {
            override fun check(item: JavaField, events: ConditionEvents) {
                val ann = item.getAnnotationOfTypeOrNull(OneToOne::class.java) ?: return

                val isInverse = ann.mappedBy.isNotBlank()
                if (!isInverse) return

                val fetch = ann.readEnumPropertyOrNull("fetch", FetchType::class.java) ?: FetchType.EAGER

                // If they explicitly wrote LAZY on inverse side, that's misleading.
                val explicitlyLazy = fetch == FetchType.LAZY

                events.add(
                    SimpleConditionEvent(
                        item,
                        !explicitlyLazy,
                        "${item.owner.fullName}.${item.name} is inverse @OneToOne(mappedBy='${ann.mappedBy}') " +
                                "but specifies fetch=LAZY. This is usually ignored; remove it or document a proven strategy (enhancement/vendor-specific)."
                    )
                )
            }
        }

    // ---------------------------------------------------------------------------------------------
    // Small reflection helper: read enum properties from runtime annotations safely
    // ---------------------------------------------------------------------------------------------

    private fun <E : Enum<E>> Annotation.readEnumPropertyOrNull(propertyName: String, enumType: Class<E>): E? =
        try {
            val m = this.annotationClass.java.getMethod(propertyName)
            val value = m.invoke(this)
            enumType.cast(value)
        } catch (_: Exception) {
            null
        }

    private fun <A : Annotation> JavaField.getAnnotationOfTypeOrNull(annotationType: Class<A>): A? =
        try {
            getAnnotationOfType(annotationType)
        } catch (_: IllegalArgumentException) {
            null
        }
}
