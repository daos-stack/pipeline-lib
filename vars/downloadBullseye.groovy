// vars/downloadBullseye.groovy

/**
 * downloadBullseye.groovy
 *
 * Download bullseye.
 *
 */
void call() {
    echo "[${env.STAGE_NAME}] Downloading bullseye"
    String tools_url = "${env.JENKINS_URL}job/daos-stack/job/tools/job/master/lastSuccessfulBuild"
    String bullseye_url = "${tools_url}/artifact/bullseyecoverage-linux.tar"
    httpRequest url: bullseye_url, httpMode: 'GET', outputFile: 'bullseye.tar'
}
