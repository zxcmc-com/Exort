package com.zxcmc.exort.chunkloader;

public interface ChunkLoaderAuditFileWriter extends AutoCloseable {
  void write(String line);

  @Override
  void close();

  static ChunkLoaderAuditFileWriter noop() {
    return NoopChunkLoaderAuditFileWriter.INSTANCE;
  }

  final class NoopChunkLoaderAuditFileWriter implements ChunkLoaderAuditFileWriter {
    private static final NoopChunkLoaderAuditFileWriter INSTANCE =
        new NoopChunkLoaderAuditFileWriter();

    private NoopChunkLoaderAuditFileWriter() {}

    @Override
    public void write(String line) {}

    @Override
    public void close() {}
  }
}
