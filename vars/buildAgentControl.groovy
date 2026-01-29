/* groovylint-disable DuplicateStringLiteral, DuplicateNumberLiteral
 */
// vars/buildAgentControl.groovy

  /**
   * buildAgentControl step method
   *
   * Control a build agent online status.
   *
   * @param config Map of parameters passed
   *
   * config['action']        'offline' to take a build agent offline.
   *
   * config['message']       Reason for taking build agent offline.
   *
   * config['subject']       Subject for e-mail notification.
   */
void call(Map config = [:]) {
    // E-mail needs a newline after the message before
    // adding build information.
    message = """${config['message']}
"""
    try {
        emailStatusSystem subject: config['subject'],
                          body: message
        buildAgentSystem action: config['action'],
                         reason: config['message']
    } catch (java.lang.NoSuchMethodError e) {
        echo 'Did not find emailStatusSystem or buildAgentSystem'
    }
}
