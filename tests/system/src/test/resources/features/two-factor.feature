@system @two-factor
Feature: Two-factor authentication

  Two-factor is optional for members and required for anybody holding a granted role.
  With it on, a right password is only a challenge; the code from the authenticator app
  is what opens a sign-in, and no code opens one twice.

  Scenario: With two-factor on, the password alone is only a challenge
    Given a member who has set up two-factor
    When they sign in with their password
    Then they are asked for a code
    And no sign-in has been opened

  Scenario: The code from their authenticator app signs them in
    Given a member who has set up two-factor
    When they sign in with their password and a code from their authenticator app
    Then they are signed in with two-factor

  Scenario: A code cannot be used twice
    Given a member who has set up two-factor
    And they have signed in with a code
    When they sign in again with that same code
    Then the code is refused

  Scenario: A granted role without two-factor opens nothing
    Given a board member who has not set up two-factor
    When they sign in
    Then they are signed in
    And they are told to set up two-factor first
    And the board's pages refuse them

  Scenario: After an admin resets two-factor, the password alone no longer gets in
    Given a member who has set up two-factor
    When an admin resets their two-factor
    Then they receive a re-enrolment link
    And signing in with their password alone is refused
    And signing in with their password and the re-enrolment link works

  Scenario: A backup code signs in once and never again
    Given a member who has set up two-factor and kept their backup codes
    When they sign in with their password and a backup code
    Then they are signed in
    When they sign in again with that same backup code
    Then the code is refused

  Scenario: A trusted browser skips the code, and only that browser
    Given a member who has set up two-factor
    When they sign in with a code and trust this browser
    Then signing in again from that browser needs no code
    And signing in from another browser still asks for a code

  Scenario: A granted role sets up two-factor straight after signing in, without the password again
    Given a board member who has not set up two-factor
    When they sign in
    And they set up two-factor on that sign-in without giving their password
    Then the board's pages open to them

  Scenario: Granting a role to somebody without two-factor signs them out everywhere
    Given a member who is signed in without two-factor
    When an admin grants them the board role
    Then their sign-in has ended
    And the role email tells them to sign in again to set up two-factor
    And their next sign-in sets up two-factor without their password
