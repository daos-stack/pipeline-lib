// vars/stageStatusFilename.groovy

import java.net.URLEncoder

@NonCPS
String urlEncode() {
    return URLEncoder.encode(env.STAGE_NAME, 'UTF-8')
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

String call(Map config= [:]) {
    String directory = 'stage_status'
    if (!fileExists(directory)) {
        fileOperations([folderCreateOperation(directory)])
    }
    return directory + '/' + urlEncode() + ".status"
}
