// vars/durationSeconds.groovy

  /**
   * durationSeconds step method
   *
   * @param startDate, Date object with start time.
   * @param endDate, Date object with end time, default current date time.
   * returns: Duration time in seconds.
   */

int call(Date startDate, Date endDate=null) {
    if (endDate == null) {
        endDate = new Date()
    }
    int delta = (endDate.getTime() - startDate.getTime())/1000
    return delta
}
