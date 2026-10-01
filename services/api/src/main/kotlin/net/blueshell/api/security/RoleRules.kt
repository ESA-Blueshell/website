package net.blueshell.api.security

import org.springframework.security.access.prepost.PreAuthorize

/*
 * A rule that asks only which role somebody holds, read off the authorities the principal already
 * carries, inherited roles included. A rule that depends on the row being asked about, such as
 * whose sign-up or whose membership it is, stays a permission evaluator. See api ADR-014.
 */

/** Member or above. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('MEMBER')")
annotation class MemberOnly

/** Board or above. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('BOARD')")
annotation class BoardOnly

/** Admin or above. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAuthority('ADMIN')")
annotation class AdminOnly
