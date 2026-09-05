@system @recovery
Feature: Recovery emails

  A board member sends the emails that let somebody into their account: the activation
  for an account that signed itself up, the activation for one the board created, and the
  password reset. Each can be read before it is sent.

  A preview is a render and nothing else. It issues no token, because a recovery link is
  a credential and one that exists is one that can be used, and it sends nothing, so
  reading an email has no effect on the person it describes. It is also somebody's name
  and address, so only a person who may email that account may read it.

  Which purposes the endpoints refuse, and with what, is specified by
  RecoveryControllerIT.

  Signing up issues a user activation link and sends its email, so a fresh account already
  holds one of each. The scenarios below say so where it matters and count from there.

  Background:
    Given a board member signed in to the recovery manager

  Scenario: Reading the activation email before sending it
    Given an account that has not been activated
    When they preview the "USER_ACTIVATION" email
    Then the preview is returned
    And the preview is addressed to that account
    And the preview link carries no token

  Scenario: Previewing issues no link
    Given an account that has not been activated
    When they preview the "USER_ACTIVATION" email
    And they preview the "MEMBER_ACTIVATION" email
    Then no link was issued

  Scenario: Previewing sends nothing
    Given an account that has not been activated
    When they preview the "USER_ACTIVATION" email
    Then no email was sent

  Scenario: The member activation is a different email from the user activation
    Given an account that has not been activated
    When they preview the "USER_ACTIVATION" email
    And they preview the "MEMBER_ACTIVATION" email
    Then the two previews differ

  Scenario: Reading the password reset email of an active account
    Given an account that has been activated
    When they preview the "PASSWORD_RESET" email
    Then the preview is returned
    And the preview link carries no token

  Scenario: Somebody who may not email the account cannot read its recovery email
    Given an account that has not been activated
    And a member signed in who may not email that account
    When they preview the "USER_ACTIVATION" email
    Then they are not shown the email

  Scenario: The kind asked for is the kind sent, not the kind outstanding
    Given an account that has not been activated
    And it has 1 outstanding "USER_ACTIVATION" link
    And it has no outstanding "MEMBER_ACTIVATION" link
    When they resend the "MEMBER_ACTIVATION" email
    Then the send is accepted
    And the account has 1 outstanding "MEMBER_ACTIVATION" link
    And an email was sent

  Scenario: Resending retires the link it replaces
    Given an account that has not been activated
    And it has 1 outstanding "USER_ACTIVATION" link
    When they resend the "USER_ACTIVATION" email
    Then the send is accepted
    And the account has 1 outstanding "USER_ACTIVATION" link

  Scenario: Resending leaves a link of the other kind alone
    Given an account that has not been activated
    And it has 1 outstanding "USER_ACTIVATION" link
    When they resend the "MEMBER_ACTIVATION" email
    Then the send is accepted
    And the account has 1 outstanding "USER_ACTIVATION" link
    And the account has 1 outstanding "MEMBER_ACTIVATION" link

  Scenario: An account that is already active is sent no activation
    Given an account that has been activated
    When they resend the "USER_ACTIVATION" email
    Then the send is accepted
    And no email was sent
