Feature: Test Sources Controller methods

  Scenario: Test list Folders of gallery
    When api call /listFolders test
    Then response status code is 200
    And response key response.body.status equal to 200
    And response key response.body.list.totalElements equal to 10
