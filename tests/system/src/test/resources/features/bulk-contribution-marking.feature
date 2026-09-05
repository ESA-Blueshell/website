@system @contributions
Feature: Bulk contribution marking

  A board member records contributions for many users at once from a table they
  loaded earlier, so the selection can name users the action cannot touch. Such a
  request is refused whole, because acting on part of a selection leaves the operator
  believing something happened that did not.

  What a refusal answers, and which reason it names, is specified by
  ContributionBulkControllerIT. What the association guarantees about the rows
  themselves is here.

  Background:
    Given a board member signed in to the user manager
    And a contribution period they can record against

  Scenario: Recording contributions for a selection of members
    Given two members with no contribution for the period
    When they mark the selection paid
    Then both members have a contribution for the period

  Scenario: Removing contributions for a selection of members
    Given two members with a contribution for the period
    When they mark the selection unpaid
    Then neither member has a contribution for the period

  Scenario: Recording twice records one contribution
    Given a member with no contribution for the period
    And they have marked the selection paid
    When they mark the selection paid again
    Then the member has exactly one contribution for the period

  Scenario: A selection naming a deleted user records nothing at all
    Given a member with no contribution for the period
    And a user in the selection who has since been deleted
    When they mark the selection paid
    Then the remaining member has no contribution for the period

  Scenario: A selection naming an honorary member records nothing at all
    Given a member with no contribution for the period
    And an honorary member in the selection
    When they mark the selection paid
    Then the remaining member has no contribution for the period
