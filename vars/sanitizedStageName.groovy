// vars/sanitizedStageName.groovy

  /**
   * sanitizedStageName step method
   *
   * @param config Map of parameters passed, Unused
   * returns: String with cleaned up filename.
   */

/* groovylint-disable-next-line UnusedMethodParameter */
String call(Map config= [:]) {
    return env.STAGE_NAME.replaceAll('[ /%+\'"=:;]', '_')
}
