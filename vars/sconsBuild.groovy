// vars/sconsBuild.groovy

import com.intel.checkoutScm

def call(Map config) {

    def c = new com.intel.checkoutScm()
    c.checkoutScmWithSubmodules()

    githubNotify credentialsId: 'daos-jenkins-commit-status',
                 description: env.STAGE_NAME,
                 context: "build" + "/" + env.STAGE_NAME,
                 status: "PENDING"

    script = '''if git show -s --format=%B | grep "^Skip-build: true"; then
                    exit 0
                fi
                scons -c
                # scons -c is not perfect so get out the big hammer
                rm -rf _build.external install build
                SCONS_ARGS="--update-prereq=all --build-deps=yes USE_INSTALLED=all install"
                # the config cache is unreliable so always force a reconfig
                # with "--config=force"
                if ! scons --config=force $SCONS_ARGS; then
                    rc=\${PIPESTATUS[0]}
                    echo "$SCONS_ARGS failed"
                    cat config.log || true
                    exit \$rc
                fi'''
    rc = sh(script: script, returnStatus: true)

    // All of this really should be done in the post section of the main
    // Jenkinsfile but it cannot due to
    // https://issues.jenkins-ci.org/browse/JENKINS-39203
    // Once that is fixed all of the below should be pushed up into the
    // Jenkinsfile post { stable/unstable/failure/etc. }
    if (rc != 0) {
        stepResult name: env.STAGE_NAME, context: "build", result: "FAILURE"
    } else if (rc == 0) {
        stepResult name: env.STAGE_NAME, context: "build", result: "SUCCESS"
    }
}
