// vars/configureBullseye.groovy

/**
 * configureBullseye.groovy
 *
 * Download and install bullseye.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      prefix          where to install bullseye
 * @return a scripted stage to run in a pipeline
 */
Map call(Map kwargs = [:]) {
    String prefix = kwargs.get('prefix', '/opt/BullseyeCoverage')
    Boolean download_only = kwargs.get('download_only', false)

    echo "[${env.STAGE_NAME}] Downloading bullseye"
    String tools_url = "${env.JENKINS_URL}job/daos-stack/job/tools/job/master/lastSuccessfulBuild"
    String bullseye_url = "${tools_url}/artifact/bullseyecoverage-linux.tar"
    httpRequest url: bullseye_url, httpMode: 'GET', outputFile: 'bullseye.tar'

    if(download_only) {
        return true
    }

    echo "[${env.STAGE_NAME}] Installing bullseye"
    return sh(label: 'Install bullseye',
            /* groovylint-disable-next-line GStringExpressionWithinString */
            script: '''mkdir -p bullseye
                       tar -C bullseye --strip-components=1 -xf bullseye.tar
                       pushd bullseye
                       ./install --quiet --key "'''+ env.BULLSEYE + '''" --prefix ''' + prefix +
                    '''popd
                       rm -rf bullseye.tar bullseye''',
            returnStatus: true) == 0
}
