@system @harness
Feature: Acceptance harness self-check

  Scenario: the harness wires Gherkin to step definitions
    Given the acceptance harness is wired
    Then it can execute a scenario
