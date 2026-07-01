// vars/scriptedTestStage.groovy

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

/**
 * scriptedTestStage.groovy
 *
 * Get a test stage in scripted syntax.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      name                  test stage name
 *      runStage              whether or not to run the test stage
 *      pragmaSuffix          test stage commit pragma suffix, e.g. '-hw-medium'
 *      label                 test stage default cluster label
 *      testBranch            if specified, checkout sources from this branch before running tests
 *      jobStatus             Map of status for each stage in the job/build
 *      functionalTestArgs    Map of arguments to pass to functionalTest() for the stage
 *      unitTestArgs          Map of arguments to pass to unitTest() for the stage
 *      unitTestPostArgs      Map of arguments to pass to unitTestPost() for the stage
 *      testRpmArgs           Map of arguments to pass to testRpm() for the stage
 *      testRpmPostArgs       Map of arguments to pass to testRpmPost() for the stage
 *      archiveArtifactsArgs  Map of arguments to pass to archiveArtifacts() for the stage
 * @return a scripted stage to run in a pipeline
 */ 
Map call(Map kwargs = [:]) {
    // General parameters
    String name = kwargs.get('name', 'Unknown Functional Test Stage')
    Boolean runStage = kwargs.get('runStage', true) as Boolean
    String pragmaSuffix = kwargs.get('pragmaSuffix', '')
    String label = kwargs.get('label')
    String testBranch = kwargs.get('testBranch', '')
    Map jobStatus = kwargs.get('jobStatus', [:])

    // Functional test stage parameters
    Map functionalTestArgs = kwargs.get('functionalTestArgs', [:])

    // Unit Test stage parameters
    Map unitTestArgs = kwargs.get('unitTestArgs', [:])
    Map unitTestPostArgs = kwargs.get('unitTestPostArgs', [:])

    // Test RPM stage parameters
    Map testRpmArgs = kwargs.get('testRpmArgs', [:])
    Map testRpmPostArgs = kwargs.get('testRpmPostArgs', [:])

    // Artifact archiving stage parameters
    Map archiveArtifactsArgs = kwargs.get('archiveArtifactsArgs', [:])

    return {
        stage("${name}") {
            if (!runStage) {
                println("[${name}] Stage skipped by runStage=false")
                Utils.markStageSkippedForConditional("${name}")
                return
            }

            if (pragmaSuffix) {
                label = cachedCommitPragma("Test-label${pragmaSuffix}", label)
            }

            node(label) {
                // Ensure access to any branch provisioning scripts exist
                if (testBranch) {
                    println("[${name}] Check out '${testBranch}' from version control")
                    checkoutScm(
                        url: 'https://github.com/daos-stack/daos.git',
                        branch: testBranch,
                        withSubmodules: false,
                        pruneStaleBranch: true)
                } else {
                    println("[${name}] Check out branch from version control")
                    checkoutScm(pruneStaleBranch: true)
                }

                try {
                    if (functionalTestArgs) {
                        println("[${name}] Running functionalTest() on ${label} with tags=${tags}")
                        jobStatusUpdate(jobStatus, name, functionalTest(functionalTestArgs))
                    } else if (unitTestArgs) {
                        println("[${name}] Running unitTest() on ${label} with tags=${tags}")
                        jobStatusUpdate(jobStatus, name, unitTest(unitTestArgs))
                    } else if (testRpmArgs) {
                        println("[${name}] Running testRpm() on ${label} with tags=${tags}")
                        jobStatusUpdate(jobStatus, name, testRpm(testRpmArgs))
                    } else {
                        println("[${name}] No test arguments provided!")
                    }
                } finally {
                    if (functionalTestArgs) {
                        println("[${name}] Running functionalTestPostV2()")
                        functionalTestPostV2()
                    }
                    if (unitTestPostArgs) {
                        println("[${name}] Running unitTestPost()")
                        unitTestPost(unitTestPostArgs)
                    }
                    if (archiveArtifactsArgs) {
                        println("[${name}] Running archiveArtifacts()")
                        archiveArtifacts(archiveArtifactsArgs)
                    }
                    if (testRpmArgs) {
                        println("[${name}] Running archiveArtifacts()")
                        // Extract first node from comma-delimited list
                        String firstNode = env.NODELIST.split(',')[0].trim()
                        sh label: 'Fetch and stage artifacts',
                        script: "hostname; ssh -i ci_key jenkins@ ${firstNode}" +
                                " ls -ltar /tmp; mkdir -p \"${name}\" && " +
                                "scp -i ci_key jenkins@${firstNode}" +
                                ':/tmp/{{suite_dmg,daos_{server_helper,{control,agent}}}.log,daos_server.log.*}' +
                                " \"${name}/\""
                        archiveArtifacts(artifacts: "${name}/**")
                    }
                    jobStatusUpdate(jobStatus, name)
                }
            }
            println("[${name}] Finished with ${jobStatus}")
        }
    }
}
