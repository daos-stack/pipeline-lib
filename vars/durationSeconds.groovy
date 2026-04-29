/* groovylint-disable DuplicateNumberLiteral, UnnecessaryGetter */
// vars/durationSeconds.groovy

  /**
   * durationSeconds step method
   *
   * @param startTime, long value with start time in milliseconds.
   * @param endTime, long value with end time in milliseconds, default current time.
   * returns: Duration time in seconds.
   */

int call(long startTime, long endTime=0) {
    long actualEndTime
    if (endTime == 0) {
        actualEndTime = System.currentTimeMillis()
    } else {
        actualEndTime = endTime
    }
    int delta = (actualEndTime - startTime) / 1000
    return delta
}

  /**
   * durationSeconds step method (Date overload)
   *
   * @param startDate, Date object with start time.
   * @param endDate, Date object with end time, default current time.
   * returns: Duration time in seconds.
   */

int call(Date startDate, Date endDate=null) {
    long startTime = startDate.getTime()
    long endTime = endDate ? endDate.getTime() : System.currentTimeMillis()
    int delta = (endTime - startTime) / 1000
    return delta
}
