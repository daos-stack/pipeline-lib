// vars/publishToRepository.groovy

/**
 * run.groovy
 *
 * Wrapper for publishToRepositorySystem.
 * 
 * The publishToRepositorySystem must be provided as a shared
 * groovy library local to the running Jenkins for this routine.
 *
 * If it is not provided this routine will not do anything.
 *
 *
 * @param config Map of parameters passed
 * @return none
 *
 * config['arch']           Architecture, default 'x86_64'.
 * config['maturity']       Maturity level: eg: 'stable'|'dev'|'test'.
 *                          Default 'test'.
 * config['product']        Name of product.
 * config['repo_dir']       Directory to post artifacts from.
 * config['tech']           Distro/version code for reposiory eg: 'centos7'
 *                          'el8', 'leap15', etc.
 * config['test']           Test by creating a temporary repo and then
 *                          deleting it.
 *                          Default false.  Used to unit test this step.
 * config['type']           Type of repository.  Default 'hosted'.
 * config['download_dir']   If present, download the artifacts after the upload
 *                          to validate.
 *                          The publishToRepositorySystem step should dowload
 *                          the artifacts back to this directory and fail the
 *                          step if the contents differ.
 * config['publish_branch'] The branch to publish from.  Defaults to release/*
 *                          and master.
 */

def call(Map config = [:]) {
    if (env.REPOSITORY_URL == null) {
        println('No REPOSITORY_URL environment variable configured.')
        return
    }

    if (config['publish_branch']) {
        // Don't publish from PRs (unless testing)
        if (env.BRANCH_NAME != config['publish_branch'] && !config['test']) {
            return
        }
    } else {
        // Only publish on known landing (i.e. release) branches (unless testing)
        if (!env.BRANCH_NAME.startsWith("release/") &&
            env.BRANCH_NAME != 'master' &&
            !config['test']) {
                return
        }
    }

    try {
        return publishToRepositorySystem(config)
    } catch (java.lang.NoSuchMethodError e) {
        println('A REPOSITORY_URL environment variable was configured.')
        println('Could not find a publishToRepositorySystem step in' +
                ' a shared groovy library')
        return
    }
}
