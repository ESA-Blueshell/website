@system @contributions
Feature: Bulk contribution marking

  A board member records contributions for many users at once from a table they
  loaded earlier, so the selection can name users the action cannot touch. Such a
  request is refused whole and names the offending ids, because acting on part of a
  selection leaves the operator believing something happened that did not.

  Background:
    Given a board member signed in to the user manager
    And a contribution period they can record against

  Scenario: Recording contributions for a selection of members
    Given two members with no contribution for the period
    When they mark the selection paid
    Then the request succeeds
    And both members have a contribution for the period
    And 2 rows are reported as applied

  Scenario: Removing contributions for a selection of members
    Given two members with a contribution for the period
    When they mark the selection unpaid
    Then the request succeeds
    And neither member has a contribution for the period
    And 2 rows are reported as applied

  Scenario: A member already paid is reported as unchanged rather than applied
    Given a member with a contribution for the period
    When they mark the selection paid
    Then the request succeeds
    And 0 rows are reported as applied
    And 1 row is reported as unchanged

  Scenario: Repeating a request does not record a second contribution
    Given a member with no contribution for the period
    And they have marked the selection paid
    When they mark the selection paid again
    Then the request succeeds
    And the member has exactly one contribution for the period

  Scenario: A selection naming a deleted user is refused with that id
    Given a member with no contribution for the period
    And a user in the selection who has since been deleted
    When they mark the selection paid
    Then the request is refused as a conflict
    And the refusal reports "UnknownUserIds" against "userIds"
    And the refusal names the deleted user
    And the remaining member has no contribution for the period

  Scenario: A selection naming an honorary member is refused with that id
    Given a member with no contribution for the period
    And an honorary member in the selection
    When they mark the selection paid
    Then the request is refused as a conflict
    And the refusal reports "HonoraryUserIds" against "userIds"
    And the remaining member has no contribution for the period

  Scenario: Every reason a selection was refused is reported together
    Given an honorary member in the selection
    And a user in the selection who has since been deleted
    When they mark the selection paid
    Then the request is refused as a conflict
    And the refusal reports both "UnknownUserIds" and "HonoraryUserIds"

  Scenario: A selection naming a period that no longer exists is refused
    Given a member with no contribution for the period
    And the contribution period has since been deleted
    When they mark the selection paid
    Then the request is refused as a conflict
    And the refusal reports "UnknownContributionPeriodId" against "contributionPeriodId"

  Scenario: An empty selection is refused as a bad request
    When they mark an empty selection paid
    Then the request is refused as invalid

  Scenario Outline: A member without permission cannot record contributions
    Given a "<role>" signed in
    And a member with no contribution for the period
    When they mark the selection paid
    Then the request is forbidden

    Examples:
      | role   |
      | MEMBER |
