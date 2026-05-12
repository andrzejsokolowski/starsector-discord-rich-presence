package com.jagrosh.discordipc.entities.pipe;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.Callback;
import com.jagrosh.discordipc.entities.Packet;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * Windows named-pipe implementation using {@link AsynchronousFileChannel}.
 *
 * WHY NOT FileChannel:
 *   FileChannel.read() and FileChannel.write() both synchronise on an internal
 *   positionLock.  The DiscordIPC reading loop holds that lock indefinitely
 *   while blocking for the next Discord message.  Any concurrent write from the
 *   game's main thread (to send a presence update) tries to acquire the same
 *   lock → deadlock → Starsector freezes during save loading.
 *
 * WHY AsynchronousFileChannel:
 *   Uses Windows OVERLAPPED I/O under the hood.  Reads and writes are issued as
 *   independent operations with no shared Java-level lock, so they can proceed
 *   concurrently without interfering.
 *
 * WHY NOT RandomAccessFile:
 *   Starsector's security sandbox instruments (and blocks) calls to
 *   RandomAccessFile.  FileChannel and AsynchronousFileChannel use a different
 *   NIO code path that is not intercepted.
 *
 * PATH NORMALISATION:
 *   DiscordIPC passes \\?\pipe\discord-ipc-N (Windows extended-length prefix).
 *   Java NIO rejects '?' in paths.  We replace \\?\ with \\.\ (device namespace)
 *   which reaches the same named pipe and is accepted by Paths.get().
 */
public class WindowsPipe extends Pipe {

    // Named pipes are sequential — position is always irrelevant and must be 0.
    private static final long PIPE_POS = 0L;

    private final AsynchronousFileChannel channel;

    WindowsPipe(IPCClient ipcClient, HashMap<String, Callback> callbacks, String location)
            throws IOException {
        super(ipcClient, callbacks);

        String normalizedPath = location.replace("\\\\?\\", "\\\\.\\" );

        Set<StandardOpenOption> opts = new HashSet<>();
        opts.add(StandardOpenOption.READ);
        opts.add(StandardOpenOption.WRITE);

        // Use a daemon-thread executor so these I/O threads never block JVM exit.
        channel = AsynchronousFileChannel.open(
                Paths.get(normalizedPath),
                opts,
                Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "DiscordRPC-IO");
                    t.setDaemon(true);
                    return t;
                })
        );
    }

    // ── Pipe contract ────────────────────────────────────────────────────────

    @Override
    public void write(byte[] b) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(b);
        while (buf.hasRemaining()) {
            try {
                channel.write(buf, PIPE_POS).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Pipe write interrupted", e);
            } catch (ExecutionException e) {
                throw new IOException("Pipe write failed: " + e.getCause(), e);
            }
        }
    }

    @Override
    public Packet read() throws IOException, JSONException {
        // Discord IPC frame: [opcode: uint32 LE][length: uint32 LE][json: UTF-8]
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        readFully(header);
        header.flip();
        int opcode = header.getInt();
        int length = header.getInt();

        ByteBuffer body = ByteBuffer.allocate(length);
        readFully(body);
        String json = new String(body.array(), StandardCharsets.UTF_8);

        return new Packet(Packet.OpCode.values()[opcode], new JSONObject(json));
    }

    @Override
    public void close() throws IOException {
        try {
            send(Packet.OpCode.CLOSE, new JSONObject(), null);
        } catch (Exception ignored) {}
        setStatus(PipeStatus.DISCONNECTED);
        channel.close();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void readFully(ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            try {
                int n = channel.read(buf, PIPE_POS).get();
                if (n == -1) throw new IOException("Pipe closed unexpectedly");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Pipe read interrupted", e);
            } catch (ExecutionException e) {
                throw new IOException("Pipe read failed: " + e.getCause(), e);
            }
        }
    }
}
