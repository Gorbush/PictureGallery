Feature: Test Sources Controller methods

  Scenario: Test findPath of gallery - root
    When api call /findPath ''
    Then response status code is 200
    And print response.body
    And response key response.body.status equal to 200
    And response key response.body.list.numberOfElements equal to 2

  Scenario: Test findPath of gallery - jpg
    When api call /findPath 'jpg'
    Then response status code is 200
    And print response.body
    And response key response.body.status equal to 200
    And response key response.body.list.numberOfElements equal to 1
    And response key response.body.list.content[0].name equal to 'imagetestsuite'

  Scenario: Test listFolders of gallery - root
    When api call /listFolders ''
    Then response status code is 200
    And print response.body
    And response key response.body.status equal to 200
    And response key response.body.list.numberOfElements equal to 2

  Scenario: Test listFolders of gallery - jpg
    When api call /listFolders 'jpg'
    Then response status code is 200
    And print response.body
    And response key response.body.status equal to 200
    And response key response.body.list.numberOfElements equal to 1
    And response key response.body.list.content[0].name equal to 'ImageTestSuite'
