Feature: Test Main Controller methods

  Scenario: Test list Folders of gallery
    When api call /listFolders
    Then response status code is 200
    And response key body.status equal to 200
    And response key body.list.size equal to 10
