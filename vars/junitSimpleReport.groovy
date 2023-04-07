// vars/junitSimpleReport.groovy

  /**
   * junitSimpleReport step method
   *
   * Create and post a simple JUnit test report for a test suite with a
   * single test.
   *
   * @param config Map of parameters passed
   *
   * config['class']               Class of test, default 'simple'
   * config['errors']              count of errors, default 0
   * config['fails']               count of failures, default 0
   *                               Only one of errors or fails can be 1.
   * config['file']                JUnit file name.
   *                               Default suite + "_results.xml"
   * config['message']             Error or failure message.  Default ''
   * config['name']                JUnit testcase name.
   *                               Default 'simple'
   * config['suite']               Test suite to for JUnit file.
   *                               Default sanitizedStageName()
   * config['testdata']            Test data Default ''.
   */

void call(Map config = [:]) {
    int zero = 0
    int one = 1
    simple = 'simple'
    String jclass = config.get('class', simple)
    String jname = config.get('name', simple)
    String jsuite = config.get('suite', sanitizedStageName())
    String jfile = config.get('file', jsuite + '_results.xml')
    String je = config.get('errors', zero)
    String jf = config.get('fails', zero)
    String message = config.get('message', '')
    String testdata = config.get('testdata', '')
    boolean ignoreFailure = config.get('ingnoreFailure', false)
    String tresult = None
    // Enforce consistency
    if (jf > zero) {
        tresult = 'failure'
        jf = one
    }
    if (je > zero) {
        tresult = 'error'
        jf = zero
        if (je > one) {
            je = one
        }
    }
    String xml = """
<testsuite skip="0" failures="${jf}" errors="${je}" tests="1" name="${jsuite}">
  <testcase name="${jname}" classname="${jclass}">
"""
    if (tresult) {
        xml += "    <${tresult} message=\"${message}\" type=\"$tresult\">\n"
        if (testdata) {
            xml += "      <![CDATA[ ${testdata} ]]>\n"
        }
        xml += "    </${tresult}>"
    }
    xml += '''
  </testcase>
</testsuite>
'''
    writeFile(file: jfile, text: "${xml}")
    archiveArtifacts artifacts: jfile
    // groovylint-disable-next-line NoDouble
    double healthScale = 1.0
    if (ignoreFailure) {
        healthScale = 0.0
    }

    junit testResults: jfile,
          healthScaleFactor: healthScale
}
