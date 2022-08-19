// vars/sanitizedStageName.groovy

  /**
   * sanitizedStageName step method
   *
   * @param config Map of parameters passed, Unused
   * returns: String with cleaned up filename.
   */

String call(Map config= [:]) {
    return env.STAGE_NAME.replaceAll(~'[ /%+\'"=:;]') { x -> '_' }
}
