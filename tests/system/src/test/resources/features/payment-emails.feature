@system @contributions
Feature: Payment emails

  A treasurer asks a selection of members for what they owe for one contribution period,
  in one send. Members on direct debit are told what will be taken and when; everybody
  else is asked to transfer by a date. Which one a member gets follows from their flag and
  can be overruled per member, because a mandate that failed this morning is chased by
  transfer and no flag knows that yet.

  Background:
    Given a board member signed in to the user manager
    And a contribution period they can send payment emails for

  Scenario: One confirmation writes both statements
    Given a member who pays by transfer
    And a member who pays by direct debit
    When they send the payment emails
    Then the request succeeds
    And the member who pays by transfer is sent a contribution reminder
    And the member who pays by direct debit is sent an incasso notification
    And 1 contribution reminder and 1 incasso notification are reported

  Scenario: A member moved onto the other email gets that one
    Given a member who pays by direct debit
    When they move that member onto the contribution reminder and send
    Then the request succeeds
    And the member who pays by direct debit is sent a contribution reminder
    And no incasso notification is recorded

  Scenario: An honorary member is never written to
    Given a member who pays by transfer
    And an honorary member in the selection
    When they send the payment emails
    Then the request succeeds
    And the honorary member is sent nothing
    And 1 member is reported as not written to

  Scenario: A member who has paid is left out until forcibly included
    Given a member who has already paid for the period
    When they send the payment emails
    Then the request succeeds
    And 1 member is reported as not written to
    When they forcibly include that member and send
    Then the request succeeds
    And 1 contribution reminder and 0 incasso notifications are reported

  Scenario: Ticking back in somebody the send still will not write to is refused
    Given an honorary member in the selection
    When they forcibly include that member and send
    Then the request is refused as a conflict
    And the refusal reports "NonRecipientForcedUserIds" against "forciblyIncludedUserIds"
    And the honorary member is sent nothing

  Scenario: Chasing a member records every ask
    Given a member who pays by transfer
    When they send the payment emails
    And they send the payment emails
    Then the request succeeds
    And that member has 2 contribution reminders recorded

  Scenario: An overridden fee type is what the record states
    Given a member who pays by transfer
    When they send the payment emails charging that member the alumni fee
    Then the request succeeds
    And the recorded contribution reminder states the alumni fee

  Scenario: A fee type naming somebody the send skips refuses the whole thing
    Given a member who pays by transfer
    And an honorary member in the selection
    When they send the payment emails charging the honorary member the alumni fee
    Then the request is refused as a conflict
    And the refusal reports "NonRecipientFeeTypeUserIds" against "feeTypeOverrides"
    And nothing is recorded for the period

  Scenario: An email chosen for somebody the send skips refuses the whole thing
    Given a member who pays by transfer
    And an honorary member in the selection
    When they move the honorary member onto the contribution reminder and send
    Then the request is refused as a conflict
    And the refusal reports "NonRecipientEmailKindUserIds" against "kindOverrides"
    And nothing is recorded for the period

  Scenario: An id naming nobody is refused rather than dropped
    Given a member who pays by transfer
    When they send the payment emails naming an id that was never a user
    Then the request is refused as a conflict
    And the refusal reports "UnknownUserIds" against "userIds"
    And the refusal names the id that was never a user
    And nothing is recorded for the period

  Scenario: Naming the same member twice is refused
    Given a member who pays by transfer
    When they send the payment emails naming that member twice
    Then the request is refused as a conflict
    And the refusal reports "DuplicateUserIds" against "userIds"
    And nothing is recorded for the period

  Scenario: Ticking back in somebody who is not in the selection is refused
    Given a member who pays by transfer
    When they tick back in an id that is not in the selection and send
    Then the request is refused as a conflict
    And the refusal reports "UnknownForcedUserIds" against "forciblyIncludedUserIds"
    And nothing is recorded for the period

  Scenario: A payment request is refused without the date it promises
    Given a member who pays by transfer
    When they send the payment emails without a payment due date
    Then the request is refused as invalid
    And the refusal reports "DateRequired" against "paymentDueDate"
    And nothing is recorded for the period

  Scenario: A date that has already passed is refused against its own field
    Given a member who pays by transfer
    When they send the payment emails with a payment due date that has passed
    Then the request is refused as invalid
    And the refusal reports "Future" against "paymentDueDate"
    And nothing is recorded for the period

  Scenario: A date far beyond the period is refused, one shortly after it is not
    Given a member who pays by transfer
    When they send the payment emails with a payment due date long after the period
    Then the request is refused as invalid
    And the refusal reports "DateOutsideContributionPeriod" against "paymentDueDate"
    And nothing is recorded for the period
    When they send the payment emails with a payment due date shortly after the period
    Then the request succeeds
    And 1 contribution reminder and 0 incasso notifications are reported

  Scenario: A date nobody in the batch needs may be left out
    Given a member who pays by transfer
    When they send the payment emails without a debit date
    Then the request succeeds
    And 1 contribution reminder and 0 incasso notifications are reported

  Scenario: An empty selection is refused
    When they send the payment emails to nobody
    Then the request is refused as invalid

  Scenario: A member may not send payment emails
    Given a "MEMBER" signed in
    And a member who pays by transfer
    When they send the payment emails
    Then the request is forbidden
