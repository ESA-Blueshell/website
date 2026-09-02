@system @contributions
Feature: Payment emails

  Twice a year the treasurer asks the members who owe a contribution for it. A member who
  pays by direct debit is told what will be taken and when; everybody else is asked to
  transfer. Neither email quotes an amount without the reason that amount applies, and the
  association keeps a record of every asking, because the question a treasurer answers later
  is who was asked and when.

  Background:
    Given a board member signed in to the user manager
    And a contribution period they can send payment emails for

  Scenario: A member who pays by transfer is asked to pay what they owe
    Given a member who pays by transfer
    When they send the payment emails
    Then that member receives a contribution reminder
    And it states the full-year fee and what it comes to
    And it says where to transfer the money

  Scenario: A member who pays by direct debit is told what will be taken
    Given a member who pays by direct debit
    When they send the payment emails
    Then that member receives an incasso notification
    And it states the full-year fee and what it comes to
    And it asks them to transfer nothing

  Scenario: One send reaches both kinds of member
    Given a member who pays by transfer
    And a member who pays by direct debit
    When they send the payment emails
    Then each member receives the email their payment method calls for

  Scenario: A member the treasurer moves receives the other email
    Given a member who pays by direct debit
    When they move that member onto the contribution reminder and send
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
