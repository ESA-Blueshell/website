@system @account
Feature: Creating an account

  An account is unusable until the person behind it proves they can read the
  email address they registered with. Creating one is also not joining: an
  account and a membership are separate things.

  Everything typed on the way in stays correctable until the address is
  confirmed, because until then nobody has been able to use the account.

  Scenario: A new account cannot be used until the email address is confirmed
    Given an applicant who has registered an account
    Then their account is not yet usable
    And they are sent a confirmation email

  Scenario: Confirming the email address makes the account usable
    Given an applicant who has registered an account
    When they confirm their email address
    Then their account is usable

  Scenario: Creating an account does not make anybody a member
    Given an applicant who has registered an account
    When they confirm their email address
    Then they are not a member

  Scenario: The confirmation email can be asked for again
    Given an applicant who has registered an account
    When they ask for the confirmation email again
    Then another confirmation email is sent to them
    And only the most recent confirmation link works

  Scenario: Details can be corrected before the address is confirmed
    Given an applicant who is not signed in
    And they have begun a membership signup
    When they change their first name to "Corrected"
    Then their first name is "Corrected"
    And their account is not yet usable

  Scenario: Details are no longer the signup session's to change once confirmed
    Given an applicant who is not signed in
    And they have begun a membership signup
    And they have confirmed their email address
    When they change their first name to "TooLate"
    Then the request is refused
