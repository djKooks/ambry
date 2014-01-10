package com.github.ambry.store;

import com.github.ambry.config.StoreConfig;
import com.github.ambry.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The index implementation that is responsible for adding and modifying index entries,
 * recovering an index from the log and commit and recover index to disk . This class
 * is not thread safe and expects the caller to do appropriate synchronization.
 */
public class BlobIndex {
  protected ConcurrentHashMap<StoreKey, BlobIndexValue> index = new ConcurrentHashMap<StoreKey, BlobIndexValue>();
  protected Scheduler scheduler;
  private AtomicLong logEndOffset;
  private BlobJournal indexJournal;
  private File indexFile;
  private static final String indexFileName = "index_current";
  private IndexPersistor persistor;
  private StoreKeyFactory factory;
  private Log log;
  private Logger logger = LoggerFactory.getLogger(getClass());
  public static final Short version = 0;

  public BlobIndex(String datadir,
                   Scheduler scheduler,
                   Log log,
                   StoreConfig config,
                   StoreKeyFactory factory) throws StoreException  {
    try {
      logEndOffset = new AtomicLong(0);
      indexJournal = new BlobJournal();
      this.log = log;
      this.factory = factory;
      // check if file exist and recover from it
      indexFile = new File(datadir, indexFileName);
      persistor = new IndexPersistor();
      persistor.read();
      logger.info("read index from file {}", datadir);

      // start scheduler thread to persist index in the background
      this.scheduler = scheduler;
      this.scheduler.schedule("index persistor",
                              persistor,
                              config.storeDataFlushDelaySeconds + new Random().nextInt(SystemTime.SecsPerMin),
                              config.storeDataFlushIntervalSeconds,
                              TimeUnit.SECONDS);
    }
    catch (StoreException e) {
      // ignore any exception while reading the index file
      // reset to recover from start
      logEndOffset.set(0);
      index.clear();
    }
    catch (Exception e) {
      throw new StoreException("Error while creating index", e, StoreErrorCodes.Index_Creation_Failure);
    }
  }

  public void addToIndex(BlobIndexEntry entry, long fileEndOffset) {
    verifyFileEndOffset(fileEndOffset);
    index.put(entry.getKey(), entry.getValue());
    this.logEndOffset.set(fileEndOffset);
  }

  public void addToIndex(ArrayList<BlobIndexEntry> entries, long fileEndOffset) {
    verifyFileEndOffset(fileEndOffset);
    for (BlobIndexEntry entry : entries) {
      index.put(entry.getKey(), entry.getValue());
    }
    this.logEndOffset.set(fileEndOffset);
  }

  public boolean exists(StoreKey key) {
    return index.containsKey(key);
  }

  public void markAsDeleted(StoreKey id, long fileEndOffset) throws StoreException {
    verifyFileEndOffset(fileEndOffset);
    BlobIndexValue value = index.get(id);
    if (value == null) {
      logger.error("id {} not present in index. marking id as deleted failed", id);
      throw new StoreException("id not present in index : " + id, StoreErrorCodes.ID_Not_Found);
    }
    value.setFlag(BlobIndexValue.Flags.Delete_Index);
    index.put(id, value);
    this.logEndOffset.set(fileEndOffset);
  }

  public void updateTTL(StoreKey id, long ttl, long fileEndOffset) throws StoreException {
    verifyFileEndOffset(fileEndOffset);
    BlobIndexValue value = index.get(id);
    if (value == null) {
      logger.error("id {} not present in index. updating ttl failed", id);
      throw new StoreException("id not present in index : " + id, StoreErrorCodes.ID_Not_Found);
    }
    value.setTimeToLive(ttl);
    index.put(id, value);
    this.logEndOffset.set(fileEndOffset);
  }

  public BlobReadOptions getBlobReadInfo(StoreKey id) throws StoreException {
    BlobIndexValue value = index.get(id);
    if (value == null) {
      logger.error("id {} not present in index. cannot find blob", id);
      throw new StoreException("id not present in index " + id, StoreErrorCodes.ID_Not_Found);
    }
    else if (value.isFlagSet(BlobIndexValue.Flags.Delete_Index)) {
      logger.error("id {} has been deleted", id);
      throw new StoreException("id has been deleted in index " + id, StoreErrorCodes.ID_Deleted);
    }
    else if (value.getTimeToLiveInMs() != BlobIndexValue.TTL_Infinite &&
             SystemTime.getInstance().milliseconds() > value.getTimeToLiveInMs()) {
      logger.error("id {} has expired ttl {}", id, value.getTimeToLiveInMs());
      throw new StoreException("id not present in index " + id, StoreErrorCodes.TTL_Expired);
    }
    return new BlobReadOptions(value.getOffset(), value.getSize(), value.getTimeToLiveInMs());
  }

  public List<StoreKey> findMissingEntries(List<StoreKey> keys) {
    List<StoreKey> missingEntries = new ArrayList<StoreKey>();
    for (StoreKey key : keys) {
      if (!exists(key))
        missingEntries.add(key);
    }
    return missingEntries;
  }

  // TODO need to fix this
  public ArrayList<BlobIndexValue> getIndexEntrySince(long offset) {
    // check journal
    // return complete index
    return null;
  }

  public void close() throws StoreException, IOException {
    persistor.write();
  }

  public long getCurrentEndOffset() {
    return logEndOffset.get();
  }

  private void verifyFileEndOffset(long fileEndOffset) {
    if (this.logEndOffset.get() > fileEndOffset) {
      logger.error("File end offset provided to the index is less than the current end offset. " +
              "logEndOffset {} inputFileEndOffset {}", logEndOffset.get(), fileEndOffset);
      throw new IllegalArgumentException("File end offset provided to the index is less than the current end offset. " +
              "logEndOffset " + logEndOffset.get() + " inputFileEndOffset " + fileEndOffset);
    }
  }

  /**
   * Persists the entire in memory index to a file. It does a safe persistence by writing to a temp file
   * and swapping the contents.
   */
  class IndexPersistor implements Runnable {

    private Object lock = new Object();
    private Short version = 0;
    private int Crc_Size = 8;
    private int Log_End_Offset_Size = 8;

    public void write() throws StoreException, IOException {
      logger.info("writing index to disk for {}", indexFile.getPath());
      // write to temp file and then swap with the existing file
      DataOutputStream writer = null;

      synchronized(lock) {
        try {
          // write to temp file and then swap with the existing file
          File temp = new File(indexFile.getAbsolutePath() + ".tmp");
          FileOutputStream fileStream = new FileOutputStream(temp);
          CrcOutputStream crc = new CrcOutputStream(fileStream);
          writer = new DataOutputStream(crc);

          // before iterating the map, get the current file end pointer
          long fileEndPointer = logEndOffset.get();

          // flush the log to ensure everything till the fileEndPointer is flushed
          log.flush();

          // write the current version
          writer.writeShort(BlobIndex.version);

          // write the entries
          for (Map.Entry<StoreKey, BlobIndexValue> entry : index.entrySet()) {
            writer.write(entry.getKey().toBytes());
            writer.write(entry.getValue().getBytes().array());
          }
          writer.writeLong(fileEndPointer);
          long crcValue = crc.getValue();
          writer.writeLong(crcValue);

          // flush and overwrite old file
          fileStream.getChannel().force(true);
          // replace current index file with temp file atomically
          // TODO how to handle the return type
          temp.renameTo(indexFile);
        }
        catch (IOException e) {
          logger.error("IO error while persisting index to disk {}", indexFile.getAbsoluteFile());
          throw new StoreException("IO error while persisting index to disk " +
                                   indexFile.getAbsolutePath(), e, StoreErrorCodes.IOError);
        }
        finally {
          if (writer != null)
            writer.close();
        }
      }
      logger.info("Completed writing index to file");
    }

    public void run() {
      try {
        write();
      }
      catch (Exception e) {
        logger.info("Error while persisting the index to disk {}", e);
      }
    }

    public void read() throws IOException, StoreException {
      logger.info("Reading index from file {}", indexFile.getPath());
      synchronized(lock) {
        indexFile.createNewFile();
        CrcInputStream crcStream = new CrcInputStream(new FileInputStream(indexFile));
        DataInputStream stream = new DataInputStream(crcStream);
        try {
          short version = stream.readShort();
          switch (version) {
            case 0:
              while (stream.available() > (Crc_Size + Log_End_Offset_Size)) {
                StoreKey key = factory.getStoreKey(stream);
                byte[] value = new byte[BlobIndexValue.Index_Value_Size_In_Bytes];
                stream.read(value);
                BlobIndexValue blobValue = new BlobIndexValue(ByteBuffer.wrap(value));
                index.put(key, blobValue);
              }
              long endOffset = stream.readLong();
              logEndOffset.set(endOffset);
              long crc = crcStream.getValue();
              if (crc != stream.readLong())
                throw new StoreException("Crc check does not match", StoreErrorCodes.Index_Creation_Failure);
              break;
            default:
              throw new StoreException("Invalid version in index file", StoreErrorCodes.Index_Version_Error);
          }
        }
        catch (IOException e) {
          throw new StoreException("IO error while reading from file " +
                  indexFile.getAbsolutePath(), e, StoreErrorCodes.IOError);
        }
        finally {
          stream.close();
        }
      }
    }
  }
}