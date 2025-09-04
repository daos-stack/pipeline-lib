// vars/getSummaryStage.groovy

/**
 * getSummaryStage.groovy
 *
 * Get a functional test stage in scripted syntax.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      name             name of the stage
 *      docker_filename  docker filename
 *      script_stashes   list of stash names to access with runScriptWithStashes
 *      script_name      shell script to run with runScriptWithStashes
 *      script_label     label to use when running the script in runScriptWithStashes
 *      artifacts        artifacts to archive
 *      job_status       Map of status for each stage in the job/build
 * @return a scripted stage to run in a pipeline
 */

List call(Map kwargs = [:]) {
    String name = kwargs.get('name', 'Unknown Summary Stage')
    String docker_filename = kwargs.get('docker_filename', 'utils/docker/Dockerfile.el.8')
    List script_stashes = kwargs.get('script_stashes', [])
    String script_name = kwargs.get('script_name', 'ci/functional_test_summary.sh')
    String script_label = kwargs.get('script_label', 'Generate Functional Test Summary')
    String artifacts = kwargs.get('artifacts', 'unknown_test_summary/*')
    Map job_status = kwargs.get('job_status', [:])

    return {
        stage("${name}") {
            agent {
                dockerfile {
                    filename docker_filename
                    label 'docker_runner'
                    additionalBuildArgs dockerBuildArgs(add_repos: false)
                }
                try {
                    job_step_update(
                        job_status,
                        name,
                        runScriptWithStashes(
                            stashes: script_stashes,
                            script: script_name,
                            label: script_label
                        )
                    )
                }
                finally {
                    always {
                        archiveArtifacts(artifacts: artifacts, allowEmptyArchive: false)
                        job_status_update(job_status, name)
                    }
                }
            }
        }
    }
}
