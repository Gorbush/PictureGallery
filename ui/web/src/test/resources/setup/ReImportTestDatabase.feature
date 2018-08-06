Feature: Feature to cleanup and rebuild GalleryMineTest database

  Scenario: Cleanup database
    When check if test database
    Then database cleanup all
    When run import for test folder
    When wait import request id=importRequestId become APPROVING for 300 seconds
    And print response.body
    When run forced approve for import request id=importRequestId
    And wait import request id=importRequestId become APPROVAL_COMPLETE for 300 seconds
    And print response.body
