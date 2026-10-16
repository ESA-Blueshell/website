@system @contributions
Feature: Payment emails

  Twice a year the treasurer asks the members who owe a contribution for it. A member who
  pays by direct debit is collected from by an incasso run instead, so they are sent a
  reminder only when the treasurer ticks them on purpose. No email quotes an amount without
  the reason that amount applies, and the association keeps a record of every asking,
  because the question a treasurer answers later is who was asked and when.

  Background:
    Given a board member signed in to the user manager
    And a contribution period they can send payment emails for

  Scenario: A member who pays by transfer is asked to pay what they owe
    Given a member who pays by transfer
    When they send the payment emails
    Then that member receives a contribution reminder
    And it states the full-year fee and what it comes to
    And it says where to transfer the money

  Scenario: A member who pays by direct debit is left out unless ticked
    Given a member who pays by transfer
    And a member who pays by direct debit
    When they send the payment emails
    Then the member who pays by direct debit receives no payment email

  Scenario: A member who pays by direct debit and is ticked on purpose is asked to transfer
    Given a member who pays by direct debit
    When they tick that member in and send the payment emails
    Then that member receives a contribution reminder
    And they are not told that anything will be taken from their account

  Scenario: A member who owes no contribution is never written to
    Given a member who pays by transfer
    And an honorary member among the selected
    When they send the payment emails
    Then the honorary member receives no payment email

  Scenario: A fee the treasurer chooses is the fee the email states
    Given a member who pays by transfer
    When they send the payment emails charging that member the alumni fee
    Then that member receives a contribution reminder
    And it states the alumni fee and what it comes to

  Scenario: Chasing a member is recorded each time
    Given a member who pays by transfer
    When they send the payment emails
    And they send the payment emails
    Then that member has been asked twice for this period
