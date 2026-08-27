@system @security
Feature: What a signup session may do

  A signup session lets somebody who is not signed in finish their own
  application, and nothing else. It authorises three writes against one account.
  If it ever satisfies a general permission check it becomes authority over the
  whole self-service surface, which is the failure this feature exists to catch.

  Specified by ADR-024.

  Background:
    Given an applicant who is not signed in
    And they have begun a membership signup

  Scenario Outline: A signup session is refused anything outside its scope
    When they use their signup session to "<attempt>"
    Then the request is refused

    Examples:
      | attempt                                          |
      | change the password on that account              |
      | read that account's details back                 |
      | submit an application through the signed-in route |
      | sign up for an event                             |

  Scenario: A retired signup session is refused
    Given they have saved their address during signup
    And they have accepted the membership conditions during signup
    And they have confirmed their email address
    When they save their address during signup
    Then the request is refused

  Scenario: The email address cannot be changed once it is confirmed
    Given they have confirmed their email address
    When they correct their email address
    Then the request is refused
