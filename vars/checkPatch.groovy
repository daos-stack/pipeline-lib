// vars/checkPatch.groovy

import com.intel.checkoutScm

def call(Map config) {

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
        doGenerateSubmoduleConfigurations: false,
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

    script = 'GH_USER="' + config['user'] + '"' + \
             ' GH_PASS="' + config['password'] + '"' + \
             ' CHECKPATCH_IGNORED_FILES="' + ignored_files + '"' + \
             ' jenkins/code_review/jenkins_github_checkwarn.sh'
    rc = sh(script: script, returnStatus: true)

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
