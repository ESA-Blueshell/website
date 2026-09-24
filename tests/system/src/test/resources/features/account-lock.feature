@system @account-lock
Feature: Locking an account

  Every change to how somebody signs in is told to them with a link that locks the
  account. The link never undoes the change: the inbox it went to may be the one that
  was taken. An admin unlocks after hearing from the person.

  Scenario: A security notification carries a link that locks the account
    Given a member who has changed their password
    Then they receive a security notification with a lock link
    When they follow the lock link
    Then signing in is refused because the account is locked

  Scenario: An admin unlocks, and the person is sent a password reset
    Given a member whose account is locked
    When an admin unlocks it, giving a reason
    Then they receive a password reset email
