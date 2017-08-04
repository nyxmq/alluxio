/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.worker.netty;

import com.codahale.metrics.Counter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Represents a read request received from netty channel.
 */
@ThreadSafe
class ReadRequestContext {
  private final ReadRequest mRequest;

  /**
   * Set to true if the packet reader is active. The following invariants must be maintained:
   * 1. If true, there will be at least one more packet (data, eof or error) to be sent to netty.
   * 2. If false, there will be no more packets sent to netty until it is set to true again.
   */
  @GuardedBy("mLock")
  private boolean mPacketReaderActive;
  /**
   * The next pos to queue to the netty buffer. mPosToQueue - mPosToWrite is the bytes that are
   * in netty buffer.
   */
  @GuardedBy("mLock")
  private long mPosToQueue;
  /** The next pos to write to the channel. */
  @GuardedBy("mLock")
  private long mPosToWrite;

  /**
   * mEof, mCancel and mError are the notifications processed by the packet reader thread. They can
   * be set by either the netty I/O thread or the packet reader thread. mError overrides mCancel
   * and mEof, mEof overrides mCancel.
   *
   * These notifications determine 3 ways to complete a read request.
   * 1. mEof: The read request is fulfilled. All the data requested by the client or all the data in
   *    the block/file has been read. The packet reader replies a SUCCESS response when processing
   *    mEof.
   * 2. mCancel: The read request is cancelled by the client. A cancel request is ignored if mEof
   *    is set. The packet reader replies a CANCEL response when processing mCancel.
   *    Note: The client can send a cancel request after the server has sent a SUCCESS response. But
   *    it is not possible for the client to send a CANCEL request after the channel has been
   *    released. So it is impossible for a CANCEL request from one read request to cancel
   *    another read request.
   * 3. mError: mError is set whenever an error occurs. It can be from an exception when reading
   *    packet, or writing packet to netty or the client closes the channel etc. An ERROR response
   *    is optionally sent to the client when packet reader thread process mError. The channel
   *    is closed after this error response is sent.
   *
   * Note: it is guaranteed that only one of SUCCESS and CANCEL responses is sent at most once
   * because the packet reader thread won't be restarted as long as mCancel or mEof is set except
   * when error happens (mError overrides mCancel and mEof).
   */
  @GuardedBy("mLock")
  private boolean mEof;
  @GuardedBy("mLock")
  private boolean mCancel;
  @GuardedBy("mLock")
  private Error mError;

  /** This is set when the SUCCESS or CANCEL response is sent. This is only for sanity check. */
  private volatile boolean mDone;

  private Counter mCounter;

  ReadRequestContext(ReadRequest request) {
    mRequest = request;
    mPosToQueue = 0;
    mPosToWrite = 0;
    mPacketReaderActive = false;
    mEof = false;
    mCancel = false;
    mError = null;
    mDone = false;
  }

  /**
   * @return request
   */
  public ReadRequest getRequest() {
    return mRequest;
  }

  public boolean isPacketReaderActive() {
    return mPacketReaderActive;
  }

  public long getPosToQueue() {
    return mPosToQueue;
  }

  public long getPosToWrite() {
    return mPosToWrite;
  }

  public boolean isEof() {
    return mEof;
  }

  public boolean isCancel() {
    return mCancel;
  }

  @Nullable
  public Error getError() {
    return mError;
  }

  public boolean isDone() {
    return mDone;
  }

  /**
   * @return counter
   */
  @Nullable
  public Counter getCounter() {
    return mCounter;
  }

  public void setPacketReaderActive(boolean packetReaderActive) {
    mPacketReaderActive = packetReaderActive;
  }

  public void setPosToQueue(long posToQueue) {
    mPosToQueue = posToQueue;
  }

  public void setPosToWrite(long posToWrite) {
    mPosToWrite = posToWrite;
  }


  public void setEof(boolean eof) {
    mEof = eof;
  }

  public void setCancel(boolean cancel) {
    mCancel = cancel;
  }

  public void setError(Error error) {
    mError = error;
  }

  public void setDone(boolean done) {
    mDone = done;
  }

  /**
   * @param counter counter to set
   */
  public void setCounter(Counter counter) {
    mCounter = counter;
  }
}
