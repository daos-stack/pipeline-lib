// vars/checkPatch.groovy

import com.intel.checkoutScm

def call(Map config) {

    def c= new com.intel.checkoutScm()
    c.checkoutScmWithSubmodules()

    // Need the jenkins module to do linting
    checkout([
        $class: 'GitSCM',
        branches: [[name: 'master']],
        doGenerateSubmoduleConfigurations: false,
        extensions: [[$class: 'RelativeTargetDirectory',
                      relativeTargetDir: 'jenkins']],
        submoduleCfg: [],
        userRemoteConfigs: [[
            credentialsId: 'bf21c68b-9107-4a38-8077-e929e644996a',
            url: 'ssh://review.hpdd.intel.com:29418/exascale/jenkins'
        ]]
    ])

    sh "GH_USER=${config.user} GH_PASS=${config.password} \
        jenkins/code_review/jenkins_github_checkwarn.sh || true"
}
