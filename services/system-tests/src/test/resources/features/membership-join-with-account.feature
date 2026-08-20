@system @membership
Feature: Joining with an account you already have

  Someone who already has a usable account can become a member without proving
  anything about their email address again — signing in already required a
  confirmed one. This is the path taken by a person who made an account to sign
  up for events and decides to join later, and by anyone who lost their signup
  session part-way through and came back signed in.

  Background:
    Given an applicant with an account they can sign in to

  Scenario: An existing account holder becomes a member
    Given they have completed their member profile
    And they have an address on file
    When they submit their membership application
    Then they are a member

  Scenario: Joining with an existing account asks for no further confirmation
    Given they have completed their member profile
    And they have an address on file
    When they submit their membership application
    Then no further confirmation email is sent to them

  Scenario: Becoming a member grants the member role
    Given they have completed their member profile
    And they have an address on file
    When they submit their membership application
    Then they hold the MEMBER role
