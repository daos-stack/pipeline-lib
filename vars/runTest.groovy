// vars/runTest.groovy

/**
 * runTest.groovy
 *
 * runTest pipeline step
 *
 */

def call(Map config = [:]) {
  /**
   * runTest step method
   *
   * @param config Map of parameters passed
   * @return None
   *
   * config['junit_files'] Junit files to look for errors in
   * config['script'] The test code to run
   * config['stashes'] Stashes from the build to unstash
   * config['failure_artifacts'] Artifacts to link to when test fails, if any
   * config['ignore_failure'] Whether a FAILURE result should post a failed step
   *
   * config['context'] Context name for SCM to identify the specific stage to
   *                   update status for.
   *                   Default is 'test/' + env.STAGE_NAME.
   *
   *  Important:
   *     The SCM status checking for passing may expect a specific name.
   *
   *     Matrix stages must override this setting to include matrix axes
   *     names to ensure a unique name is generated.
   *
   *     Or the default name has to be changed in a way that is compatible
   *     with a future Matrix implementation.
   *
   * config['flow_name'] Stage Flow name for logging.
   *                     Default is env.STAGE_NAME.
   *                     For sh steps, the stage flow name is the label
   *                     assigned to the shell script.
   *
   * config['description']  Description to report for SCM status.
   *                        Default env.STAGE_NAME.
   */

    // Todo
    // This routine is not "MATRIX" safe as it has an scmNOTIFY context
    // that only has env.STAGE_NAME.
    // The stepResult step is also using context differently than scmNotify
    // This has to be change here and in the
    // github expectations at the same time to also include any Matrix
    // environment variables.

    def context = config.get('context', 'test/' + env.STAGE_NAME)
    def description = config.get('description', env.STAGE_NAME)
    def flow_name = config.get('flow_name', env.STAGE_NAME)

    println("#### runTest calling jobStatus")
    jobStatus('JUNIT_failure', 'Entered runTest')
    println("#### runTest returned from jobStatus")

    dir('install') {
        deleteDir()
    }
    if (config['stashes']) {
        config['stashes'].each {
            unstash it
        }
    }

    def ignore_failure = false
    if (config['ignore_failure']) {
        ignore_failure = true
    }

    scmNotify description: description,
              context: context,
              status: "PENDING"

    // We really shouldn't even get here if $NO_CI_TESTING is true as the
    // when{} block for the stage should skip it entirely.  But we'll leave
    // this for historical purposes
    def script = '''skipped=false
                    if [ "''' + env.NO_CI_TESTING + '''" == 'true' ]; then
                        skipped=true
                    fi
                    if git show -s --format=%B | grep "^Skip-test: true"; then
                        skipped=true
                    fi
                    if ${skipped}; then
                        # cart
                        testdir1="install/Linux/TESTING/testLogs"
                        testdir2="${testdir1}_valgrind"
                        # daos
                        testdir3="src/tests/ftest/avocado/job-results"
                        mkdir -p "${testdir1}"
                        mkdir -p "${testdir2}"
                        mkdir -p "${testdir3}"
                        touch "${testdir1}/skipped_tests"
                        touch "${testdir2}/skipped_tests"
                        touch "${testdir3}/skipped_tests"
                        exit 0
                    fi\n''' + config['script']
    if (config['failure_artifacts']) {
        script += '''\nset +x\necho -n "Test artifacts can be found at: "
                     echo "${JOB_URL%/job/*}/view/change-requests/job/$BRANCH_NAME/$BUILD_ID/artifact/''' +
                          config['failure_artifacts'] + '"'
    }

    def cb_result = currentBuild.result

    int rc = 0
    rc = sh(script: script, label: flow_name, returnStatus: true)

    if (cb_result != currentBuild.result) {
      println "The runTest script changed build result to " +
              "${currentBuild.result}."
    }

    // All of this really should be done in the post section of the main
    // Jenkinsfile but it cannot due to
    // https://issues.jenkins-ci.org/browse/JENKINS-39203
    // Once that is fixed all of the below should be pushed up into the
    // Jenkinsfile post { stable/unstable/failure/etc. }
    def status = "SUCCESS"
    if (rc != 0) {
        status = "FAILURE"
    } else if (rc == 0) {
        def test_failure = false
        def test_error = false
        if (config['junit_files']) {
            def filesList = []
            config['junit_files'].split().each {
                filesList.addAll(findFiles(glob: it))
            }
            if (filesList) {
                String junit_xml = filesList.collect{"'" + it + "'"}.join(' ')
                if (sh(label: 'Check junit xml files for errors',
                       script: 'grep "<error " ' + junit_xml,
                       returnStatus: true) == 0) {
                    status = "FAILURE"
                    println "Found at least one error in the Junit files."
                } else if (sh(label: 'Check junit xml files for failures',
                              script: 'grep "<failure " ' + junit_xml,
                              returnStatus: true) == 0) {
                    status = "UNSTABLE"
                    println "Found at least one failure in the junit files."
                }
                if (junit_xml.indexOf("pipeline-test-failure.xml") > -1) {
                    test_failure = true
                } else if (junit_xml.indexOf("pipeline-test-error.xml") > -1) {
                    test_error = true
                }
            }
        }
        // If we are testing this library, make sure the result is as expected
        if (test_failure || test_error) {
            def expected_status
            if (test_failure) {
                expected_status = "UNSTABLE"
            } else if (test_error) {
                expected_status = "FAILURE"
            }
            if (status == expected_status) {
                echo "Expected status ${status} found"
                status = "SUCCESS"
            } else {
                // and fail the step if it's not
                echo "Expected status ${expected_status} not found.  status == ${status}"
                status = "UNSTABLE"
            }
        }
    }

    stepResult name: description,
               context: context,
               flow_name: flow_name,
               result: status,
               junit_files: config['junit_files'],
               ignore_failure: ignore_failure

    if (status != 'SUCCESS') {
        if (ignore_failure) {
            catchError(stageResult: 'UNSTABLE',
                       buildResult: 'SUCCESS') {
                error(env.STAGE_NAME + " failed: " + rc)
            }
	    } else {
	        error(env.STAGE_NAME + " failed: " + rc)
	    }
    }

}
