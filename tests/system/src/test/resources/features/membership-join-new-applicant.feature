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

  Scenario: Details stay correctable after the application is in
    Given an applicant who is not signed in
    And they have begun a membership signup
    And they have saved their address during signup
    And they have accepted the membership conditions during signup
    When they change their first name to "Corrected"
    Then their first name is "Corrected"
    And their address is on file
    And they are not a member

  Scenario: The agreement stands once the application is in
    Given an applicant who is not signed in
    And they have begun a membership signup
    And they have saved their address during signup
    And they have accepted the membership conditions during signup
    When they confirm their email address
    Then they are a member
    And they have exactly one membership

  # Joining is the one moment nothing is owed yet, which is why this email may offer a
  # direct debit mandate as a way to pay rather than as an arrangement for later.
  Scenario: A new member is told what they owe and how to pay it
    Given a contribution period covering today
    And they have begun a membership signup
    And they have saved their address during signup
    And they have accepted the membership conditions during signup
    When they confirm their email address
    Then they are a member
    And they are told what they owe and how to pay it
    And they are given two weeks to pay

  # Otherwise the treasurer's next send reads as a first request, carrying a different
  # deadline than the one this member already has.
  Scenario: Asking a new member on joining is recorded like any other ask
    Given a contribution period covering today
    And they have begun a membership signup
    And they have saved their address during signup
    And they have accepted the membership conditions during signup
    When they confirm their email address
    Then the asking is on record

  Scenario: An applicant who has not finished joining is never asked to pay
    Given a contribution period covering today
    And they have begun a membership signup
    And they have saved their address during signup
    When they accept the membership conditions during signup
    Then they are not a member
    And they are not asked to pay anything
