@system @membership
Feature: Membership eligibility

  A membership represents a declared obligation to the association, so it is
  only created for an applicant whose account carries every fact the application
  needs. Submitting twice does not join twice.

  Background:
    Given an applicant with an account they can sign in to

  Scenario Outline: An incomplete application is refused
    Given their application is missing "<missing fact>"
    When they submit their membership application
    Then their application is refused
    And they are not a member

    Examples:
      | missing fact           |
      | their member profile   |
      | their address          |

  Scenario: Submitting the same application twice does not join twice
    Given they have completed their member profile
    And they have an address on file
    And they have submitted their membership application
    When they submit their membership application again
    Then they have exactly one membership
