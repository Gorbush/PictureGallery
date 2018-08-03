Feature: Feature to cleanup and rebuild GalleryMineTest database

  Scenario: Cleanup database
    When check if test database
    And database cleanup all
    Then run import for test folder
    And wait import request become APPROVING for 30 seconds
    And response key body.list.totalElements equal to 10
