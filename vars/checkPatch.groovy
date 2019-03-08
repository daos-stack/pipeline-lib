// vars/checkPatch.groovy

/**
 * checkPatch.groovy
 *
 * checkPatch variable
 */

/**
 * Method to check the status of a patch and notify GitHub of the results
 * 
 * @param config Map of parameters passed.
 *
 * config['ignored_files'] is colon delimited string of files not to check.
 * config['review_creds'] is the credentials needed for a code review.
 * config['branch'] is the code_review repo branch to use.
 *
 */
def call(Map config = [:]) {

    if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
      println "Jenkins not configured to notify github of builds."
      return
    }

    if (env.CHANGE_ID == null) {
      println "This was not triggered by a pull request."
      return
    }

    checkoutScm withSubmodules: true
    def branch = 'master'
    if (config['branch']) {
        branch = config['branch']
    }

    def ignored_files="code_review/checkpatch.pl"
    if (config['ignored_files']) {
        ignored_files += ":" + config['ignored_files']
    }

    // Need the jenkins module to do linting
    checkoutScm url: 'https://github.com/daos-stack/code_review.git',
                checkoutDir: 'code_review',
                branch: branch

    githubNotify credentialsId: 'daos-jenkins-commit-status',
                 description: env.STAGE_NAME,
                 context: "pre-build" + "/" + env.STAGE_NAME,
                 status: "PENDING"

    int rc = 1
    def script = 'CHECKPATCH_IGNORED_FILES="' + ignored_files + '"' + \
                 ' code_review/jenkins_github_checkwarn.sh'

    if (config['review_creds']) {
      withCredentials([[$class: 'UsernamePasswordMultiBinding', 
                      credentialsId: config['review_creds'],
                      usernameVariable: 'GH_USER',
                      passwordVariable: 'GH_PASS']]) {
        rc = sh(script: script, label: env.STAGE_NAME, returnStatus: true)
      }
    } else {
      // Alternate method using username/password
      script = 'GH_USER="' + config['user'] + '"' + \
        ' GH_PASS="' + config['password'] + '" ' + script
      rc = sh(script: script, label: env.STAGE_NAME, returnStatus: true)
    }

    // All of this really should be done in the post section of the main
    // Jenkinsfile but it cannot due to
    // https://issues.jenkins-ci.org/browse/JENKINS-39203
    // Once that is fixed all of the below should be pushed up into the
    // Jenkinsfile post { stable/unstable/failure/etc. }
    def status = ''
    if (rc != 0) {
        status = "FAILURE"
    } else if (rc == 0) {
        status = "SUCCESS"
    }
    stepResult name: env.STAGE_NAME, context: "pre-build", result: status,
               ignore_failure: true
}
