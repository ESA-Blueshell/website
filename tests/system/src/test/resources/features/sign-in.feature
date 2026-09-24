@system @sign-in
Feature: How long a sign-in lasts

  A sign-in is a record the api holds. It ends thirty days after it began however much it
  is used, after fourteen days unused, and when the password is reset.

  @moves-clock
  Scenario: A sign-in left alone ends after fourteen days
    Given a member who is signed in
    When fifteen days pass without them using the site
    Then their sign-in is refused

  @moves-clock
  Scenario: A sign-in used every day still ends after thirty days
    Given a member who is signed in
    When they use the site every day for thirty-one days
    Then their sign-in is refused

  Scenario: A password reset ends every sign-in
    Given a member who is signed in
    When they reset their password through the emailed link
    Then their sign-in is refused
