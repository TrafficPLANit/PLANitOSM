package org.goplanit.osm.test;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Counts the warnings, and the more severe records, a logger publishes while this is one of its handlers, so a test
 * can check how many were logged without depending on their text
 *
 * @author markr
 */
public class WarningRecorder extends Handler {

  /** number of records counted */
  private int count = 0;

  /**
   * Number of warnings and more severe records counted
   *
   * @return count
   */
  public int getCount() {
    return count;
  }

  /**
   * Start counting from zero again
   */
  public void reset() {
    count = 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void publish(LogRecord record) {
    if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
      ++count;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void flush() {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
  }
}
