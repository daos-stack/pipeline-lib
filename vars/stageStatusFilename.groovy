/* groovylint-disable ParameterName */
// vars/stageStatusFilename.groovy

import java.net.URLEncoder

@NonCPS
String urlEncodedStageName(String stage_name=env.STAGE_NAME, String postfix='') {
    return URLEncoder.encode(stage_name + (postfix == '' ? '' : '-') + postfix, 'UTF-8')
}

  /**
   * stageStatusFilename step method
   *
   * This does not create the file, it is so the methods
   * that create / fetch / read the STAGE status file all use
   * the same name and it is properly URLEncoded.
   *
   * It does make sure that the directory for the stage
   * status file exists.
   *
   * @param config Map of parameters passed, Unused
   * returns: String with Url Encoded pathname for the stage status.
   */

String call(String stage_name=env.STAGE_NAME, String postfix='') {
    String directory = 'stage_status'
    if (!fileExists(directory)) {
        fileOperations([folderCreateOperation(directory)])
    }
    return directory + '/' + urlEncodedStageName(stage_name, postfix) + '.status'
}
