@system @membership
Feature: Joining without an account yet

  A new applicant fills the whole form in one sitting. Two facts make them a
  member and they arrive independently — the email address gets confirmed, and
  the application gets submitted — so whichever lands second is the one that
  starts the membership. Neither one alone does.

  Specified by ADR-024 and ADR-025; see docs/flows/membership-signup/README.md.

  Background:
    Given an applicant who is not signed in

  Scenario: The application is submitted first, then the email confirmed
    Given they have begun a membership signup
    And they have saved their address during signup
    When they accept the membership conditions during signup
    Then they are not a member
    When they confirm their email address
    Then they are a member

  Scenario: The email is confirmed first, then the application submitted
    Given they have begun a membership signup
    When they confirm their email address
    Then their account is usable
    And they are not a member
    And their signup session is still usable
    When they save their address during signup
    And they accept the membership conditions during signup
    Then they are a member

  Scenario: Confirming the email address does not retire the signup session
    Given they have begun a membership signup
    When they confirm their email address
    Then their signup session is still usable

  Scenario: An application without a confirmed address never becomes a membership
    Given they have begun a membership signup
    And they have saved their address during signup
    When they accept the membership conditions during signup
    Then they are not a member
    And their account is not yet usable

  Scenario: Becoming a member retires the signup session
    Given they have begun a membership signup
    And they have saved their address during signup
    And they have accepted the membership conditions during signup
    When they confirm their email address
    Then they are a member
    And their signup session is retired

  Scenario: A mistyped email address can be corrected before confirming
    Given they have begun a membership signup
    And they have saved their address during signup
    And they have accepted the membership conditions during signup
    When they correct their email address
    Then the confirmation email goes to the corrected address
    And their address and their acceptance are still on file

  Scenario: An expired signup session cannot be used
    Given they have begun a membership signup
    And their signup session has expired
    When they save their address during signup
    Then the request is refused
