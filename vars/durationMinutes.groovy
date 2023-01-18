// vars/durationMinutes.groovy

  /**
   * durationMinutes step method
   *
   * This does not create the file, it is so the methods
   * that create / fetch / read the STAGE status file all use
   * the same name and it is properly URLEncoded.
   *
   * It does make sure that the directory for the stage
   * status file exists.
   *
   * @param startDate, Date object with start time.
   * @param endDate, Date object with end time, default current date time.
   * returns: Duration time in minutes.
   */

int call(Date startDate, Date endDate=null) {
    if (endDate == null) {
        endDate = new Date()
    }
    int delta = (endDate.getTime() - startDate.getTime())/1000/60
    return delta
}
