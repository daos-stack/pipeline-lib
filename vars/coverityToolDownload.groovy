// vars/coverityToolDownload.groovy

/**
 * coverityToolDownload.groovy
 *
 * This is a routine to download the Coverity tool kit.
 * 
 * As the Coverity tool kit requires authentication it is actually
 * downloaded by an optional additional system library.
 *
 * The coverityToolDownloadSystem needs to provided as a shared groovy
 * library local to the running Jenkins for doing Coverity tests.
 *
 * @param config Map of parameters passed
 * @return 0 if successful, -1 if could not find the system step.
 *
 * config['project']        Coverity Project Name.
 * config['tool_path']      Directory to install tool in.
 */

def call(Map config = [:]) {
    try {
        coverityToolDownloadSystem(config)
    } catch (java.lang.NoSuchMethodError e) {
        println('Could not find a coverityToolDownloadSystem step in' +
                ' a shared groovy library')
        return -1
    }
    return 0
}
