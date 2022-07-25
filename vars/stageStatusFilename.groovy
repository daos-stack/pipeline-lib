// vars/stageStatusFilename.groovy

import java.net.URLEncoder

@NonCPS
String urlEncode() {
    return URLEncoder.encode(env.STAGE_NAME, 'UTF-8')
}

  /**
   * stageStatusFilename step method
   *
   * @param config Map of parameters passed, Unused
   * returns: String with Url Encoded filename for stage name status.
   */

String call(Map config= [:]) {
    String directory = 'stage_status'
    if (!fileExists(directory)) {
        fileOperations([folderCreateOperation(directory)])
    }
    return directory + '/' + urlEncode() + ".status"
}
