@system @signin
Feature: Signing in

  Signing in is the gate everything behind the site sits behind, so it answers
  only two questions: is this account confirmed, and is this the right password.
  It never reveals whether an account exists.

  Scenario: A confirmed account can sign in
    Given an applicant with an account they can sign in to
    When they sign in
    Then they are signed in

  Scenario: An unconfirmed account cannot sign in
    Given an applicant who has registered an account
    When they sign in
    Then they are not signed in

  Scenario: Confirming the address is what opens the gate
    Given an applicant who has registered an account
    When they confirm their email address
    And they sign in
    Then they are signed in

  Scenario: The wrong password is refused
    Given an applicant with an account they can sign in to
    When they sign in with the wrong password
    Then they are not signed in
    And the refusal does not say whether the account exists

  Scenario: An account that does not exist is refused the same way
    Given an applicant with an account they can sign in to
    When somebody signs in as an account that does not exist
    Then they are not signed in
    And the refusal does not say whether the account exists

  Scenario: Signing in does not make anybody a member
    Given an applicant with an account they can sign in to
    When they sign in
    Then they are signed in
    And they are not a member
