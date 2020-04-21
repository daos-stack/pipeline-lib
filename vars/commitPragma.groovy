// vars/commitPgragma.groovy

/**
 * commitPgragma.groovy
 *
 * commitPgragma variablecommitPgragma
 */

/**
 * Method to get a commit pragma value
 *
 *
 * @param config Map of parameters passed.
 *
 * config['pragma']     Pragma to get the value of
 * config['def_val']    Value to return if not found
 */
def call(Map config = [:]) {

    def def_value = ''
    if (config['def_val']) {
        def_value = config['def_val']
    }
    def value = sh(script: '''b=$(git show -s --format=%B |
                                  sed -ne 's/^''' + config['pragma'] +
                           ''': *\\(.*\\)/\\1/p')
                              if [ -n "$b" ]; then
                                  echo "$b"
                              else
                                  echo "''' + def_value + '''"
                              fi''',
                returnStdout: true)
    return value.trim()

}