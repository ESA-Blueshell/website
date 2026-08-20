@system @account
Feature: Registering an account

  An account is unusable until the person behind it proves they can read the
  email address they registered with. Registering is also not joining: an
  account and a membership are separate things.

  Scenario: A new account cannot be used until the email address is confirmed
    Given an applicant who has registered an account
    Then their account is not yet usable
    And they are sent a confirmation email

  Scenario: Confirming the email address makes the account usable
    Given an applicant who has registered an account
    When they confirm their email address
    Then their account is usable

  Scenario: Registering and confirming does not make anybody a member
    Given an applicant who has registered an account
    When they confirm their email address
    Then they are not a member
