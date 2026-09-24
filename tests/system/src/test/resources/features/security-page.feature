@system @security-page
Feature: The security page

  The person sees where they are signed in and ends what they do not recognise.

  Scenario: Signing out everywhere else keeps this browser signed in
    Given a member who is signed in in two browsers
    When they sign out everywhere else from the first
    Then the first browser is still signed in and the second is not

  Scenario: Changing the password is told to the person with a lock link
    Given a member who has changed their password
    Then they receive a security notification with a lock link
