// vars/stageMessage.groovy

/**
 * stageMessage.groovy
 *
 * Echo a message for the stage.
 *
 * @param message String containing the text to display from the stage
 */
Map call(String message) {
    echo "[${env.STAGE_NAME}] ${message}"
}
