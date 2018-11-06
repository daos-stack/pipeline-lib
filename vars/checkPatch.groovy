// vars/checkPatch.groovy

/**
 * checkPatch.groovy
 *
 * checkPatch variable
 */

import com.intel.checkoutScm

/**
 * Method to check the status of a patch and notify GitHub of the results
 * 
 * @param config Map of parameters passed.
 *
 * config['ignored_files'] is colon delimited string of files not to check.
 * config['jenkins_review'] is the optional revision number to use.
 * config['review_creds'] is the credentials needed for a code review.
 *
 */
def call(Map config = [:]) {

    if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
      println "Jenkins not configured to notify github of builds."
      return
    }

    def c = new com.intel.checkoutScm()
    c.checkoutScmWithSubmodules()
    if (config['jenkins_review']) {
        rev_num = config['jenkins_review']
        branch = 'FETCH_HEAD'
        refspec = 'refs/changes/' + rev_num
    } else {
        branch = 'master'
        refspec = '+refs/heads/master:refs/remotes/origin/master'
    }

    ignored_files="code_review/checkpatch.pl"
    if (config['ignored_files']) {
        ignored_files += ":" + config['ignored_files']
    }

    // Need the jenkins module to do linting
    checkout([
        $class: 'GitSCM',
        branches: [[name: branch]],
        extensions: [[$class: 'RelativeTargetDirectory',
                              relativeTargetDir: 'jenkins']],
        submoduleCfg: [],
        userRemoteConfigs: [[
            credentialsId: 'bf21c68b-9107-4a38-8077-e929e644996a',
            refspec: refspec,
            url: 'ssh://review.hpdd.intel.com:29418/exascale/jenkins'
        ]]
    ])

    githubNotify credentialsId: 'daos-jenkins-commit-status',
                 description: env.STAGE_NAME,
                 context: "pre-build" + "/" + env.STAGE_NAME,
                 status: "PENDING"

    rc = 1
    script = 'CHECKPATCH_IGNORED_FILES="' + ignored_files + '"' + \
             ' jenkins/code_review/jenkins_github_checkwarn.sh'

    if (config['review_creds']) {
      withCredentials([[$class: 'UsernamePasswordMultiBinding', 
                      credentialsId: config['review_creds'],
                      usernameVariable: 'GH_USER',
                      passwordVariable: 'GH_PASS']]) {
        rc = sh(script: script, returnStatus: true)
      }
    } else {
      // Older method using username/password
      script = 'GH_USER="' + config['user'] + '"' + \
        ' GH_PASS="' + config['password'] + '"' + script
      rc = sh(script: script, returnStatus: true)
    }

    // All of this really should be done in the post section of the main
    // Jenkinsfile but it cannot due to
    // https://issues.jenkins-ci.org/browse/JENKINS-39203
    // Once that is fixed all of the below should be pushed up into the
    // Jenkinsfile post { stable/unstable/failure/etc. }
    if (rc != 0) {
        status = "FAILURE"
    } else if (rc == 0) {
        status = "SUCCESS"
    }
    stepResult name: env.STAGE_NAME, context: "pre-build", result: status
}
