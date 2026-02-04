package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import jakarta.persistence.*

/**
 * ArchUnit rules to guide refactors towards “clean” Kotlin + Spring Boot + JPA mappings.
 *
 * Intentionally strict; expected to fail during refactor.
 */
@AnalyzeClasses(
    packages = [ArchitecturePackages.MODEL_BASE],
    importOptions = [ImportOption.DoNotIncludeTests::class]
)
class JpaMappingArchTest {

    @ArchTest
    val toOneManyToOneMustBeLazy: ArchRule =
        fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areAnnotatedWith(ManyToOne::class.java)
            .should(haveFetchTypeLazyFromRuntimeAnnotation(ManyToOne::class.java))
            .because("@ManyToOne defaults to EAGER; prefer LAZY.")

    @ArchTest
    val toOneOneToOneMustBeLazy: ArchRule =
        fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areAnnotatedWith(OneToOne::class.java)
            .should(haveFetchTypeLazyFromRuntimeAnnotation(OneToOne::class.java))
            .because("@OneToOne defaults to EAGER; prefer LAZY.")

    @ArchTest
    val toOneAssociationsShouldUsePrivateUnderscoreBackingField: ArchRule =
        fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areAnnotatedWith(ManyToOne::class.java)
            .or().areAnnotatedWith(OneToOne::class.java)
            .should(bePrivateAndUnderscorePrefixed())
            .because("Use private underscore-backed fields for JPA relations to keep control/invariants.")

    @ArchTest
    val relationsWithSeparateIdColumnMustBeReadOnlyOrMapsId: ArchRule =
        fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areAnnotatedWith(ManyToOne::class.java)
            .or().areAnnotatedWith(OneToOne::class.java)
            .should(relationWithIdFieldMustBeReadOnlyOrMapsId())
            .because("If you keep both <name>Id + <name> relation, ensure a single source of truth.")

    @ArchTest
    val collectionAssociationsShouldBePrivateUnderscoreVals: ArchRule =
        fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areAnnotatedWith(OneToMany::class.java)
            .or().areAnnotatedWith(ManyToMany::class.java)
            .should(bePrivateUnderscoreAndFinal())
            .because("Keep mutable collections private; expose read-only views.")

    @ArchTest
    val idClassTypesMustImplementSerializable: ArchRule =
        classes()
            .that().areAnnotatedWith(IdClass::class.java)
            .should(idClassValueMustImplementSerializable())
            .because("JPA requires IdClass types to be Serializable.")

    @ArchTest
    val noLombokInModel: ArchRule =
        noClasses()
            .that().resideInAPackage(ArchitecturePackages.MODEL)
            .should().dependOnClassesThat().resideInAnyPackage("lombok..")
            .because("Lombok in Kotlin entities is usually a smell; prefer Kotlin-native patterns.")

    // ---------------------------------------------------------------------------------------------
    // Conditions
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

    private fun relationWithIdFieldMustBeReadOnlyOrMapsId(): ArchCondition<JavaField> =
        object : ArchCondition<JavaField>("be read-only if a matching <name>Id field exists, or use @MapsId") {
            override fun check(item: JavaField, events: ConditionEvents) {
                val owner = item.owner
                val associationName = item.name.removePrefix("_")
                val idFieldName = associationName + "Id"

                val hasIdField = owner.allFields.any { it.name == idFieldName }
                if (!hasIdField) return

                val hasMapsId = item.getAnnotationOfType(MapsId::class.java) != null

                val joinColumn: JoinColumn? = item.getAnnotationOfType(JoinColumn::class.java)
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

    // ---------------------------------------------------------------------------------------------
    // Small reflection helper: read enum properties from runtime annotations safely
    // ---------------------------------------------------------------------------------------------

    private fun <E : Enum<E>> Annotation.readEnumPropertyOrNull(
        propertyName: String,
        enumType: Class<E>
    ): E? {
        return try {
            val m = this.annotationClass.java.getMethod(propertyName)
            val value = m.invoke(this)
            enumType.cast(value)
        } catch (_: Exception) {
            null
        }
    }
}
