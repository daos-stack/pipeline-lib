// vars/getTestTag.groovy


def call(Map commitPragmaMapping, List<String> paths, String defaultTag = 'pr') {

    def commitTags = getCommitTags() as Set

    def allTags = [] as Set
    allTags.addAll(commitTags)

    def ftestTagMap = new org.example.ftest.FtestTagMap(
        org.example.ftest.FtestTagMap.allFtestPythonFiles()
    )
    
    paths.each { path ->
        commitPragmaMapping.each { pattern, config ->
            if (path ==~ /${pattern}/) {
                
                def testTagConfig = config.get('test-tag', defaultTag)
                
                if (testTagConfig instanceof String) {
                    testTagConfig = [
                        tags: testTagConfig,
                        handler: 'direct'
                    ]
                }
                
                def handler = testTagConfig.handler
                
                if (handler == 'FtestTagMap') {
                    try {
                        allTags.addAll(ftestTagMap.minimalTags(path))
                    } catch (Exception e) {
                        allTags.addAll(testTagConfig.tags.split(' '))
                    }
                } else if (handler == 'direct') {
                    allTags.addAll(testTagConfig.tags.split(' '))
                } else {
                    error "Invalid handler: ${handler}"
                }
                
                if (testTagConfig.get('stop_on_match', config.get('stop_on_match', false))) {
                    return
                }
            }
        }
    }

    def validTags = ftestTagMap.uniqueTags()
    invalid = allTags - validTags
    if (invalid) {
        error "test-tag does not match any tests: ${invalid.join(', ')}"
    }
    
    return allTags.sort().join(' ')
}
