// vars/Date.groovy

/**
 * Date.groovy
 *
 * Jenkins sandbox-compatible replacement for java.util.Date
 * Uses System.currentTimeMillis() which is allowed in the sandbox
 */

/**
 * Create a new Date object using current time in milliseconds
 * Returns the current time in milliseconds since epoch
 *
 * @return long Current time in milliseconds
 */
long call() {
    return System.currentTimeMillis()
}

/**
 * Create a Date object from a specific time in milliseconds
 *
 * @param timeInMillis Time in milliseconds since epoch
 * @return long Time in milliseconds
 */
long call(long timeInMillis) {
    return timeInMillis
}

/**
 * Get time in milliseconds from a Date value
 * For compatibility with code expecting .getTime()
 *
 * @param dateValue The date value (already in milliseconds)
 * @return long Time in milliseconds
 */
long getTime(long dateValue) {
    return dateValue
}
